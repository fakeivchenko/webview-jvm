package dev.ivchenko.webview;

import dev.ivchenko.webview.exception.BackendNotAvailableException;
import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.util.PlatformUtil;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Entry point: creates a webview using whichever backend fits the machine it runs on.
 *
 * <pre>{@code
 * try (WebviewBackend webview = Webview.create(WebviewParameters.builder()
 *         .title("Docs")
 *         .url("https://example.com")
 *         .build())) {
 *     webview.run();
 * }
 * }</pre>
 *
 * <p>Backends are found with {@link ServiceLoader}, so selection is decided by what is on the runtime classpath - put
 * {@code webview-jvm-gtk} there for Linux, and the corresponding artifact for another platform, without changing any
 * code.</p>
 */
@UtilityClass
public class Webview {
    /**
     * Creates a window with {@link WebviewParameters#defaults()}.
     *
     * @throws BackendNotAvailableException if no backend supports this machine
     */
    public WebviewBackend create() {
        return create(WebviewParameters.defaults());
    }

    /**
     * Creates a window using the highest-priority supported backend, navigating to {@link WebviewParameters#url()} when
     * one is set.
     *
     * @throws BackendNotAvailableException if no backend supports this machine
     */
    public WebviewBackend create(WebviewParameters parameters) {
        WebviewBackendProvider provider = provider()
                .orElseThrow(() -> new BackendNotAvailableException(noBackendMessage()));
        WebviewBackend backend = provider.create(parameters);
        if (parameters.url() != null) backend.navigate(parameters.url());
        return backend;
    }

    /** The backend that {@link #create} would use, if any. */
    public Optional<WebviewBackendProvider> provider() {
        return providers().stream()
                .filter(WebviewBackendProvider::isSupported)
                .max(Comparator.comparingInt(WebviewBackendProvider::priority));
    }

    /** Every backend on the classpath, supported or not. Useful for diagnostics. */
    public List<WebviewBackendProvider> providers() {
        List<WebviewBackendProvider> found = new ArrayList<>();
        ServiceLoader.load(WebviewBackendProvider.class, WebviewBackendProvider.class.getClassLoader())
                .forEach(found::add);
        return List.copyOf(found);
    }

    private String noBackendMessage() {
        List<WebviewBackendProvider> providers = providers();
        if (providers.isEmpty()) {
            return "No webview backend on the classpath. Add one, for example webview-jvm-gtk on Linux.";
        }
        String rejected = providers.stream()
                .map(provider -> provider.name() + " (unsupported here)")
                .collect(Collectors.joining(", "));
        return "No webview backend supports this machine (" + PlatformUtil.osName() + "). Found: " + rejected;
    }
}
