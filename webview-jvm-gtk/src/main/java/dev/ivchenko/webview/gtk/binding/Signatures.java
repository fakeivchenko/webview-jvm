package dev.ivchenko.webview.gtk.binding;

import dev.ivchenko.webview.foreign.CLayouts;
import lombok.experimental.UtilityClass;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;

/**
 * Every native signature the GTK backend binds, in one place.
 *
 * <p>Descriptors are named after their shape rather than after one function, because the same shape serves many calls
 * and the JIT only needs the shape. Where a signature is long enough that a shape name would be unreadable, the
 * function's own name is used instead.</p>
 *
 * <p>Loading this class has no side effects - it opens no library and creates no {@link java.lang.foreign.Linker} - so
 * a build-time tool such as {@code native-image} can inspect the descriptors without touching the toolkit.</p>
 */
@UtilityClass
public class Signatures {
    /** {@code gint}, {@code gboolean}, {@code guint}, {@code GQuark}. */
    public final ValueLayout.OfInt C_INT = CLayouts.C_INT;

    /** {@code glong}, {@code gulong}, {@code gssize}, {@code gint64}: GTK targets are LP64. */
    public final ValueLayout.OfLong C_LONG = CLayouts.C_LONG_LONG;

    /** Any {@code T*}. */
    public final AddressLayout C_POINTER = CLayouts.C_POINTER;

    // --- shapes ---

    /** {@code void f(void)} */
    public final FunctionDescriptor VOID_VOID = FunctionDescriptor.ofVoid();

    /** {@code T* f(void)} */
    public final FunctionDescriptor POINTER_VOID = FunctionDescriptor.of(C_POINTER);
    public final FunctionDescriptor INT_VOID = FunctionDescriptor.of(C_INT);

    /** {@code void f(T*)} */
    public final FunctionDescriptor VOID_POINTER = FunctionDescriptor.ofVoid(C_POINTER);

    /** {@code T* f(U*)} */
    public final FunctionDescriptor POINTER_POINTER = FunctionDescriptor.of(C_POINTER, C_POINTER);

    /** {@code T* f(gsize)} */
    public final FunctionDescriptor POINTER_LONG = FunctionDescriptor.of(C_POINTER, C_LONG);

    /** {@code T* f(gint)} */
    public final FunctionDescriptor POINTER_INT = FunctionDescriptor.of(C_POINTER, C_INT);

    /** {@code gint f(T*)} */
    public final FunctionDescriptor INT_POINTER = FunctionDescriptor.of(C_INT, C_POINTER);

    /** {@code gboolean f(T*, U*)} - {@code g_idle_add}, {@code gtk_init_check}, handler registration. */
    public final FunctionDescriptor INT_POINTER_POINTER = FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER);

    /** {@code void f(T*, U*)} */
    public final FunctionDescriptor VOID_POINTER_POINTER = FunctionDescriptor.ofVoid(C_POINTER, C_POINTER);

    /** {@code void f(T*, U*, V*)} */
    public final FunctionDescriptor VOID_POINTER_POINTER_POINTER =
            FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER);

    /** {@code void f(T*, gint)} */
    public final FunctionDescriptor VOID_POINTER_INT = FunctionDescriptor.ofVoid(C_POINTER, C_INT);

    /** {@code void f(T*, gint, gint)} */
    public final FunctionDescriptor VOID_POINTER_INT_INT = FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT);

    /** {@code void f(T*, U*, gint64, V*)} - {@code webkit_uri_scheme_request_finish}. */
    public final FunctionDescriptor VOID_POINTER_POINTER_LONG_POINTER =
            FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_LONG, C_POINTER);

    /** {@code T* f(U*, gssize, V*)} - {@code g_memory_input_stream_new_from_data}. */
    public final FunctionDescriptor POINTER_POINTER_LONG_POINTER =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_LONG, C_POINTER);

    /** {@code T* f(GQuark, gint, const gchar*)} - {@code g_error_new_literal}. */
    public final FunctionDescriptor POINTER_INT_INT_POINTER =
            FunctionDescriptor.of(C_POINTER, C_INT, C_INT, C_POINTER);

    /** {@code T* f(U*, V*, W*)} - {@code webkit_web_view_evaluate_javascript_finish}. */
    public final FunctionDescriptor POINTER_POINTER_POINTER_POINTER =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    /** {@code void f(T*, U*, V*, W*, X*)} - {@code webkit_web_context_register_uri_scheme}. */
    public final FunctionDescriptor VOID_POINTER_X5 =
            FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    // --- named signatures, too long to describe by shape ---

    /**
     * {@code gulong g_signal_connect_data(gpointer, const gchar*, GCallback, gpointer, GClosureNotify, GConnectFlags)}
     */
    public final FunctionDescriptor G_SIGNAL_CONNECT_DATA =
            FunctionDescriptor.of(C_LONG, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_INT);

    /**
     * {@code WebKitUserScript* webkit_user_script_new(const gchar*, WebKitUserContentInjectedFrames,
     * WebKitUserScriptInjectionTime, const gchar* const*, const gchar* const*)}
     */
    public final FunctionDescriptor WEBKIT_USER_SCRIPT_NEW =
            FunctionDescriptor.of(C_POINTER, C_POINTER, C_INT, C_INT, C_POINTER, C_POINTER);

    /**
     * {@code void webkit_web_view_evaluate_javascript(WebKitWebView*, const char*, gssize, const char*, const char*,
     * GCancellable*, GAsyncReadyCallback, gpointer)}
     */
    public final FunctionDescriptor WEBKIT_EVALUATE_JAVASCRIPT = FunctionDescriptor.ofVoid(
            C_POINTER, C_POINTER, C_LONG, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    // --- callbacks (upcalls) ---

    /** {@code gboolean (*GSourceFunc)(gpointer)} */
    public final FunctionDescriptor G_SOURCE_FUNC = INT_POINTER;

    /** {@code void (*GAsyncReadyCallback)(GObject*, GAsyncResult*, gpointer)} */
    public final FunctionDescriptor G_ASYNC_READY_CALLBACK = VOID_POINTER_POINTER_POINTER;

    /** {@code void (*)(GtkWidget*, gpointer)} - the {@code destroy} signal. */
    public final FunctionDescriptor WIDGET_CALLBACK = VOID_POINTER_POINTER;

    /** {@code void (*)(WebKitUserContentManager*, WebKitJavascriptResult*, gpointer)} - bridge messages. */
    public final FunctionDescriptor SCRIPT_MESSAGE_CALLBACK = VOID_POINTER_POINTER_POINTER;

    /** {@code void (*WebKitURISchemeRequestCallback)(WebKitURISchemeRequest*, gpointer)} */
    public final FunctionDescriptor URI_SCHEME_REQUEST_CALLBACK = VOID_POINTER_POINTER;

    /** {@code void (*)(WebKitWebView*, WebKitLoadEvent, gpointer)} - {@code load-changed}. */
    public final FunctionDescriptor LOAD_CHANGED_CALLBACK = FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_POINTER);

    /**
     * {@code gboolean (*)(WebKitWebView*, WebKitContextMenu*, GdkEvent*, WebKitHitTestResult*, gpointer)} -
     * {@code context-menu}.
     */
    public final FunctionDescriptor CONTEXT_MENU_CALLBACK =
            FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER);

    /** {@code gboolean (*)(WebKitWebView*, WebKitLoadEvent, gchar*, GError*, gpointer)} - {@code load-failed}. */
    public final FunctionDescriptor LOAD_FAILED_CALLBACK =
            FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER, C_POINTER, C_POINTER);
}
