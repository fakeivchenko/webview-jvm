package dev.ivchenko.webview.gradle;

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * The {@code webview { }} block: everything the plugin puts into the native image, with the defaults the library's own
 * example ships with.
 *
 * <pre>{@code
 * webview {
 *     imageName = "my-app"
 *     maxHeapSize = "64m"
 *     windows {
 *         icon = file("src/main/windows/app.ico")
 *         fileDescription = "My app"
 *     }
 *     macos {
 *         bundleIdentifier = "com.example.myapp"
 *     }
 * }
 * }</pre>
 */
public abstract class WebviewExtension {
    /** Name of the executable; defaults to the project name. */
    public abstract Property<String> getImageName();

    /** {@code -Os}: a desktop application holds little live data, so size wins over peak throughput. Default on. */
    public abstract Property<Boolean> getOptimizeForSize();

    /** {@code -R:MaxHeapSize}, keeping a long session from growing on garbage; default {@code 64m}, empty to leave unset. */
    public abstract Property<String> getMaxHeapSize();

    /** Further {@code native-image} arguments, appended after the plugin's own. */
    public abstract ListProperty<String> getBuildArgs();

    @Nested
    public abstract WindowsExtension getWindows();

    @Nested
    public abstract MacOsExtension getMacos();

    public void windows(Action<? super WindowsExtension> action) {
        action.execute(this.getWindows());
    }

    public void macos(Action<? super MacOsExtension> action) {
        action.execute(this.getMacos());
    }
}
