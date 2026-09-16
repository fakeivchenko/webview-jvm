package dev.ivchenko.webview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WebviewParametersTest {
    @AfterEach
    void clearProperty() {
        System.clearProperty(WebviewParameters.DEV_SERVER_URL_PROPERTY);
    }

    @Test
    void defaultsFillEveryComponent() {
        WebviewParameters parameters = WebviewParameters.defaults();
        Assertions.assertEquals("webview-jvm", parameters.title());
        Assertions.assertEquals(1024, parameters.width());
        Assertions.assertEquals(768, parameters.height());
        Assertions.assertNull(parameters.url());
        Assertions.assertFalse(parameters.isDevelopment());
    }

    @Test
    void nonPositiveSizesFallBackToDefaults() {
        WebviewParameters parameters = WebviewParameters.builder().width(0).height(-5).build();
        Assertions.assertEquals(1024, parameters.width());
        Assertions.assertEquals(768, parameters.height());
    }

    @Test
    void blankDevServerUrlMeansNone() {
        Assertions.assertFalse(WebviewParameters.builder().devServerUrl("   ").build().isDevelopment());
    }

    @Test
    void devServerUrlComesFromTheSystemPropertyWhenNotSet() {
        System.setProperty(WebviewParameters.DEV_SERVER_URL_PROPERTY, "http://localhost:5173");

        Assertions.assertEquals("http://localhost:5173", WebviewParameters.defaults().devServerUrl());
        Assertions.assertEquals("http://localhost:9999",
                WebviewParameters.builder().devServerUrl("http://localhost:9999").build().devServerUrl(),
                "an explicit value wins over the property");
    }

    @Test
    void toBuilderKeepsUnchangedComponents() {
        WebviewParameters base = WebviewParameters.builder().title("Docs").width(640).build();
        WebviewParameters changed = base.toBuilder().height(480).build();
        Assertions.assertEquals("Docs", changed.title());
        Assertions.assertEquals(640, changed.width());
        Assertions.assertEquals(480, changed.height());
    }
}
