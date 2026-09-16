package dev.ivchenko.webview.testing;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.event.LoadEvent;
import dev.ivchenko.webview.event.LoadState;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Waits for page loads, so tests read state only once the engine has settled. */
public final class Loads {
    private Loads() {
    }

    /** Completes on {@link LoadState#FINISHED}; fails on {@link LoadState#FAILED}. */
    public static CompletableFuture<LoadEvent> expectFinished(WebviewBackend webview) {
        CompletableFuture<LoadEvent> loaded = new CompletableFuture<>();
        webview.onLoad(event -> {
            if (event.state() == LoadState.FAILED) {
                loaded.completeExceptionally(new IllegalStateException("Load failed: " + event.message()));
            } else if (event.state() == LoadState.FINISHED) {
                loaded.complete(event);
            }
        });
        return loaded;
    }

    /** Completes on the first {@link LoadState#FAILED}. */
    public static CompletableFuture<LoadEvent> expectFailed(WebviewBackend webview) {
        CompletableFuture<LoadEvent> failed = new CompletableFuture<>();
        webview.onLoad(event -> {
            if (event.state() == LoadState.FAILED) failed.complete(event);
        });
        return failed;
    }

    /** Evaluates {@code script} and waits for the answer. */
    public static String eval(WebviewBackend webview, String script) throws Exception {
        return webview.eval(script).get(20, TimeUnit.SECONDS);
    }

    /** Polls a page expression until it is no longer {@code undefined}. */
    public static String awaitValue(WebviewBackend webview, String expression) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String value = eval(webview, "String(%s)".formatted(expression));
            if (!"undefined".equals(value)) return value;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + expression);
    }
}
