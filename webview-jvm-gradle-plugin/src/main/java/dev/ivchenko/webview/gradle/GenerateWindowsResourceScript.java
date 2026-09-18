package dev.ivchenko.webview.gradle;

import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Writes the {@code .rc} script - icon and version block - that {@link CompileWindowsResources} compiles. */
public abstract class GenerateWindowsResourceScript extends DefaultTask {
    private static final Pattern VERSION_NUMBER = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?");

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract RegularFileProperty getIcon();

    @Input
    public abstract Property<String> getFileDescription();

    @Input
    public abstract Property<String> getProductName();

    @Input
    @Optional
    public abstract Property<String> getCompanyName();

    @Input
    @Optional
    public abstract Property<String> getCopyright();

    @Input
    public abstract Property<String> getVersion();

    @Input
    public abstract Property<String> getOriginalFilename();

    @OutputFile
    public abstract RegularFileProperty getScript();

    @TaskAction
    @SneakyThrows
    public void generate() {
        List<String> lines = new ArrayList<>();
        if (this.getIcon().isPresent()) {
            lines.add("1 ICON \"" + this.getIcon().get().getAsFile().getAbsolutePath().replace('\\', '/') + "\"");
            lines.add("");
        }
        String numeric = numericVersion(this.getVersion().get());
        lines.add("1 VERSIONINFO");
        lines.add("FILEVERSION     " + numeric);
        lines.add("PRODUCTVERSION  " + numeric);
        lines.add("BEGIN");
        lines.add("    BLOCK \"StringFileInfo\"");
        lines.add("    BEGIN");
        lines.add("        BLOCK \"040904b0\"");
        lines.add("        BEGIN");
        lines.add(value("FileDescription", this.getFileDescription().get()));
        lines.add(value("ProductName", this.getProductName().get()));
        lines.add(value("FileVersion", this.getVersion().get()));
        lines.add(value("ProductVersion", this.getVersion().get()));
        lines.add(value("OriginalFilename", this.getOriginalFilename().get()));
        if (this.getCompanyName().isPresent()) lines.add(value("CompanyName", this.getCompanyName().get()));
        if (this.getCopyright().isPresent()) lines.add(value("LegalCopyright", this.getCopyright().get()));
        lines.add("        END");
        lines.add("    END");
        lines.add("    BLOCK \"VarFileInfo\"");
        lines.add("    BEGIN");
        lines.add("        VALUE \"Translation\", 0x409, 1200");
        lines.add("    END");
        lines.add("END");

        Path output = this.getScript().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, String.join("\r\n", lines) + "\r\n", StandardCharsets.US_ASCII);
    }

    /** {@code 1.2.3-SNAPSHOT} becomes {@code 1,2,3,0}: the numeric form the version block requires. */
    static String numericVersion(String version) {
        var matcher = VERSION_NUMBER.matcher(version);
        if (!matcher.lookingAt()) return "0,0,0,0";
        StringBuilder numeric = new StringBuilder();
        for (int group = 1; group <= 4; group++) {
            if (group > 1) numeric.append(',');
            numeric.append(matcher.group(group) == null ? "0" : matcher.group(group));
        }
        return numeric.toString();
    }

    private static String value(String name, String text) {
        return "            VALUE \"" + name + "\", \"" + text.replace("\"", "\"\"") + "\"";
    }
}
