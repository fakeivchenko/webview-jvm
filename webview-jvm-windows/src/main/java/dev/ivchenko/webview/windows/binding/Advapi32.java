package dev.ivchenko.webview.windows.binding;

import dev.ivchenko.webview.foreign.NativeLibraries;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/** Bindings to {@code advapi32.dll}: reading the registry. */
@UtilityClass
public class Advapi32 {
    private final SymbolLookup ADVAPI32 = NativeLibraries.load("advapi32.dll");

    public final MemorySegment HKEY_LOCAL_MACHINE = MemorySegment.ofAddress(0x80000002L);
    public final MemorySegment HKEY_CURRENT_USER = MemorySegment.ofAddress(0x80000001L);

    /** {@code RRF_RT_REG_SZ} */
    private final int REG_SZ_ONLY = 0x2;
    private final int ERROR_SUCCESS = 0;
    private final int BUFFER_CHARS = 2048;

    private final MethodHandle REG_GET_VALUE =
            NativeLibraries.downcall(ADVAPI32, "RegGetValueW", Signatures.INT_POINTER_X3_INT_POINTER_X3);

    /** A {@code REG_SZ} value, or empty if the key or value does not exist. */
    @SneakyThrows
    public Optional<String> readString(MemorySegment root, String subKey, String value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(BUFFER_CHARS * 2L);
            MemorySegment size = arena.allocate(Signatures.C_INT);
            size.set(Signatures.C_INT, 0, BUFFER_CHARS * 2);
            int status = (int) REG_GET_VALUE.invokeExact(root, Wide.allocate(arena, subKey),
                    Wide.allocate(arena, value), REG_SZ_ONLY, MemorySegment.NULL, buffer, size);
            return status == ERROR_SUCCESS ? Optional.of(Wide.read(buffer)) : Optional.empty();
        }
    }
}
