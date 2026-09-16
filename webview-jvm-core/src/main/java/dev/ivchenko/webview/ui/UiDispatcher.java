package dev.ivchenko.webview.ui;

import dev.ivchenko.webview.util.ThrowableUtil;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/**
 * A dedicated UI thread with a task queue, shared by every backend.
 *
 * <p>Native UI toolkits are single threaded: the toolkit may only be touched from the thread that initialised it. This
 * class owns that thread, runs the toolkit's event loop on it, and marshals work from other threads onto it, so
 * backends can offer a thread-safe API on top.</p>
 *
 * <p>A subclass supplies the toolkit specifics: {@link #initialize()}, {@link #runEventLoop()}, {@link #wakeUp()}.</p>
 */
public abstract class UiDispatcher {
    private final String threadName;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final CountDownLatch initialised = new CountDownLatch(1);

    private volatile Thread thread;
    private volatile Throwable initFailure;

    protected UiDispatcher(String threadName) {
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

    /** Whether the calling thread is the UI thread; calls from it must not block on it. */
    public final boolean isDispatchThread() {
        return Thread.currentThread() == this.thread;
    }

    /** Runs {@code task} on the UI thread without waiting for it. */
    public final void post(Runnable task) {
        this.checkInitialised();
        this.tasks.add(task);
        this.wakeUp();
    }

    /** Runs {@code task} on the UI thread and waits for it to finish. */
    public final void run(Runnable task) {
        this.call(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Runs {@code action} on the UI thread and returns its result. Failures surface with their original type; calls
     * made from the UI thread itself run inline rather than deadlocking.
     */
    public final <T> T call(Supplier<T> action) {
        if (this.isDispatchThread()) return action.get();

        CompletableFuture<T> result = new CompletableFuture<>();
        this.post(() -> {
            try {
                result.complete(action.get());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw e;
        }
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
