package dev.ivchenko.webview.event;

/** Stage of a page load, modelled after the WebKit load events so that every backend can report the same lifecycle. */
public enum LoadState {
    /** A new load has been requested and started. */
    STARTED,
    /** The load was redirected to a different URI. */
    REDIRECTED,
    /** The response was received and the content is about to be rendered. */
    COMMITTED,
    /** The load completed successfully. */
    FINISHED,
    /** The load was aborted with an error. */
    FAILED
}
