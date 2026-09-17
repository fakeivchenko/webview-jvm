package dev.ivchenko.webview.gtk.binding;

import dev.ivchenko.webview.exception.ScriptEvaluationFailedException;
import dev.ivchenko.webview.foreign.NativeLibraries;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bindings to WebKitGTK 4.1 (the GTK 3 flavour) and the JavaScriptCore value API. */
@UtilityClass
public class WebKit {
    private final SymbolLookup WEBKIT =
            NativeLibraries.load("libwebkit2gtk-4.1.so.0", "libwebkit2gtk-4.1.so");
    private final SymbolLookup JSC =
            NativeLibraries.load("libjavascriptcoregtk-4.1.so.0", "libjavascriptcoregtk-4.1.so");

    /** {@code WebKitLoadEvent} */
    public final int LOAD_STARTED = 0;
    public final int LOAD_REDIRECTED = 1;
    public final int LOAD_COMMITTED = 2;
    public final int LOAD_FINISHED = 3;

    /** {@code WEBKIT_USER_CONTENT_INJECT_ALL_FRAMES} */
    private final int INJECT_ALL_FRAMES = 0;

    /** {@code WEBKIT_USER_SCRIPT_INJECT_AT_DOCUMENT_START} */
    private final int INJECT_AT_DOCUMENT_START = 0;

    private final MethodHandle WEB_VIEW_NEW_WITH_USER_CONTENT_MANAGER = NativeLibraries.downcall(
            WEBKIT, "webkit_web_view_new_with_user_content_manager", Signatures.POINTER_POINTER);
    private final MethodHandle WEB_VIEW_LOAD_URI =
            NativeLibraries.downcall(WEBKIT, "webkit_web_view_load_uri", Signatures.VOID_POINTER_POINTER);
    private final MethodHandle WEB_VIEW_LOAD_HTML =
            NativeLibraries.downcall(WEBKIT, "webkit_web_view_load_html", Signatures.VOID_POINTER_POINTER_POINTER);
    private final MethodHandle WEB_VIEW_GET_URI =
            NativeLibraries.downcall(WEBKIT, "webkit_web_view_get_uri", Signatures.POINTER_POINTER);
    private final MethodHandle GET_MAJOR_VERSION =
            NativeLibraries.downcall(WEBKIT, "webkit_get_major_version", Signatures.INT_VOID);
    private final MethodHandle GET_MINOR_VERSION =
            NativeLibraries.downcall(WEBKIT, "webkit_get_minor_version", Signatures.INT_VOID);
    private final MethodHandle GET_MICRO_VERSION =
            NativeLibraries.downcall(WEBKIT, "webkit_get_micro_version", Signatures.INT_VOID);
    private final MethodHandle EVALUATE_JAVASCRIPT = NativeLibraries.downcall(
            WEBKIT, "webkit_web_view_evaluate_javascript", Signatures.WEBKIT_EVALUATE_JAVASCRIPT);
    private final MethodHandle EVALUATE_JAVASCRIPT_FINISH = NativeLibraries.downcall(
            WEBKIT, "webkit_web_view_evaluate_javascript_finish", Signatures.POINTER_POINTER_POINTER_POINTER);
    private final MethodHandle WEB_CONTEXT_GET_DEFAULT =
            NativeLibraries.downcall(WEBKIT, "webkit_web_context_get_default", Signatures.POINTER_VOID);
    private final MethodHandle USER_CONTENT_MANAGER_NEW =
            NativeLibraries.downcall(WEBKIT, "webkit_user_content_manager_new", Signatures.POINTER_VOID);
    private final MethodHandle REGISTER_SCRIPT_MESSAGE_HANDLER = NativeLibraries.downcall(
            WEBKIT, "webkit_user_content_manager_register_script_message_handler",
            Signatures.INT_POINTER_POINTER);
    private final MethodHandle USER_CONTENT_MANAGER_ADD_SCRIPT = NativeLibraries.downcall(
            WEBKIT, "webkit_user_content_manager_add_script", Signatures.VOID_POINTER_POINTER);
    private final MethodHandle USER_SCRIPT_NEW =
            NativeLibraries.downcall(WEBKIT, "webkit_user_script_new", Signatures.WEBKIT_USER_SCRIPT_NEW);
    private final MethodHandle USER_SCRIPT_UNREF =
            NativeLibraries.downcall(WEBKIT, "webkit_user_script_unref", Signatures.VOID_POINTER);
    private final MethodHandle JAVASCRIPT_RESULT_GET_JS_VALUE = NativeLibraries.downcall(
            WEBKIT, "webkit_javascript_result_get_js_value", Signatures.POINTER_POINTER);
    private final MethodHandle REGISTER_URI_SCHEME = NativeLibraries.downcall(
            WEBKIT, "webkit_web_context_register_uri_scheme", Signatures.VOID_POINTER_X5);
    private final MethodHandle URI_SCHEME_REQUEST_GET_PATH = NativeLibraries.downcall(
            WEBKIT, "webkit_uri_scheme_request_get_path", Signatures.POINTER_POINTER);
    private final MethodHandle URI_SCHEME_REQUEST_FINISH = NativeLibraries.downcall(
            WEBKIT, "webkit_uri_scheme_request_finish", Signatures.VOID_POINTER_POINTER_LONG_POINTER);
    private final MethodHandle URI_SCHEME_REQUEST_FINISH_ERROR = NativeLibraries.downcall(
            WEBKIT, "webkit_uri_scheme_request_finish_error", Signatures.VOID_POINTER_POINTER);
    private final MethodHandle VALUE_TO_STRING =
            NativeLibraries.downcall(JSC, "jsc_value_to_string", Signatures.POINTER_POINTER);

    private final AtomicBoolean CONTEXT_RETAINED = new AtomicBoolean();
    private final AtomicBoolean SCHEME_REGISTERED = new AtomicBoolean();

    /**
     * Takes one permanent reference to the default {@code WebKitWebContext}.
     *
     * <p>WebKit installs an {@code atexit} handler that unrefs the default context. If the last web view has already
     * been destroyed by then, that drops the refcount to zero and WebKit aborts (SIGABRT) while disposing the website
     * data manager. The extra reference keeps the singleton alive for the lifetime of the process, which is where it
     * belongs anyway. Must be called on the GTK thread.</p>
     */
    @SneakyThrows
    public void retainDefaultWebContext() {
        if (!CONTEXT_RETAINED.compareAndSet(false, true)) return;

        MemorySegment context = (MemorySegment) WEB_CONTEXT_GET_DEFAULT.invokeExact();
        if (!context.equals(MemorySegment.NULL)) GLib.ref(context);
    }

    /**
     * Teaches the default web context to serve {@code scheme://}, dispatching every request to {@code callback}. A
     * scheme can only be registered once per context, hence the latch.
     */
    @SneakyThrows
    public void registerUriScheme(String scheme, MemorySegment callback) {
        if (!SCHEME_REGISTERED.compareAndSet(false, true)) return;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment context = (MemorySegment) WEB_CONTEXT_GET_DEFAULT.invokeExact();
            REGISTER_URI_SCHEME.invokeExact(context, arena.allocateFrom(scheme), callback,
                    MemorySegment.NULL, MemorySegment.NULL);
        }
    }

    /** {@code webkit_user_content_manager_new}: the holder for user scripts and message channels. */
    @SneakyThrows
    public MemorySegment userContentManagerNew() {
        return (MemorySegment) USER_CONTENT_MANAGER_NEW.invokeExact();
    }

    /**
     * Opens the {@code name} message channel, so the page can reach the host through
     * {@code window.webkit.messageHandlers.<name>.postMessage()}.
     */
    @SneakyThrows
    public boolean registerScriptMessageHandler(MemorySegment userContentManager, String name) {
        try (Arena arena = Arena.ofConfined()) {
            return (int) REGISTER_SCRIPT_MESSAGE_HANDLER
                    .invokeExact(userContentManager, arena.allocateFrom(name)) != 0;
        }
    }

    /** Arranges for {@code source} to run in every frame, before the document's own scripts. */
    @SneakyThrows
    public void addUserScript(MemorySegment userContentManager, String source) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment script = (MemorySegment) USER_SCRIPT_NEW.invokeExact(
                    arena.allocateFrom(source),
                    INJECT_ALL_FRAMES,
                    INJECT_AT_DOCUMENT_START,
                    MemorySegment.NULL,  // allow_list: every URL
                    MemorySegment.NULL); // block_list
            USER_CONTENT_MANAGER_ADD_SCRIPT.invokeExact(userContentManager, script);
            USER_SCRIPT_UNREF.invokeExact(script);
        }
    }

    /** Reads the string a page posted through a message channel. */
    @SneakyThrows
    public String scriptMessageText(MemorySegment javascriptResult) {
        // get_js_value is transfer-none: the result owns the value, so it must not be unreffed.
        MemorySegment value = (MemorySegment) JAVASCRIPT_RESULT_GET_JS_VALUE.invokeExact(javascriptResult);
        return GLib.takeString((MemorySegment) VALUE_TO_STRING.invokeExact(value));
    }

    /** {@code webkit_web_view_new_with_user_content_manager}; the widget is floating. */
    @SneakyThrows
    public MemorySegment webViewNew(MemorySegment userContentManager) {
        return (MemorySegment) WEB_VIEW_NEW_WITH_USER_CONTENT_MANAGER.invokeExact(userContentManager);
    }

    /** {@code webkit_web_view_load_uri}. */
    @SneakyThrows
    public void loadUri(MemorySegment webView, String uri) {
        try (Arena arena = Arena.ofConfined()) {
            WEB_VIEW_LOAD_URI.invokeExact(webView, arena.allocateFrom(uri));
        }
    }

    /** {@code webkit_web_view_load_html}; a {@code null} base URI leaves relative links unresolvable. */
    @SneakyThrows
    public void loadHtml(MemorySegment webView, String html, String baseUri) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment base = baseUri == null ? MemorySegment.NULL : arena.allocateFrom(baseUri);
            WEB_VIEW_LOAD_HTML.invokeExact(webView, arena.allocateFrom(html), base);
        }
    }

    /** {@code webkit_web_view_get_uri}: the URI being shown, or {@code null} before any load. */
    @SneakyThrows
    public String uri(MemorySegment webView) {
        return NativeLibraries.string((MemorySegment) WEB_VIEW_GET_URI.invokeExact(webView));
    }

    /** The version of the WebKitGTK library actually loaded, as {@code major.minor.micro}. */
    @SneakyThrows
    public String version() {
        return "%d.%d.%d".formatted((int) GET_MAJOR_VERSION.invokeExact(), (int) GET_MINOR_VERSION.invokeExact(),
                (int) GET_MICRO_VERSION.invokeExact());
    }

    /** The path part of a custom-scheme request, for example {@code /app/index.html}. */
    @SneakyThrows
    public String uriSchemeRequestPath(MemorySegment request) {
        return NativeLibraries.string((MemorySegment) URI_SCHEME_REQUEST_GET_PATH.invokeExact(request));
    }

    /** Answers a custom-scheme request; the stream is consumed by WebKit. */
    @SneakyThrows
    public void uriSchemeRequestFinish(MemorySegment request, MemorySegment stream, long length,
                                       String contentType) {
        try (Arena arena = Arena.ofConfined()) {
            URI_SCHEME_REQUEST_FINISH.invokeExact(request, stream, length, arena.allocateFrom(contentType));
        }
    }

    /** Fails a custom-scheme request; WebKit takes ownership of {@code error}. */
    @SneakyThrows
    public void uriSchemeRequestFinishError(MemorySegment request, MemorySegment error) {
        URI_SCHEME_REQUEST_FINISH_ERROR.invokeExact(request, error);
    }

    /**
     * Starts an asynchronous evaluation; {@code callback} is invoked on the GTK thread and must complete it with
     * {@link #evaluateJavascriptFinish}.
     */
    @SneakyThrows
    public void evaluateJavascript(MemorySegment webView, String script, MemorySegment callback,
                                   MemorySegment userData) {
        try (Arena arena = Arena.ofConfined()) {
            EVALUATE_JAVASCRIPT.invokeExact(
                    webView,
                    arena.allocateFrom(script),
                    -1L,                 // length: NUL terminated
                    MemorySegment.NULL,  // world_name: default world
                    MemorySegment.NULL,  // source_uri
                    MemorySegment.NULL,  // cancellable
                    callback,
                    userData);
        }
    }

    /**
     * Completes an evaluation started by {@link #evaluateJavascript}, returning the result rendered as a string.
     *
     * @throws ScriptEvaluationFailedException when the script threw or the evaluation failed
     */
    @SneakyThrows
    public String evaluateJavascriptFinish(MemorySegment webView, MemorySegment result) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment error = arena.allocate(Signatures.C_POINTER);
            error.set(Signatures.C_POINTER, 0, MemorySegment.NULL);

            MemorySegment value = (MemorySegment) EVALUATE_JAVASCRIPT_FINISH.invokeExact(webView, result, error);
            if (value.equals(MemorySegment.NULL)) {
                String message = GLib.takeErrorMessage(error.get(Signatures.C_POINTER, 0));
                throw new ScriptEvaluationFailedException(
                        message == null ? "Script evaluation failed" : message);
            }
            try {
                return GLib.takeString((MemorySegment) VALUE_TO_STRING.invokeExact(value));
            } finally {
                GLib.unref(value);
            }
        }
    }
}
