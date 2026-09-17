plugins {
    id("application")
    id("io.freefair.lombok")
    id("org.graalvm.buildtools.native") version "1.1.12"
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

application {
    mainClass = "dev.ivchenko.webview.example.WebviewExampleApplication"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    // webview-jvm
    implementation(project(":webview-jvm-core"))
    runtimeOnly(project(":webview-jvm-gtk"))
    runtimeOnly(project(":webview-jvm-windows"))
    runtimeOnly(project(":webview-jvm-macos"))
    runtimeOnly(project(":webview-jvm-chrome"))
    runtimeOnly("org.eclipse.parsson:parsson:1.1.9")

    // Logging: slf4j-simple has no XML configuration, so no JAXP ends up in the native image
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

val windows = org.gradle.internal.os.OperatingSystem.current().isWindows
val macOs = org.gradle.internal.os.OperatingSystem.current().isMacOsX

val windowsResources = tasks.register<Exec>("windowsResources") {
    description = "Compiles src/main/windows/app.rc into the .res linked into the native executable."
    group = "build"
    onlyIf { windows }
    val script = layout.projectDirectory.file("src/main/windows/app.rc")
    val output = layout.buildDirectory.file("windows/app.res")
    inputs.files(script, layout.projectDirectory.file("src/main/windows/app.ico"))
    outputs.file(output)
    workingDir = script.asFile.parentFile
    executable = if (windows) resourceCompiler() else "rc"
    args("/nologo", "/fo", output.get().asFile.absolutePath, script.asFile.name)
    doFirst { output.get().asFile.parentFile.mkdirs() }
}

graalvmNative {
    // Gradle runs on a plain JDK; GRAALVM_HOME names the GraalVM to build with.
    toolchainDetection = false
    binaries {
        named("main") {
            imageName = "webview-jvm-example"
            buildArgs("--enable-native-access=ALL-UNNAMED")
            // A desktop app holds a few megabytes of live data: -Os trades nothing visible for size, and a capped
            // heap keeps a long session from growing on garbage the collector never needed to keep.
            buildArgs("-Os", "-R:MaxHeapSize=64m")
            if (windows) {
                // A GUI subsystem executable opens no console window; native-image's entry point is still main,
                // so the linker is told not to look for WinMain.
                buildArgs("-H:+UnlockExperimentalVMOptions",
                        "-H:NativeLinkerOption=" + layout.buildDirectory.file("windows/app.res").get().asFile.absolutePath,
                        "-H:NativeLinkerOption=/SUBSYSTEM:WINDOWS",
                        "-H:NativeLinkerOption=/ENTRY:mainCRTStartup",
                        "-H:-UnlockExperimentalVMOptions")
            }
            if (macOs) {
                // An Info.plist embedded the way the java launcher embeds its own: WebKit's helper processes need the
                // bundle identifier, and the menu bar and Dock show the name.
                buildArgs("-H:+UnlockExperimentalVMOptions",
                        "-H:NativeLinkerOption=-Wl,-sectcreate,__TEXT,__info_plist,"
                                + layout.projectDirectory.file("src/main/macos/Info.plist").asFile.absolutePath,
                        "-H:-UnlockExperimentalVMOptions")
            }
        }
    }
}

tasks.nativeCompile {
    dependsOn(windowsResources)
}

/** rc.exe from the PATH (a Visual Studio prompt) or, failing that, the newest Windows SDK. */
fun resourceCompiler(): String {
    val onPath = System.getenv("PATH").split(';').map { File(it, "rc.exe") }.firstOrNull { it.isFile }
    if (onPath != null) return onPath.absolutePath
    val kits = File(System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)", "Windows Kits\\10\\bin")
    return kits.listFiles { file -> file.isDirectory && file.name.startsWith("10.") }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "x64\\rc.exe") }
            ?.firstOrNull { it.isFile }
            ?.absolutePath
            ?: throw GradleException("rc.exe not found: install the Windows SDK or run from a Visual Studio prompt")
}
