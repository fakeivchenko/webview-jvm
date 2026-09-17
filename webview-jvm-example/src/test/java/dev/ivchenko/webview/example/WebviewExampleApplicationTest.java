package dev.ivchenko.webview.example;

import dev.ivchenko.webview.Webview;
import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.testing.DisplayAssumptions;
import dev.ivchenko.webview.testing.Loads;
import dev.ivchenko.webview.testing.Screenshots;
import dev.ivchenko.webview.testing.Tags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/** Opens the example the way {@code main} does and checks that the page filled itself in from Java. */
@Tag(Tags.DISPLAY)
@Timeout(90)
class WebviewExampleApplicationTest {
    @Test
    void thePageAsksJavaAboutItself() throws Exception {
        DisplayAssumptions.assumeDisplay();
        String backend = Webview.provider().map(WebviewBackendProvider::name).orElseThrow();

        try (WebviewBackend webview = WebviewExampleApplication.open()) {
            var loaded = Loads.expectFinished(webview);
            webview.show();
            loaded.get(60, TimeUnit.SECONDS);

            // The page fills these in once its systemInfo() promise resolves, shortly after the load event.
            Assertions.assertEquals(backend, awaitText(webview, "backend"));
            Assertions.assertEquals(webview.engine(), awaitText(webview, "engine"));
            Assertions.assertEquals("webview-jvm example", webview.title());
            Screenshots.capture("example-app");
        }
    }

    private static String awaitText(WebviewBackend webview, String id) throws Exception {
        String text = "";
        for (int attempt = 0; attempt < 100; attempt++) {
            text = Loads.eval(webview, "document.getElementById('%s').textContent".formatted(id));
            if (!text.isEmpty() && !"…".equals(text)) return text;
            Thread.sleep(100);
        }
        return text;
    }
}
