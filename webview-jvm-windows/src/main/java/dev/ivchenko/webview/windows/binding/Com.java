package dev.ivchenko.webview.windows.binding;

import dev.ivchenko.webview.foreign.NativeLibraries;
import dev.ivchenko.webview.windows.exception.ComCallFailedException;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.UUID;

/**
 * Calling COM objects through their vtables.
 *
 * <p>A COM object is a pointer to a struct whose first word points to a table of function pointers, and a method call
 * is a C call through slot {@code n} with the object as first argument. So each method shape gets one
 * {@link MethodHandle} that takes the function address first, and calling method {@code n} on {@code object} is "read
 * slot {@code n}, invoke". No IDL, no proxies: the slot numbers come straight from the SDK header.</p>
 */
@UtilityClass
public class Com {
    public final int S_OK = 0;
    public final int E_NOINTERFACE = 0x80004002;
    public final int E_POINTER = 0x80004003;

    /** {@code IID_IUnknown} */
    public final MemorySegment IID_IUNKNOWN = guid("00000000-0000-0000-C000-000000000046");

    private final int ADD_REF = 1;
    private final int RELEASE = 2;

    private final MethodHandle CALL_P = NativeLibraries.downcall(Signatures.INT_POINTER);
    private final MethodHandle CALL_P_I = NativeLibraries.downcall(Signatures.INT_POINTER_INT);
    private final MethodHandle CALL_P_P = NativeLibraries.downcall(Signatures.INT_POINTER_POINTER);
    private final MethodHandle CALL_P_P_P = NativeLibraries.downcall(Signatures.INT_POINTER_POINTER_POINTER);
    private final MethodHandle CALL_P_P_I = NativeLibraries.downcall(Signatures.INT_POINTER_POINTER_INT);
    private final MethodHandle CALL_P_RECT = NativeLibraries.downcall(Signatures.INT_POINTER_RECT);
    private final MethodHandle CALL_P_P_I_P_P_P = NativeLibraries.downcall(Signatures.INT_POINTER_POINTER_INT_POINTER_X3);

    /** The function pointer in vtable slot {@code index} of {@code object}. */
    public MemorySegment slot(MemorySegment object, int index) {
        MemorySegment vtable = object.reinterpret(Signatures.C_POINTER.byteSize()).get(Signatures.C_POINTER, 0);
        return vtable.reinterpret((index + 1L) * Signatures.C_POINTER.byteSize())
                .getAtIndex(Signatures.C_POINTER, index);
    }

    @SneakyThrows
    public int call(MemorySegment object, int index) {
        return (int) CALL_P.invokeExact(slot(object, index), object);
    }

    @SneakyThrows
    public int call(MemorySegment object, int index, int argument) {
        return (int) CALL_P_I.invokeExact(slot(object, index), object, argument);
    }

    @SneakyThrows
    public int call(MemorySegment object, int index, MemorySegment argument) {
        return (int) CALL_P_P.invokeExact(slot(object, index), object, argument);
    }

    @SneakyThrows
    public int call(MemorySegment object, int index, MemorySegment first, MemorySegment second) {
        return (int) CALL_P_P_P.invokeExact(slot(object, index), object, first, second);
    }

    @SneakyThrows
    public int call(MemorySegment object, int index, MemorySegment first, int second) {
        return (int) CALL_P_P_I.invokeExact(slot(object, index), object, first, second);
    }

    /** A method taking a {@code RECT} by value. */
    @SneakyThrows
    public int callWithRect(MemorySegment object, int index, MemorySegment rect) {
        return (int) CALL_P_RECT.invokeExact(slot(object, index), object, rect);
    }

    /** {@code ICoreWebView2Environment::CreateWebResourceResponse}'s shape. */
    @SneakyThrows
    public int call(MemorySegment object, int index, MemorySegment stream, int status,
                    MemorySegment reason, MemorySegment headers, MemorySegment out) {
        return (int) CALL_P_P_I_P_P_P.invokeExact(slot(object, index), object, stream, status, reason, headers, out);
    }

    public void addRef(MemorySegment object) {
        call(object, ADD_REF);
    }

    public void release(MemorySegment object) {
        if (object != null && !object.equals(MemorySegment.NULL)) call(object, RELEASE);
    }

    /** Throws when {@code hresult} is a failure code. */
    public void check(String call, int hresult) {
        if (hresult < 0) throw new ComCallFailedException(call, hresult);
    }

    /** Reads an {@code [out] T**} slot. */
    public MemorySegment pointerAt(MemorySegment out) {
        return out.get(Signatures.C_POINTER, 0);
    }

    /**
     * A {@code GUID} in its binary layout: the first three fields little-endian, the last eight bytes as written -
     * which is why the textual form cannot simply be copied.
     */
    public MemorySegment guid(String text) {
        UUID uuid = UUID.fromString(text);
        MemorySegment guid = NativeLibraries.ARENA.allocate(Signatures.GUID);
        long high = uuid.getMostSignificantBits();
        long low = uuid.getLeastSignificantBits();
        guid.set(Signatures.C_INT, 0, (int) (high >>> 32));
        guid.set(Signatures.C_SHORT, 4, (short) (high >>> 16));
        guid.set(Signatures.C_SHORT, 6, (short) high);
        for (int i = 0; i < 8; i++) {
            guid.set(ValueLayout.JAVA_BYTE, 8 + i, (byte) (low >>> (56 - 8 * i)));
        }
        return guid;
    }

    /** Whether two 16-byte GUIDs are equal. */
    public boolean sameGuid(MemorySegment first, MemorySegment second) {
        return first.reinterpret(16).mismatch(second.reinterpret(16)) == -1;
    }
}
