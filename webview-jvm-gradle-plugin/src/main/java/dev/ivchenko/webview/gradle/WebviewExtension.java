package dev.ivchenko.webview.gradle;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
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
 *     icon = file("src/main/icons/app.png")
 *     maxHeapSize = "64m"
 *     windows {
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

    /**
     * The application icon as one image, a square PNG of 256 pixels or more; the plugin renders every size a platform
     * wants from it. On Windows it becomes the executable's {@code .ico}.
     */
    public abstract RegularFileProperty getIcon();

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
