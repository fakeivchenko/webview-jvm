# Development

## Building and testing

```bash
./gradlew build                 # compiles, runs headless and display tests, builds jars and Javadoc
./gradlew test                  # headless tests only: core logic, backend discovery, metadata
./gradlew displayTest           # tests that open a real window (skipped without a display)
./gradlew networkTest           # loads a public site; never part of a default run
```

Display tests are skipped when there is no `DISPLAY`/`WAYLAND_DISPLAY`, so a headless machine
still gets a green build. CI must not take that shortcut: `-Dwebview.requireDisplay=true` turns
the skip into a failure, and the tests run under Xvfb:

```bash
xvfb-run -a ./gradlew displayTest -Dwebview.requireDisplay=true
```

`-Dwebview.screenshots=true` makes the display tests photograph the screen while their window is
up, into `build/screenshots/` of each backend module; CI attaches them to every run as the
`screenshots-linux` and `screenshots-windows` artifacts.

`Dockerfile.test` reproduces the CI environment (Ubuntu 24.04, GTK, WebKitGTK, Xvfb) locally:

```bash
docker buildx build -f Dockerfile.test .
```

## Building native executable

The example module is configured for GraalVM `native-image`. With a GraalVM 25 installed:

```bash
GRAALVM_HOME=/path/to/graalvm ./gradlew :webview-jvm-example:nativeCompile
./webview-jvm-example/build/native/nativeCompile/webview-jvm-example
```

Or without installing anything, the way Quarkus' container build works:

```bash
docker buildx build -f Dockerfile.native -o build/native-docker .
./build/native-docker/webview-jvm-example
```

On Windows the build needs the Visual Studio C++ toolchain in the environment (an *x64 Native
Tools* prompt, or `vcvars64.bat`). The result is a **GUI-subsystem** executable - a double-click
opens the window and nothing else, no console - with an icon and version block generated and
compiled by the [Gradle plugin](gradle-plugin.md). The window class loads icon resource 1 of the
running module, so the same icon shows in the title bar and taskbar.

The plugin lives in `webview-jvm-gradle-plugin`, an included build (`pluginManagement {
includeBuild(...) }` in `settings.gradle.kts`), so the example applies it by id straight from the
source tree; `./gradlew :webview-jvm-gradle-plugin:test` runs its TestKit tests and the root
`publish` task publishes it alongside the library.

The reachability metadata for the FFM stubs ships inside each backend jar, so an application
registers nothing itself. A contract test (`NativeImageMetadataContractTest`) keeps that metadata
in sync with the bindings on every platform: a stub that is bound but not registered fails the
build, not the binary at run time.

The version is a Gradle property: `-PprojectVersion=1.2.3`, `0.0.0-dev` when absent.
`./gradlew publishToMavenLocal` installs the jars into `~/.m2` for trying them from another
project.

## Developing the Windows backend from Linux

The Windows backend compiles and its headless tests run anywhere; only the display tests, the
native build and real debugging need Windows. `scripts/windows/` drives a Windows VM over SSH
(`WIN_HOST`, default `win`; `WIN_SRC_DIR`, default `C:\src`):

```bash
scripts/windows/sync-to-vm.sh :webview-jvm-windows:test           # copy the tree, run a Gradle task
scripts/windows/run-in-session.sh :webview-jvm-windows:displayTest -Dwebview.requireDisplay=true
```

The second script exists because a command run over SSH lands in session 0, where windows have
no desktop and WebView2 refuses to attach (`ERROR_INVALID_WINDOW_HANDLE`). It runs the task
through a scheduled task in the logged-on user's session instead and streams the log back.
