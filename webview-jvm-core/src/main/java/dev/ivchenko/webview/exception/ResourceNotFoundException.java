package dev.ivchenko.webview.exception;

/** A classpath resource a page asked for does not exist. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
