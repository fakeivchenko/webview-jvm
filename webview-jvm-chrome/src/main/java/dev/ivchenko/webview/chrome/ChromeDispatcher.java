package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.ui.EventLoopDispatcher;

import java.util.concurrent.Semaphore;

/**
 * The single thread the Chrome backend serialises its work on.
 *
 * <p>The protocol itself has no thread affinity, but {@link dev.ivchenko.webview.AbstractWebviewBackend} expects one
 * dispatch thread per backend family, and ordering the browser's events and the application's calls on one thread
 * keeps the backend as simple as the native ones.</p>
 */
class ChromeDispatcher extends EventLoopDispatcher {
    private static final ChromeDispatcher INSTANCE = new ChromeDispatcher();

    private final Semaphore wake = new Semaphore(0);

    private ChromeDispatcher() {
        super("webview-chrome");
    }

    static ChromeDispatcher instance() {
        INSTANCE.start();
        return INSTANCE;
    }

    @Override
    protected void initialize() {
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    protected void runEventLoop() {
        while (true) {
            this.wake.acquireUninterruptibly();
            this.wake.drainPermits();
            this.drainTasks();
        }
    }

    @Override
    protected void wakeUp() {
        this.wake.release();
    }
}
