package dev.ivchenko.webview.gtk;

import dev.ivchenko.webview.gtk.binding.GLib;
import dev.ivchenko.webview.gtk.binding.Gtk;
import dev.ivchenko.webview.gtk.binding.WebKit;
import dev.ivchenko.webview.testing.contract.NativeImageMetadataContractTest;
import dev.ivchenko.webview.util.PlatformUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

class GtkNativeImageMetadataTest extends NativeImageMetadataContractTest {
    @BeforeEach
    void requireLinux() {
        Assumptions.assumeTrue(PlatformUtil.isUnixDesktop(), "Binds GTK libraries: Linux only");
    }

    @Override
    protected String metadataPath() {
        return "META-INF/native-image/dev.ivchenko.webview/webview-jvm-gtk/reachability-metadata.json";
    }

    @Override
    protected List<Class<?>> bindingClasses() {
        return List.of(GLib.class, Gtk.class, WebKit.class, GtkDispatcher.class, GtkWebviewBackend.class);
    }
}
