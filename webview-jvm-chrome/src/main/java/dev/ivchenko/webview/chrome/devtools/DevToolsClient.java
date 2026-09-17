package dev.ivchenko.webview.chrome.devtools;

import dev.ivchenko.webview.chrome.exception.DevToolsCommandFailedException;
import dev.ivchenko.webview.util.ThrowableUtil;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import lombok.Getter;

import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * One Chrome DevTools Protocol connection: JSON-RPC over a WebSocket, commands answered by id, events fanned out by
 * method name.
 *
 * <p>Connected straight to the page target rather than to the browser endpoint, so no session bookkeeping is needed:
 * every {@code Page.*}, {@code Runtime.*} and {@code Fetch.*} command addresses the one window, and the {@code Browser}
 * domain is reachable from there too. Events arrive on the connection's reader thread; the owner decides where to run
 * them.</p>
 */
public class DevToolsClient implements AutoCloseable {
    private static final String HOST = "127.0.0.1";
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private final WebSocketTransport transport;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<JsonObject>>> listeners = new ConcurrentHashMap<>();
    private final List<Runnable> closeListeners = new CopyOnWriteArrayList<>();

    @Getter
    private volatile boolean closed;

    private DevToolsClient(int port, String path) {
        this.transport = WebSocketTransport.connect(HOST, port, path, this::handleMessage, this::handleClosed);
    }

    /** Connects to the first page target of the browser listening on {@code port}. */
    public static DevToolsClient connectToPage(int port) {
        JsonArray targets;
        try (JsonReader reader = Json.createReader(
                new StringReader(WebSocketTransport.httpGet(HOST, port, "/json/list")))) {
            targets = reader.readArray();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Could not list the browser's DevTools targets", e);
        }
        for (JsonObject target : targets.getValuesAs(JsonObject.class)) {
            if ("page".equals(target.getString("type", null))) {
                return new DevToolsClient(port, URI.create(target.getString("webSocketDebuggerUrl")).getRawPath());
            }
        }
        throw new IllegalStateException("The browser opened no page target");
    }

    /** A builder for command parameters. */
    public static JsonObjectBuilder params() {
        return Json.createObjectBuilder();
    }

    /** Sends {@code method} without parameters and waits for its result. */
    public JsonObject call(String method) {
        return this.call(method, params());
    }

    /** Sends {@code method} and waits for its result. */
    public JsonObject call(String method, JsonObjectBuilder params) {
        return this.send(method, params).join();
    }

    /** Sends {@code method}; the future completes with its {@code result} or fails with the protocol error. */
    public CompletableFuture<JsonObject> send(String method, JsonObjectBuilder params) {
        if (this.closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("DevTools connection is closed"));
        }
        long id = this.nextId.getAndIncrement();
        CompletableFuture<JsonObject> result = new CompletableFuture<JsonObject>()
                .orTimeout(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        this.pending.put(id, result);
        result.whenComplete((_, _) -> this.pending.remove(id));
        try {
            this.transport.send(params().add("id", id).add("method", method).add("params", params).build().toString());
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    /** Subscribes to an event such as {@code Page.loadEventFired}; the consumer receives its {@code params}. */
    public void on(String method, Consumer<JsonObject> listener) {
        this.listeners.computeIfAbsent(method, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /** Runs once the connection is gone - because the browser exited, or {@link #close()} was called. */
    public void onClose(Runnable listener) {
        this.closeListeners.add(listener);
    }

    @Override
    public void close() {
        this.transport.close();
    }

    private void handleClosed() {
        if (this.closed) return;
        this.closed = true;
        IllegalStateException gone = new IllegalStateException("DevTools connection is closed");
        this.pending.values().forEach(future -> future.completeExceptionally(gone));
        this.pending.clear();
        for (Runnable listener : this.closeListeners) {
            try {
                listener.run();
            } catch (Throwable t) {
                ThrowableUtil.report(t);
            }
        }
    }

    private void handleMessage(String text) {
        JsonObject message;
        try (JsonReader reader = Json.createReader(new StringReader(text))) {
            message = reader.readObject();
        } catch (RuntimeException e) {
            ThrowableUtil.report(e);
            return;
        }
        if (message.get("id") instanceof JsonNumber id) {
            CompletableFuture<JsonObject> future = this.pending.remove(id.longValue());
            if (future == null) return;
            if (message.get("error") instanceof JsonObject error) {
                future.completeExceptionally(new DevToolsCommandFailedException(
                        error.getString("message", "DevTools command failed"), error.getInt("code", 0)));
            } else {
                future.complete(message.getJsonObject("result"));
            }
            return;
        }
        List<Consumer<JsonObject>> subscribers = this.listeners.get(message.getString("method", ""));
        if (subscribers == null) return;
        JsonObject params = message.get("params") instanceof JsonObject object ? object : JsonValue.EMPTY_JSON_OBJECT;
        for (Consumer<JsonObject> subscriber : subscribers) {
            try {
                subscriber.accept(params);
            } catch (Throwable t) {
                ThrowableUtil.report(t);
            }
        }
    }
}
