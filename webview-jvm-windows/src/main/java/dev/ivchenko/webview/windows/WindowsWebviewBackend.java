package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.AbstractWebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.event.LoadEvent;
import dev.ivchenko.webview.event.LoadState;
import dev.ivchenko.webview.exception.ResourceNotFoundException;
import dev.ivchenko.webview.exception.ScriptEvaluationFailedException;
import dev.ivchenko.webview.foreign.CallbackRegistry;
import dev.ivchenko.webview.foreign.NativeLibraries;
import dev.ivchenko.webview.util.MimeTypeUtil;
import dev.ivchenko.webview.util.ResourceUtil;
import dev.ivchenko.webview.util.ScriptUtil;
import dev.ivchenko.webview.util.ThrowableUtil;
import dev.ivchenko.webview.windows.binding.Com;
import dev.ivchenko.webview.windows.binding.ComCallback;
import dev.ivchenko.webview.windows.binding.Shlwapi;
import dev.ivchenko.webview.windows.binding.Signatures;
import dev.ivchenko.webview.windows.binding.User32;
import dev.ivchenko.webview.windows.binding.WebView2;
import dev.ivchenko.webview.windows.binding.Wide;
import dev.ivchenko.webview.windows.exception.ComCallFailedException;
import dev.ivchenko.webview.windows.util.JsonStringUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A webview backed by a Win32 window and WebView2, bound entirely through the Foreign Function &amp; Memory API - no
 * JNI, no {@code WebView2Loader.dll}, no native artifacts of our own.
 *
 * <p>Instances are safe to use from any thread; every call is marshalled onto the shared UI thread, which is also the
 * COM apartment every WebView2 callback arrives on. The window procedure is static and finds its backend through the
 * window's {@code GWLP_USERDATA} slot, so one upcall stub serves every window.</p>
 *
 * <p>WebView2 has no custom URI schemes, so classpath resources are served under {@code http://app.localhost/}:
 * requests to that host are intercepted before they reach the network and answered from the jar, and {@code .localhost}
 * is a secure context in Chromium.</p>
 */
public class WindowsWebviewBackend extends AbstractWebviewBackend {
    private static final String WINDOW_CLASS = "webview-jvm";
    private static final String RESOURCE_ORIGIN = "http://app.localhost/";
    private static final long CREATION_TIMEOUT_SECONDS = 60;

    private static final CallbackRegistry<WindowsWebviewBackend> WINDOWS = new CallbackRegistry<>();

    private static final MemorySegment WINDOW_PROC = NativeLibraries.upcall(
            MethodHandles.lookup(), WindowsWebviewBackend.class, "windowProc",
            MethodType.methodType(long.class, MemorySegment.class, int.class, long.class, long.class),
            Signatures.LONG_POINTER_INT_LONG_LONG);

    private static volatile boolean windowClassRegistered;

    private final long id;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    private volatile MemorySegment hwnd;
    private volatile MemorySegment environment;
    private volatile MemorySegment controller;
    private volatile MemorySegment webView;

    public WindowsWebviewBackend() {
        this(WebviewParameters.defaults());
    }

    public WindowsWebviewBackend(WebviewParameters parameters) {
        super(WindowsDispatcher.instance(), parameters);
        if (this.dispatcher().isDispatchThread()) {
            throw new IllegalStateException("Cannot create a window from the UI thread: creation pumps messages");
        }
        this.id = WINDOWS.register(this);
        try {
            this.dispatcher().run(() -> {
                registerWindowClass();
                MemorySegment window = User32.createWindow(WINDOW_CLASS, parameters.title(),
                        parameters.width(), parameters.height());
                User32.userData(window, this.id);
                this.hwnd = window;
                User32.resizeClient(window, parameters.width(), parameters.height());

                MemorySegment handler = ComCallback.completion(WebView2.IID_ENVIRONMENT_COMPLETED,
                        this::onEnvironmentCreated);
                WebView2.createEnvironment(userDataFolder().toString(), handler);
                Com.release(handler);
            });
            this.ready.get(CREATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            WINDOWS.remove(this.id);
            this.dispatcher().run(this::destroyQuietly);
            throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException("WebView2 creation failed", e);
        }
    }

    @Override
    public String engine() {
        return this.dispatcher().call(() -> "WebView2 " + WebView2.browserVersion(this.alive(this.environment)));
    }

    @Override
    public String title() {
        return this.dispatcher().call(() -> User32.title(this.window()));
    }

    @Override
    public void title(String title) {
        this.dispatcher().run(() -> User32.setTitle(this.window(), title));
    }

    @Override
    public int width() {
        return this.dispatcher().call(() -> User32.clientSize(this.window())[0]);
    }

    @Override
    public int height() {
        return this.dispatcher().call(() -> User32.clientSize(this.window())[1]);
    }

    @Override
    public void size(int width, int height) {
        this.dispatcher().run(() -> User32.resizeClient(this.window(), width, height));
    }

    @Override
    public boolean isResizable() {
        return this.dispatcher().call(() -> (User32.style(this.window()) & User32.WS_THICKFRAME) != 0);
    }

    @Override
    public void resizable(boolean resizable) {
        this.dispatcher().run(() -> {
            long style = User32.style(this.window());
            long resizing = User32.WS_THICKFRAME | User32.WS_MAXIMIZEBOX;
            User32.style(this.window(), resizable ? style | resizing : style & ~resizing);
        });
    }

    @Override
    public void navigate(String url) {
        Objects.requireNonNull(url, "url");
        this.dispatcher().run(() -> WebView2.navigate(this.view(), url));
    }

    @Override
    public String url() {
        return this.dispatcher().call(() -> {
            String source = WebView2.source(this.view());
            return source == null || source.isEmpty() || "about:blank".equals(source) ? null : source;
        });
    }

    @Override
    public void html(String html) {
        Objects.requireNonNull(html, "html");
        this.dispatcher().run(() -> WebView2.navigateToString(this.view(), html));
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code ExecuteScript} reports a thrown exception as a {@code null} result rather than a failure, and
     * serialises everything as JSON. The script is therefore wrapped: it evaluates the caller's text in global scope,
     * catches, and returns a single string tagged with its outcome, which the completion decodes back into a value or a
     * {@link ScriptEvaluationFailedException}.</p>
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
                MemorySegment handler = ComCallback.completion(WebView2.IID_EXECUTE_SCRIPT_COMPLETED,
                        (hresult, json) -> completeEvaluation(result, hresult, Wide.read(json)));
                WebView2.executeScript(this.view(), wrapped, handler);
                Com.release(handler);
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result;
    }

    @Override
    protected void injectOnDocumentStart(String script) {
        this.dispatcher().run(() -> {
            MemorySegment handler = ComCallback.completion(WebView2.IID_ADD_SCRIPT_COMPLETED, (_, _) -> { });
            WebView2.addScriptToExecuteOnDocumentCreated(this.view(), script, handler);
            Com.release(handler);
        });
    }

    @Override
    protected String bridgeTransportScript() {
        return "(message) => window.chrome.webview.postMessage(message)";
    }

    @Override
    protected String resourceUri(String path) {
        return RESOURCE_ORIGIN + (path.startsWith("/") ? path.substring(1) : path);
    }

    @Override
    public void show() {
        this.dispatcher().run(() -> {
            User32.show(this.window());
            WebView2.setVisible(this.controller, true);
        });
    }

    @Override
    public void close() {
        if (this.isClosed()) return;

        // DestroyWindow sends WM_DESTROY synchronously, which is what completes the close.
        this.dispatcher().run(() -> {
            MemorySegment current = this.hwnd;
            if (!this.isClosed() && current != null) User32.destroy(current);
        });
    }

    private void onEnvironmentCreated(int hresult, MemorySegment createdEnvironment) {
        if (hresult < 0) {
            this.ready.completeExceptionally(new ComCallFailedException("WebView2 environment creation", hresult));
            return;
        }
        Com.addRef(createdEnvironment);
        this.environment = createdEnvironment;
        try {
            MemorySegment handler = ComCallback.completion(WebView2.IID_CONTROLLER_COMPLETED, this::onControllerCreated);
            WebView2.createController(createdEnvironment, this.hwnd, handler);
            Com.release(handler);
        } catch (RuntimeException e) {
            this.ready.completeExceptionally(e);
        }
    }

    private void onControllerCreated(int hresult, MemorySegment createdController) {
        if (hresult < 0) {
            this.ready.completeExceptionally(new ComCallFailedException("WebView2 controller creation", hresult));
            return;
        }
        try {
            Com.addRef(createdController);
            this.controller = createdController;
            this.webView = WebView2.coreWebView2(createdController);
            this.fitWebView();
            WebView2.disableDefaultContextMenu(this.webView);

            this.subscribe(WebView2::onNavigationStarting, WebView2.IID_NAVIGATION_STARTING, (_, arguments) ->
                    this.emitLoad(LoadEvent.of(LoadState.STARTED, WebView2.navigationStartingUri(arguments))));
            this.subscribe(WebView2::onContentLoading, WebView2.IID_CONTENT_LOADING, (sender, _) ->
                    this.emitLoad(LoadEvent.of(LoadState.COMMITTED, WebView2.source(sender))));
            this.subscribe(WebView2::onNavigationCompleted, WebView2.IID_NAVIGATION_COMPLETED, (sender, arguments) -> {
                String uri = WebView2.source(sender);
                this.emitLoad(WebView2.isNavigationSuccessful(arguments)
                        ? LoadEvent.of(LoadState.FINISHED, uri)
                        : LoadEvent.failed(uri, WebView2.navigationErrorStatus(arguments)));
            });
            this.subscribe(WebView2::onWebMessageReceived, WebView2.IID_WEB_MESSAGE_RECEIVED, (_, arguments) -> {
                String message = WebView2.webMessageAsString(arguments);
                if (message != null) this.handleBridgeMessage(message);
            });
            WebView2.addWebResourceRequestedFilter(this.webView, RESOURCE_ORIGIN + "*");
            this.subscribe(WebView2::onWebResourceRequested, WebView2.IID_WEB_RESOURCE_REQUESTED,
                    (_, arguments) -> this.serveResource(arguments));

            this.installBridge();
            this.ready.complete(null);
        } catch (RuntimeException e) {
            this.ready.completeExceptionally(e);
        }
    }

    private void subscribe(EventRegistration registration, MemorySegment iid, ComCallback.Event handler) {
        MemorySegment callback = ComCallback.event(iid, handler);
        registration.add(this.webView, callback);
        Com.release(callback);
    }

    /** Answers one {@code http://app.localhost/} request out of the classpath. */
    private void serveResource(MemorySegment arguments) {
        String uri = WebView2.requestedUri(arguments);
        String path = uri.startsWith(RESOURCE_ORIGIN) ? uri.substring(RESOURCE_ORIGIN.length()) : uri;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);

        MemorySegment response;
        try {
            byte[] content = ResourceUtil.read(path);
            MemorySegment stream = Shlwapi.memoryStream(content);
            try {
                response = WebView2.createResponse(this.environment, stream, 200, "OK",
                        "Content-Type: " + MimeTypeUtil.of(path));
            } finally {
                Com.release(stream);
            }
        } catch (ResourceNotFoundException e) {
            response = WebView2.createResponse(this.environment, MemorySegment.NULL, 404, "Not Found", "");
            if (WebView2.isDocumentRequest(arguments)) this.emitLoad(LoadEvent.failed(uri, e.getMessage()));
        }
        try {
            WebView2.respond(arguments, response);
        } finally {
            Com.release(response);
        }
    }

    private static void completeEvaluation(CompletableFuture<String> result, int hresult, String json) {
        if (hresult < 0) {
            result.completeExceptionally(new ComCallFailedException("ExecuteScript", hresult));
            return;
        }
        String tagged = JsonStringUtil.decode(json);
        if (tagged == null || tagged.isEmpty()) {
            result.complete("undefined");
        } else if (tagged.charAt(0) == 'E') {
            result.completeExceptionally(new ScriptEvaluationFailedException(tagged.substring(1)));
        } else {
            result.complete(tagged.substring(1));
        }
    }

    private void fitWebView() {
        int[] size = User32.clientSize(this.hwnd);
        WebView2.setBounds(this.controller, size[0], size[1]);
    }

    private MemorySegment window() {
        return this.alive(this.hwnd);
    }

    private MemorySegment view() {
        return this.alive(this.webView);
    }

    private MemorySegment alive(MemorySegment handle) {
        if (this.isClosed() || handle == null) throw new IllegalStateException("Webview window is closed");
        return handle;
    }

    private void handleDestroyed() {
        MemorySegment closingController = this.controller;
        MemorySegment closingView = this.webView;
        this.hwnd = null;
        this.webView = null;
        this.controller = null;
        this.markClosed();
        if (closingController != null) {
            try {
                WebView2.close(closingController);
            } catch (RuntimeException e) {
                ThrowableUtil.report(e);
            }
            Com.release(closingController);
        }
        Com.release(closingView);
    }

    private void destroyQuietly() {
        MemorySegment current = this.hwnd;
        if (current != null && !this.isClosed()) User32.destroy(current);
    }

    private static Path userDataFolder() {
        String local = System.getenv("LOCALAPPDATA");
        Path folder = Path.of(local != null ? local : System.getProperty("java.io.tmpdir"), "webview-jvm", "WebView2");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the WebView2 user data folder " + folder, e);
        }
        return folder;
    }

    private static synchronized void registerWindowClass() {
        if (windowClassRegistered) return;
        User32.registerClass(WINDOW_CLASS, WINDOW_PROC);
        windowClassRegistered = true;
    }

    /** {@code WNDPROC} of every window this backend creates; must never let a throwable unwind into user32. */
    @SuppressWarnings({"unused", "resource"})
    private static long windowProc(MemorySegment hwnd, int message, long wParam, long lParam) {
        try {
            WindowsWebviewBackend backend = WINDOWS.lookup(User32.userData(hwnd));
            if (backend != null) {
                if (message == User32.WM_SIZE && backend.controller != null) {
                    backend.fitWebView();
                } else if (message == User32.WM_DESTROY) {
                    WINDOWS.unregister(backend.id);
                    backend.handleDestroyed();
                }
            }
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
        return User32.defWindowProc(hwnd, message, wParam, lParam);
    }

    /** One of the {@code add_*} methods of {@code ICoreWebView2}. */
    @FunctionalInterface
    private interface EventRegistration {
        void add(MemorySegment webView, MemorySegment handler);
    }
}
