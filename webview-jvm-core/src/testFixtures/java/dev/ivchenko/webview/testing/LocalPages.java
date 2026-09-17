package dev.ivchenko.webview.testing;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A throwaway HTTP server on {@code 127.0.0.1}, so a test can load a real {@code http://} page without depending on
 * anything outside the machine.
 */
public class LocalPages implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, String> pages = new ConcurrentHashMap<>();

    public LocalPages() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", exchange -> {
            String body = this.pages.get(exchange.getRequestURI().getPath());
            byte[] bytes = (body == null ? "not found" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        this.server.start();
    }

    /** Serves {@code html} at {@code path} and returns its full URL. */
    public String page(String path, String html) {
        this.pages.put(path, html);
        return this.url(path);
    }

    public String url(String path) {
        return "http://127.0.0.1:" + this.server.getAddress().getPort() + path;
    }

    @Override
    public void close() {
        this.server.stop(0);
    }
}
