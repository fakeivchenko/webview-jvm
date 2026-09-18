# Gradle plugin

`dev.ivchenko.webview` configures a GraalVM `native-image` build of a webview-jvm application. It
applies the GraalVM Native Build Tools plugin underneath, so `graalvmNative { }` remains available
for anything beyond what is listed here.

```kotlin
pluginManagement {
    repositories {
        maven("https://repo.ivchenko.dev/releases")
        gradlePluginPortal()
    }
}
```

```kotlin
plugins {
    id("application")
    id("dev.ivchenko.webview") version "<version>"
}

webview {
    imageName = "my-app"            // default: the project name
    optimizeForSize = true          // -Os; default true
    maxHeapSize = "64m"             // -R:MaxHeapSize; default 64m, "" to leave unset
    buildArgs.add("--verbose")      // appended to native-image

    windows {
        icon = file("src/main/windows/app.ico")   // resource id 1: the executable's and the window's icon
        console = false                            // default false: GUI subsystem, no console window
        fileDescription = "My app"                 // default: imageName
        productName = "My product"                 // default: imageName
        companyName = "Example & Co"
        copyright = "(c) Example"
        version = "1.2.3"                          // default: the project version
        resourceScript = file("custom.rc")         // replaces the generated script entirely
    }

    macos {
        bundleIdentifier = "com.example.myapp"     // default: <group>.<name>
        bundleName = "My app"                      // default: imageName
        version = "1.2.3"                          // default: the project version
        infoPlist = file("Info.plist")             // replaces the generated property list entirely
    }
}
```

What the plugin does:

- Adds `--enable-native-access=ALL-UNNAMED` to the native image and to the `application` plugin's
  default JVM arguments, so `run` needs nothing extra.
- Sets `toolchainDetection = false`: Gradle runs on any JDK, `GRAALVM_HOME` names the GraalVM.
- Windows: `generateWindowsResourceScript` writes the `.rc` from the settings above,
  `compileWindowsResources` compiles it with `rc.exe` (from the `PATH` of a Visual Studio prompt,
  or the newest installed Windows SDK), and the `.res` is linked in with `/SUBSYSTEM:WINDOWS` and
  `/ENTRY:mainCRTStartup` unless `console = true`.
- macOS: `generateInfoPlist` writes the property list and the linker embeds it into the
  `__TEXT,__info_plist` section, the way the `java` launcher carries its own. WebKit's helper
  processes need the bundle identifier; the menu bar and Dock show the name.

The generated files land in `build/webview/`.
