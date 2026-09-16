package dev.ivchenko.webview.windows.exception;

import lombok.Getter;

/** A Win32 or COM call returned a failure {@code HRESULT}. */
@Getter
public class ComCallFailedException extends RuntimeException {
    private final int hresult;

    public ComCallFailedException(String call, int hresult) {
        super("%s failed with HRESULT 0x%08X".formatted(call, hresult));
        this.hresult = hresult;
    }
}
