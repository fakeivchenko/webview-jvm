package dev.ivchenko.webview;

import lombok.Builder;

/**
 * The window a backend should create.
 *
 * <p>Every component has a usable default, applied by the canonical constructor, so a caller only states what it
 * actually cares about:</p>
 *
 * <pre>{@code
 * WebviewParameters.builder().title("Docs").width(1280).height(800).build()
 * }</pre>
 *
 * @param title window title
 * @param width initial window width in pixels; non-positive means the default
 * @param height initial window height in pixels; non-positive means the default
 * @param url the URL to load once the window exists, or {@code null} to leave it blank. A convenience for simple cases:
 *     {@link Webview#create} navigates before returning, so to observe a load from its very first event, leave this
 *     unset, register the listener, and call {@link WebviewBackend#navigate} yourself.
 * @param devServerUrl where the frontend is being served from during development, for example
 *     {@code http://localhost:5173}. When set, {@link WebviewBackend#loadResource} opens this instead of the bundled
 *     files, so a Vite or webpack dev server with hot reload drives the window while the Java side stays exactly as it
 *     will ship. Defaults to the {@code webview.devServerUrl} system property, then the {@code WEBVIEW_DEV_SERVER_URL}
 *     environment variable, then nothing - so switching modes never needs a code change.
 */
@Builder(toBuilder = true)
public record WebviewParameters(String title, int width, int height, String url, String devServerUrl) {
    public static final String DEV_SERVER_URL_PROPERTY = "webview.devServerUrl";
    public static final String DEV_SERVER_URL_VARIABLE = "WEBVIEW_DEV_SERVER_URL";

    private static final String DEFAULT_TITLE = "webview-jvm";
    private static final int DEFAULT_WIDTH = 1024;
    private static final int DEFAULT_HEIGHT = 768;

    public WebviewParameters {
        if (title == null) title = DEFAULT_TITLE;
        if (width <= 0) width = DEFAULT_WIDTH;
        if (height <= 0) height = DEFAULT_HEIGHT;
        if (devServerUrl == null) devServerUrl = System.getProperty(DEV_SERVER_URL_PROPERTY);
        if (devServerUrl == null) devServerUrl = System.getenv(DEV_SERVER_URL_VARIABLE);
        if (devServerUrl != null && devServerUrl.isBlank()) devServerUrl = null;
    }

    /** A window with every default: untitled, 1024&times;768, blank. */
    public static WebviewParameters defaults() {
        return builder().build();
    }

    /** Whether the frontend comes from a dev server rather than from the classpath. */
    public boolean isDevelopment() {
        return this.devServerUrl != null;
    }
}
