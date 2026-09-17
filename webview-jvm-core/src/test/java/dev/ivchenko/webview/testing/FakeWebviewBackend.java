package dev.ivchenko.webview.testing;

import dev.ivchenko.webview.AbstractWebviewBackend;
import dev.ivchenko.webview.WebviewParameters;
import dev.ivchenko.webview.event.LoadEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An {@link AbstractWebviewBackend} that records what the base class asks of it. Every script the base class evaluates
 * or injects, and every URL it navigates to, is kept for assertions.
 */
public class FakeWebviewBackend extends AbstractWebviewBackend {
    public final List<String> injected = new CopyOnWriteArrayList<>();
    public final List<String> evaluated = new CopyOnWriteArrayList<>();
    public final List<String> navigated = new CopyOnWriteArrayList<>();

    private String title;
    private int width;
    private int height;
    private boolean resizable = true;

    public FakeWebviewBackend() {
        this(WebviewParameters.defaults());
    }

    public FakeWebviewBackend(WebviewParameters parameters) {
        super(new FakeUiDispatcher(), parameters);
        this.title = parameters.title();
        this.width = parameters.width();
        this.height = parameters.height();
        this.installBridge();
    }

    /** Exposes the protected hook so tests can play the engine's part. */
    public void receive(String message) {
        this.handleBridgeMessage(message);
    }

    /** Exposes the protected hook so tests can play the engine's part. */
    public void emit(LoadEvent event) {
        this.emitLoad(event);
    }

    @Override
    protected void injectOnDocumentStart(String script) {
        this.injected.add(script);
    }

    @Override
    protected String bridgeTransportScript() {
        return "(message) => fakeHost.post(message)";
    }

    @Override
    public CompletableFuture<String> eval(String script) {
        this.evaluated.add(script);
        return CompletableFuture.completedFuture("undefined");
    }

    @Override
    public void navigate(String url) {
        this.navigated.add(url);
    }

    @Override
    public String url() {
        return this.navigated.isEmpty() ? null : this.navigated.getLast();
    }

    @Override
    public void html(String html) {
        this.navigated.add("about:blank");
    }

    @Override
    public String engine() {
        return "fake 0";
    }

    @Override
    public String title() {
        return this.title;
    }

    @Override
    public void title(String title) {
        this.title = title;
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public void size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean isResizable() {
        return this.resizable;
    }

    @Override
    public void resizable(boolean resizable) {
        this.resizable = resizable;
    }

    @Override
    public void show() {
    }

    @Override
    public void close() {
        this.markClosed();
    }
}
