package dev.ivchenko.webview.gradle;

import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configures a webview-jvm application for GraalVM {@code native-image}: the flags the library needs and the ones a
 * desktop application wants, plus what each platform expects an executable to carry.
 *
 * <ul>
 *     <li>{@code --enable-native-access=ALL-UNNAMED}, for the JVM run and the native image alike.</li>
 *     <li>{@code -Os} and a capped heap, both switchable in {@code webview { }}.</li>
 *     <li>Windows: a GUI subsystem executable with an icon and a version block, compiled by {@code rc.exe}.</li>
 *     <li>macOS: an embedded {@code Info.plist} with the bundle identifier WebKit's helper processes need.</li>
 * </ul>
 *
 * <p>The GraalVM Native Build Tools plugin is applied underneath; anything it offers can still be configured directly
 * through {@code graalvmNative { }}.</p>
 */
public class WebviewPlugin implements Plugin<Project> {
    /** The {@code native-image} entry point stays {@code main}; the linker must not look for {@code WinMain}. */
    private static final List<String> WINDOWS_GUI_SUBSYSTEM =
            List.of("-H:NativeLinkerOption=/SUBSYSTEM:WINDOWS", "-H:NativeLinkerOption=/ENTRY:mainCRTStartup");

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(NativeImagePlugin.class);
        WebviewExtension extension = project.getExtensions().create("webview", WebviewExtension.class);
        this.applyDefaults(project, extension);

        project.getPluginManager().withPlugin("application", _ -> {
            JavaApplication application = project.getExtensions().getByType(JavaApplication.class);
            List<String> jvmArgs = new ArrayList<>();
            application.getApplicationDefaultJvmArgs().forEach(jvmArgs::add);
            jvmArgs.add("--enable-native-access=ALL-UNNAMED");
            application.setApplicationDefaultJvmArgs(jvmArgs);
        });

        TaskProvider<GenerateWindowsIcon> windowsIcon = project.getTasks().register(
                "generateWindowsIcon", GenerateWindowsIcon.class, task -> {
                    task.setDescription("Renders the application icon into a multi-size Windows .ico.");
                    task.setGroup("build");
                    task.getSource().set(extension.getIcon());
                    task.getSizes().set(extension.getWindows().getIconSizes());
                    task.getIcon().set(project.getLayout().getBuildDirectory().file("webview/windows/app.ico"));
                    task.onlyIf(_ -> extension.getIcon().isPresent());
                });
        extension.getWindows().getIcon().convention(windowsIcon.flatMap(GenerateWindowsIcon::getIcon)
                .filter(_ -> extension.getIcon().isPresent()));

        TaskProvider<GenerateWindowsResourceScript> resourceScript = project.getTasks().register(
                "generateWindowsResourceScript", GenerateWindowsResourceScript.class, task -> {
                    task.setDescription("Writes the icon and version resource script of the Windows executable.");
                    task.setGroup("build");
                    task.getIcon().set(extension.getWindows().getIcon());
                    task.getFileDescription().set(extension.getWindows().getFileDescription());
                    task.getProductName().set(extension.getWindows().getProductName());
                    task.getCompanyName().set(extension.getWindows().getCompanyName());
                    task.getCopyright().set(extension.getWindows().getCopyright());
                    task.getVersion().set(extension.getWindows().getVersion());
                    task.getOriginalFilename().set(extension.getImageName().map(name -> name + ".exe"));
                    task.getScript().set(project.getLayout().getBuildDirectory().file("webview/windows/app.rc"));
                });
        TaskProvider<CompileWindowsResources> resources = project.getTasks().register(
                "compileWindowsResources", CompileWindowsResources.class, task -> {
                    task.setDescription("Compiles the Windows resource script with rc.exe.");
                    task.setGroup("build");
                    task.getScript().set(extension.getWindows().getResourceScript()
                            .orElse(resourceScript.flatMap(GenerateWindowsResourceScript::getScript)));
                    task.getResource().set(project.getLayout().getBuildDirectory().file("webview/windows/app.res"));
                });
        TaskProvider<GenerateInfoPlist> infoPlist = project.getTasks().register(
                "generateInfoPlist", GenerateInfoPlist.class, task -> {
                    task.setDescription("Writes the Info.plist embedded into the macOS executable.");
                    task.setGroup("build");
                    task.getBundleIdentifier().set(extension.getMacos().getBundleIdentifier());
                    task.getBundleName().set(extension.getMacos().getBundleName());
                    task.getVersion().set(extension.getMacos().getVersion());
                    task.getInfoPlist().set(project.getLayout().getBuildDirectory().file("webview/macos/Info.plist"));
                });
        Provider<RegularFile> plist = extension.getMacos().getInfoPlist()
                .orElse(infoPlist.flatMap(GenerateInfoPlist::getInfoPlist));

        GraalVMExtension graal = project.getExtensions().getByType(GraalVMExtension.class);
        // Gradle runs on whatever JDK the build uses; GRAALVM_HOME names the GraalVM to build with.
        graal.getToolchainDetection().set(false);
        graal.getBinaries().named("main", options -> {
            options.getImageName().set(extension.getImageName());
            options.getBuildArgs().addAll(project.provider(() -> this.buildArgs(extension, resources, plist)));
        });
        project.getTasks().named("nativeCompile", task -> {
            if (isWindows()) task.dependsOn(resources);
            if (isMacOs() && !extension.getMacos().getInfoPlist().isPresent()) task.dependsOn(infoPlist);
        });
    }

    private void applyDefaults(Project project, WebviewExtension extension) {
        extension.getImageName().convention(project.getName());
        extension.getOptimizeForSize().convention(true);
        extension.getMaxHeapSize().convention("64m");

        WindowsExtension windows = extension.getWindows();
        windows.getConsole().convention(false);
        windows.getIconSizes().convention(List.of(16, 24, 32, 48, 64, 128, 256));
        windows.getFileDescription().convention(extension.getImageName());
        windows.getProductName().convention(extension.getImageName());
        windows.getVersion().convention(project.provider(() -> project.getVersion().toString()));

        MacOsExtension macos = extension.getMacos();
        macos.getBundleIdentifier().convention(project.provider(() -> project.getGroup().toString().isEmpty()
                ? project.getName()
                : project.getGroup() + "." + project.getName()));
        macos.getBundleName().convention(extension.getImageName());
        macos.getVersion().convention(project.provider(() -> project.getVersion().toString()));
    }

    private List<String> buildArgs(WebviewExtension extension, TaskProvider<CompileWindowsResources> resources,
                                   Provider<RegularFile> plist) {
        List<String> args = new ArrayList<>();
        args.add("--enable-native-access=ALL-UNNAMED");
        if (extension.getOptimizeForSize().get()) args.add("-Os");
        String heap = extension.getMaxHeapSize().getOrElse("");
        if (!heap.isEmpty()) args.add("-R:MaxHeapSize=" + heap);

        List<String> linker = new ArrayList<>();
        if (isWindows()) {
            linker.add("-H:NativeLinkerOption=" + resources.get().getResource().get().getAsFile().getAbsolutePath());
            if (!extension.getWindows().getConsole().get()) linker.addAll(WINDOWS_GUI_SUBSYSTEM);
        }
        if (isMacOs()) {
            linker.add("-H:NativeLinkerOption=-Wl,-sectcreate,__TEXT,__info_plist,"
                    + plist.get().getAsFile().getAbsolutePath());
        }
        if (!linker.isEmpty()) {
            args.add("-H:+UnlockExperimentalVMOptions");
            args.addAll(linker);
            args.add("-H:-UnlockExperimentalVMOptions");
        }
        args.addAll(extension.getBuildArgs().get());
        return args;
    }

    private static boolean isWindows() {
        return osName().contains("win");
    }

    private static boolean isMacOs() {
        return osName().contains("mac");
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
