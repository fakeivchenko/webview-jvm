<h1 align="center">webview-jvm</h1>

<p align="center">
  Desktop windows for Java, drawn by the operating system's own web engine.<br>
  <b>No JNI. No native code. No native artifacts.</b> Compiles to a 15 MB executable.
</p>

<p align="center">
  <a href="https://github.com/fakeivchenko/webview-jvm/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/fakeivchenko/webview-jvm/actions/workflows/ci.yml/badge.svg?branch=dev"></a>
  <a href="https://repo.ivchenko.dev/#/releases/dev/ivchenko/webview/webview-jvm-core"><img alt="Latest release" src="https://repo.ivchenko.dev/api/badge/latest/releases/dev/ivchenko/webview/webview-jvm-core?color=40c14a&name=release"></a>
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-blue">
  <img alt="GraalVM native-image ready" src="https://img.shields.io/badge/GraalVM-native--image%20ready-2f6fd6">
</p>

Build the UI with HTML, CSS and JavaScript, ship it inside the native executable or jar package, drive it from Java.
The window is drawn by the web engine the operating system already has - WebKitGTK on Linux, WebView2 on
Windows, WKWebView on macOS - so nothing is bundled and nothing is compiled. The whole application can be turned into
a small self-contained binary with <a href="https://www.graalvm.org/jdk25/reference-manual/native-image/">GraalVM Native Image</a>.

## Simple example

```java
try (WebviewBackend webview = Webview.create(WebviewParameters.builder()
        .title("My app")
        .width(1024)
        .height(768)
        .build())) {
    webview.bind("greet", name -> "Hello, " + name + "!");   // page -> Java
    webview.loadResource("app/index.html");                  // served from the classpath
    webview.run();                                           // blocks until the window closes
}
```

```js
// app/index.html
const greeting = await window.greet("world");   // "Hello, world!"
```

Windows, resources from the jar, the bridge between the page and Java, load events and the
dev-server workflow are covered in the **[usage guide](docs/usage.md)**.

## Why webview-jvm

- **No native artifacts** Pure Java on top of the system's own web engine. No JNI, no bundled
  libraries, no compiler at build time.
- **Small and fast as a native binary.** About 15 MB, starts in a few hundred milliseconds, runs
  in a few megabytes of heap. No JVM to install, no Chromium to bundle.
- **One jar, every platform.** The same artifact runs on Linux, Windows and macOS; a backend for
  another OS simply stays inert.
- **A real bridge, both ways.** Call Java from the page and get a `Promise`; run scripts from Java
  and get a `CompletableFuture`. Handlers run on virtual threads and may block.
- **Your frontend workflow.** Point it at a Vite/React/Vue dev server while developing - hot
  reload and the bridge both work - and at the files inside the jar in production.
- **Thread-safe and tested.** Call it from any thread. Both backends pass one contract test suite
  on CI; the suite ships as a jar so a third-party backend can prove itself the same way.

## Supported platforms

| OS              | Architecture  | Engine                | Module                | JVM | Native image | Tested on                             |
|-----------------|---------------|-----------------------|-----------------------|:---:|:------------:|---------------------------------------|
| Linux           | x86_64        | GTK 3 + WebKitGTK 4.1 | `webview-jvm-gtk`     | ✅  |      ✅      | Ubuntu 24.04 (CI), Arch-based desktop |
| Linux           | aarch64       | GTK 3 + WebKitGTK 4.1 | `webview-jvm-gtk`     | ✅¹ |     ✅¹      | -                                     |
| FreeBSD         | x86_64        | GTK 3 + WebKitGTK 4.1 | `webview-jvm-gtk`     | ✅¹ |      -       | -                                     |
| Windows 10 / 11 | x86_64        | Win32 + WebView2      | `webview-jvm-windows` | ✅  |      ✅      | Windows 11, Windows Server (CI)       |
| Windows 11      | ARM64         | Win32 + WebView2      | `webview-jvm-windows` | ✅¹ |     ❌²      | -                                     |
| macOS 12+       | x86_64, arm64 | Cocoa + WKWebView     | `webview-jvm-macos`   | ✅  |      ✅      | macOS 15 Intel and macOS 14 arm64 (CI) |
| any of the above | any          | installed Chrome / Chromium / Edge | `webview-jvm-chrome` | 🧪 | 🧪 | Linux (Chromium), Windows 11 (Edge) |

✅ working and covered by CI · ✅¹ expected to work, not yet tested · ❌² no GraalVM `native-image`
for Windows ARM64 · 🧪 **experimental** fallback: used only when no native engine is
present (or when asked for with `-Dwebview.backend=chromium`), drives a browser the machine already
has over the DevTools protocol; looks and behaves like the native backends, but the window appears
as soon as it is created and `resizable(false)` is not enforced

## Requirements

- **Java 25.**
- **Linux:** GTK 3 and WebKitGTK 4.1
- **macOS:** nothing to install - AppKit and WebKit are part of the system. Under the JVM the
  library uses the process main thread the `java` launcher keeps for AppKit; in a native image the
  application's `main` is that thread, so call `run()` from it.
- **Windows:** the WebView2 Runtime - present on Windows 11 and wherever Edge is installed;
  otherwise use [Microsoft's installer](https://developer.microsoft.com/microsoft-edge/webview2/).
- The JVM flag `--enable-native-access=ALL-UNNAMED`.

## Installation

```kotlin
repositories {
    maven("https://repo.ivchenko.dev/releases")
}

dependencies {
    implementation("dev.ivchenko.webview:webview-jvm-core:<version>")
    runtimeOnly("dev.ivchenko.webview:webview-jvm-gtk:<version>")
    runtimeOnly("dev.ivchenko.webview:webview-jvm-windows:<version>")
    runtimeOnly("dev.ivchenko.webview:webview-jvm-macos:<version>")

    // optional, experimental: fall back to an installed Chrome/Chromium/Edge when the native engine is missing
    runtimeOnly("dev.ivchenko.webview:webview-jvm-chrome:<version>")
    runtimeOnly("org.eclipse.parsson:parsson:1.1.9")
}
```

## Native executable

```bash
GRAALVM_HOME=/path/to/graalvm ./gradlew :webview-jvm-example:nativeCompile
```

Or, with nothing installed but Docker:

```bash
docker buildx build -f Dockerfile.native -o build/native-docker .
```

The Windows build produces a GUI executable with its own icon - a double-click opens the window
and nothing else. Details, container build and metadata are in
[docs/development.md](docs/development.md#native-executable).

## Example

```bash
./gradlew :webview-jvm-example:run
```

A window with a page that asks the JVM about itself, hashes text in Java, shows an image served
from the jar and receives a heap-usage figure pushed from Java once a second.

Ideas and pull requests are welcome - open an issue first for anything that touches the
`WebviewBackend` contract.

## More

- [Usage guide](docs/usage.md) - windows, resources, bridge, events, dev server
- [Development](docs/development.md) - building, tests, CI, releases, native image, Windows VM
- [Design notes](docs/design.md) - why it is built the way it is
