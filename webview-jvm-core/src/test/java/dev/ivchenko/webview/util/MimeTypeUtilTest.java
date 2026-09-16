package dev.ivchenko.webview.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MimeTypeUtilTest {
    @ParameterizedTest
    @CsvSource({
            "app/index.html, text/html",
            "styles.css, text/css",
            "app.js, text/javascript",
            "module.mjs, text/javascript",
            "images/linux.SVG, image/svg+xml",
            "font.woff2, font/woff2",
            "data.json, application/json",
    })
    void mapsKnownExtensions(String path, String expected) {
        Assertions.assertEquals(expected, MimeTypeUtil.of(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"README", "archive.tar.xyz", "trailing.", ".hidden"})
    void fallsBackToOctetStream(String path) {
        Assertions.assertEquals("application/octet-stream", MimeTypeUtil.of(path));
    }
}
