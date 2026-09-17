package dev.ivchenko.webview.ui;

import dev.ivchenko.webview.util.ThrowableUtil;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

/**
 * A {@link UiDispatcher} that owns its UI thread: it starts one, initialises the toolkit on it and runs the toolkit's
 * event loop there for the rest of the process lifetime.
 *
 * <p>A subclass supplies the toolkit specifics: {@link #initialize()}, {@link #runEventLoop()}, {@link #wakeUp()}.</p>
 */
public abstract class EventLoopDispatcher extends UiDispatcher {
    private final String threadName;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final CountDownLatch initialised = new CountDownLatch(1);

    private volatile Thread thread;
    private volatile Throwable initFailure;

    protected EventLoopDispatcher(String threadName) {
        this.threadName = threadName;
    }

    /**
     * Starts the UI thread and blocks until the toolkit is initialised. Idempotent; every later call simply verifies
     * that initialisation succeeded.
     *
     * @throws IllegalStateException if the toolkit could not be initialised
     */
    public final void start() {
        this.startThread();
        try {
            this.initialised.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + this.threadName, e);
        }
        this.checkInitialised();
    }

    @Override
    public final boolean isDispatchThread() {
        return Thread.currentThread() == this.thread;
    }

    @Override
    public final void post(Runnable task) {
        this.checkInitialised();
        this.tasks.add(task);
        this.wakeUp();
    }

    /**
     * Runs every queued task. A subclass calls this from whatever callback {@link #wakeUp()} schedules on the event
     * loop. Task failures are reported, never propagated, so that nothing unwinds into native code.
     */
    protected final void drainTasks() {
        Runnable task;
        while ((task = this.tasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                ThrowableUtil.report(t);
            }
        }
    }

    /**
     * Initialises the toolkit on the UI thread.
     *
     * @throws RuntimeException if the toolkit is unavailable; the failure is re-thrown to whoever calls
     *     {@link #start()} or {@link #post}
     */
    protected abstract void initialize();

    /** Runs the toolkit's event loop for the rest of the process lifetime. */
    protected abstract void runEventLoop();

    /**
     * Asks the event loop to call {@link #drainTasks()} soon. Called from arbitrary threads, so the underlying toolkit
     * primitive must be thread safe.
     */
    protected abstract void wakeUp();

    private synchronized void startThread() {
        if (this.thread != null) return;

        Thread worker = new Thread(this::loop, this.threadName);
        worker.setDaemon(true);
        this.thread = worker;
        worker.start();
    }

    private void loop() {
        try {
            this.initialize();
        } catch (Throwable t) {
            // Any failure, Error included: otherwise the latch never opens and start() hangs.
            this.initFailure = t;
        } finally {
            this.initialised.countDown();
        }
        if (this.initFailure == null) this.runEventLoop();
    }

    private void checkInitialised() {
        Throwable failure = this.initFailure;
        if (failure != null) throw new IllegalStateException("UI toolkit is not available", failure);
    }
}
