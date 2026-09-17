package dev.ivchenko.webview.macos;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.util.PlatformUtil;

/**
 * Registers the Cocoa / WKWebView backend with {@link dev.ivchenko.webview.Webview}.
 *
 * <p>Every Mac has AppKit and WebKit, so the platform check is the whole test; there is no library that could be
 * missing.</p>
 */
public class MacWebviewBackendProvider implements WebviewBackendProvider {
    @Override
    public String name() {
        return "cocoa-wkwebview";
    }

    @Override
    public boolean isSupported() {
        return PlatformUtil.isMacOs();
    }

    @Override
    public WebviewBackend create(WebviewParameters parameters) {
        return new MacWebviewBackend(parameters);
    }
}
