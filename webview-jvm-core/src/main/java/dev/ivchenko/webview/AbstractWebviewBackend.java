package dev.ivchenko.webview;

import dev.ivchenko.webview.bridge.BridgeProtocol;
import dev.ivchenko.webview.event.LoadEvent;
import dev.ivchenko.webview.ui.UiDispatcher;
import dev.ivchenko.webview.util.ResourceUtil;
import dev.ivchenko.webview.util.ThrowableUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The parts of a {@link WebviewBackend} that do not depend on a particular toolkit: listener bookkeeping, the
 * closed-window signal, the blocking {@link #run()} loop, and the whole Java&#8596;page bridge except its transport.
 *
 * <p>A subclass marshals its native calls onto {@link #dispatcher()} and plugs in four things: the two abstract members
 * below, a call to {@link #installBridge()} once the native view exists, and a call to
 * {@link #handleBridgeMessage(String)} from whatever callback the engine delivers messages on.</p>
 */
public abstract class AbstractWebviewBackend implements WebviewBackend {
    /**
     * Where bound handlers run. Virtual threads, because a handler is allowed to block - on I/O, on a lock, on the UI
     * thread itself through {@link #eval} - and a pool of platform threads would either starve or grow without bound.
     */
    private static final Executor HANDLER_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final UiDispatcher dispatcher;
    private final WebviewParameters parameters;
    private final List<Consumer<LoadEvent>> loadListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Function<String, String>> bindings = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> closeSignal = new CompletableFuture<>();

    private volatile boolean closed;

    protected AbstractWebviewBackend(UiDispatcher dispatcher, WebviewParameters parameters) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
    }

    /** The UI thread every native call of this backend has to run on. */
    protected final UiDispatcher dispatcher() {
        return this.dispatcher;
    }

    @Override
    public final void onLoad(Consumer<LoadEvent> listener) {
        this.loadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * {@inheritDoc}
     *
     * <p>In development the bundled files are bypassed entirely: the dev server owns the whole page, the way Tauri's
     * {@code devUrl} supersedes {@code frontendDist}. The bridge is unaffected, because it is injected into every
     * document whatever its origin.</p>
     */
    @Override
    public void loadResource(String path) {
        if (this.parameters.isDevelopment()) {
            this.navigate(this.parameters.devServerUrl());
            return;
        }
        this.navigate(this.resourceUri(path));
    }

    /**
     * The URL under which the classpath resource {@code path} is served by this backend. WebKit registers a real custom
     * scheme ({@code app://local/...}); an engine without that facility intercepts requests to a reserved host instead,
     * and says so here.
     */
    protected String resourceUri(String path) {
        return ResourceUtil.uri(path);
    }

    @Override
    public final void bind(String name, Function<String, String> handler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("Not a JavaScript identifier: " + name);
        }
        this.bindings.put(name, handler);

        String script = BridgeProtocol.bindingScript(name);
        // Once for documents loaded from now on, once for the document already on screen.
        this.injectOnDocumentStart(script);
        this.eval(script);
    }

    /**
     * Installs the page-side bridge runtime. A subclass calls this once its native view exists, before any page is
     * loaded.
     */
    protected final void installBridge() {
        this.injectOnDocumentStart(BridgeProtocol.bootstrapScript(this.bridgeTransportScript()));
    }

    /**
     * Handles one raw message from the page. Called on the UI thread; the handler itself runs elsewhere so that slow
     * application code cannot freeze the window.
     */
    protected final void handleBridgeMessage(String message) {
        String[] parts = message.split(BridgeProtocol.SEPARATOR, 3);
        long id = parts.length == 3 ? parseId(parts[0]) : -1;
        if (id < 0) {
            ThrowableUtil.report(new IllegalStateException("Malformed bridge message: " + message));
            return;
        }
        String name = parts[1];
        String payload = parts[2];

        Function<String, String> handler = this.bindings.get(name);
        if (handler == null) {
            this.eval(BridgeProtocol.rejectScript(id, "No handler bound for " + name));
            return;
        }
        CompletableFuture
                .supplyAsync(() -> handler.apply(payload), HANDLER_EXECUTOR)
                .whenComplete((result, failure) -> {
                    if (this.isClosed()) return;
                    this.eval(failure == null
                            ? BridgeProtocol.resolveScript(id, result)
                            : BridgeProtocol.rejectScript(id, rootMessage(failure)));
                });
    }

    /**
     * Arranges for {@code script} to run in every document before its own scripts do. Every engine offers this:
     * {@code webkit_user_content_manager_add_script}, {@code WKUserScript},
     * {@code AddScriptToExecuteOnDocumentCreated}.
     */
    protected abstract void injectOnDocumentStart(String script);

    /**
     * A JavaScript expression evaluating to a function of one string that delivers it to this backend - the only part
     * of the bridge that differs between engines.
     */
    protected abstract String bridgeTransportScript();

    /**
     * Delivers {@code event} to every listener. Called on the UI thread, so a listener that throws is reported rather
     * than propagated - one bad listener must not skip the others or unwind into the toolkit.
     */
    protected final void emitLoad(LoadEvent event) {
        for (Consumer<LoadEvent> listener : this.loadListeners) {
            try {
                listener.accept(event);
            } catch (Throwable t) {
                ThrowableUtil.report(t);
            }
        }
    }

    /** Records that the native window is gone, releasing anyone blocked in {@link #run()}. */
    protected final void markClosed() {
        this.closed = true;
        this.closeSignal.complete(null);
    }

    /** Whether the native window has been destroyed, by the user or by {@link #close()}. */
    public final boolean isClosed() {
        return this.closed;
    }

    @Override
    public void run() {
        if (this.dispatcher.isDispatchThread()) {
            throw new IllegalStateException("run() would block the UI thread");
        }
        this.show();
        this.closeSignal.join();
    }

    private static long parseId(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
