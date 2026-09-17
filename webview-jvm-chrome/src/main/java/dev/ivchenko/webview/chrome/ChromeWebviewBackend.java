package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.AbstractWebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.chrome.devtools.DevToolsClient;
import dev.ivchenko.webview.event.LoadEvent;
import dev.ivchenko.webview.event.LoadState;
import dev.ivchenko.webview.exception.ResourceNotFoundException;
import dev.ivchenko.webview.exception.ScriptEvaluationFailedException;
import dev.ivchenko.webview.util.MimeTypeUtil;
import dev.ivchenko.webview.util.ResourceUtil;
import dev.ivchenko.webview.util.ScriptUtil;
import dev.ivchenko.webview.util.ThrowableUtil;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Experimental.</b> A webview that is a real browser window: an installed Chrome, Chromium or Edge in application
 * mode, driven over the DevTools protocol.
 *
 * <p>This is the fallback for a machine whose native engine is missing - WebKitGTK not installed, WebView2 Runtime
 * absent - and it keeps the contract of the native backends: the same window parameters, the same
 * {@code app://}-style resource origin as WebView2 ({@code http://app.localhost/}, answered by request interception
 * before anything reaches the network), the same bridge, the same load events, the browser's own context menu
 * suppressed, and the window title pinned to what the application set rather than to whatever the page's
 * {@code <title>} says.</p>
 *
 * <p>What cannot be mirrored: the window is on screen from the moment it is created, because a browser has no hidden
 * window to show later, and {@link #resizable(boolean)} is remembered but not enforced - the protocol has no way to fix
 * a window's size.</p>
 */
public class ChromeWebviewBackend extends AbstractWebviewBackend {
    /** Where the application's own files appear to the page; the same convention as the WebView2 backend. */
    static final String RESOURCE_ORIGIN = "http://app.localhost/";

    /** The page-side function the bridge posts through; installed with {@code Runtime.addBinding}. */
    private static final String BRIDGE_BINDING = "__webviewChromePost";

    private static final Pattern EDGE_VERSION = Pattern.compile("Edg/([0-9.]+)");

    private final ChromeProcess process;
    private final DevToolsClient devTools;
    private final int windowId;
    private final String executableName;

    private volatile String title;
    private volatile String url = "about:blank";
    private volatile boolean resizable = true;
    private volatile String titleScriptId;

    ChromeWebviewBackend(Path executable, WebviewParameters parameters) {
        super(ChromeDispatcher.instance(), parameters);
        this.title = parameters.title();
        this.executableName = executable.getFileName().toString().toLowerCase(Locale.ROOT);
        this.process = ChromeProcess.launch(executable, parameters);
        try {
            this.devTools = DevToolsClient.connectToPage(this.process.devToolsPort());
            this.devTools.onClose(() -> this.dispatcher().post(this::handleDestroyed));
            this.windowId = this.devTools.call("Browser.getWindowForTarget").getInt("windowId");
            this.subscribe();
            this.devTools.call("Page.enable");
            this.devTools.call("Runtime.enable");
            this.devTools.call("Runtime.addBinding", DevToolsClient.params().add("name", BRIDGE_BINDING));
            this.devTools.call("Fetch.enable", DevToolsClient.params().add("patterns", Json.createArrayBuilder()
                    .add(DevToolsClient.params().add("urlPattern", RESOURCE_ORIGIN + "*"))));
            this.injectOnDocumentStart("document.addEventListener('contextmenu', event => event.preventDefault());");
            this.pinTitle(this.title);
            this.installBridge();
        } catch (RuntimeException | Error e) {
            this.process.close();
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code Browser.getVersion} reports every Chromium-based browser as {@code Chrome/<version>}; the user agent
     * tells Edge apart, and the executable's name tells Chromium apart from Google Chrome.</p>
     */
    @Override
    public String engine() {
        this.checkOpen();
        JsonObject version = this.devTools.call("Browser.getVersion");
        String product = version.getString("product", "Chrome/?");
        String number = product.substring(product.indexOf('/') + 1);
        Matcher edge = EDGE_VERSION.matcher(version.getString("userAgent", ""));
        if (edge.find()) return "Microsoft Edge " + edge.group(1);
        return (this.executableName.contains("chromium") ? "Chromium " : "Google Chrome ") + number;
    }

    @Override
    public String title() {
        this.checkOpen();
        return this.title;
    }

    @Override
    public void title(String title) {
        Objects.requireNonNull(title, "title");
        this.checkOpen();
        this.title = title;
        this.pinTitle(title);
    }

    @Override
    public int width() {
        return this.bounds().getInt("width");
    }

    @Override
    public int height() {
        return this.bounds().getInt("height");
    }

    @Override
    public void size(int width, int height) {
        this.checkOpen();
        this.devTools.call("Browser.setWindowBounds", DevToolsClient.params()
                .add("windowId", this.windowId)
                .add("bounds", DevToolsClient.params().add("width", width).add("height", height)));
    }

    @Override
    public boolean isResizable() {
        this.checkOpen();
        return this.resizable;
    }

    @Override
    public void resizable(boolean resizable) {
        this.checkOpen();
        this.resizable = resizable;
    }

    @Override
    public void navigate(String url) {
        Objects.requireNonNull(url, "url");
        this.checkOpen();
        this.dispatcher().run(() -> this.emitLoad(LoadEvent.of(LoadState.STARTED, url)));
        String errorText = this.devTools.call("Page.navigate", DevToolsClient.params().add("url", url))
                .getString("errorText", null);
        if (errorText != null) {
            this.dispatcher().post(() -> this.emitLoad(LoadEvent.failed(url, errorText)));
        }
    }

    @Override
    public String url() {
        this.checkOpen();
        return this.url;
    }

    @Override
    public void html(String html) {
        Objects.requireNonNull(html, "html");
        this.navigate("data:text/html;charset=utf-8;base64,"
                + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wrapped the same way as on WebView2: the caller's text is evaluated in global scope and the outcome comes
     * back as one tagged string, so a thrown error becomes a {@link ScriptEvaluationFailedException} and every other
     * value its {@code String()} form.</p>
     */
    @Override
    public CompletableFuture<String> eval(String script) {
        Objects.requireNonNull(script, "script");
        if (this.isClosed()) {
            return CompletableFuture.failedFuture(new IllegalStateException("The window is closed"));
        }
        String wrapped = """
                (function () {
                    try { return "S" + String((0, eval)(%s)); }
                    catch (error) { return "E" + (error && error.message !== undefined ? error.message : String(error)); }
                })()""".formatted(ScriptUtil.quote(script));
        JsonObjectBuilder params = DevToolsClient.params().add("expression", wrapped).add("returnByValue", true);
        return this.devTools.send("Runtime.evaluate", params).thenApply(result -> {
            if (result.containsKey("exceptionDetails")) {
                throw new ScriptEvaluationFailedException(
                        result.getJsonObject("exceptionDetails").getString("text", "Script evaluation failed"));
            }
            String tagged = result.getJsonObject("result").getString("value", "");
            if (tagged.isEmpty()) return "undefined";
            if (tagged.charAt(0) == 'E') throw new ScriptEvaluationFailedException(tagged.substring(1));
            return tagged.substring(1);
        });
    }

    @Override
    public void show() {
        this.checkOpen();
    }

    @Override
    public void close() {
        if (this.isClosed()) return;
        this.devTools.send("Browser.close", DevToolsClient.params()).exceptionally(_ -> null);
        this.dispatcher().run(this::handleDestroyed);
    }

    @Override
    protected void injectOnDocumentStart(String script) {
        this.devTools.call("Page.addScriptToEvaluateOnNewDocument", DevToolsClient.params().add("source", script));
    }

    @Override
    protected String bridgeTransportScript() {
        return "(message) => window.%s(message)".formatted(BRIDGE_BINDING);
    }

    @Override
    protected String resourceUri(String path) {
        return RESOURCE_ORIGIN + (path.startsWith("/") ? path.substring(1) : path);
    }

    private void subscribe() {
        this.devTools.on("Page.frameNavigated", params -> {
            JsonObject frame = params.getJsonObject("frame");
            if (frame.containsKey("parentId") || !frame.containsKey("url")) return;
            String committed = frame.getString("url");
            this.url = committed;
            this.dispatcher().post(() -> this.emitLoad(LoadEvent.of(LoadState.COMMITTED, committed)));
        });
        this.devTools.on("Page.loadEventFired",
                _ -> this.dispatcher().post(() -> this.emitLoad(LoadEvent.of(LoadState.FINISHED, this.url))));
        this.devTools.on("Runtime.bindingCalled", params -> {
            if (!BRIDGE_BINDING.equals(params.getString("name", null))) return;
            String message = params.getString("payload", "");
            this.dispatcher().post(() -> this.handleBridgeMessage(message));
        });
        this.devTools.on("Fetch.requestPaused", this::serveResource);
    }

    /**
     * Answers one request for the application's own files out of the classpath. A missing file is reported the way
     * the native backends report it - a failed load naming the path - and answered with a 404 so the browser does not
     * also raise a network error for it.
     */
    private void serveResource(JsonObject request) {
        String uri = request.getJsonObject("request").getString("url", "");
        String path = uri.substring(RESOURCE_ORIGIN.length());
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);

        JsonObjectBuilder response = DevToolsClient.params().add("requestId", request.getString("requestId"));
        try {
            byte[] content = ResourceUtil.read(path);
            response.add("responseCode", 200);
            response.add("responseHeaders", Json.createArrayBuilder().add(DevToolsClient.params()
                    .add("name", "Content-Type")
                    .add("value", MimeTypeUtil.of(path))));
            response.add("body", Base64.getEncoder().encodeToString(content));
        } catch (ResourceNotFoundException e) {
            response.add("responseCode", 404);
            response.add("body", "");
            if ("Document".equals(request.getString("resourceType", null))) {
                this.dispatcher().post(() -> this.emitLoad(LoadEvent.failed(uri, e.getMessage())));
            }
        }
        this.devTools.send("Fetch.fulfillRequest", response).whenComplete((_, failure) -> {
            if (failure != null && !this.devTools.isClosed()) ThrowableUtil.report(failure);
        });
    }

    private void handleDestroyed() {
        if (this.isClosed()) return;
        this.markClosed();
        this.devTools.close();
        this.process.close();
    }

    /**
     * Gives the window a title of its own, which application mode does not have: the title bar shows
     * {@code document.title}, so the real title is pinned to what the application set, while the page keeps seeing its
     * own {@code <title>} through a replacement {@code document.title} accessor. From the page's point of view nothing
     * changed; from the desktop's, the window is named like a native one.
     */
    private void pinTitle(String title) {
        String script = """
                (() => {
                    if (window.__webviewSetTitle) { window.__webviewSetTitle(%1$s); return; }
                    let pinned = %1$s;
                    let pageTitle = "";
                    const native = Object.getOwnPropertyDescriptor(Document.prototype, "title");
                    const pin = () => { if (native.get.call(document) !== pinned) native.set.call(document, pinned); };
                    const capture = () => {
                        const current = native.get.call(document);
                        if (current !== pinned) pageTitle = current;
                        pin();
                    };
                    Object.defineProperty(Document.prototype, "title", {
                        configurable: true,
                        get() { return pageTitle; },
                        set(value) { pageTitle = String(value); pin(); }
                    });
                    new MutationObserver(capture).observe(document, { childList: true, subtree: true, characterData: true });
                    window.__webviewSetTitle = (value) => { pinned = value; pin(); };
                    document.addEventListener("DOMContentLoaded", capture);
                    capture();
                })();""".formatted(ScriptUtil.quote(title));
        String previous = this.titleScriptId;
        if (previous != null) {
            this.devTools.call("Page.removeScriptToEvaluateOnNewDocument",
                    DevToolsClient.params().add("identifier", previous));
        }
        this.titleScriptId = this.devTools
                .call("Page.addScriptToEvaluateOnNewDocument", DevToolsClient.params().add("source", script))
                .getString("identifier");
        this.eval(script);
    }

    private JsonObject bounds() {
        this.checkOpen();
        return this.devTools.call("Browser.getWindowBounds", DevToolsClient.params().add("windowId", this.windowId))
                .getJsonObject("bounds");
    }

    private void checkOpen() {
        if (this.isClosed()) throw new IllegalStateException("The window is closed");
    }
}
