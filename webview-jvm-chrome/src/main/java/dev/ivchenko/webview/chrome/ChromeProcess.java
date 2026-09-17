package dev.ivchenko.webview.chrome;

import dev.ivchenko.webview.WebviewParameters;
import lombok.SneakyThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A browser process launched as an application window ({@code --app=}): no tabs, no address bar, no menus - a title
 * bar around the page, which is what a webview looks like.
 *
 * <p>Every window gets its own throw-away profile so the user's real browser state (sessions, extensions, sync) never
 * leaks in and nothing of the application is left behind. The DevTools port is picked by the browser and read back
 * from the {@code DevToolsActivePort} file it writes into that profile. {@code -Dwebview.chrome.args} or
 * {@code WEBVIEW_CHROME_ARGS} appends further command line switches, space separated.</p>
 */
class ChromeProcess implements AutoCloseable {
    /** System property with extra command line switches for the browser. */
    public static final String ARGS_PROPERTY = "webview.chrome.args";

    /** Environment variable with the same meaning as {@link #ARGS_PROPERTY}. */
    public static final String ARGS_VARIABLE = "WEBVIEW_CHROME_ARGS";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration EXIT_TIMEOUT = Duration.ofSeconds(5);

    private final Process process;
    private final Path profile;
    private final int devToolsPort;

    private ChromeProcess(Process process, Path profile, int devToolsPort) {
        this.process = process;
        this.profile = profile;
        this.devToolsPort = devToolsPort;
    }

    @SneakyThrows
    static ChromeProcess launch(Path executable, WebviewParameters parameters) {
        Path profile = Files.createTempDirectory("webview-jvm-chrome-");
        List<String> command = new ArrayList<>(List.of(
                executable.toString(),
                "--app=data:text/html,",
                "--user-data-dir=" + profile,
                "--window-size=" + parameters.width() + "," + parameters.height(),
                "--remote-debugging-port=0",
                "--remote-allow-origins=*",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-sync",
                "--disable-extensions",
                "--disable-background-networking",
                "--disable-component-update",
                "--disable-features=Translate,MediaRouter,OptimizationHints",
                "--password-store=basic",
                "--use-mock-keychain",
                "--hide-crash-restore-bubble",
                "--no-service-autorun"));
        String extra = System.getProperty(ARGS_PROPERTY, System.getenv(ARGS_VARIABLE));
        if (extra != null && !extra.isBlank()) command.addAll(List.of(extra.strip().split("\\s+")));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        // The browser does not watch its parent: without this, a JVM that exits without closing the window leaves
        // a full Chrome behind.
        Runtime.getRuntime().addShutdownHook(new Thread(process::destroy, "webview-chrome-shutdown"));
        try {
            return new ChromeProcess(process, profile, awaitDevToolsPort(process, profile));
        } catch (RuntimeException e) {
            process.destroyForcibly();
            deleteRecursively(profile);
            throw e;
        }
    }

    int devToolsPort() {
        return this.devToolsPort;
    }

    @Override
    public void close() {
        try {
            if (!this.process.waitFor(EXIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                this.process.destroyForcibly();
                this.process.waitFor(EXIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            this.process.destroyForcibly();
        }
        deleteRecursively(this.profile);
    }

    @SneakyThrows
    private static int awaitDevToolsPort(Process process, Path profile) {
        Path marker = profile.resolve("DevToolsActivePort");
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException("The browser exited during startup with code " + process.exitValue());
            }
            if (Files.exists(marker)) {
                try {
                    List<String> lines = Files.readAllLines(marker);
                    if (!lines.isEmpty() && !lines.getFirst().isBlank()) {
                        return Integer.parseInt(lines.getFirst().strip());
                    }
                } catch (IOException _) {
                    // Windows refuses to open the file while the browser still holds it for writing; next round.
                }
            }
            // The browser gives no signal other than the file appearing; polling is the only way to wait for it.
            //noinspection BusyWait
            Thread.sleep(50);
        }
        throw new IllegalStateException("The browser did not open its DevTools port within " + STARTUP_TIMEOUT);
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException _) {
                    // Chrome may still be flushing its profile; a leftover temp directory is not worth failing for.
                }
            });
        } catch (IOException _) {
            // Chrome may still be flushing its profile; a leftover temp directory is not worth failing for.
        }
    }
}
