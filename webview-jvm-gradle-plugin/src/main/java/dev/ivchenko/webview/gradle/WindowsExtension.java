package dev.ivchenko.webview.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * What a Windows executable carries besides its code: the subsystem it is linked for, and the icon and version block
 * Explorer shows.
 *
 * <p>The plugin writes a resource script from these values and compiles it with the Windows SDK's {@code rc.exe};
 * {@link #getResourceScript()} replaces the generated script with one of your own.</p>
 */
public abstract class WindowsExtension {
    /**
     * A ready-made {@code .ico} for the executable and its windows, replacing the one rendered from
     * {@link WebviewExtension#getIcon()}; resource id 1, which the backend loads at run time.
     */
    public abstract RegularFileProperty getIcon();

    /** Pixel sizes rendered into the generated {@code .ico}; default 16, 24, 32, 48, 64, 128 and 256. */
    public abstract ListProperty<Integer> getIconSizes();

    /** Whether to keep a console window; default off, so a double-click opens the window and nothing else. */
    public abstract Property<Boolean> getConsole();

    /** {@code FileDescription} of the version block; defaults to the image name. */
    public abstract Property<String> getFileDescription();

    /** {@code ProductName}; defaults to the image name. */
    public abstract Property<String> getProductName();

    /** {@code CompanyName}; empty by default. */
    public abstract Property<String> getCompanyName();

    /** {@code LegalCopyright}; empty by default. */
    public abstract Property<String> getCopyright();

    /** {@code FileVersion} and {@code ProductVersion}, {@code major.minor.patch[.build]}; defaults to the project version. */
    public abstract Property<String> getVersion();

    /** A complete {@code .rc} script to compile instead of the generated one. */
    public abstract RegularFileProperty getResourceScript();
}
