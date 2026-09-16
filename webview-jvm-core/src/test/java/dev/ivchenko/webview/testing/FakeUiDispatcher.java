package dev.ivchenko.webview.testing;

import dev.ivchenko.webview.ui.UiDispatcher;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** A {@link UiDispatcher} over a queue instead of a toolkit: the same threading, no native code. */
public final class FakeUiDispatcher extends UiDispatcher {
    private final BlockingQueue<Boolean> wakeUps = new LinkedBlockingQueue<>();
    private final Throwable initFailure;

    public FakeUiDispatcher() {
        this(null);
    }

    /** A dispatcher whose toolkit fails to initialise with {@code initFailure}. */
    public FakeUiDispatcher(Throwable initFailure) {
        super("fake-ui");
        this.initFailure = initFailure;
    }

    @Override
    protected void initialize() {
        if (this.initFailure instanceof RuntimeException runtime) throw runtime;
        if (this.initFailure instanceof Error error) throw error;
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    protected void runEventLoop() {
        try {
            while (true) {
                this.wakeUps.take();
                this.drainTasks();
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected void wakeUp() {
        this.wakeUps.add(Boolean.TRUE);
    }
}
