package dev.ivchenko.webview.gtk;

import dev.ivchenko.webview.util.PlatformUtil;
import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.foreign.NativeLibraries;
import dev.ivchenko.webview.spi.WebviewBackendProvider;

/**
 * Registers the GTK 3 / WebKitGTK 4.1 backend with {@link dev.ivchenko.webview.Webview}.
 *
 * <p>Discovered through {@code META-INF/services}, so simply having this jar on the classpath is what makes the backend
 * available on Linux and the BSDs.</p>
 */
public final class GtkWebviewBackendProvider implements WebviewBackendProvider {
    @Override
    public String name() {
        return "gtk3-webkit2gtk-4.1";
    }

    @Override
    public boolean isSupported() {
        // Check the cheap thing first: probing libraries means dlopen, and webkit2gtk is ~90 MB.
        if (!PlatformUtil.isUnixDesktop()) return false;

        return NativeLibraries.isLoadable("libgtk-3.so.0", "libgtk-3.so")
                && NativeLibraries.isLoadable("libwebkit2gtk-4.1.so.0", "libwebkit2gtk-4.1.so");
    }

    @Override
    public WebviewBackend create(WebviewParameters parameters) {
        return new GtkWebviewBackend(parameters);
    }
}
