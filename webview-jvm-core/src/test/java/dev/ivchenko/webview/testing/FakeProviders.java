package dev.ivchenko.webview.testing;

import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.spi.WebviewBackendProvider;

/**
 * Providers registered in {@code META-INF/services} of the test classpath. {@link #supported} switches the two viable
 * ones off together, since a service file cannot be edited per test.
 */
public class FakeProviders {
    public static volatile boolean supported = true;

    private FakeProviders() {
    }

    public static class Preferred implements WebviewBackendProvider {
        @Override
        public String name() {
            return "fake-preferred";
        }

        @Override
        public boolean isSupported() {
            return supported;
        }

        @Override
        public int priority() {
            return 10;
        }

        @Override
        public WebviewBackend create(WebviewParameters parameters) {
            return new FakeWebviewBackend(parameters);
        }
    }

    public static class Fallback implements WebviewBackendProvider {
        @Override
        public String name() {
            return "fake-fallback";
        }

        @Override
        public boolean isSupported() {
            return supported;
        }

        @Override
        public WebviewBackend create(WebviewParameters parameters) {
            throw new AssertionError("The lower-priority provider must not be chosen");
        }
    }

    public static class OtherPlatform implements WebviewBackendProvider {
        @Override
        public String name() {
            return "fake-other-platform";
        }

        @Override
        public boolean isSupported() {
            return false;
        }

        @Override
        public int priority() {
            return 100;
        }

        @Override
        public WebviewBackend create(WebviewParameters parameters) {
            throw new AssertionError("An unsupported provider must not be chosen");
        }
    }
}
