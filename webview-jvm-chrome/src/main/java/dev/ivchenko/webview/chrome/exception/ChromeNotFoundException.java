package dev.ivchenko.webview.chrome.exception;

/** No Chrome, Chromium or Edge executable could be located on this machine. */
public class ChromeNotFoundException extends RuntimeException {
    public ChromeNotFoundException(String message) {
        super(message);
    }
}
