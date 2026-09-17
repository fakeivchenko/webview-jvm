package dev.ivchenko.webview.macos.binding;

import dev.ivchenko.webview.foreign.NativeLibraries;
import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

/** AppKit: the application object and windows. */
@UtilityClass
public class AppKit {
    static {
        // Loaded for the classes it registers; nothing is looked up by symbol.
        SymbolLookup _ = NativeLibraries.load("/System/Library/Frameworks/AppKit.framework/AppKit");
    }

    /** {@code NSWindowStyleMaskResizable}. */
    public final long STYLE_RESIZABLE = 1 << 3;

    private final long STYLE_TITLED_CLOSABLE_MINIATURIZABLE = 1 | (1 << 1) | (1 << 2);
    private final long ACTIVATION_POLICY_REGULAR = 0;
    private final long BACKING_STORE_BUFFERED = 2;
    private final long EVENT_TYPE_APPLICATION_DEFINED = 15;

    /** {@code +[NSApplication sharedApplication]}, made a regular application with a Dock icon and a menu bar. */
    public MemorySegment application() {
        MemorySegment application = ObjC.send(ObjC.cls("NSApplication"), "sharedApplication");
        boolean _ = ObjC.sendBool(application, "setActivationPolicy:", ACTIVATION_POLICY_REGULAR);
        return application;
    }

    /** {@code -[NSApplication run]}; returns only after {@link #stopRunLoop}. */
    public void run() {
        ObjC.sendVoid(application(), "run");
    }

    /**
     * {@code -[NSApplication stop:]} followed by an application-defined event: {@code stop:} only takes effect once
     * the run loop processes an event, so one is posted to make sure it does.
     */
    public void stopRunLoop() {
        MemorySegment application = application();
        ObjC.sendVoid(application, "stop:", MemorySegment.NULL);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment event = ObjC.sendOtherEvent(ObjC.cls("NSEvent"),
                    "otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:data2:",
                    EVENT_TYPE_APPLICATION_DEFINED, Foundation.point(arena, 0, 0), 0L, 0.0, 0L, MemorySegment.NULL,
                    (short) 0, 0L, 0L);
            ObjC.sendVoid(application, "postEvent:atStart:", event, true);
        }
    }

    /** Brings the application to the front, the way a freshly launched one comes up. */
    public void activate() {
        ObjC.sendVoid(application(), "activateIgnoringOtherApps:", true);
    }

    /** A titled, closable, resizable window owning its content rect; retained by the caller, not by its closing. */
    public MemorySegment window(int width, int height, String title) {
        MemorySegment window;
        try (Arena arena = Arena.ofConfined()) {
            window = ObjC.sendWithRect(ObjC.send(ObjC.cls("NSWindow"), "alloc"), "initWithContentRect:styleMask:backing:defer:",
                    Foundation.rect(arena, 0, 0, width, height), STYLE_TITLED_CLOSABLE_MINIATURIZABLE | STYLE_RESIZABLE,
                    BACKING_STORE_BUFFERED, false);
        }
        ObjC.sendVoid(window, "setReleasedWhenClosed:", false);
        setTitle(window, title);
        ObjC.sendVoid(window, "center");
        return window;
    }

    public void setTitle(MemorySegment window, String title) {
        ObjC.sendVoid(window, "setTitle:", Foundation.string(title));
    }

    public String title(MemorySegment window) {
        return Foundation.string(ObjC.send(window, "title"));
    }

    /** {@code {width, height}} of the content area. */
    public int[] contentSize(MemorySegment window) {
        double[] frame = Foundation.rect(ObjC.send(window, "contentView"), "frame");
        return new int[] {(int) Math.round(frame[2]), (int) Math.round(frame[3])};
    }

    public void setContentSize(MemorySegment window, int width, int height) {
        try (Arena arena = Arena.ofConfined()) {
            ObjC.sendVoidSize(window, "setContentSize:", Foundation.size(arena, width, height));
        }
    }

    public long styleMask(MemorySegment window) {
        return ObjC.sendLong(window, "styleMask");
    }

    public void setStyleMask(MemorySegment window, long styleMask) {
        ObjC.sendVoid(window, "setStyleMask:", styleMask);
    }

    public void setDelegate(MemorySegment window, MemorySegment delegate) {
        ObjC.sendVoid(window, "setDelegate:", delegate);
    }

    public void setContentView(MemorySegment window, MemorySegment view) {
        ObjC.sendVoid(window, "setContentView:", view);
    }

    public void show(MemorySegment window) {
        ObjC.sendVoid(window, "makeKeyAndOrderFront:", MemorySegment.NULL);
    }

    /** {@code -[NSWindow close]}; the delegate hears {@code windowWillClose:} synchronously. */
    public void close(MemorySegment window) {
        ObjC.sendVoid(window, "close");
    }
}
