package dev.ivchenko.webview.bridge;

import dev.ivchenko.webview.util.ScriptUtil;
import lombok.experimental.UtilityClass;

/**
 * The JavaScript half of the Java&#8596;page bridge, and the wire format both halves agree on.
 *
 * <p>Deliberately platform neutral. Every engine worth supporting can inject a script before the document loads and
 * hand a string back to the host, but each names that channel differently: WebKitGTK and WKWebView use
 * {@code window.webkit.messageHandlers.<name>.postMessage}, WebView2 uses {@code window.chrome.webview.postMessage}. So
 * the backend supplies one expression - how to post a string - and everything above it, the promise bookkeeping and the
 * message framing, is shared.</p>
 *
 * <p>A message is {@code id <US> name <US> payload}, with {@link #SEPARATOR} between the fields: a byte that cannot
 * occur in a JavaScript identifier and that callers are unlikely to put in a payload, which keeps the framing free of a
 * JSON dependency on either side.</p>
 */
@UtilityClass
public class BridgeProtocol {
    /** Name of the message channel; also the global the page-side runtime lives on. */
    public final String CHANNEL = "__webviewBridge";

    /**
     * Field separator inside a bridge message.
     *
     * <p>ASCII unit separator, not NUL: the message reaches the host as a C string, so a NUL byte would truncate it at
     * the first field.</p>
     */
    public final String SEPARATOR = "\u001f";

    /**
     * The runtime injected into every document before it runs its own scripts.
     *
     * @param postMessage a JavaScript expression evaluating to a function that takes one string and delivers it to the
     *     host
     */
    public String bootstrapScript(String postMessage) {
        return """
                (function () {
                    if (window.%1$s) return;
                    const pending = new Map();
                    let counter = 0;
                    const post = %2$s;
                    window.%1$s = {
                        call(name, payload) {
                            const id = ++counter;
                            return new Promise((resolve, reject) => {
                                pending.set(id, { resolve, reject });
                                const text = payload === undefined ? ""
                                    : typeof payload === "string" ? payload : JSON.stringify(payload);
                                post(id + "\\u001f" + name + "\\u001f" + text);
                            });
                        },
                        settle(id, value, error) {
                            const entry = pending.get(id);
                            if (!entry) return;
                            pending.delete(id);
                            if (error === null) entry.resolve(value); else entry.reject(new Error(error));
                        }
                    };
                })();
                """.formatted(CHANNEL, postMessage);
    }

    /**
     * Exposes one bound name to the page as {@code window.<name>(payload)}, returning a promise. A string payload is
     * passed through as is; anything else is sent as JSON.
     */
    public String bindingScript(String name) {
        return "window[%s] = (payload) => window.%s.call(%s, payload);"
                .formatted(ScriptUtil.quote(name), CHANNEL, ScriptUtil.quote(name));
    }

    /** Completes the page-side promise {@code id} with {@code value}. */
    public String resolveScript(long id, String value) {
        return "window.%s.settle(%d, %s, null);".formatted(CHANNEL, id, ScriptUtil.quote(value));
    }

    /** Fails the page-side promise {@code id} with {@code message}. */
    public String rejectScript(long id, String message) {
        return "window.%s.settle(%d, null, %s);"
                .formatted(CHANNEL, id, ScriptUtil.quote(message == null ? "Handler failed" : message));
    }
}
