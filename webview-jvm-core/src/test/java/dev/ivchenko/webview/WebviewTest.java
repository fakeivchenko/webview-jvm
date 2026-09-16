package dev.ivchenko.webview;

import dev.ivchenko.webview.exception.BackendNotAvailableException;
import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.testing.FakeProviders;
import dev.ivchenko.webview.testing.FakeWebviewBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Backend discovery over the three fake providers registered on the test classpath. */
class WebviewTest {
    @AfterEach
    void restoreProviders() {
        FakeProviders.supported = true;
    }

    @Test
    void listsEveryProviderOnTheClasspath() {
        List<String> names = Webview.providers().stream().map(WebviewBackendProvider::name).sorted().toList();
        Assertions.assertEquals(List.of("fake-fallback", "fake-other-platform", "fake-preferred"), names);
    }

    @Test
    void picksTheHighestPrioritySupportedProvider() {
        // fake-other-platform has the highest priority of all but is unsupported here.
        Assertions.assertEquals("fake-preferred", Webview.provider().orElseThrow().name());
    }

    @Test
    void createHandsTheParametersToTheProviderAndNavigates() {
        WebviewParameters parameters = WebviewParameters.builder()
                .title("Docs")
                .url("https://example.com")
                .build();

        try (WebviewBackend webview = Webview.create(parameters)) {
            FakeWebviewBackend fake = Assertions.assertInstanceOf(FakeWebviewBackend.class, webview);
            Assertions.assertEquals("Docs", fake.title());
            Assertions.assertEquals(List.of("https://example.com"), fake.navigated);
        }
    }

    @Test
    void createWithoutUrlDoesNotNavigate() {
        try (WebviewBackend webview = Webview.create()) {
            Assertions.assertTrue(((FakeWebviewBackend) webview).navigated.isEmpty());
        }
    }

    @Test
    void failsWithANamedListWhenNothingSupportsThisMachine() {
        FakeProviders.supported = false;

        BackendNotAvailableException failure =
                Assertions.assertThrows(BackendNotAvailableException.class, Webview::create);
        Assertions.assertTrue(failure.getMessage().contains("fake-preferred (unsupported here)"));
        Assertions.assertTrue(failure.getMessage().contains("fake-other-platform (unsupported here)"));
    }
}
