package dev.ivchenko.webview.exception;

/**
 * A script passed to {@link dev.ivchenko.webview.WebviewBackend#eval(String)} threw, or the engine refused to run it.
 *
 * <p>Declared in core rather than in a backend: every engine can fail this way, and callers should not have to catch a
 * different type per platform. It arrives through the returned future, never from the {@code eval} call itself, because
 * evaluation is asynchronous.</p>
 */
public class ScriptEvaluationFailedException extends RuntimeException {
    public ScriptEvaluationFailedException(String message) {
        super(message);
    }
}
