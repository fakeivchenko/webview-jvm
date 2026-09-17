package dev.ivchenko.webview.foreign;

import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maps native {@code void*} user data onto Java objects.
 *
 * <p>C callbacks carry no captured state, only an opaque pointer. Handing out real object addresses is not an option
 * under a moving collector, so each object is registered under a generated id and the id travels as the user data
 * pointer. That lets one static upcall stub serve every instance - which also keeps the stub set fixed, as
 * {@code native-image} requires.</p>
 *
 * @param <T> the type the callbacks dispatch to
 */
public class CallbackRegistry<T> {
    private final Map<Long, T> entries = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    /** Registers {@code value} and returns the id to pass as user data. */
    public long register(T value) {
        long id = this.ids.incrementAndGet();
        this.entries.put(id, value);
        return id;
    }

    /** The entry registered under {@code id}, or {@code null} if it was unregistered. */
    public T lookup(long id) {
        return this.entries.get(id);
    }

    /** Looks up the entry a callback's user data pointer refers to; {@code null} if it is gone. */
    public T lookup(MemorySegment userData) {
        return this.lookup(userData.address());
    }

    /** Removes and returns the entry under {@code id}; later callbacks carrying it find nothing. */
    public T unregister(long id) {
        return this.entries.remove(id);
    }

    /** {@link #unregister(long)} for a callback's user data pointer. */
    public T unregister(MemorySegment userData) {
        return this.unregister(userData.address());
    }

    /** {@link #unregister(long)} when the caller has no use for the entry. */
    public void remove(long id) {
        this.entries.remove(id);
    }

    /** Turns an id from {@link #register} into the {@code void*} to hand to native code. */
    public static MemorySegment userData(long id) {
        return MemorySegment.ofAddress(id);
    }
}
