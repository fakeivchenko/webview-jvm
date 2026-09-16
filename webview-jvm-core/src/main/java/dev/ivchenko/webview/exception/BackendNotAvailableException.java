package dev.ivchenko.webview.exception;

/**
 * No webview backend on the classpath supports the machine the application is running on.
 *
 * <p>Either no backend artifact was added as a runtime dependency, or the ones present rejected this platform - on
 * Linux that usually means GTK or WebKitGTK is not installed. The message lists what was found, so the two cases are
 * distinguishable without a debugger.</p>
 */
public class BackendNotAvailableException extends RuntimeException {
    public BackendNotAvailableException(String message) {
        super(message);
    }
}
