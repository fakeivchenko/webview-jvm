package dev.ivchenko.webview.event;

import java.util.Objects;

/**
 * A page load lifecycle notification.
 *
 * @param state the stage the load has reached
 * @param uri the URI being loaded, may be {@code null} when the backend does not know it yet
 * @param message failure description, non-{@code null} only for {@link LoadState#FAILED}
 */
public record LoadEvent(LoadState state, String uri, String message) {
    public LoadEvent {
        Objects.requireNonNull(state, "state");
    }

    /** A transition that is not a failure. */
    public static LoadEvent of(LoadState state, String uri) {
        return new LoadEvent(state, uri, null);
    }

    /** A {@link LoadState#FAILED} transition carrying the engine's description of the failure. */
    public static LoadEvent failed(String uri, String message) {
        return new LoadEvent(LoadState.FAILED, uri, message);
    }
}
