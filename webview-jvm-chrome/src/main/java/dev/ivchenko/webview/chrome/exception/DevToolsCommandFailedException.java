package dev.ivchenko.webview.chrome.exception;

import lombok.Getter;

/** The browser answered a DevTools command with a protocol error. */
@Getter
public class DevToolsCommandFailedException extends RuntimeException {
    private final int code;

    public DevToolsCommandFailedException(String message, int code) {
        super(message + " (DevTools error " + code + ")");
        this.code = code;
    }
}
