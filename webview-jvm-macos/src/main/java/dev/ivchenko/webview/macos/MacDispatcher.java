package dev.ivchenko.webview.macos;

import dev.ivchenko.webview.foreign.NativeLibraries;
import dev.ivchenko.webview.macos.binding.AppKit;
import dev.ivchenko.webview.macos.binding.ObjC;
import dev.ivchenko.webview.macos.binding.Signatures;
import dev.ivchenko.webview.ui.UiDispatcher;
import dev.ivchenko.webview.util.ThrowableUtil;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * The process main thread, which is the only thread AppKit accepts - and one the library does not own.
 *
 * <p>Two situations arise. Under the {@code java} launcher the main thread is parked in a {@code CFRunLoop} while Java
 * code runs on another thread; work is handed over with {@code dispatch_async} onto the main queue, and the first
 * batch ends by starting {@code -[NSApplication run]}, which then services the queue for the rest of the process. In a
 * native image the application's own {@code main} is the main thread: calls made from it run inline, and
 * {@link dev.ivchenko.webview.WebviewBackend#run()} is what starts the application loop.</p>
 */
public class MacDispatcher extends UiDispatcher {
    private static final MacDispatcher INSTANCE = new MacDispatcher();
    private static final MemorySegment DRAIN_STUB = NativeLibraries.upcall(
            MethodHandles.lookup(), MacDispatcher.class, "drain",
            MethodType.methodType(void.class, MemorySegment.class), Signatures.DISPATCH_FUNCTION);

    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    private volatile boolean started;
    private volatile boolean applicationRunning;

    private MacDispatcher() {
    }

    /** The dispatcher, with the application object created on first use. */
    public static MacDispatcher instance() {
        INSTANCE.start();
        return INSTANCE;
    }

    @Override
    public boolean isDispatchThread() {
        return ObjC.isMainThread();
    }

    @Override
    public void post(Runnable task) {
        this.tasks.add(task);
        ObjC.dispatchToMainQueue(DRAIN_STUB, MemorySegment.NULL);
    }

    @Override
    protected <T> T execute(Supplier<T> action) {
        MemorySegment pool = ObjC.autoreleasePoolPush();
        try {
            return action.get();
        } finally {
            ObjC.autoreleasePoolPop(pool);
        }
    }

    /** Whether {@code -[NSApplication run]} is already looping on the main thread. */
    boolean isApplicationRunning() {
        return this.applicationRunning;
    }

    /** Runs the application loop on the calling main thread until {@link AppKit#stopRunLoop()}. */
    void runApplication() {
        this.applicationRunning = true;
        try {
            AppKit.run();
        } finally {
            this.applicationRunning = false;
        }
    }

    private synchronized void start() {
        if (this.started) return;
        this.started = true;
        if (this.isDispatchThread()) {
            this.execute(AppKit::application);
            return;
        }
        this.post(AppKit::application);
        this.post(this::runApplication);
    }

    @SuppressWarnings("unused")
    private static void drain(MemorySegment context) {
        for (Runnable task = INSTANCE.tasks.poll(); task != null; task = INSTANCE.tasks.poll()) {
            INSTANCE.runReported(task);
        }
    }

    private void runReported(Runnable task) {
        try {
            this.execute(() -> {
                task.run();
                return null;
            });
        } catch (Throwable t) {
            ThrowableUtil.report(t);
        }
    }
}
