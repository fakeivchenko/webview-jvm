package dev.ivchenko.webview.spi;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;

/**
 * A platform's webview implementation, discovered through {@link java.util.ServiceLoader}.
 *
 * <p>Implementations register themselves in {@code META-INF/services/dev.ivchenko.webview.spi.WebviewBackendProvider}.
 * Keeping the lookup on the service path is what lets {@code webview-jvm-core} stay free of any platform code: a
 * backend jar is simply present or absent on the runtime classpath.</p>
 */
public interface WebviewBackendProvider {
    /** Short identifier for diagnostics, e.g. {@code "gtk3-webkit2gtk-4.1"}. */
    String name();

    /**
     * Whether this backend can run here - right operating system, and the native libraries it binds are actually
     * present. Must not initialise the toolkit or start any thread: it is called on every candidate, including ones
     * that will not be chosen.
     */
    boolean isSupported();

    /**
     * Ranking among supported providers; the highest wins. Use it when a platform has more than one viable
     * implementation (say GTK 4 preferred over GTK 3).
     */
    default int priority() {
        return 0;
    }

    /** Creates a window; only called after {@link #isSupported()} returned {@code true}. */
    WebviewBackend create(WebviewParameters parameters);
}
