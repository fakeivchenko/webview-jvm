package dev.ivchenko.webview.util;

import lombok.experimental.UtilityClass;

/**
 * Last-resort handling for throwables that must not escape.
 *
 * <p>Native toolkits call back into Java on their own threads. Letting a Java throwable unwind into C is undefined
 * behaviour, so every callback and every listener invocation funnels failures here instead of propagating them.</p>
 */
@UtilityClass
public class ThrowableUtil {
    /** Hands {@code t} to the current thread's uncaught exception handler and returns. */
    public void report(Throwable t) {
        Thread current = Thread.currentThread();
        current.getUncaughtExceptionHandler().uncaughtException(current, t);
    }
}
