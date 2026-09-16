package dev.ivchenko.webview.testing.contract;

import dev.ivchenko.webview.Webview;
import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.event.LoadEvent;
import dev.ivchenko.webview.exception.ScriptEvaluationFailedException;
import dev.ivchenko.webview.testing.Loads;
import dev.ivchenko.webview.testing.Tags;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Failure paths and the window lifecycle: what happens when things go wrong or away. */
@Tag(Tags.DISPLAY)
@Timeout(60)
public abstract class LifecycleContractTest extends DisplayContractTest {
    @Test
    void unreachableHostReportsAFailedLoad() throws Exception {
        // A port nothing listens on: refused at once, no network involved. Not a low port - browser
        // engines silently cancel loads to their "unsafe port" list (1, 7, 25, ...) instead of failing them.
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        String url = "http://127.0.0.1:" + closedPort + "/";

        try (WebviewBackend webview = Webview.create()) {
            var failed = Loads.expectFailed(webview);
            webview.navigate(url);

            LoadEvent event = failed.get(30, TimeUnit.SECONDS);
            Assertions.assertEquals(url, event.uri());
            Assertions.assertNotNull(event.message());
        }
    }

    @Test
    void missingResourceReportsAFailedLoad() throws Exception {
        try (WebviewBackend webview = Webview.create()) {
            var failed = Loads.expectFailed(webview);
            webview.loadResource("test-app/does-not-exist.html");

            LoadEvent event = failed.get(30, TimeUnit.SECONDS);
            Assertions.assertTrue(event.message().contains("test-app/does-not-exist.html"), event.message());
        }
    }

    @Test
    void aThrowingScriptFailsTheFuture() throws Exception {
        try (WebviewBackend webview = Webview.create()) {
            var loaded = Loads.expectFinished(webview);
            webview.html("<html><body></body></html>");
            loaded.get(30, TimeUnit.SECONDS);

            ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                    () -> webview.eval("throw new Error('kaboom')").get(20, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(ScriptEvaluationFailedException.class, failure.getCause());
        }
    }

    @Test
    void aClosedWindowRejectsEveryCall() {
        try (WebviewBackend webview = Webview.create()) {
            webview.show();
            webview.close();

            Assertions.assertThrows(IllegalStateException.class, webview::title);
            Assertions.assertThrows(IllegalStateException.class, () -> webview.navigate("about:blank"));
            ExecutionException failure = Assertions.assertThrows(ExecutionException.class,
                    () -> webview.eval("1").get(20, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(IllegalStateException.class, failure.getCause());

            Assertions.assertDoesNotThrow(webview::close, "close() must be idempotent");
        }
    }

    @Test
    void closingFromAnotherThreadUnblocksRun() throws Exception {
        try (WebviewBackend webview = Webview.create(WebviewParameters.builder().title("run").build())) {
            Thread runner = new Thread(webview::run, "run-caller");
            runner.start();

            Thread.sleep(500);
            Assertions.assertTrue(runner.isAlive(), "run() must block while the window is open");
            webview.close();
            runner.join(10_000);
            Assertions.assertFalse(runner.isAlive(), "run() must return once the window is closed");
        }
    }

    @Test
    void twoWindowsAreIndependent() throws Exception {
        try (WebviewBackend first = Webview.create(WebviewParameters.builder().title("first").build());
             WebviewBackend second = Webview.create(WebviewParameters.builder().title("second").build())) {
            var firstLoaded = Loads.expectFinished(first);
            var secondLoaded = Loads.expectFinished(second);
            first.html("<html><head><title>one</title></head></html>");
            second.html("<html><head><title>two</title></head></html>");
            firstLoaded.get(30, TimeUnit.SECONDS);
            secondLoaded.get(30, TimeUnit.SECONDS);

            Assertions.assertEquals("one", Loads.eval(first, "document.title"));
            Assertions.assertEquals("two", Loads.eval(second, "document.title"));

            first.close();
            Assertions.assertEquals("two", Loads.eval(second, "document.title"), "closing one must not touch the other");
        }
    }
}
