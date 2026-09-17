package dev.ivchenko.webview.windows;

import dev.ivchenko.webview.ui.EventLoopDispatcher;
import dev.ivchenko.webview.windows.binding.Kernel32;
import dev.ivchenko.webview.windows.binding.Ole32;
import dev.ivchenko.webview.windows.binding.Signatures;
import dev.ivchenko.webview.windows.binding.User32;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * The process-wide Win32 UI thread.
 *
 * <p>WebView2 lives in a single-threaded COM apartment: the thread that creates a controller owns it, receives its
 * callbacks, and must pump messages for them to arrive. This dispatcher is that thread - it enters the apartment, runs
 * {@code GetMessage}/{@code DispatchMessage} for the rest of the process, and drains queued tasks whenever a
 * {@code WM_APP} posted from another thread lands in its queue.</p>
 */
public class WindowsDispatcher extends EventLoopDispatcher {
    private static final WindowsDispatcher INSTANCE = new WindowsDispatcher();

    private volatile int threadId;

    private WindowsDispatcher() {
        super("webview-win32");
    }

    /** Returns the dispatcher, starting the UI thread on first use. */
    public static WindowsDispatcher instance() {
        INSTANCE.start();
        return INSTANCE;
    }

    @Override
    protected void initialize() {
        int hresult = Ole32.coInitializeApartment();
        if (hresult < 0) throw new IllegalStateException("CoInitializeEx failed with HRESULT 0x%08X".formatted(hresult));
        this.threadId = Kernel32.currentThreadId();
        User32.ensureMessageQueue();
    }

    @Override
    protected void runEventLoop() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment message = arena.allocate(Signatures.MSG);
            while (User32.getMessage(message)) {
                if (User32.messageId(message) == User32.WM_APP) {
                    this.drainTasks();
                } else {
                    User32.dispatch(message);
                }
            }
        }
    }

    @Override
    protected void wakeUp() {
        User32.postThreadMessage(this.threadId, User32.WM_APP);
    }
}
