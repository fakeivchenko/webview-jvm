package dev.ivchenko.webview.example;

import dev.ivchenko.webview.Webview;
import dev.ivchenko.webview.WebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.event.LoadEvent;
import dev.ivchenko.webview.exception.BackendNotAvailableException;
import dev.ivchenko.webview.spi.WebviewBackendProvider;
import dev.ivchenko.webview.util.PlatformUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A small desktop application on top of webview-jvm, showing both halves of the bridge.
 *
 * <p>The page is the application's own HTML, CSS, JavaScript and images, served straight out of the jar - no files on
 * disk, nothing fetched from the network. Two Java handlers are published to it with {@link WebviewBackend#bind}, and a
 * background ticker pushes into the page with {@link WebviewBackend#eval}, so traffic runs in both directions.</p>
 *
 * <p>While the frontend is being worked on, point the window at the dev server instead of the jar -
 * {@code WEBVIEW_DEV_SERVER_URL=http://localhost:5173 ./gradlew :webview-jvm-example:run} - and hot reload drives this
 * same window with this same Java code.</p>
 *
 * <p>Note what this module compiles against: {@code webview-jvm-core} only, with the backends as {@code runtimeOnly}
 * dependencies. Nothing here names GTK or WebView2. Supporting another platform is a build file change rather than a
 * code change, because {@link Webview} resolves the backend through {@link java.util.ServiceLoader} at startup.</p>
 *
 * <p>Run it with {@code ./gradlew :webview-jvm-example:run}.</p>
 */
@Slf4j
@UtilityClass
public class WebviewExampleApplication {
    private static final String PAGE = "app/index.html";
    private static final long BYTES_PER_MEGABYTE = 1024 * 1024;

    /** Opens the window and blocks until it is closed. */
    @SuppressWarnings("unused")
    static void main(String[] args) {
        WebviewParameters parameters = WebviewParameters.builder()
                .title("webview-jvm example")
                .width(960)
                .height(720)
                .build();

        try (WebviewBackend webview = Webview.create(parameters)) {
            webview.onLoad(WebviewExampleApplication::logLoad);

            // Bind before loading: handlers are injected into every document as it starts.
            webview.bind("systemInfo", _ -> systemInfo());
            webview.bind("sha256", WebviewExampleApplication::sha256);

            webview.loadResource(PAGE);
            pushHeapUsageWhileOpen(webview);
        } catch (BackendNotAvailableException e) {
            log.error("{}", e.getMessage());
        }
        log.info("Window closed, exiting");
    }

    /**
     * Runs the window, pushing a number into the page once a second until the user closes it.
     * {@link WebviewBackend#run()} blocks, so the ticker lives alongside it; closing the executor cancels the periodic
     * task once the window is gone.
     */
    private static void pushHeapUsageWhileOpen(WebviewBackend webview) {
        try (ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "heap-ticker");
            thread.setDaemon(true);
            return thread;
        })) {
            ticker.scheduleAtFixedRate(() -> pushHeapUsage(webview), 0, 1, TimeUnit.SECONDS);
            webview.run();
        }
    }

    private static void pushHeapUsage(WebviewBackend webview) {
        Runtime runtime = Runtime.getRuntime();
        long usedMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEGABYTE;
        webview.eval("window.onHeapUsage(%d);".formatted(usedMegabytes));
    }

    /** Answers {@code window.systemInfo()}. Handlers exchange strings; this one sends JSON. */
    private static String systemInfo() {
        String backend = Webview.provider().map(WebviewBackendProvider::name).orElse("unknown");
        return """
                {"java": "%s", "os": "%s %s", "backend": "%s", "platform": "%s", "platformName": "%s"}"""
                .formatted(
                        System.getProperty("java.version"),
                        System.getProperty("os.name"),
                        System.getProperty("os.arch"),
                        backend,
                        platform(),
                        platformName());
    }

    /** Which image the page should show; the names match the files under {@code app/images}. */
    private static String platform() {
        if (PlatformUtil.isLinux()) return "linux";
        if (PlatformUtil.isWindows()) return "windows";
        if (PlatformUtil.isMacOs()) return "macos";
        return "other";
    }

    private static String platformName() {
        if (PlatformUtil.isLinux()) return "Linux";
        if (PlatformUtil.isWindows()) return "Windows";
        if (PlatformUtil.isMacOs()) return "macOS";
        return PlatformUtil.osName();
    }

    /**
     * Answers {@code window.sha256(text)}. Handlers run off the UI thread, so work like this cannot freeze the window,
     * and a throw here rejects the promise the page is awaiting.
     */
    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void logLoad(LoadEvent event) {
        switch (event.state()) {
            case STARTED -> log.info("Loading {}", event.uri());
            case FAILED -> log.error("Could not load {}: {}", event.uri(), event.message());
            case FINISHED -> log.info("Loaded {}", event.uri());
            default -> log.debug("{} {}", event.state(), event.uri());
        }
    }
}
