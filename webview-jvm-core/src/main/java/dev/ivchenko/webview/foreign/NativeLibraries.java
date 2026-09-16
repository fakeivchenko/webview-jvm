package dev.ivchenko.webview.foreign;

import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Helpers around the Foreign Function &amp; Memory API, shared by every backend.
 *
 * <p>Backends bind native libraries exclusively through this class, which keeps every {@link FunctionDescriptor} and
 * every upcall target enumerable through {@link #downcalls()} and {@link #upcalls()}. {@code native-image} can only
 * compile stubs it knew about at build time, so a test compares those sets with the reachability metadata shipped in
 * the backend jar.</p>
 */
@UtilityClass
public class NativeLibraries {
    private final Set<FunctionDescriptor> DOWNCALLS = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<UpcallTarget> UPCALLS = Collections.synchronizedSet(new LinkedHashSet<>());

    /** A Java method exposed to native code as a function pointer. */
    public record UpcallTarget(Class<?> owner, String method, FunctionDescriptor descriptor) {
    }

    /** The platform's C ABI linker. */
    public final Linker LINKER = Linker.nativeLinker();

    /**
     * Backing arena for library handles and upcall stubs. Both live for as long as the process does, so a global arena
     * avoids any lifetime bookkeeping.
     */
    public final Arena ARENA = Arena.global();

    /** Opens the first of {@code sonames} that the platform loader accepts. */
    public SymbolLookup load(String... sonames) {
        UnsatisfiedLinkError failure =
                new UnsatisfiedLinkError("None of " + String.join(", ", sonames) + " could be loaded");
        for (String soname : sonames) {
            try {
                return SymbolLookup.libraryLookup(soname, ARENA);
            } catch (IllegalArgumentException e) {
                failure.addSuppressed(e);
            }
        }
        throw failure;
    }

    /** Whether any of {@code sonames} can be opened, without reporting failure as an error. */
    public boolean isLoadable(String... sonames) {
        try {
            load(sonames);
            return true;
        } catch (UnsatisfiedLinkError _) {
            return false;
        }
    }

    /**
     * Binds the C function {@code symbol} with the given signature.
     *
     * @throws UnsatisfiedLinkError if the library has no such symbol
     */
    public MethodHandle downcall(SymbolLookup library, String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = library.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + symbol));
        DOWNCALLS.add(descriptor);
        return LINKER.downcallHandle(address, descriptor);
    }

    /**
     * A handle for calling any function of the given signature, taking the function's address as its first argument -
     * how COM methods are called, since their addresses come from vtables.
     */
    public MethodHandle downcall(FunctionDescriptor descriptor) {
        DOWNCALLS.add(descriptor);
        return LINKER.downcallHandle(descriptor);
    }

    /**
     * Binds a static Java method as a C callback. The stub is allocated in {@link #ARENA}, so one stub can be shared by
     * every window and can never dangle while native code holds it.
     *
     * @param lookup a lookup created in the class that declares {@code method}, so that private callbacks stay private
     */
    public MemorySegment upcall(MethodHandles.Lookup lookup, Class<?> owner, String method,
                                MethodType type, FunctionDescriptor descriptor) {
        try {
            MethodHandle target = lookup.findStatic(owner, method, type);
            UPCALLS.add(new UpcallTarget(owner, method, descriptor));
            return LINKER.upcallStub(target, descriptor, ARENA);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Every distinct signature bound with {@link #downcall} so far, in binding order. */
    public Set<FunctionDescriptor> downcalls() {
        return Collections.unmodifiableSet(DOWNCALLS);
    }

    /** Every method exposed with {@link #upcall} so far, in binding order. */
    public Set<UpcallTarget> upcalls() {
        return Collections.unmodifiableSet(UPCALLS);
    }

    /** Reads a NUL terminated UTF-8 string from a pointer that may be {@code NULL}. */
    public String string(MemorySegment pointer) {
        if (pointer == null || pointer.equals(MemorySegment.NULL)) return null;
        return pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
