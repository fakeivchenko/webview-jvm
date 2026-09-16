package dev.ivchenko.webview.windows.binding;

import lombok.experimental.UtilityClass;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.nio.charset.StandardCharsets;

/** UTF-16 ({@code LPCWSTR}) strings, the form every Win32 and WebView2 API takes. */
@UtilityClass
public class Wide {
    /** A NUL-terminated UTF-16 copy of {@code text}. */
    public MemorySegment allocate(SegmentAllocator allocator, String text) {
        return allocator.allocateFrom(text, StandardCharsets.UTF_16LE);
    }

    /** Reads a NUL-terminated UTF-16 string from a pointer that may be {@code NULL}. */
    public String read(MemorySegment pointer) {
        if (pointer == null || pointer.equals(MemorySegment.NULL)) return null;
        return pointer.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_16LE);
    }

    /** Reads a string the callee allocated with {@code CoTaskMemAlloc}, then frees it. */
    public String take(MemorySegment pointer) {
        String value = read(pointer);
        if (value != null) Ole32.coTaskMemFree(pointer);
        return value;
    }
}
