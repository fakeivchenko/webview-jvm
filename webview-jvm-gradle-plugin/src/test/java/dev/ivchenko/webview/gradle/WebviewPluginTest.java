package dev.ivchenko.webview.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class WebviewPluginTest {
    @TempDir
    Path project;

    @BeforeEach
    void writeProject() throws IOException {
        Files.writeString(this.project.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n");
        Files.createDirectories(this.project.resolve("icons"));
        Files.write(this.project.resolve("icons/app.ico"), new byte[] {0, 0, 1, 0});
        Files.writeString(this.project.resolve("build.gradle.kts"), """
                plugins {
                    id("application")
                    id("dev.ivchenko.webview")
                }
                group = "com.example"
                version = "1.2.3-SNAPSHOT"
                application { mainClass = "com.example.Main" }
                webview {
                    imageName = "demo-app"
                    buildArgs.add("--verbose")
                    windows {
                        icon = file("icons/app.ico")
                        fileDescription = "Demo \\"quoted\\""
                        companyName = "Example & Co"
                    }
                    macos {
                        bundleName = "Demo"
                    }
                }
                tasks.register("printBuildArgs") {
                    val args = graalvmNative.binaries.named("main").flatMap { it.buildArgs }
                    val jvmArgs = application.applicationDefaultJvmArgs
                    doLast {
                        println("BUILD_ARGS=" + args.get().joinToString(" "))
                        println("JVM_ARGS=" + jvmArgs.joinToString(" "))
                    }
                }
                """);
    }

    @Test
    void configuresTheNativeImageAndTheJvmRun() {
        BuildResult result = this.run("printBuildArgs");
        String args = line(result, "BUILD_ARGS=");
        Assertions.assertTrue(args.startsWith("--enable-native-access=ALL-UNNAMED -Os -R:MaxHeapSize=64m"), args);
        Assertions.assertTrue(args.endsWith("--verbose"), args);
        Assertions.assertTrue(line(result, "JVM_ARGS=").contains("--enable-native-access=ALL-UNNAMED"));
    }

    @Test
    void generatesTheWindowsResourceScript() throws IOException {
        this.run("generateWindowsResourceScript");
        String script = Files.readString(this.project.resolve("build/webview/windows/app.rc"));
        Assertions.assertTrue(script.contains("1 ICON \"" + this.project.resolve("icons/app.ico").toAbsolutePath()
                .toString().replace('\\', '/') + "\""), script);
        Assertions.assertTrue(script.contains("FILEVERSION     1,2,3,0"), script);
        Assertions.assertTrue(script.contains("VALUE \"FileDescription\", \"Demo \"\"quoted\"\"\""), script);
        Assertions.assertTrue(script.contains("VALUE \"ProductName\", \"demo-app\""), script);
        Assertions.assertTrue(script.contains("VALUE \"OriginalFilename\", \"demo-app.exe\""), script);
        Assertions.assertTrue(script.contains("VALUE \"CompanyName\", \"Example & Co\""), script);
        Assertions.assertFalse(script.contains("LegalCopyright"), script);
    }

    @Test
    void generatesTheInfoPlist() throws IOException {
        this.run("generateInfoPlist");
        String plist = Files.readString(this.project.resolve("build/webview/macos/Info.plist"));
        Assertions.assertTrue(plist.contains("<string>com.example.demo</string>"), plist);
        Assertions.assertTrue(plist.contains("<key>CFBundleName</key>\n    <string>Demo</string>"), plist);
        Assertions.assertTrue(plist.contains("<string>1.2.3-SNAPSHOT</string>"), plist);
    }

    @Test
    void versionNumbersFitTheVersionBlock() {
        Assertions.assertEquals("1,2,3,0", GenerateWindowsResourceScript.numericVersion("1.2.3-SNAPSHOT"));
        Assertions.assertEquals("2,0,0,0", GenerateWindowsResourceScript.numericVersion("2"));
        Assertions.assertEquals("1,2,3,4", GenerateWindowsResourceScript.numericVersion("1.2.3.4.5"));
        Assertions.assertEquals("0,0,0,0", GenerateWindowsResourceScript.numericVersion("unspecified"));
    }

    private BuildResult run(String task) {
        return GradleRunner.create()
                .withProjectDir(this.project.toFile())
                .withPluginClasspath()
                .withArguments(task, "-q")
                .build();
    }

    private static String line(BuildResult result, String prefix) {
        return result.getOutput().lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + prefix + " in:\n" + result.getOutput()));
    }
}
