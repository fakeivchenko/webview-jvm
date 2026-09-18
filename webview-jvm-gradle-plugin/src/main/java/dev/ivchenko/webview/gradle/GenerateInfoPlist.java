package dev.ivchenko.webview.gradle;

import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the {@code Info.plist} that is embedded into the macOS executable's {@code __info_plist} section. */
public abstract class GenerateInfoPlist extends DefaultTask {
    @Input
    public abstract Property<String> getBundleIdentifier();

    @Input
    public abstract Property<String> getBundleName();

    @Input
    public abstract Property<String> getVersion();

    @OutputFile
    public abstract RegularFileProperty getInfoPlist();

    @TaskAction
    @SneakyThrows
    public void generate() {
        String plist = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>CFBundleIdentifier</key>
                    <string>%s</string>
                    <key>CFBundleName</key>
                    <string>%s</string>
                    <key>CFBundleDisplayName</key>
                    <string>%s</string>
                    <key>CFBundlePackageType</key>
                    <string>APPL</string>
                    <key>CFBundleShortVersionString</key>
                    <string>%s</string>
                    <key>NSHighResolutionCapable</key>
                    <true/>
                </dict>
                </plist>
                """.formatted(escape(this.getBundleIdentifier().get()), escape(this.getBundleName().get()),
                escape(this.getBundleName().get()), escape(this.getVersion().get()));
        Path output = this.getInfoPlist().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, plist, StandardCharsets.UTF_8);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
