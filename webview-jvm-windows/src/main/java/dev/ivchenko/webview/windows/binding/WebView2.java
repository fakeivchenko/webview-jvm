package dev.ivchenko.webview.windows.binding;

import dev.ivchenko.webview.foreign.NativeLibraries;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.List;

/**
 * Bindings to WebView2: environment creation through the runtime's own export, and the {@code ICoreWebView2*} methods
 * the backend uses, addressed by vtable slot.
 *
 * <p>Slot numbers and IIDs are those of SDK 1.0.4191.47's {@code WebView2.h}. The interfaces used are the original,
 * unversioned ones, which every runtime implements; nothing here needs a {@code _2}, {@code _3}… extension.</p>
 */
@UtilityClass
public class WebView2 {
    public final MemorySegment IID_ENVIRONMENT_COMPLETED = Com.guid("4e8a3389-c9d8-4bd2-b6b5-124fee6cc14d");
    public final MemorySegment IID_CONTROLLER_COMPLETED = Com.guid("6c4819f3-c9b7-4260-8127-c9f5bde7f68c");
    public final MemorySegment IID_WEB_MESSAGE_RECEIVED = Com.guid("57213f19-00e6-49fa-8e07-898ea01ecbd2");
    public final MemorySegment IID_NAVIGATION_STARTING = Com.guid("9adbe429-f36d-432b-9ddc-f8881fbd76e3");
    public final MemorySegment IID_NAVIGATION_COMPLETED = Com.guid("d33a35bf-1c49-4f98-93ab-006e0533fe1c");
    public final MemorySegment IID_CONTENT_LOADING = Com.guid("364471e7-f2be-4910-bdba-d72077d51c4b");
    public final MemorySegment IID_WEB_RESOURCE_REQUESTED = Com.guid("ab00b74c-15f1-4646-80e8-e76341d25d71");
    public final MemorySegment IID_EXECUTE_SCRIPT_COMPLETED = Com.guid("49511172-cc67-4bca-9923-137112f4c4cc");
    public final MemorySegment IID_ADD_SCRIPT_COMPLETED = Com.guid("b99369f3-9b11-47b5-bc6f-8e7895fcea17");

    /** {@code COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL} */
    public final int RESOURCE_CONTEXT_ALL = 0;
    /** {@code COREWEBVIEW2_WEB_RESOURCE_CONTEXT_DOCUMENT} */
    public final int RESOURCE_CONTEXT_DOCUMENT = 1;

    /** {@code COREWEBVIEW2_WEB_ERROR_STATUS}, by ordinal. */
    public final List<String> WEB_ERROR_STATUS = List.of(
            "UNKNOWN", "CERTIFICATE_COMMON_NAME_IS_INCORRECT", "CERTIFICATE_EXPIRED",
            "CLIENT_CERTIFICATE_CONTAINS_ERRORS", "CERTIFICATE_REVOKED", "CERTIFICATE_IS_INVALID",
            "SERVER_UNREACHABLE", "TIMEOUT", "ERROR_HTTP_INVALID_SERVER_RESPONSE", "CONNECTION_ABORTED",
            "CONNECTION_RESET", "DISCONNECTED", "CANNOT_CONNECT", "HOST_NAME_NOT_RESOLVED",
            "OPERATION_CANCELED", "REDIRECT_FAILED", "UNEXPECTED_ERROR",
            "VALID_AUTHENTICATION_CREDENTIALS_REQUIRED", "VALID_PROXY_AUTHENTICATION_REQUIRED");

    // ICoreWebView2Environment
    private final int ENVIRONMENT_CREATE_CONTROLLER = 3;
    private final int ENVIRONMENT_CREATE_RESPONSE = 4;
    // ICoreWebView2Controller
    private final int CONTROLLER_PUT_IS_VISIBLE = 4;
    private final int CONTROLLER_PUT_BOUNDS = 6;
    private final int CONTROLLER_CLOSE = 24;
    private final int CONTROLLER_GET_CORE_WEBVIEW2 = 25;
    // ICoreWebView2
    private final int WEBVIEW_GET_SETTINGS = 3;
    private final int WEBVIEW_GET_SOURCE = 4;
    private final int WEBVIEW_NAVIGATE = 5;
    private final int WEBVIEW_NAVIGATE_TO_STRING = 6;
    private final int WEBVIEW_ADD_NAVIGATION_STARTING = 7;
    private final int WEBVIEW_ADD_CONTENT_LOADING = 9;
    private final int WEBVIEW_ADD_NAVIGATION_COMPLETED = 15;
    private final int WEBVIEW_ADD_SCRIPT_ON_DOCUMENT_CREATED = 27;
    private final int WEBVIEW_EXECUTE_SCRIPT = 29;
    private final int WEBVIEW_ADD_WEB_MESSAGE_RECEIVED = 34;
    private final int WEBVIEW_ADD_WEB_RESOURCE_REQUESTED = 55;
    private final int WEBVIEW_ADD_WEB_RESOURCE_REQUESTED_FILTER = 57;
    // ICoreWebView2Settings
    private final int SETTINGS_PUT_DEFAULT_CONTEXT_MENUS_ENABLED = 14;
    // event args
    private final int NAVIGATION_STARTING_GET_URI = 3;
    private final int NAVIGATION_COMPLETED_GET_IS_SUCCESS = 3;
    private final int NAVIGATION_COMPLETED_GET_ERROR_STATUS = 4;
    private final int WEB_MESSAGE_TRY_GET_AS_STRING = 5;
    private final int RESOURCE_REQUESTED_GET_REQUEST = 3;
    private final int RESOURCE_REQUESTED_PUT_RESPONSE = 5;
    private final int RESOURCE_REQUESTED_GET_CONTEXT = 7;
    private final int REQUEST_GET_URI = 3;

    /**
     * The one export used from {@code EmbeddedBrowserWebView.dll}. Loading this class loads the runtime;
     * {@link WebView2Runtime#library()} answers "is it installed?" without doing so.
     */
    private final MethodHandle CREATE_ENVIRONMENT_INTERNAL = NativeLibraries.downcall(
            Kernel32.symbols(Kernel32.loadLibrary(WebView2Runtime.library()
                    .orElseThrow(() -> new UnsatisfiedLinkError("WebView2 Runtime is not installed"))
                    .toString())),
            "CreateWebViewEnvironmentWithOptionsInternal",
            Signatures.INT_BOOL_INT_POINTER_X3);

    private final VarHandle RECT_RIGHT = Signatures.RECT.varHandle(MemoryLayout.PathElement.groupElement("right"));
    private final VarHandle RECT_BOTTOM = Signatures.RECT.varHandle(MemoryLayout.PathElement.groupElement("bottom"));

    /**
     * {@code CreateWebViewEnvironmentWithOptionsInternal(true, installed, userDataFolder, NULL, handler)}: what
     * {@code WebView2Loader.dll}'s public function does after locating the runtime. Must be called on an STA thread;
     * the handler fires on that thread.
     */
    @SneakyThrows
    public void createEnvironment(String userDataFolder, MemorySegment handler) {
        try (Arena arena = Arena.ofConfined()) {
            Com.check("CreateWebViewEnvironmentWithOptionsInternal", (int) CREATE_ENVIRONMENT_INTERNAL.invokeExact(
                    true, 0, Wide.allocate(arena, userDataFolder), MemorySegment.NULL, handler));
        }
    }

    public void createController(MemorySegment environment, MemorySegment hwnd, MemorySegment handler) {
        Com.check("CreateCoreWebView2Controller", Com.call(environment, ENVIRONMENT_CREATE_CONTROLLER, hwnd, handler));
    }

    /** A response object the caller owns; {@code stream} may be {@code NULL} for an empty body. */
    public MemorySegment createResponse(MemorySegment environment, MemorySegment stream, int status,
                                        String reason, String headers) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Signatures.C_POINTER);
            Com.check("CreateWebResourceResponse", Com.call(environment, ENVIRONMENT_CREATE_RESPONSE, stream, status,
                    Wide.allocate(arena, reason), Wide.allocate(arena, headers), out));
            return Com.pointerAt(out);
        }
    }

    /** The {@code ICoreWebView2} behind a controller; the caller owns the returned reference. */
    public MemorySegment coreWebView2(MemorySegment controller) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Signatures.C_POINTER);
            Com.check("get_CoreWebView2", Com.call(controller, CONTROLLER_GET_CORE_WEBVIEW2, out));
            return Com.pointerAt(out);
        }
    }

    public void setVisible(MemorySegment controller, boolean visible) {
        Com.check("put_IsVisible", Com.call(controller, CONTROLLER_PUT_IS_VISIBLE, visible ? 1 : 0));
    }

    /** Sizes the view to {@code width} by {@code height} at the parent's origin. */
    public void setBounds(MemorySegment controller, int width, int height) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rect = arena.allocate(Signatures.RECT);
            RECT_RIGHT.set(rect, 0L, width);
            RECT_BOTTOM.set(rect, 0L, height);
            Com.check("put_Bounds", Com.callWithRect(controller, CONTROLLER_PUT_BOUNDS, rect));
        }
    }

    public void close(MemorySegment controller) {
        Com.check("Close", Com.call(controller, CONTROLLER_CLOSE));
    }

    /** Switches the engine's own right-click menu off; the page's {@code contextmenu} handlers are unaffected. */
    public void disableDefaultContextMenu(MemorySegment webView) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Signatures.C_POINTER);
            Com.check("get_Settings", Com.call(webView, WEBVIEW_GET_SETTINGS, out));
            MemorySegment settings = Com.pointerAt(out);
            try {
                Com.check("put_AreDefaultContextMenusEnabled",
                        Com.call(settings, SETTINGS_PUT_DEFAULT_CONTEXT_MENUS_ENABLED, 0));
            } finally {
                Com.release(settings);
            }
        }
    }

    public String source(MemorySegment webView) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Signatures.C_POINTER);
            Com.check("get_Source", Com.call(webView, WEBVIEW_GET_SOURCE, out));
            return Wide.take(Com.pointerAt(out));
        }
    }

    public void navigate(MemorySegment webView, String uri) {
        try (Arena arena = Arena.ofConfined()) {
            Com.check("Navigate", Com.call(webView, WEBVIEW_NAVIGATE, Wide.allocate(arena, uri)));
        }
    }

    public void navigateToString(MemorySegment webView, String html) {
        try (Arena arena = Arena.ofConfined()) {
            Com.check("NavigateToString", Com.call(webView, WEBVIEW_NAVIGATE_TO_STRING, Wide.allocate(arena, html)));
        }
    }

    public void onNavigationStarting(MemorySegment webView, MemorySegment handler) {
        addEvent(webView, WEBVIEW_ADD_NAVIGATION_STARTING, handler, "add_NavigationStarting");
    }

    public void onContentLoading(MemorySegment webView, MemorySegment handler) {
        addEvent(webView, WEBVIEW_ADD_CONTENT_LOADING, handler, "add_ContentLoading");
    }

    public void onNavigationCompleted(MemorySegment webView, MemorySegment handler) {
        addEvent(webView, WEBVIEW_ADD_NAVIGATION_COMPLETED, handler, "add_NavigationCompleted");
    }

    public void onWebMessageReceived(MemorySegment webView, MemorySegment handler) {
        addEvent(webView, WEBVIEW_ADD_WEB_MESSAGE_RECEIVED, handler, "add_WebMessageReceived");
    }

    public void onWebResourceRequested(MemorySegment webView, MemorySegment handler) {
        addEvent(webView, WEBVIEW_ADD_WEB_RESOURCE_REQUESTED, handler, "add_WebResourceRequested");
    }

    /** Routes requests matching {@code uriPattern} (wildcards allowed) through the requested event. */
    public void addWebResourceRequestedFilter(MemorySegment webView, String uriPattern) {
        try (Arena arena = Arena.ofConfined()) {
            Com.check("AddWebResourceRequestedFilter", Com.call(webView, WEBVIEW_ADD_WEB_RESOURCE_REQUESTED_FILTER,
                    Wide.allocate(arena, uriPattern), RESOURCE_CONTEXT_ALL));
        }
    }

    public void addScriptToExecuteOnDocumentCreated(MemorySegment webView, String script, MemorySegment handler) {
        try (Arena arena = Arena.ofConfined()) {
            Com.check("AddScriptToExecuteOnDocumentCreated", Com.call(webView, WEBVIEW_ADD_SCRIPT_ON_DOCUMENT_CREATED,
                    Wide.allocate(arena, script), handler));
        }
    }

    public void executeScript(MemorySegment webView, String script, MemorySegment handler) {
        try (Arena arena = Arena.ofConfined()) {
            Com.check("ExecuteScript", Com.call(webView, WEBVIEW_EXECUTE_SCRIPT, Wide.allocate(arena, script), handler));
        }
    }

    public String navigationStartingUri(MemorySegment arguments) {
        return uri(arguments, NAVIGATION_STARTING_GET_URI);
    }

    public boolean isNavigationSuccessful(MemorySegment arguments) {
        return integer(arguments, NAVIGATION_COMPLETED_GET_IS_SUCCESS, "get_IsSuccess") != 0;
    }

    /** The failed navigation's {@code COREWEBVIEW2_WEB_ERROR_STATUS}, as its enum name. */
    public String navigationErrorStatus(MemorySegment arguments) {
        int status = integer(arguments, NAVIGATION_COMPLETED_GET_ERROR_STATUS, "get_WebErrorStatus");
        return status >= 0 && status < WEB_ERROR_STATUS.size() ? WEB_ERROR_STATUS.get(status) : "STATUS_" + status;
    }

    /** The message a page posted, if it posted a string; {@code null} for any other JSON value. */
    public String webMessageAsString(MemorySegment arguments) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Signatures.C_POINTER);
            int hresult = Com.call(arguments, WEB_MESSAGE_TRY_GET_AS_STRING, out);
            return hresult < 0 ? null : Wide.take(Com.pointerAt(out));
        }
    }

    /** The intercepted request's URI. */
    public String requestedUri(MemorySegment arguments) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Signatures.C_POINTER);
            Com.check("get_Request", Com.call(arguments, RESOURCE_REQUESTED_GET_REQUEST, out));
            MemorySegment request = Com.pointerAt(out);
            try {
                return uri(request, REQUEST_GET_URI);
            } finally {
                Com.release(request);
            }
        }
    }

    public boolean isDocumentRequest(MemorySegment arguments) {
        return integer(arguments, RESOURCE_REQUESTED_GET_CONTEXT, "get_ResourceContext") == RESOURCE_CONTEXT_DOCUMENT;
    }

    public void respond(MemorySegment arguments, MemorySegment response) {
        Com.check("put_Response", Com.call(arguments, RESOURCE_REQUESTED_PUT_RESPONSE, response));
    }

    private void addEvent(MemorySegment webView, int slot, MemorySegment handler, String name) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment token = arena.allocate(Signatures.C_LONG_PTR);
            Com.check(name, Com.call(webView, slot, handler, token));
        }
    }

    private String uri(MemorySegment object, int slot) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Signatures.C_POINTER);
            Com.check("get_Uri", Com.call(object, slot, out));
            return Wide.take(Com.pointerAt(out));
        }
    }

    private int integer(MemorySegment object, int slot, String name) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(Signatures.C_INT);
            Com.check(name, Com.call(object, slot, out));
            return out.get(Signatures.C_INT, 0);
        }
    }
}
