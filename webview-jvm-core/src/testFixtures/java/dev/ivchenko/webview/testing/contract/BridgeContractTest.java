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
import java.util.function.Function;

/** Serving the application's own files, and calling Java from the page. */
@Tag(Tags.DISPLAY)
@Timeout(90)
public abstract class BridgeContractTest extends DisplayContractTest {
    @Test
    void servesClasspathResourcesAndCallsBackIntoJava() throws Exception {
        try (WebviewBackend webview = Webview.create(WebviewParameters.builder().title("bridge").build())) {
            var loaded = Loads.expectFinished(webview);
            webview.bind("reverse", payload -> new StringBuilder(payload).reverse().toString());
            webview.bind("boom", _ -> {
                throw new IllegalStateException("handler exploded");
            });

            webview.loadResource("test-app/index.html");
            loaded.get(60, TimeUnit.SECONDS);

            Assertions.assertEquals("bridge test", Loads.eval(webview, "document.querySelector('h1').textContent"));
            Assertions.assertEquals("true", Loads.eval(webview, "String(window.__scriptLoaded)"));
            Assertions.assertEquals("rgb(17, 34, 51)",
                    Loads.eval(webview, "getComputedStyle(document.querySelector('h1')).color"));

            // Trailing `undefined` because eval cannot hand a Promise back to Java.
            Loads.eval(webview, "window.reverse('webview').then(value => { window.__reversed = value; }); undefined;");
            Assertions.assertEquals("weivbew", Loads.awaitValue(webview, "window.__reversed"));

            Loads.eval(webview, "window.boom('x').catch(error => { window.__failure = error.message; }); undefined;");
            Assertions.assertEquals("handler exploded", Loads.awaitValue(webview, "window.__failure"));
        }
    }

    @Test
    void bindingAfterThePageLoadedStillWorks() throws Exception {
        try (WebviewBackend webview = Webview.create()) {
            var loaded = Loads.expectFinished(webview);
            webview.loadResource("test-app/index.html");
            loaded.get(60, TimeUnit.SECONDS);

            webview.bind("late", payload -> "late:" + payload);
            Loads.eval(webview, "window.late('x').then(value => { window.__late = value; }); undefined;");
            Assertions.assertEquals("late:x", Loads.awaitValue(webview, "window.__late"));
        }
    }

    @Test
    void payloadsSurviveTheRoundTripIntact() throws Exception {
        try (WebviewBackend webview = Webview.create()) {
            var loaded = Loads.expectFinished(webview);
            webview.bind("echo", Function.identity());
            webview.loadResource("test-app/index.html");
            loaded.get(60, TimeUnit.SECONDS);

            // Quotes, backslashes, newlines and non-Latin text: none may break either side's quoting.
            Loads.eval(webview, """
                    window.echo('a "b" \\\\ c\\nпривет 世界').then(value => { window.__echo = value; }); undefined;""");
            Assertions.assertEquals("a \"b\" \\ c\nпривет 世界", Loads.awaitValue(webview, "window.__echo"));

            Loads.eval(webview, "window.echo({ n: 1, s: 'x' }).then(value => { window.__json = value; }); undefined;");
            Assertions.assertEquals("{\"n\":1,\"s\":\"x\"}", Loads.awaitValue(webview, "window.__json"));
        }
    }
}
