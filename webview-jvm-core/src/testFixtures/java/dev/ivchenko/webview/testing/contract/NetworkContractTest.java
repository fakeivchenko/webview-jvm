package dev.ivchenko.webview.testing.contract;

import dev.ivchenko.webview.Webview;
import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.testing.Loads;
import dev.ivchenko.webview.testing.Tags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * Loads a real site over the internet: TLS, redirects, a heavy page. Not part of any default run -
 * {@code ./gradlew networkTest} - because it depends on a network and on a third party.
 */
@Tag(Tags.DISPLAY)
@Tag(Tags.NETWORK)
@Timeout(120)
public abstract class NetworkContractTest extends DisplayContractTest {
    @Test
    void rendersGoogle() throws Exception {
        try (WebviewBackend webview = Webview.create(WebviewParameters.builder().title("google.com").build())) {
            var loaded = Loads.expectFinished(webview);
            webview.show();
            webview.navigate("https://www.google.com/");
            loaded.get(90, TimeUnit.SECONDS);

            Assertions.assertTrue(Loads.eval(webview, "document.title").toLowerCase().contains("google"));
            Assertions.assertEquals("true", Loads.eval(webview, "String(document.body.innerText.length > 0)"));
        }
    }
}
