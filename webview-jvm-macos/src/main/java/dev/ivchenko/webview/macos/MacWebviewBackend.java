package dev.ivchenko.webview.macos;

import dev.ivchenko.webview.AbstractWebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.bridge.BridgeProtocol;
import dev.ivchenko.webview.event.LoadEvent;
import dev.ivchenko.webview.event.LoadState;
import dev.ivchenko.webview.exception.ResourceNotFoundException;
import dev.ivchenko.webview.exception.ScriptEvaluationFailedException;
import dev.ivchenko.webview.foreign.CallbackRegistry;
import dev.ivchenko.webview.foreign.NativeLibraries;
import dev.ivchenko.webview.macos.binding.AppKit;
import dev.ivchenko.webview.macos.binding.Foundation;
import dev.ivchenko.webview.macos.binding.ObjC;
import dev.ivchenko.webview.macos.binding.Signatures;
import dev.ivchenko.webview.macos.binding.WebKit;
import dev.ivchenko.webview.util.MimeTypeUtil;
import dev.ivchenko.webview.util.ResourceUtil;
import dev.ivchenko.webview.util.ScriptUtil;
import dev.ivchenko.webview.util.ThrowableUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@code NSWindow} holding a {@code WKWebView}, driven through the Objective-C runtime.
 *
 * <p>Everything WebKit and AppKit tell the backend arrives through one Objective-C class defined at run time,
 * {@code WebviewJvmDelegate}, whose methods are Java upcall stubs: it is the window delegate, the navigation delegate,
 * the script message handler and the URL scheme handler of one window. Each window gets its own instance, and the
 * instance address is the key back to the backend.</p>
 *
 * <p>The application's own files are served under {@code app://local/} by a URL scheme handler, the same scheme
 * WebKitGTK uses, so the same page runs unchanged on both.</p>
 */
public class MacWebviewBackend extends AbstractWebviewBackend {
    private static final Map<Long, MacWebviewBackend> DELEGATES = new ConcurrentHashMap<>();
    private static final CallbackRegistry<PendingEvaluation> PENDING_EVALUATIONS = new CallbackRegistry<>();

    private static final MemorySegment ON_WINDOW_WILL_CLOSE = delegateStub("onWindowWillClose", 1);
    private static final MemorySegment ON_DID_START = delegateStub("onDidStartProvisionalNavigation", 2);
    private static final MemorySegment ON_DID_COMMIT = delegateStub("onDidCommitNavigation", 2);
    private static final MemorySegment ON_DID_FINISH = delegateStub("onDidFinishNavigation", 2);
    private static final MemorySegment ON_DID_FAIL_PROVISIONAL = delegateStub("onDidFailNavigation", 3);
    private static final MemorySegment ON_DID_RECEIVE_MESSAGE = delegateStub("onDidReceiveScriptMessage", 2);
    private static final MemorySegment ON_START_TASK = delegateStub("onStartUrlSchemeTask", 2);
    private static final MemorySegment ON_STOP_TASK = delegateStub("onStopUrlSchemeTask", 2);
    private static final MemorySegment ON_EVALUATION_COMPLETE = NativeLibraries.upcall(
            MethodHandles.lookup(), MacWebviewBackend.class, "onEvaluationComplete",
            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
            Signatures.COMPLETION_BLOCK);

    private static final MemorySegment DELEGATE_CLASS = ObjC.defineClass("WebviewJvmDelegate", ObjC.cls("NSObject"),
            Map.of(
                    "windowWillClose:", new ObjC.MethodStub(ON_WINDOW_WILL_CLOSE, "v@:@"),
                    "webView:didStartProvisionalNavigation:", new ObjC.MethodStub(ON_DID_START, "v@:@@"),
                    "webView:didCommitNavigation:", new ObjC.MethodStub(ON_DID_COMMIT, "v@:@@"),
                    "webView:didFinishNavigation:", new ObjC.MethodStub(ON_DID_FINISH, "v@:@@"),
                    "webView:didFailProvisionalNavigation:withError:",
                    new ObjC.MethodStub(ON_DID_FAIL_PROVISIONAL, "v@:@@@"),
                    "webView:didFailNavigation:withError:", new ObjC.MethodStub(ON_DID_FAIL_PROVISIONAL, "v@:@@@"),
                    "userContentController:didReceiveScriptMessage:",
                    new ObjC.MethodStub(ON_DID_RECEIVE_MESSAGE, "v@:@@"),
                    "webView:startURLSchemeTask:", new ObjC.MethodStub(ON_START_TASK, "v@:@@"),
                    "webView:stopURLSchemeTask:", new ObjC.MethodStub(ON_STOP_TASK, "v@:@@")));

    private volatile MemorySegment window;
    private volatile MemorySegment webView;
    private volatile MemorySegment userContentController;
    private volatile MemorySegment delegate;
    private volatile String loading = "about:blank";
    private volatile String reportedFailure;
    private volatile boolean runningApplication;

    public MacWebviewBackend() {
        this(WebviewParameters.defaults());
    }

    public MacWebviewBackend(WebviewParameters parameters) {
        super(MacDispatcher.instance(), parameters);
        this.dispatcher().run(() -> {
            MemorySegment newDelegate = ObjC.send(ObjC.send(DELEGATE_CLASS, "alloc"), "init");
            DELEGATES.put(newDelegate.address(), this);

            MemorySegment configuration = WebKit.configuration();
            WebKit.setUrlSchemeHandler(configuration, newDelegate, ResourceUtil.SCHEME);
            MemorySegment controller = Foundation.retain(WebKit.userContentController(configuration));
            WebKit.addScriptMessageHandler(controller, newDelegate, BridgeProtocol.CHANNEL);

            MemorySegment newWebView = WebKit.webView(parameters.width(), parameters.height(), configuration);
            Foundation.release(configuration);
            WebKit.setNavigationDelegate(newWebView, newDelegate);

            MemorySegment newWindow = AppKit.window(parameters.width(), parameters.height(), parameters.title());
            AppKit.setContentView(newWindow, newWebView);
            AppKit.setDelegate(newWindow, newDelegate);

            this.delegate = newDelegate;
            this.userContentController = controller;
            this.webView = newWebView;
            this.window = newWindow;
            this.injectOnDocumentStart("document.addEventListener('contextmenu', event => event.preventDefault());");
            this.installBridge();
        });
    }

    @Override
    public String engine() {
        return this.dispatcher().call(() -> "WKWebView " + WebKit.version());
    }

    @Override
    public String title() {
        return this.dispatcher().call(() -> AppKit.title(this.window()));
    }

    @Override
    public void title(String title) {
        Objects.requireNonNull(title, "title");
        this.dispatcher().run(() -> AppKit.setTitle(this.window(), title));
    }

    @Override
    public int width() {
        return this.dispatcher().call(() -> AppKit.contentSize(this.window())[0]);
    }

    @Override
    public int height() {
        return this.dispatcher().call(() -> AppKit.contentSize(this.window())[1]);
    }

    @Override
    public void size(int width, int height) {
        this.dispatcher().run(() -> AppKit.setContentSize(this.window(), width, height));
    }

    @Override
    public boolean isResizable() {
        return this.dispatcher().call(() -> (AppKit.styleMask(this.window()) & AppKit.STYLE_RESIZABLE) != 0);
    }

    @Override
    public void resizable(boolean resizable) {
        this.dispatcher().run(() -> {
            long styleMask = AppKit.styleMask(this.window());
            AppKit.setStyleMask(this.window(), resizable
                    ? styleMask | AppKit.STYLE_RESIZABLE
                    : styleMask & ~AppKit.STYLE_RESIZABLE);
        });
    }

    @Override
    public void navigate(String url) {
        Objects.requireNonNull(url, "url");
        this.dispatcher().run(() -> {
            this.loading = url;
            this.reportedFailure = null;
            WebKit.loadUrl(this.webView(), url);
        });
    }

    @Override
    public String url() {
        return this.dispatcher().call(() -> {
            String url = WebKit.url(this.webView());
            return url == null ? "about:blank" : url;
        });
    }

    @Override
    public void html(String html) {
        Objects.requireNonNull(html, "html");
        this.dispatcher().run(() -> {
            this.reportedFailure = null;
            WebKit.loadHtml(this.webView(), html);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wrapped like the other engines: the caller's text runs in global scope and the outcome comes back as one
     * tagged string, so every value arrives as its {@code String()} form and a thrown error as a
     * {@link ScriptEvaluationFailedException} carrying its message rather than WebKit's generic one.</p>
     */
    @Override
    public CompletableFuture<String> eval(String script) {
        Objects.requireNonNull(script, "script");
        CompletableFuture<String> result = new CompletableFuture<>();
        String wrapped = """
                (function () {
                    try { return "S" + String((0, eval)(%s)); }
                    catch (error) { return "E" + (error && error.message !== undefined ? error.message : String(error)); }
                })()""".formatted(ScriptUtil.quote(script));
        this.dispatcher().post(() -> {
            try {
                Arena arena = Arena.ofAuto();
                long id = PENDING_EVALUATIONS.register(new PendingEvaluation(result, arena));
                WebKit.evaluateJavaScript(this.webView(), wrapped, ObjC.block(arena, ON_EVALUATION_COMPLETE, id));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }

    @Override
    public void show() {
        this.dispatcher().run(() -> {
            AppKit.show(this.window());
            AppKit.activate();
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>On the main thread of a process that has not started its application loop yet - a native image's
     * {@code main} - this is the call that runs the loop, and it returns when the window closes. Everywhere else the
     * loop is already running on the main thread and the caller simply waits.</p>
     */
    @Override
    public void run() {
        MacDispatcher dispatcher = (MacDispatcher) this.dispatcher();
        if (!dispatcher.isDispatchThread() || dispatcher.isApplicationRunning()) {
            super.run();
            return;
        }
        this.show();
        this.runningApplication = true;
        dispatcher.runApplication();
    }

    @Override
    public void close() {
        if (this.isClosed()) return;
        this.dispatcher().run(() -> {
            MemorySegment current = this.window;
            if (!this.isClosed() && current != null) AppKit.close(current);
        });
    }

    @Override
    protected void injectOnDocumentStart(String script) {
        this.dispatcher().run(() -> WebKit.addUserScript(this.alive(this.userContentController), script));
    }

    @Override
    protected String bridgeTransportScript() {
        return "(message) => window.webkit.messageHandlers.%s.postMessage(message)".formatted(BridgeProtocol.CHANNEL);
    }

    private MemorySegment window() {
        return this.alive(this.window);
    }

    private MemorySegment webView() {
        return this.alive(this.webView);
    }

    private MemorySegment alive(MemorySegment handle) {
        if (this.isClosed() || handle == null) throw new IllegalStateException("The window is closed");
        return handle;
    }

    @SuppressWarnings("resource")
    private void handleDestroyed() {
        MemorySegment closingDelegate = this.delegate;
        if (closingDelegate != null) DELEGATES.remove(closingDelegate.address());
        this.markClosed();

        MemorySegment closingWebView = this.webView;
        if (closingWebView != null) WebKit.setNavigationDelegate(closingWebView, MemorySegment.NULL);
        if (this.window != null) AppKit.setDelegate(this.window, MemorySegment.NULL);
        Foundation.release(this.userContentController);
        Foundation.release(closingWebView);
        Foundation.release(this.window);
        Foundation.release(closingDelegate);
        this.window = null;
        this.webView = null;
        this.userContentController = null;
        this.delegate = null;

        if (this.runningApplication) {
            this.runningApplication = false;
            AppKit.stopRunLoop();
        }
    }

    private void handleLoadFailed(MemorySegment error) {
        String url = Foundation.errorFailingUrl(error);
        if (url == null) url = this.loading;
        if (url.equals(this.reportedFailure)) return;
        this.reportedFailure = url;
        this.emitLoad(LoadEvent.failed(url, Foundation.errorDescription(error)));
    }

    /** Serves one {@code app://} request out of the classpath, or fails it naming the missing path. */
    private void serveResource(MemorySegment task) {
        String url = WebKit.taskUrl(task);
        String path = url.substring(url.indexOf("//") + 2);
        path = path.substring(path.indexOf('/') + 1);
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        try {
            WebKit.finishTask(task, url, MimeTypeUtil.of(path), ResourceUtil.read(path));
        } catch (ResourceNotFoundException e) {
            if (url.equals(this.loading)) {
                this.reportedFailure = url;
                this.emitLoad(LoadEvent.failed(url, e.getMessage()));
            }
            WebKit.failTask(task, e.getMessage());
        }
    }

    private record PendingEvaluation(CompletableFuture<String> result, Arena memory) {
    }

    private static MemorySegment delegateStub(String method, int arguments) {
        MethodType type = MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class);
        for (int i = 0; i < arguments; i++) type = type.appendParameterTypes(MemorySegment.class);
        return NativeLibraries.upcall(MethodHandles.lookup(), MacWebviewBackend.class, method, type, switch (arguments) {
            case 1 -> Signatures.DELEGATE_1;
            case 2 -> Signatures.DELEGATE_2;
            default -> Signatures.DELEGATE_3;
        });
    }

    private static MacWebviewBackend backendOf(MemorySegment delegate) {
        return DELEGATES.get(delegate.address());
    }

    // --- WebviewJvmDelegate methods; every one receives self and _cmd first, as Objective-C passes them ---

    @SuppressWarnings({"unused", "resource"})
    private static void onWindowWillClose(MemorySegment self, MemorySegment command, MemorySegment notification) {
        try {
            MacWebviewBackend backend = backendOf(self);
            if (backend != null) backend.handleDestroyed();
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    @SuppressWarnings({"unused", "resource"})
    private static void onDidStartProvisionalNavigation(MemorySegment self, MemorySegment command,
                                                        MemorySegment webView, MemorySegment navigation) {
        try {
            MacWebviewBackend backend = backendOf(self);
            if (backend != null) backend.emitLoad(LoadEvent.of(LoadState.STARTED, backend.url()));
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    @SuppressWarnings({"unused", "resource"})
    private static void onDidCommitNavigation(MemorySegment self, MemorySegment command, MemorySegment webView,
                                              MemorySegment navigation) {
        try {
            MacWebviewBackend backend = backendOf(self);
            if (backend != null) backend.emitLoad(LoadEvent.of(LoadState.COMMITTED, backend.url()));
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    @SuppressWarnings({"unused", "resource"})
    private static void onDidFinishNavigation(MemorySegment self, MemorySegment command, MemorySegment webView,
                                              MemorySegment navigation) {
        try {
            MacWebviewBackend backend = backendOf(self);
            if (backend != null) backend.emitLoad(LoadEvent.of(LoadState.FINISHED, backend.url()));
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    @SuppressWarnings({"unused", "resource"})
    private static void onDidFailNavigation(MemorySegment self, MemorySegment command, MemorySegment webView,
                                            MemorySegment navigation, MemorySegment error) {
        try {
            MacWebviewBackend backend = backendOf(self);
            if (backend != null) backend.handleLoadFailed(error);
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    @SuppressWarnings({"unused", "resource"})
    private static void onDidReceiveScriptMessage(MemorySegment self, MemorySegment command,
                                                  MemorySegment controller, MemorySegment message) {
        try {
            MacWebviewBackend backend = backendOf(self);
            if (backend != null) backend.handleBridgeMessage(WebKit.messageBody(message));
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    @SuppressWarnings({"unused", "resource"})
    private static void onStartUrlSchemeTask(MemorySegment self, MemorySegment command, MemorySegment webView,
                                             MemorySegment task) {
        try {
            MacWebviewBackend backend = backendOf(self);
            if (backend != null) backend.serveResource(task);
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }

    /** Resources are answered in one go inside {@link #onStartUrlSchemeTask}, so there is nothing left to stop. */
    @SuppressWarnings("unused")
    private static void onStopUrlSchemeTask(MemorySegment self, MemorySegment command, MemorySegment webView,
                                            MemorySegment task) {
    }

    @SuppressWarnings("unused")
    private static void onEvaluationComplete(MemorySegment block, MemorySegment result, MemorySegment error) {
        PendingEvaluation pending = PENDING_EVALUATIONS.unregister(ObjC.blockContext(block));
        if (pending == null) return;
        try {
            if (!ObjC.isNull(error)) {
                pending.result().completeExceptionally(
                        new ScriptEvaluationFailedException(Foundation.errorDescription(error)));
                return;
            }
            String tagged = Foundation.string(result);
            if (tagged == null || tagged.isEmpty()) {
                pending.result().complete("undefined");
            } else if (tagged.charAt(0) == 'E') {
                pending.result().completeExceptionally(new ScriptEvaluationFailedException(tagged.substring(1)));
            } else {
                pending.result().complete(tagged.substring(1));
            }
        } catch (Throwable t) {
            pending.result().completeExceptionally(t);
        }
    }
}
