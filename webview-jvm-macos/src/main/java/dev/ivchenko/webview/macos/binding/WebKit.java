package dev.ivchenko.webview.macos.binding;

import dev.ivchenko.webview.foreign.NativeLibraries;
import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** WebKit: {@code WKWebView}, its configuration, user scripts, script messages and URL scheme tasks. */
@UtilityClass
public class WebKit {
    static {
        // Loaded for the classes it registers; nothing is looked up by symbol.
        SymbolLookup _ = NativeLibraries.load("/System/Library/Frameworks/WebKit.framework/WebKit");
    }

    private final Pattern MACOS_RELEASE = Pattern.compile("(\\d+(?:\\.\\d+)+)");
    private final long INJECT_AT_DOCUMENT_START = 0;
    private final long AUTORESIZE_WIDTH_AND_HEIGHT = (1 << 1) | (1 << 4);

    /** {@code +[WKWebViewConfiguration new]}, owned by the caller. */
    public MemorySegment configuration() {
        return ObjC.send(ObjC.cls("WKWebViewConfiguration"), "new");
    }

    public MemorySegment userContentController(MemorySegment configuration) {
        return ObjC.send(configuration, "userContentController");
    }

    /** Routes every {@code scheme://} request of views built from {@code configuration} to {@code handler}. */
    public void setUrlSchemeHandler(MemorySegment configuration, MemorySegment handler, String scheme) {
        ObjC.sendVoid(configuration, "setURLSchemeHandler:forURLScheme:", handler, Foundation.string(scheme));
    }

    /** A view that fills its window and follows its resizing; owned by the caller. */
    public MemorySegment webView(int width, int height, MemorySegment configuration) {
        MemorySegment webView;
        try (Arena arena = Arena.ofConfined()) {
            webView = ObjC.sendWithRect(ObjC.send(ObjC.cls("WKWebView"), "alloc"), "initWithFrame:configuration:",
                    Foundation.rect(arena, 0, 0, width, height), configuration);
        }
        ObjC.sendVoid(webView, "setAutoresizingMask:", AUTORESIZE_WIDTH_AND_HEIGHT);
        return webView;
    }

    public void setNavigationDelegate(MemorySegment webView, MemorySegment delegate) {
        ObjC.sendVoid(webView, "setNavigationDelegate:", delegate);
    }

    /** Opens the {@code name} message channel: {@code window.webkit.messageHandlers.<name>.postMessage()}. */
    public void addScriptMessageHandler(MemorySegment userContentController, MemorySegment handler, String name) {
        ObjC.sendVoid(userContentController, "addScriptMessageHandler:name:", handler, Foundation.string(name));
    }

    /** Runs {@code source} in every frame of every later document, before the document's own scripts. */
    public void addUserScript(MemorySegment userContentController, String source) {
        MemorySegment script = ObjC.send(ObjC.send(ObjC.cls("WKUserScript"), "alloc"),
                "initWithSource:injectionTime:forMainFrameOnly:", Foundation.string(source), INJECT_AT_DOCUMENT_START,
                false);
        ObjC.sendVoid(userContentController, "addUserScript:", script);
        Foundation.release(script);
    }

    public void loadUrl(MemorySegment webView, String url) {
        MemorySegment request = ObjC.send(ObjC.cls("NSURLRequest"), "requestWithURL:", Foundation.url(url));
        MemorySegment _ = ObjC.send(webView, "loadRequest:", request);
    }

    public void loadHtml(MemorySegment webView, String html) {
        MemorySegment _ = ObjC.send(webView, "loadHTMLString:baseURL:", Foundation.string(html), MemorySegment.NULL);
    }

    /** The current URL, or {@code null} before the first load. */
    public String url(MemorySegment webView) {
        MemorySegment url = ObjC.send(webView, "URL");
        return ObjC.isNull(url) ? null : Foundation.urlString(url);
    }

    /** {@code evaluateJavaScript:completionHandler:}; {@code completion} is a block from {@link ObjC#block}. */
    public void evaluateJavaScript(MemorySegment webView, String script, MemorySegment completion) {
        ObjC.sendVoid(webView, "evaluateJavaScript:completionHandler:", Foundation.string(script), completion);
    }

    /** The body of a {@code WKScriptMessage} posted as a string. */
    public String messageBody(MemorySegment message) {
        return Foundation.string(ObjC.send(message, "body"));
    }

    /** The URL a {@code WKURLSchemeTask} asks for. */
    public String taskUrl(MemorySegment task) {
        return Foundation.urlString(ObjC.send(ObjC.send(task, "request"), "URL"));
    }

    /** Answers a scheme task with a complete resource. */
    public void finishTask(MemorySegment task, String url, String mimeType, byte[] content) {
        MemorySegment response = ObjC.send(ObjC.send(ObjC.cls("NSURLResponse"), "alloc"),
                "initWithURL:MIMEType:expectedContentLength:textEncodingName:", Foundation.url(url),
                Foundation.string(mimeType), content.length, MemorySegment.NULL);
        ObjC.sendVoid(task, "didReceiveResponse:", response);
        ObjC.sendVoid(task, "didReceiveData:", Foundation.data(content));
        ObjC.sendVoid(task, "didFinish");
        Foundation.release(response);
    }

    /** Fails a scheme task; WebKit reports it to the navigation delegate as a failed provisional navigation. */
    public void failTask(MemorySegment task, String message) {
        ObjC.sendVoid(task, "didFailWithError:", Foundation.error(404, message));
    }

    /**
     * The version of the WebKit framework actually loaded. Its bundle version is a dotted build number on most
     * releases; where it is a bare number, the macOS release that ships it is the version people recognise.
     */
    public String version() {
        String build = Foundation.bundleVersion(ObjC.cls("WKWebView"));
        if (build != null && build.contains(".")) return build;
        Matcher release = MACOS_RELEASE.matcher(Foundation.operatingSystemVersion());
        return (build == null ? "0" : build) + " macOS " + (release.find() ? release.group(1) : "0");
    }
}
