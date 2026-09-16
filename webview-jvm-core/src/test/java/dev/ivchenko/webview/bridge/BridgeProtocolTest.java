package dev.ivchenko.webview.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BridgeProtocolTest {
    @Test
    void separatorIsNotNul() {
        Assertions.assertEquals(String.valueOf((char) 0x1f), BridgeProtocol.SEPARATOR);
    }

    @Test
    void bootstrapEmbedsTheTransportAndTheChannel() {
        String script = BridgeProtocol.bootstrapScript("(m) => host.post(m)");
        Assertions.assertTrue(script.contains("const post = (m) => host.post(m);"));
        Assertions.assertTrue(script.contains("window." + BridgeProtocol.CHANNEL + " = {"));
        Assertions.assertTrue(script.contains("if (window." + BridgeProtocol.CHANNEL + ") return;"),
                "must be safe to inject twice");
    }

    @Test
    void bindingPublishesAWindowFunction() {
        Assertions.assertEquals(
                "window[\"reverse\"] = (payload) => window.__webviewBridge.call(\"reverse\", payload);",
                BridgeProtocol.bindingScript("reverse"));
    }

    @Test
    void resolveAndRejectQuoteTheirArguments() {
        Assertions.assertEquals("window.__webviewBridge.settle(7, \"a \\\"b\\\"\", null);",
                BridgeProtocol.resolveScript(7, "a \"b\""));
        Assertions.assertEquals("window.__webviewBridge.settle(7, null, \"boom\");",
                BridgeProtocol.rejectScript(7, "boom"));
        Assertions.assertEquals("window.__webviewBridge.settle(7, null, \"Handler failed\");",
                BridgeProtocol.rejectScript(7, null));
    }
}
