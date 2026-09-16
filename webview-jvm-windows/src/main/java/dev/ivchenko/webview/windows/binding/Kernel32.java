package dev.ivchenko.webview.windows.binding;

import dev.ivchenko.webview.foreign.NativeLibraries;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/** Bindings to the {@code kernel32.dll} subset the backend needs. */
@UtilityClass
public class Kernel32 {
    private final SymbolLookup KERNEL32 = NativeLibraries.load("kernel32.dll");

    /** {@code LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS} */
    private final int SEARCH_DLL_DIR_AND_DEFAULTS = 0x00000100 | 0x00001000;

    private final MethodHandle GET_MODULE_HANDLE =
            NativeLibraries.downcall(KERNEL32, "GetModuleHandleW", Signatures.POINTER_POINTER);
    private final MethodHandle GET_CURRENT_THREAD_ID =
            NativeLibraries.downcall(KERNEL32, "GetCurrentThreadId", Signatures.INT_VOID);
    private final MethodHandle GET_LAST_ERROR =
            NativeLibraries.downcall(KERNEL32, "GetLastError", Signatures.INT_VOID);
    private final MethodHandle LOAD_LIBRARY_EX =
            NativeLibraries.downcall(KERNEL32, "LoadLibraryExW", Signatures.POINTER_POINTER_POINTER_INT);
    private final MethodHandle GET_PROC_ADDRESS =
            NativeLibraries.downcall(KERNEL32, "GetProcAddress", Signatures.POINTER_POINTER_POINTER);

    /** {@code GetModuleHandleW(NULL)}: the executable's own instance handle. */
    @SneakyThrows
    public MemorySegment moduleHandle() {
        return (MemorySegment) GET_MODULE_HANDLE.invokeExact(MemorySegment.NULL);
    }

    @SneakyThrows
    public int currentThreadId() {
        return (int) GET_CURRENT_THREAD_ID.invokeExact();
    }

    @SneakyThrows
    public int lastError() {
        return (int) GET_LAST_ERROR.invokeExact();
    }

    /**
     * Loads a DLL by full path with its own directory on the search path, so its private dependencies resolve - which a
     * plain {@code LoadLibrary} would not do for them.
     */
    @SneakyThrows
    public MemorySegment loadLibrary(String path) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment module = (MemorySegment) LOAD_LIBRARY_EX.invokeExact(
                    Wide.allocate(arena, path), MemorySegment.NULL, SEARCH_DLL_DIR_AND_DEFAULTS);
            if (module.equals(MemorySegment.NULL)) {
                throw new UnsatisfiedLinkError("LoadLibraryExW(" + path + ") failed, error " + lastError());
            }
            return module;
        }
    }

    /** A {@link SymbolLookup} over a module from {@link #loadLibrary}. */
    public SymbolLookup symbols(MemorySegment module) {
        return name -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment address = (MemorySegment) GET_PROC_ADDRESS.invokeExact(module, arena.allocateFrom(name));
                return address.equals(MemorySegment.NULL) ? Optional.empty() : Optional.of(address);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        };
    }
}
