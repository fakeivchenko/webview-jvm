package dev.ivchenko.webview.testing.contract;

import dev.ivchenko.webview.Webview;
import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.event.LoadEvent;
import dev.ivchenko.webview.testing.Loads;
import dev.ivchenko.webview.testing.LocalPages;
import dev.ivchenko.webview.testing.Screenshots;
import dev.ivchenko.webview.testing.Tags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * Opens a real window and renders a real {@code http://} page, served from this machine.
 *
 * <p>A backend module subclasses this so the same expectations hold on every platform; the only platform-specific fact
 * is which backend class {@link dev.ivchenko.webview.Webview} is expected to pick.</p>
 */
@Tag(Tags.DISPLAY)
@Timeout(60)
public abstract class WindowContractTest extends DisplayContractTest {
    /** The backend {@link Webview#create} must select on the machine running the tests. */
    protected abstract Class<? extends WebviewBackend> expectedBackendType();

    @Test
    void opensAWindowAndRendersAPage() throws Exception {
        WebviewParameters parameters = WebviewParameters.builder()
                .title("webview-jvm :: page")
                .width(800)
                .height(600)
                .build();

        try (LocalPages pages = new LocalPages();
             WebviewBackend webview = Webview.create(parameters)) {
            Assertions.assertInstanceOf(this.expectedBackendType(), webview, "Unexpected backend for this platform");
            String url = pages.page("/hello.html",
                    "<!DOCTYPE html><html><head><title>Local page</title></head><body><h1>Rendered</h1></body></html>");

            var loaded = Loads.expectFinished(webview);
            webview.show();
            webview.navigate(url);

            LoadEvent event = loaded.get(30, TimeUnit.SECONDS);
            Assertions.assertEquals(url, event.uri());
            Assertions.assertEquals(url, webview.url());
            Assertions.assertEquals("Local page", Loads.eval(webview, "document.title"));
            Assertions.assertEquals("Rendered", Loads.eval(webview, "document.querySelector('h1').textContent"));
            Screenshots.capture("window-local-page");

            Assertions.assertEquals("webview-jvm :: page", webview.title());
            Assertions.assertTrue(webview.engine().matches(".+ \\d+(\\.\\d+)+"), "engine: " + webview.engine());
            Assertions.assertTrue(webview.width() > 0 && webview.height() > 0, "Window has no size");
        }
    }

    @Test
    void windowPropertiesRoundTrip() throws Exception {
        try (WebviewBackend webview = Webview.create(WebviewParameters.builder().title("before").build())) {
            webview.show();

            webview.title("after");
            Assertions.assertEquals("after", webview.title());

            Assertions.assertTrue(webview.isResizable());
            webview.resizable(false);
            Assertions.assertFalse(webview.isResizable());

            webview.resizable(true);
            webview.size(640, 480);
            // Toolkits apply the request asynchronously and offer no completion signal; polling is the point.
            for (int attempt = 0; attempt < 50 && webview.width() != 640; attempt++) {
                //noinspection BusyWait
                Thread.sleep(50);
            }
            Assertions.assertEquals(640, webview.width());
            Assertions.assertEquals(480, webview.height());
        }
    }

    @Test
    void htmlReplacesThePage() throws Exception {
        try (WebviewBackend webview = Webview.create()) {
            var loaded = Loads.expectFinished(webview);
            webview.html("<!DOCTYPE html><html><head><title>Inline</title></head><body>hi</body></html>");
            loaded.get(30, TimeUnit.SECONDS);

            Assertions.assertEquals("Inline", Loads.eval(webview, "document.title"));
            Assertions.assertEquals("hi", Loads.eval(webview, "document.body.textContent"));
        }
    }
}
