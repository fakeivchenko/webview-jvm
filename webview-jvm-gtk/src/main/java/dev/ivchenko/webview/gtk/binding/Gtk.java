package dev.ivchenko.webview.gtk.binding;

import dev.ivchenko.webview.foreign.NativeLibraries;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * Bindings to the GTK 3 subset the backend needs.
 *
 * <p>Every function here must be called on the GTK thread; the class itself enforces nothing, as
 * {@link dev.ivchenko.webview.gtk.GtkDispatcher} is the only intended caller path.</p>
 */
@UtilityClass
public class Gtk {
    private final SymbolLookup GTK = NativeLibraries.load("libgtk-3.so.0", "libgtk-3.so");

    /** {@code GTK_WINDOW_TOPLEVEL} */
    public final int WINDOW_TOPLEVEL = 0;

    private final MethodHandle INIT_CHECK = NativeLibraries.downcall(GTK, "gtk_init_check", Signatures.INT_POINTER_POINTER);
    private final MethodHandle MAIN = NativeLibraries.downcall(GTK, "gtk_main", Signatures.VOID_VOID);
    private final MethodHandle WINDOW_NEW = NativeLibraries.downcall(GTK, "gtk_window_new", Signatures.POINTER_INT);
    private final MethodHandle WINDOW_SET_TITLE =
            NativeLibraries.downcall(GTK, "gtk_window_set_title", Signatures.VOID_POINTER_POINTER);
    private final MethodHandle WINDOW_GET_TITLE =
            NativeLibraries.downcall(GTK, "gtk_window_get_title", Signatures.POINTER_POINTER);
    private final MethodHandle WINDOW_SET_DEFAULT_SIZE =
            NativeLibraries.downcall(GTK, "gtk_window_set_default_size", Signatures.VOID_POINTER_INT_INT);
    private final MethodHandle WINDOW_RESIZE =
            NativeLibraries.downcall(GTK, "gtk_window_resize", Signatures.VOID_POINTER_INT_INT);
    private final MethodHandle WINDOW_GET_SIZE =
            NativeLibraries.downcall(GTK, "gtk_window_get_size", Signatures.VOID_POINTER_POINTER_POINTER);
    private final MethodHandle WINDOW_SET_RESIZABLE =
            NativeLibraries.downcall(GTK, "gtk_window_set_resizable", Signatures.VOID_POINTER_INT);
    private final MethodHandle WINDOW_GET_RESIZABLE =
            NativeLibraries.downcall(GTK, "gtk_window_get_resizable", Signatures.INT_POINTER);
    private final MethodHandle CONTAINER_ADD =
            NativeLibraries.downcall(GTK, "gtk_container_add", Signatures.VOID_POINTER_POINTER);
    private final MethodHandle WIDGET_SHOW_ALL =
            NativeLibraries.downcall(GTK, "gtk_widget_show_all", Signatures.VOID_POINTER);
    private final MethodHandle WIDGET_DESTROY =
            NativeLibraries.downcall(GTK, "gtk_widget_destroy", Signatures.VOID_POINTER);

    /**
     * {@code gtk_init_check(NULL, NULL)}: initialises GTK on the calling thread, which becomes the GTK thread for the
     * rest of the process. Returns {@code false} when no display can be opened.
     */
    @SneakyThrows
    public boolean initialize() {
        return (int) INIT_CHECK.invokeExact(MemorySegment.NULL, MemorySegment.NULL) != 0;
    }

    /** {@code gtk_main()}: runs the event loop on the calling thread and does not return. */
    @SneakyThrows
    public void main() {
        MAIN.invokeExact();
    }

    /** {@code gtk_window_new}; the widget is floating until a container takes it. */
    @SneakyThrows
    public MemorySegment windowNew(int type) {
        return (MemorySegment) WINDOW_NEW.invokeExact(type);
    }

    /** {@code gtk_window_set_title}; {@code null} clears the title. */
    @SneakyThrows
    public void windowSetTitle(MemorySegment window, String title) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = title == null ? MemorySegment.NULL : arena.allocateFrom(title);
            WINDOW_SET_TITLE.invokeExact(window, value);
        }
    }

    /** {@code gtk_window_get_title}. */
    @SneakyThrows
    public String windowGetTitle(MemorySegment window) {
        return NativeLibraries.string((MemorySegment) WINDOW_GET_TITLE.invokeExact(window));
    }

    /** {@code gtk_window_set_default_size}: the size the window opens with. */
    @SneakyThrows
    public void windowSetDefaultSize(MemorySegment window, int width, int height) {
        WINDOW_SET_DEFAULT_SIZE.invokeExact(window, width, height);
    }

    /** {@code gtk_window_resize}: resizes a window that is already realised. */
    @SneakyThrows
    public void windowResize(MemorySegment window, int width, int height) {
        WINDOW_RESIZE.invokeExact(window, width, height);
    }

    /** Returns {@code {width, height}} of the window. */
    @SneakyThrows
    public int[] windowGetSize(MemorySegment window) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment width = arena.allocate(Signatures.C_INT);
            MemorySegment height = arena.allocate(Signatures.C_INT);
            WINDOW_GET_SIZE.invokeExact(window, width, height);
            return new int[] {width.get(Signatures.C_INT, 0), height.get(Signatures.C_INT, 0)};
        }
    }

    /** {@code gtk_window_set_resizable}. */
    @SneakyThrows
    public void windowSetResizable(MemorySegment window, boolean resizable) {
        WINDOW_SET_RESIZABLE.invokeExact(window, resizable ? 1 : 0);
    }

    /** {@code gtk_window_get_resizable}. */
    @SneakyThrows
    public boolean isWindowResizable(MemorySegment window) {
        return (int) WINDOW_GET_RESIZABLE.invokeExact(window) != 0;
    }

    /** {@code gtk_container_add}: the container sinks the child's floating reference. */
    @SneakyThrows
    public void containerAdd(MemorySegment container, MemorySegment child) {
        CONTAINER_ADD.invokeExact(container, child);
    }

    /** {@code gtk_widget_show_all}: shows the widget and everything inside it. */
    @SneakyThrows
    public void widgetShowAll(MemorySegment widget) {
        WIDGET_SHOW_ALL.invokeExact(widget);
    }

    /** {@code gtk_widget_destroy}: emits {@code destroy} synchronously before returning. */
    @SneakyThrows
    public void widgetDestroy(MemorySegment widget) {
        WIDGET_DESTROY.invokeExact(widget);
    }
}
