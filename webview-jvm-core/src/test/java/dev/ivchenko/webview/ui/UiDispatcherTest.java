package dev.ivchenko.webview.ui;

import dev.ivchenko.webview.testing.FakeUiDispatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Timeout(10)
class UiDispatcherTest {
    @Test
    void runsWorkOnItsOwnThread() {
        FakeUiDispatcher dispatcher = new FakeUiDispatcher();
        dispatcher.start();

        Thread worker = dispatcher.call(Thread::currentThread);
        Assertions.assertEquals("fake-ui", worker.getName());
        Assertions.assertNotSame(Thread.currentThread(), worker);
        Assertions.assertTrue(worker.isDaemon(), "the UI thread must not keep the JVM alive");
    }

    @Test
    void callFromTheDispatchThreadRunsInlineInsteadOfDeadlocking() {
        FakeUiDispatcher dispatcher = new FakeUiDispatcher();
        dispatcher.start();

        boolean inline = dispatcher.call(() -> dispatcher.call(dispatcher::isDispatchThread));
        Assertions.assertTrue(inline);
    }

    @Test
    void failuresKeepTheirType() {
        FakeUiDispatcher dispatcher = new FakeUiDispatcher();
        dispatcher.start();

        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
                () -> dispatcher.run(() -> {
                    throw new IllegalStateException("from the UI thread");
                }));
        Assertions.assertEquals("from the UI thread", failure.getMessage());
    }

    @Test
    void postDoesNotWaitAndPreservesOrder() throws Exception {
        FakeUiDispatcher dispatcher = new FakeUiDispatcher();
        dispatcher.start();

        StringBuilder order = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        dispatcher.post(() -> order.append("a"));
        dispatcher.post(() -> order.append("b"));
        dispatcher.post(done::countDown);

        Assertions.assertTrue(done.await(5, TimeUnit.SECONDS));
        Assertions.assertEquals("ab", dispatcher.call(order::toString));
    }

    @Test
    void aThrowingTaskDoesNotKillTheLoop() {
        FakeUiDispatcher dispatcher = new FakeUiDispatcher();
        dispatcher.start();

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((_, _) -> { });
        try {
            dispatcher.post(() -> {
                throw new RuntimeException("posted failure");
            });
            Assertions.assertEquals("still alive", dispatcher.call(() -> "still alive"));
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }

    @Test
    void initialisationFailureSurfacesInsteadOfHanging() {
        FakeUiDispatcher dispatcher = new FakeUiDispatcher(new IllegalStateException("no display"));

        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, dispatcher::start);
        Assertions.assertEquals("no display", failure.getCause().getMessage());
        Assertions.assertThrows(IllegalStateException.class, () -> dispatcher.post(() -> { }));
    }

    @Test
    void initialisationErrorSurfacesInsteadOfHanging() {
        // An Error, not an exception: the latch must still open, or start() waits forever.
        FakeUiDispatcher dispatcher = new FakeUiDispatcher(new UnsatisfiedLinkError("libgtk missing"));

        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, dispatcher::start);
        Assertions.assertInstanceOf(UnsatisfiedLinkError.class, failure.getCause());
    }

    @Test
    void startIsIdempotent() {
        FakeUiDispatcher dispatcher = new FakeUiDispatcher();
        dispatcher.start();
        Thread first = dispatcher.call(Thread::currentThread);
        dispatcher.start();
        Assertions.assertSame(first, dispatcher.call(Thread::currentThread));
    }
}
