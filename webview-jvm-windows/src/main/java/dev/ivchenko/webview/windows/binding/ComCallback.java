package dev.ivchenko.webview.windows.binding;

import dev.ivchenko.webview.foreign.CallbackRegistry;
import dev.ivchenko.webview.foreign.NativeLibraries;
import dev.ivchenko.webview.util.ThrowableUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

/**
 * A COM object implemented in Java, for the handler interfaces WebView2 calls back into.
 *
 * <p>Every WebView2 handler has the same shape - {@code IUnknown} plus one {@code Invoke} - and {@code Invoke} comes in
 * two signatures: {@code (HRESULT, T*)} for completions and {@code (sender, args)} for events. So there are exactly two
 * vtables in the whole backend, shared by every instance, and an instance is a 40-byte struct: the vtable pointer, an
 * id into {@link CallbackRegistry}, a reference count, and the IID it answers to. The id, not the object address, keys
 * the registry: the upcall reads it from {@code this}.</p>
 *
 * <p>Reference counting is real. WebView2 holds a completion handler only until it fires and an event handler until the
 * view is closed; when the count reaches zero the entry is dropped and the memory, in an automatic arena, is freed once
 * unreachable.</p>
 */
public class ComCallback {
    /**
     * {@code HRESULT Invoke(HRESULT errorCode, T* result)}. The Java side never has a reason to fail the call, so the
     * handler returns nothing and the stub always answers {@code S_OK}.
     */
    public interface Completion {
        void invoke(int hresult, MemorySegment result);
    }

    /** {@code HRESULT Invoke(T* sender, U* args)}; see {@link Completion} for the return value. */
    public interface Event {
        void invoke(MemorySegment sender, MemorySegment arguments);
    }

    private static final MemoryLayout OBJECT = MemoryLayout.structLayout(
            Signatures.C_POINTER.withName("vtable"),
            Signatures.C_LONG_PTR.withName("id"),
            Signatures.C_LONG_PTR.withName("references"),
            Signatures.GUID.withName("iid"));
    private static final VarHandle VTABLE = OBJECT.varHandle(MemoryLayout.PathElement.groupElement("vtable"));
    private static final VarHandle ID = OBJECT.varHandle(MemoryLayout.PathElement.groupElement("id"));
    private static final VarHandle REFERENCES = OBJECT.varHandle(MemoryLayout.PathElement.groupElement("references"));
    private static final long IID_OFFSET = OBJECT.byteOffset(MemoryLayout.PathElement.groupElement("iid"));

    private static final CallbackRegistry<ComCallback> OBJECTS = new CallbackRegistry<>();

    private static final MemorySegment QUERY_INTERFACE = NativeLibraries.upcall(
            MethodHandles.lookup(), ComCallback.class, "queryInterface",
            MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
            Signatures.INT_POINTER_POINTER_POINTER);
    private static final MemorySegment ADD_REF = NativeLibraries.upcall(
            MethodHandles.lookup(), ComCallback.class, "addRef",
            MethodType.methodType(int.class, MemorySegment.class), Signatures.INT_POINTER);
    private static final MemorySegment RELEASE = NativeLibraries.upcall(
            MethodHandles.lookup(), ComCallback.class, "release",
            MethodType.methodType(int.class, MemorySegment.class), Signatures.INT_POINTER);
    private static final MemorySegment INVOKE_COMPLETION = NativeLibraries.upcall(
            MethodHandles.lookup(), ComCallback.class, "invokeCompletion",
            MethodType.methodType(int.class, MemorySegment.class, int.class, MemorySegment.class),
            Signatures.INT_POINTER_INT_POINTER);
    private static final MemorySegment INVOKE_EVENT = NativeLibraries.upcall(
            MethodHandles.lookup(), ComCallback.class, "invokeEvent",
            MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class, MemorySegment.class),
            Signatures.INT_POINTER_POINTER_POINTER);

    private static final MemorySegment COMPLETION_VTABLE = vtable(INVOKE_COMPLETION);
    private static final MemorySegment EVENT_VTABLE = vtable(INVOKE_EVENT);

    private final MemorySegment object;
    private final Completion completion;
    private final Event event;

    private ComCallback(MemorySegment vtable, MemorySegment iid, Completion completion, Event event) {
        this.completion = completion;
        this.event = event;
        this.object = Arena.ofAuto().allocate(OBJECT);
        VTABLE.set(this.object, 0L, vtable);
        ID.set(this.object, 0L, OBJECTS.register(this));
        REFERENCES.set(this.object, 0L, 1L);
        MemorySegment.copy(iid, 0L, this.object, IID_OFFSET, Signatures.GUID.byteSize());
    }

    /**
     * A handler answering to {@code iid} whose {@code Invoke} is a completion. The caller holds one reference and
     * should {@link Com#release} it after handing the object to WebView2.
     */
    public static MemorySegment completion(MemorySegment iid, Completion handler) {
        return new ComCallback(COMPLETION_VTABLE, iid, handler, null).object;
    }

    /** A handler answering to {@code iid} whose {@code Invoke} is an event; same ownership rule. */
    public static MemorySegment event(MemorySegment iid, Event handler) {
        return new ComCallback(EVENT_VTABLE, iid, null, handler).object;
    }

    private static MemorySegment vtable(MemorySegment invoke) {
        MemorySegment vtable = NativeLibraries.ARENA.allocate(Signatures.C_POINTER.byteSize() * 4);
        vtable.setAtIndex(Signatures.C_POINTER, 0, QUERY_INTERFACE);
        vtable.setAtIndex(Signatures.C_POINTER, 1, ADD_REF);
        vtable.setAtIndex(Signatures.C_POINTER, 2, RELEASE);
        vtable.setAtIndex(Signatures.C_POINTER, 3, invoke);
        return vtable;
    }

    private static ComCallback of(MemorySegment self) {
        return OBJECTS.lookup((long) ID.get(self.reinterpret(OBJECT.byteSize()), 0L));
    }

    // --- IUnknown and Invoke, bound by name from the upcall stubs above ---

    @SuppressWarnings("unused")
    private static int queryInterface(MemorySegment self, MemorySegment riid, MemorySegment out) {
        try {
            if (out.equals(MemorySegment.NULL)) return Com.E_POINTER;
            MemorySegment object = self.reinterpret(OBJECT.byteSize());
            MemorySegment iid = object.asSlice(IID_OFFSET, Signatures.GUID.byteSize());
            MemorySegment result = out.reinterpret(Signatures.C_POINTER.byteSize());
            if (Com.sameGuid(riid, Com.IID_IUNKNOWN) || Com.sameGuid(riid, iid)) {
                result.set(Signatures.C_POINTER, 0, self);
                addRef(self);
                return Com.S_OK;
            }
            result.set(Signatures.C_POINTER, 0, MemorySegment.NULL);
            return Com.E_NOINTERFACE;
        } catch (Throwable t) {
            ThrowableUtil.report(t);
            return Com.E_NOINTERFACE;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private static int addRef(MemorySegment self) {
        MemorySegment object = self.reinterpret(OBJECT.byteSize());
        long references = (long) REFERENCES.get(object, 0L) + 1;
        REFERENCES.set(object, 0L, references);
        return (int) references;
    }

    @SuppressWarnings("unused")
    private static int release(MemorySegment self) {
        MemorySegment object = self.reinterpret(OBJECT.byteSize());
        long references = (long) REFERENCES.get(object, 0L) - 1;
        REFERENCES.set(object, 0L, references);
        if (references == 0) OBJECTS.unregister((long) ID.get(object, 0L));
        return (int) references;
    }

    @SuppressWarnings("unused")
    private static int invokeCompletion(MemorySegment self, int hresult, MemorySegment result) {
        try {
            ComCallback callback = of(self);
            if (callback != null) callback.completion.invoke(hresult, result);
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
        return Com.S_OK;
    }

    @SuppressWarnings("unused")
    private static int invokeEvent(MemorySegment self, MemorySegment sender, MemorySegment arguments) {
        try {
            ComCallback callback = of(self);
            if (callback != null) callback.event.invoke(sender, arguments);
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
        return Com.S_OK;
    }
}
