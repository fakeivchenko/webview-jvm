package dev.ivchenko.webview.gtk.binding;

import dev.ivchenko.webview.foreign.NativeLibraries;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/**
 * Bindings to the GLib/GObject/GIO subset the backend needs.
 *
 * <p>The handles are {@code static final} deliberately: {@code invokeExact} only compiles down to a direct call when
 * the JIT can treat the handle as a constant, and these functions sit on the hot path of every window operation.</p>
 */
@UtilityClass
public class GLib {
    private final SymbolLookup GLIB = NativeLibraries.load("libglib-2.0.so.0", "libglib-2.0.so");
    private final SymbolLookup GOBJECT = NativeLibraries.load("libgobject-2.0.so.0", "libgobject-2.0.so");
    private final SymbolLookup GIO = NativeLibraries.load("libgio-2.0.so.0", "libgio-2.0.so");

    /** Return value of a {@code GSourceFunc} that must not be invoked again. */
    public final int SOURCE_REMOVE = 0;

    /** {@code struct _GError { GQuark domain; gint code; gchar *message; }} */
    private final MemoryLayout ERROR_LAYOUT = MemoryLayout.structLayout(
            Signatures.C_INT.withName("domain"),
            Signatures.C_INT.withName("code"),
            Signatures.C_POINTER.withName("message"));
    private final VarHandle ERROR_MESSAGE =
            ERROR_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("message"));

    private final MethodHandle IDLE_ADD = NativeLibraries.downcall(GLIB, "g_idle_add", Signatures.INT_POINTER_POINTER);
    private final MethodHandle FREE = NativeLibraries.downcall(GLIB, "g_free", Signatures.VOID_POINTER);
    private final MethodHandle ERROR_FREE = NativeLibraries.downcall(GLIB, "g_error_free", Signatures.VOID_POINTER);
    private final MethodHandle SIGNAL_CONNECT_DATA =
            NativeLibraries.downcall(GOBJECT, "g_signal_connect_data", Signatures.G_SIGNAL_CONNECT_DATA);
    private final MethodHandle OBJECT_REF =
            NativeLibraries.downcall(GOBJECT, "g_object_ref", Signatures.POINTER_POINTER);
    private final MethodHandle OBJECT_UNREF =
            NativeLibraries.downcall(GOBJECT, "g_object_unref", Signatures.VOID_POINTER);
    private final MethodHandle MALLOC = NativeLibraries.downcall(GLIB, "g_malloc", Signatures.POINTER_LONG);
    private final MethodHandle QUARK_FROM_STRING =
            NativeLibraries.downcall(GLIB, "g_quark_from_string", Signatures.INT_POINTER);
    private final MethodHandle ERROR_NEW_LITERAL =
            NativeLibraries.downcall(GLIB, "g_error_new_literal", Signatures.POINTER_INT_INT_POINTER);
    private final MethodHandle MEMORY_INPUT_STREAM_NEW_FROM_DATA = NativeLibraries.downcall(
            GIO, "g_memory_input_stream_new_from_data", Signatures.POINTER_POINTER_LONG_POINTER);

    /**
     * The address of {@code g_free} itself, for APIs that take a {@code GDestroyNotify} - native code frees the buffer
     * once it is done with it.
     */
    public final MemorySegment FREE_FUNCTION = GLIB.find("g_free")
            .orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: g_free"));

    /** Schedules {@code callback} on the default main context; safe to call from any thread. */
    @SneakyThrows
    public void idleAdd(MemorySegment callback, MemorySegment userData) {
        int _ = (int) IDLE_ADD.invokeExact(callback, userData);
    }

    /** {@code g_signal_connect_data} with no destroy notify and default flags. */
    @SneakyThrows
    public void signalConnect(MemorySegment instance, String signal, MemorySegment callback,
                              MemorySegment userData) {
        try (Arena arena = Arena.ofConfined()) {
            long _ = (long) SIGNAL_CONNECT_DATA.invokeExact(
                    instance, arena.allocateFrom(signal), callback, userData, MemorySegment.NULL, 0);
        }
    }

    /** {@code g_free}. */
    @SneakyThrows
    public void free(MemorySegment pointer) {
        FREE.invokeExact(pointer);
    }

    /** {@code g_object_ref}. */
    @SneakyThrows
    public void ref(MemorySegment object) {
        MemorySegment _ = (MemorySegment) OBJECT_REF.invokeExact(object);
    }

    /** {@code g_object_unref}. */
    @SneakyThrows
    public void unref(MemorySegment object) {
        OBJECT_UNREF.invokeExact(object);
    }

    /**
     * Copies {@code data} into a buffer owned by GLib, for handing to native code that will free it later. The JVM heap
     * is not an option here: a moving collector may relocate the array, and the stream outlives the call that creates
     * it.
     */
    @SneakyThrows
    public MemorySegment copyToNative(byte[] data) {
        MemorySegment buffer = ((MemorySegment) MALLOC.invokeExact((long) data.length))
                .reinterpret(data.length);
        MemorySegment.copy(MemorySegment.ofArray(data), 0L, buffer, 0L, data.length);
        return buffer;
    }

    /**
     * Wraps {@code data} in a {@code GInputStream}; the stream takes ownership and frees the buffer when it is
     * disposed.
     */
    @SneakyThrows
    public MemorySegment memoryInputStream(MemorySegment data, long length) {
        return (MemorySegment) MEMORY_INPUT_STREAM_NEW_FROM_DATA.invokeExact(data, length, FREE_FUNCTION);
    }

    /** Builds a {@code GError} the caller owns. */
    @SneakyThrows
    public MemorySegment error(String domain, int code, String message) {
        try (Arena arena = Arena.ofConfined()) {
            int quark = (int) QUARK_FROM_STRING.invokeExact(arena.allocateFrom(domain));
            return (MemorySegment) ERROR_NEW_LITERAL.invokeExact(quark, code, arena.allocateFrom(message));
        }
    }

    /** Reads a {@code gchar*} the caller owns, then frees it. */
    public String takeString(MemorySegment pointer) {
        if (pointer.equals(MemorySegment.NULL)) return null;

        String value = NativeLibraries.string(pointer);
        free(pointer);
        return value;
    }

    /** Reads {@code error->message} from a borrowed {@code GError}. */
    public String errorMessage(MemorySegment error) {
        if (error.equals(MemorySegment.NULL)) return null;

        MemorySegment struct = error.reinterpret(ERROR_LAYOUT.byteSize());
        return NativeLibraries.string((MemorySegment) ERROR_MESSAGE.get(struct, 0L));
    }

    /** Reads {@code error->message} and frees a {@code GError} the caller owns. */
    @SneakyThrows
    public String takeErrorMessage(MemorySegment error) {
        if (error.equals(MemorySegment.NULL)) return null;

        String message = errorMessage(error);
        ERROR_FREE.invokeExact(error);
        return message;
    }
}
