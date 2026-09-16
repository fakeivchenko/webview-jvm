package dev.ivchenko.webview.util;

import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.Map;

/**
 * Maps a file name to the media type a webview needs in order to render it.
 *
 * <p>A lookup table rather than {@link java.nio.file.Files#probeContentType}: resources are served from the classpath,
 * where there is no file to probe, and a wrong type on the main document makes the engine show markup as plain
 * text.</p>
 */
@UtilityClass
public class MimeTypeUtil {
    private final String DEFAULT_TYPE = "application/octet-stream";

    private final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("js", "text/javascript"),
            Map.entry("mjs", "text/javascript"),
            Map.entry("json", "application/json"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("txt", "text/plain"),
            Map.entry("wasm", "application/wasm"));

    /** The media type for {@code path}, or {@code application/octet-stream} when unknown. */
    public String of(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return DEFAULT_TYPE;

        String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(extension, DEFAULT_TYPE);
    }
}
