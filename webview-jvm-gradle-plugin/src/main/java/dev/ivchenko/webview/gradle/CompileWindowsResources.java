package dev.ivchenko.webview.gradle;

import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Runs the Windows SDK's {@code rc.exe} over a resource script; the {@code .res} it produces is handed to the linker.
 *
 * <p>The compiler is taken from the {@code PATH} when the build runs inside a Visual Studio prompt, and from the newest
 * installed Windows SDK otherwise, which is where {@code native-image} finds its own toolchain too.</p>
 */
public abstract class CompileWindowsResources extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract RegularFileProperty getScript();

    @OutputFile
    public abstract RegularFileProperty getResource();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    @SneakyThrows
    public void compile() {
        File script = this.getScript().get().getAsFile();
        File resource = this.getResource().get().getAsFile();
        Files.createDirectories(resource.getParentFile().toPath());
        this.getExecOperations().exec(spec -> {
            spec.setExecutable(resourceCompiler());
            spec.setWorkingDir(script.getParentFile());
            spec.args("/nologo", "/fo", resource.getAbsolutePath(), script.getName());
        });
    }

    static String resourceCompiler() {
        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(File.pathSeparator)) {
                File candidate = new File(entry, "rc.exe");
                if (candidate.isFile()) return candidate.getAbsolutePath();
            }
        }
        String programFiles = System.getenv("ProgramFiles(x86)");
        File kits = new File(programFiles == null ? "C:\\Program Files (x86)" : programFiles, "Windows Kits\\10\\bin");
        File[] versions = kits.listFiles(file -> file.isDirectory() && file.getName().startsWith("10."));
        return Stream.of(versions == null ? new File[0] : versions)
                .sorted(Comparator.comparing(File::getName).reversed())
                .map(version -> new File(version, "x64\\rc.exe"))
                .filter(File::isFile)
                .map(File::getAbsolutePath)
                .findFirst()
                .orElseThrow(() -> new GradleException(
                        "rc.exe not found: install the Windows SDK or run from a Visual Studio prompt"));
    }
}
