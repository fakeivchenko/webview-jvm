package dev.ivchenko.webview;

import dev.ivchenko.webview.event.LoadEvent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A single native webview window.
 *
 * <p>Implementations own a native toolkit that is almost always single threaded. Methods declared here may be called
 * from any thread: the backend is responsible for marshalling them onto its UI thread. Getters block until the UI
 * thread has answered.</p>
 */
public interface WebviewBackend extends AutoCloseable {
    /** The window title. */
    String title();

    /**
     * The web engine drawing the page, with its version - for example {@code "WebKitGTK 2.48.3"},
     * {@code "WebView2 138.0.3351.65"} or {@code "Chromium 153.0.8010.36"}. Diagnostics, not a contract: the exact
     * wording is the backend's.
     */
    String engine();

    /** Sets the window title. */
    void title(String title);

    /** Current window width in pixels. */
    int width();

    /** Current window height in pixels. */
    int height();

    /** Resizes the window; before {@link #show()} this sets the size it opens with. */
    void size(int width, int height);

    /** Whether the user can resize the window. */
    boolean isResizable();

    /** Allows or forbids resizing by the user. */
    void resizable(boolean resizable);

    /** Starts loading {@code url}; progress arrives through {@link #onLoad(Consumer)}. */
    void navigate(String url);

    /** The URL currently displayed, or {@code null} before the first navigation. */
    String url();

    /** Replaces the page with {@code html}, as if loaded from an unnamed origin. */
    void html(String html);

    /**
     * Loads a page shipped inside the application, addressed by its classpath path - for example
     * {@code loadResource("app/index.html")}.
     *
     * <p>The page is served under a custom scheme, so links, stylesheets, scripts and {@code fetch} inside it resolve
     * relative to that path exactly as they would on a web server.</p>
     */
    void loadResource(String path);

    /**
     * Evaluates {@code script} in the current page and completes with its result rendered as a string. The future fails
     * with {@link dev.ivchenko.webview.exception.ScriptEvaluationFailedException} if the script throws.
     *
     * <p>The result has to be a value the engine can hand back - a string, a number, a boolean. An object the engine
     * cannot serialise, a {@code Promise} above all, fails the future rather than waiting for it. To run something
     * asynchronous, let the script store its outcome ({@code someAsyncCall().then(v => window.result = v); undefined;})
     * and read that afterwards, or drive the call from the page through {@link #bind(String, Function)}.</p>
     */
    CompletableFuture<String> eval(String script);

    /**
     * Exposes {@code handler} to the page as {@code window.<name>(payload)}, which returns a promise resolving to
     * whatever the handler returns.
     *
     * <p>This is the page&#8594;Java direction; {@link #eval(String)} is the other one. Handlers run off the UI thread,
     * so blocking work inside one does not freeze the window, and a handler that throws rejects the page-side promise
     * instead of being swallowed.</p>
     *
     * @param name a JavaScript identifier to publish on {@code window}
     */
    void bind(String name, Function<String, String> handler);

    /** Registers a listener notified on every page load transition. */
    void onLoad(Consumer<LoadEvent> listener);

    /** Makes the window visible. */
    void show();

    /**
     * Blocks the calling thread until the window is closed, either by the user or by {@link #close()}. Must not be
     * called from the backend's UI thread.
     */
    void run();

    /** Destroys the window and releases the native resources. Idempotent. */
    @Override
    void close();
}
