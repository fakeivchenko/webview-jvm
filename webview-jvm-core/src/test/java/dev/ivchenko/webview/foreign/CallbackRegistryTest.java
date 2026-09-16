package dev.ivchenko.webview.foreign;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;

class CallbackRegistryTest {
    @Test
    void idsRoundTripThroughUserDataPointers() {
        CallbackRegistry<String> registry = new CallbackRegistry<>();
        long id = registry.register("window");

        MemorySegment userData = CallbackRegistry.userData(id);
        Assertions.assertEquals(id, userData.address());
        Assertions.assertEquals("window", registry.lookup(userData));
    }

    @Test
    void idsAreUniqueAndNeverZero() {
        CallbackRegistry<Object> registry = new CallbackRegistry<>();
        long first = registry.register(new Object());
        long second = registry.register(new Object());
        Assertions.assertNotEquals(0, first);
        Assertions.assertNotEquals(first, second);
    }

    @Test
    void unregisteredEntriesAreGone() {
        CallbackRegistry<String> registry = new CallbackRegistry<>();
        long id = registry.register("x");
        Assertions.assertEquals("x", registry.unregister(CallbackRegistry.userData(id)));
        Assertions.assertNull(registry.lookup(id));
        Assertions.assertNull(registry.unregister(id));
    }
}
