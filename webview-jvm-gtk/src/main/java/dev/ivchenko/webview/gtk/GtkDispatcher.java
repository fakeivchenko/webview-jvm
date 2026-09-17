package dev.ivchenko.webview.gtk;

import dev.ivchenko.webview.foreign.NativeLibraries;
import dev.ivchenko.webview.gtk.binding.GLib;
import dev.ivchenko.webview.gtk.binding.Gtk;
import dev.ivchenko.webview.gtk.binding.Signatures;
import dev.ivchenko.webview.ui.UiDispatcher;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The process-wide GTK thread.
 *
 * <p>GTK may only be touched from the thread that ran {@code gtk_init}, so one daemon thread initialises GTK and then
 * sits in {@code gtk_main()} for the lifetime of the process. Work posted from other threads is pulled in through a
 * {@code g_idle_add} source - the one GLib entry point that is safe to call from anywhere.</p>
 */
public class GtkDispatcher extends UiDispatcher {
    private static final GtkDispatcher INSTANCE = new GtkDispatcher();

    /** Shared {@code GSourceFunc} stub; the queue in {@link UiDispatcher} carries the work. */
    private static final MemorySegment DRAIN_STUB = NativeLibraries.upcall(
            MethodHandles.lookup(), GtkDispatcher.class, "drain",
            MethodType.methodType(int.class, MemorySegment.class), Signatures.G_SOURCE_FUNC);

    private GtkDispatcher() {
        super("webview-gtk");
    }

    /** Returns the dispatcher, starting the GTK thread on first use. */
    public static GtkDispatcher instance() {
        INSTANCE.start();
        return INSTANCE;
    }

    @Override
    protected void initialize() {
        if (!Gtk.initialize()) {
            throw new IllegalStateException(
                    "gtk_init_check() failed: no display available (check DISPLAY / WAYLAND_DISPLAY)");
        }
    }

    @Override
    protected void runEventLoop() {
        Gtk.main();
    }

    @Override
    protected void wakeUp() {
        GLib.idleAdd(DRAIN_STUB, MemorySegment.NULL);
    }

    @SuppressWarnings("unused")
    private static int drain(MemorySegment userData) {
        INSTANCE.drainTasks();
        return GLib.SOURCE_REMOVE;
    }
}
