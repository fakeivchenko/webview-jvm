package dev.ivchenko.webview.ui;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * The single thread a backend touches its toolkit from, and the way work from other threads reaches it.
 *
 * <p>Native UI toolkits are single threaded: the toolkit may only be touched from one thread. Which thread that is
 * differs - GTK and Win32 accept any thread as long as it stays the same one, so {@link EventLoopDispatcher} starts its
 * own; Cocoa insists on the process main thread, which the library does not own. This class is the part every backend
 * shares: identify the thread, hand it work, and run a call inline when already on it.</p>
 */
public abstract class UiDispatcher {
    /**
     * How long {@link #call} waits for the UI thread. Nothing a window does takes anywhere near this; a UI thread
     * that stays silent for a minute is stuck, and an exception says so where a hang would not.
     */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

    /** Whether the calling thread is the UI thread; calls from it must not block on it. */
    public abstract boolean isDispatchThread();

    /** Runs {@code task} on the UI thread without waiting for it. */
    public abstract void post(Runnable task);

    /**
     * Runs one unit of work on the UI thread. The default simply calls it; a toolkit that needs something around every
     * call - Cocoa wants an autorelease pool - overrides this.
     */
    protected <T> T execute(Supplier<T> action) {
        return action.get();
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
     *
     * @throws IllegalStateException if the UI thread does not answer within a minute
     */
    public final <T> T call(Supplier<T> action) {
        if (this.isDispatchThread()) return this.execute(action);

        CompletableFuture<T> result = new CompletableFuture<>();
        this.post(() -> {
            try {
                result.complete(this.execute(action));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(CALL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } catch (TimeoutException _) {
            throw new IllegalStateException("The UI thread did not answer within " + CALL_TIMEOUT.toSeconds() + " s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the UI thread", e);
        }
    }
}
