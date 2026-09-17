package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.util.PlatformUtil;
import dev.ivchenko.webview.windows.binding.WebView2Runtime;

/**
 * Registers the WebView2 backend with {@link dev.ivchenko.webview.Webview}.
 *
 * <p>Discovered through {@code META-INF/services}; on any other operating system it steps aside before touching a
 * single Windows library, so the jar is harmless on a Linux or macOS classpath.</p>
 */
public class WindowsWebviewBackendProvider implements WebviewBackendProvider {
    @Override
    public String name() {
        return "win32-webview2";
    }

    @Override
    public boolean isSupported() {
        if (!PlatformUtil.isWindows()) return false;
        return WebView2Runtime.library().isPresent();
    }

    @Override
    public WebviewBackend create(WebviewParameters parameters) {
        return new WindowsWebviewBackend(parameters);
    }
}
