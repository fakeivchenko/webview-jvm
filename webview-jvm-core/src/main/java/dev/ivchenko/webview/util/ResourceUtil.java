package dev.ivchenko.webview.util;

import dev.ivchenko.webview.exception.ResourceNotFoundException;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Reads the application's own files - HTML, CSS, scripts, images - out of the classpath, and defines the URL space they
 * are served under.
 *
 * <p>Pages are loaded from a custom scheme rather than {@code file://} so that relative links, {@code fetch} and module
 * imports resolve the way they would on a web server, and so the same markup works on every backend: WebKitGTK
 * registers the scheme on its web context, WKWebView uses a URL scheme handler, WebView2 intercepts the request.</p>
 */
@UtilityClass
public class ResourceUtil {
    /** Scheme the application's own resources are served under. */
    public final String SCHEME = "app";

    /** Authority of that scheme; a constant, since there is no real host involved. */
    public final String HOST = "local";

    /** The URL for {@code path}, for example {@code app://local/app/index.html}. */
    public String uri(String path) {
        return "%s://%s/%s".formatted(SCHEME, HOST, path.startsWith("/") ? path.substring(1) : path);
    }

    /**
     * Loads {@code path} (for example {@code "app/index.html"}) from the classpath.
     *
     * @throws ResourceNotFoundException if no such resource exists
     */
    public byte[] read(String path) {
        String normalised = path.startsWith("/") ? path.substring(1) : path;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = ResourceUtil.class.getClassLoader();

        try (InputStream stream = loader.getResourceAsStream(normalised)) {
            if (stream == null) throw new ResourceNotFoundException("No classpath resource: " + normalised);
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read classpath resource: " + normalised, e);
        }
    }
}
