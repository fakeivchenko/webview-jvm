package dev.ivchenko.webview.ui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
            return result.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw e;
        }
    }
}
