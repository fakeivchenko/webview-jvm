package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.chrome.exception.ChromeNotFoundException;
import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.util.PlatformUtil;

/**
 * <b>Experimental.</b> Registers the Chrome fallback with {@link dev.ivchenko.webview.Webview}.
 *
 * <p>Ranked below every native backend: it is chosen only when none of them supports the machine - or when asked for
 * by name through {@code -Dwebview.backend=chromium}. Not offered on macOS, where WKWebView is part of the system and
 * a fallback has nothing to fall back from.</p>
 */
public class ChromeWebviewBackendProvider implements WebviewBackendProvider {
    /** The name this backend answers to. */
    public static final String NAME = "chromium";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isSupported() {
        return !PlatformUtil.isMacOs() && ChromeLocator.find().isPresent();
    }

    @Override
    public int priority() {
        return -100;
    }

    @Override
    public WebviewBackend create(WebviewParameters parameters) {
        return new ChromeWebviewBackend(ChromeLocator.find().orElseThrow(() -> new ChromeNotFoundException(
                "No Chrome, Chromium or Edge found; set -D" + ChromeLocator.CHROME_PROPERTY + " to its executable")),
                parameters);
    }
}
