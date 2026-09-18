package dev.ivchenko.webview.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code Info.plist} embedded into a macOS executable, the way the {@code java} launcher embeds its own: WebKit's
 * helper processes need the bundle identifier, and the menu bar and Dock show the name.
 *
 * <p>{@link #getInfoPlist()} replaces the generated property list with one of your own.</p>
 */
public abstract class MacOsExtension {
    /** {@code CFBundleIdentifier}; defaults to the project group and name, e.g. {@code com.example.my-app}. */
    public abstract Property<String> getBundleIdentifier();

    /** {@code CFBundleName} and {@code CFBundleDisplayName}; defaults to the image name. */
    public abstract Property<String> getBundleName();

    /** {@code CFBundleShortVersionString}; defaults to the project version. */
    public abstract Property<String> getVersion();

    /** A complete {@code Info.plist} to embed instead of the generated one. */
    public abstract RegularFileProperty getInfoPlist();
}
