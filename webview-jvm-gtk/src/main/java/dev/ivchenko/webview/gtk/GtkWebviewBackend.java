package dev.ivchenko.webview.gtk;

import dev.ivchenko.webview.AbstractWebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.bridge.BridgeProtocol;
import dev.ivchenko.webview.event.LoadEvent;
import dev.ivchenko.webview.event.LoadState;
import dev.ivchenko.webview.exception.ResourceNotFoundException;
import dev.ivchenko.webview.foreign.CallbackRegistry;
import dev.ivchenko.webview.foreign.NativeLibraries;
import dev.ivchenko.webview.gtk.binding.GLib;
import dev.ivchenko.webview.gtk.binding.Gtk;
import dev.ivchenko.webview.gtk.binding.Signatures;
import dev.ivchenko.webview.gtk.binding.WebKit;
import dev.ivchenko.webview.util.MimeTypeUtil;
import dev.ivchenko.webview.util.ResourceUtil;
import dev.ivchenko.webview.util.ThrowableUtil;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A webview backed by GTK 3 and WebKitGTK 4.1, bound entirely through the Foreign Function &amp; Memory API - no JNI
 * and no native artifacts of our own.
 *
 * <p>Instances are safe to use from any thread; every call is marshalled onto the shared GTK thread. Native callbacks
 * are static and dispatch through {@link CallbackRegistry}, so a single upcall stub serves every window instead of one
 * stub per instance.</p>
 */
public class GtkWebviewBackend extends AbstractWebviewBackend {
    private static final CallbackRegistry<GtkWebviewBackend> WINDOWS = new CallbackRegistry<>();
    private static final CallbackRegistry<CompletableFuture<String>> PENDING_EVALUATIONS = new CallbackRegistry<>();

    private static final MemorySegment ON_DESTROY = NativeLibraries.upcall(
            MethodHandles.lookup(), GtkWebviewBackend.class, "onDestroy",
            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
            Signatures.WIDGET_CALLBACK);
    private static final MemorySegment ON_LOAD_CHANGED = NativeLibraries.upcall(
            MethodHandles.lookup(), GtkWebviewBackend.class, "onLoadChanged",
            MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class),
            Signatures.LOAD_CHANGED_CALLBACK);
    private static final MemorySegment ON_LOAD_FAILED = NativeLibraries.upcall(
            MethodHandles.lookup(), GtkWebviewBackend.class, "onLoadFailed",
            MethodType.methodType(int.class, MemorySegment.class, int.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class),
            Signatures.LOAD_FAILED_CALLBACK);
    private static final MemorySegment ON_CONTEXT_MENU = NativeLibraries.upcall(
            MethodHandles.lookup(), GtkWebviewBackend.class, "onContextMenu",
            MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class),
            Signatures.CONTEXT_MENU_CALLBACK);
    private static final MemorySegment ON_EVALUATION_READY = NativeLibraries.upcall(
            MethodHandles.lookup(), GtkWebviewBackend.class, "onEvaluationReady",
            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
            Signatures.G_ASYNC_READY_CALLBACK);
    private static final MemorySegment ON_BRIDGE_MESSAGE = NativeLibraries.upcall(
            MethodHandles.lookup(), GtkWebviewBackend.class, "onBridgeMessage",
            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
            Signatures.SCRIPT_MESSAGE_CALLBACK);
    private static final MemorySegment ON_RESOURCE_REQUEST = NativeLibraries.upcall(
            MethodHandles.lookup(), GtkWebviewBackend.class, "onResourceRequest",
            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class),
            Signatures.URI_SCHEME_REQUEST_CALLBACK);

    private final long id;

    private volatile MemorySegment window;
    private volatile MemorySegment webView;
    private volatile MemorySegment userContentManager;

    public GtkWebviewBackend() {
        this(WebviewParameters.defaults());
    }

    public GtkWebviewBackend(WebviewParameters parameters) {
        super(GtkDispatcher.instance(), parameters);
        this.id = WINDOWS.register(this);
        try {
            this.dispatcher().run(() -> {
                WebKit.retainDefaultWebContext();
                WebKit.registerUriScheme(ResourceUtil.SCHEME, ON_RESOURCE_REQUEST);

                MemorySegment newWindow = Gtk.windowNew(Gtk.WINDOW_TOPLEVEL);
                Gtk.windowSetTitle(newWindow, parameters.title());
                Gtk.windowSetDefaultSize(newWindow, parameters.width(), parameters.height());

                MemorySegment userData = CallbackRegistry.userData(this.id);

                // Connect before registering, otherwise early messages race the signal handler.
                MemorySegment manager = WebKit.userContentManagerNew();
                GLib.signalConnect(manager, "script-message-received::" + BridgeProtocol.CHANNEL,
                        ON_BRIDGE_MESSAGE, userData);
                if (!WebKit.registerScriptMessageHandler(manager, BridgeProtocol.CHANNEL)) {
                    throw new IllegalStateException("Message handler '" + BridgeProtocol.CHANNEL + "' already registered");
                }

                MemorySegment newWebView = WebKit.webViewNew(manager);
                Gtk.containerAdd(newWindow, newWebView);

                GLib.signalConnect(newWindow, "destroy", ON_DESTROY, userData);
                GLib.signalConnect(newWebView, "load-changed", ON_LOAD_CHANGED, userData);
                GLib.signalConnect(newWebView, "load-failed", ON_LOAD_FAILED, userData);
                GLib.signalConnect(newWebView, "context-menu", ON_CONTEXT_MENU, userData);

                this.window = newWindow;
                this.webView = newWebView;
                this.userContentManager = manager;
                this.installBridge();
            });
        } catch (RuntimeException | Error e) {
            WINDOWS.remove(this.id);
            throw e;
        }
    }

    @Override
    public String engine() {
        return "WebKitGTK " + WebKit.version();
    }

    @Override
    public String title() {
        return this.dispatcher().call(() -> Gtk.windowGetTitle(this.window()));
    }

    @Override
    public void title(String title) {
        this.dispatcher().run(() -> Gtk.windowSetTitle(this.window(), title));
    }

    @Override
    public int width() {
        return this.dispatcher().call(() -> Gtk.windowGetSize(this.window())[0]);
    }

    @Override
    public int height() {
        return this.dispatcher().call(() -> Gtk.windowGetSize(this.window())[1]);
    }

    @Override
    public void size(int width, int height) {
        this.dispatcher().run(() -> {
            Gtk.windowSetDefaultSize(this.window(), width, height);
            Gtk.windowResize(this.window(), width, height);
        });
    }

    @Override
    public boolean isResizable() {
        return this.dispatcher().call(() -> Gtk.isWindowResizable(this.window()));
    }

    @Override
    public void resizable(boolean resizable) {
        this.dispatcher().run(() -> Gtk.windowSetResizable(this.window(), resizable));
    }

    @Override
    public void navigate(String url) {
        Objects.requireNonNull(url, "url");
        this.dispatcher().run(() -> WebKit.loadUri(this.webView(), url));
    }

    @Override
    public void html(String html) {
        Objects.requireNonNull(html, "html");
        this.dispatcher().run(() -> WebKit.loadHtml(this.webView(), html, null));
    }

    @Override
    public String url() {
        return this.dispatcher().call(() -> WebKit.uri(this.webView()));
    }

    @Override
    public CompletableFuture<String> eval(String script) {
        Objects.requireNonNull(script, "script");
        CompletableFuture<String> result = new CompletableFuture<>();
        long evaluationId = PENDING_EVALUATIONS.register(result);
        this.dispatcher().post(() -> {
            try {
                WebKit.evaluateJavascript(
                        this.webView(), script, ON_EVALUATION_READY, CallbackRegistry.userData(evaluationId));
            } catch (Throwable t) {
                CompletableFuture<String> pending = PENDING_EVALUATIONS.unregister(evaluationId);
                if (pending != null) pending.completeExceptionally(t);
            }
        });
        return result;
    }

    @Override
    protected void injectOnDocumentStart(String script) {
        this.dispatcher().run(() -> WebKit.addUserScript(this.userContentManager(), script));
    }

    @Override
    protected String bridgeTransportScript() {
        return "(message) => window.webkit.messageHandlers.%s.postMessage(message)"
                .formatted(BridgeProtocol.CHANNEL);
    }

    @Override
    public void show() {
        this.dispatcher().run(() -> Gtk.widgetShowAll(this.window()));
    }

    @Override
    public void close() {
        if (this.isClosed()) return;

        // gtk_widget_destroy() emits "destroy" synchronously, which is what completes the close.
        this.dispatcher().run(() -> {
            MemorySegment current = this.window;
            if (!this.isClosed() && current != null) Gtk.widgetDestroy(current);
        });
    }

    private MemorySegment window() {
        return this.alive(this.window);
    }

    private MemorySegment webView() {
        return this.alive(this.webView);
    }

    private MemorySegment userContentManager() {
        return this.alive(this.userContentManager);
    }

    private MemorySegment alive(MemorySegment handle) {
        if (this.isClosed() || handle == null) throw new IllegalStateException("Webview window is closed");
        return handle;
    }

    private void handleDestroyed() {
        this.window = null;
        this.webView = null;
        this.userContentManager = null;
        this.markClosed();
    }

    // --- signal handlers, bound by name from the upcall stubs above; signatures are GTK's ---

    @SuppressWarnings({"unused", "resource"})
    private static void onDestroy(MemorySegment widget, MemorySegment userData) {
        try {
            GtkWebviewBackend backend = WINDOWS.unregister(userData);
            if (backend != null) backend.handleDestroyed();
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    @SuppressWarnings({"unused", "resource"})
    private static void onLoadChanged(MemorySegment webView, int loadEvent, MemorySegment userData) {
        try {
            GtkWebviewBackend backend = WINDOWS.lookup(userData);
            if (backend == null) return;

            LoadState state = switch (loadEvent) {
                case WebKit.LOAD_STARTED -> LoadState.STARTED;
                case WebKit.LOAD_REDIRECTED -> LoadState.REDIRECTED;
                case WebKit.LOAD_COMMITTED -> LoadState.COMMITTED;
                case WebKit.LOAD_FINISHED -> LoadState.FINISHED;
                default -> null;
            };
            if (state != null) backend.emitLoad(LoadEvent.of(state, WebKit.uri(webView)));
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    @SuppressWarnings({"unused", "resource"})
    private static int onLoadFailed(MemorySegment webView, int loadEvent, MemorySegment failingUri,
                                    MemorySegment error, MemorySegment userData) {
        try {
            GtkWebviewBackend backend = WINDOWS.lookup(userData);
            if (backend != null) {
                backend.emitLoad(LoadEvent.failed(NativeLibraries.string(failingUri), GLib.errorMessage(error)));
            }
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
        return 0; // FALSE: let WebKit render its own error page
    }

    /**
     * The engine's own context menu - "Reload", "View Source", "Inspect Element" - has no place in a desktop
     * application. Returning {@code TRUE} suppresses it; a page may still handle {@code contextmenu} itself.
     */
    @SuppressWarnings("unused")
    private static int onContextMenu(MemorySegment webView, MemorySegment menu, MemorySegment event,
                                     MemorySegment hitTest, MemorySegment userData) {
        return 1;
    }

    @SuppressWarnings("unused")
    private static void onEvaluationReady(MemorySegment source, MemorySegment result, MemorySegment userData) {
        CompletableFuture<String> pending = PENDING_EVALUATIONS.unregister(userData);
        if (pending == null) return;

        try {
            pending.complete(WebKit.evaluateJavascriptFinish(source, result));
        } catch (Throwable t) {
            pending.completeExceptionally(t);
        }
    }

    @SuppressWarnings({"unused", "resource"})
    private static void onBridgeMessage(MemorySegment manager, MemorySegment javascriptResult,
                                        MemorySegment userData) {
        try {
            GtkWebviewBackend backend = WINDOWS.lookup(userData);
            if (backend == null) return;

            backend.handleBridgeMessage(WebKit.scriptMessageText(javascriptResult));
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    /**
     * Serves one {@code app://} request out of the classpath.
     *
     * <p>Not tied to a window: the scheme is registered on the process-wide default web context, so a single handler
     * answers for every webview.</p>
     */
    @SuppressWarnings("unused")
    private static void onResourceRequest(MemorySegment request, MemorySegment userData) {
        String path = "";
        try {
            path = WebKit.uriSchemeRequestPath(request);
            String resource = path.startsWith("/") ? path.substring(1) : path;

            byte[] content = ResourceUtil.read(resource);
            MemorySegment buffer = GLib.copyToNative(content);
            MemorySegment stream = GLib.memoryInputStream(buffer, content.length);
            try {
                WebKit.uriSchemeRequestFinish(request, stream, content.length, MimeTypeUtil.of(resource));
            } finally {
                GLib.unref(stream);
            }
        } catch (ResourceNotFoundException e) {
            WebKit.uriSchemeRequestFinishError(request, GLib.error("webview-jvm", 404, e.getMessage()));
        } catch (Throwable t) {
            ThrowableUtil.report(t);
            WebKit.uriSchemeRequestFinishError(request,
                    GLib.error("webview-jvm", 500, "Could not serve " + path));
        }
    }
}
