package dev.ivchenko.webview.util;

import dev.ivchenko.webview.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class ResourceUtilTest {
    @Test
    void buildsAppSchemeUris() {
        Assertions.assertEquals("app://local/app/index.html", ResourceUtil.uri("app/index.html"));
        Assertions.assertEquals("app://local/app/index.html", ResourceUtil.uri("/app/index.html"));
    }

    @Test
    void readsClasspathResourcesWithOrWithoutLeadingSlash() {
        String expected = "hello from the classpath\n";
        Assertions.assertEquals(expected, new String(ResourceUtil.read("fixtures/hello.txt"), StandardCharsets.UTF_8));
        Assertions.assertEquals(expected, new String(ResourceUtil.read("/fixtures/hello.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void missingResourceIsATypedFailure() {
        ResourceNotFoundException failure = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> ResourceUtil.read("fixtures/nope.txt"));
        Assertions.assertTrue(failure.getMessage().contains("fixtures/nope.txt"));
    }
}
