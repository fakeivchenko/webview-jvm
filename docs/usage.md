# Usage guide

Everything below is available from `webview-jvm-core`; a backend on the runtime classpath does the
rest (see [Installation](../README.md#installation)).

## Opening a window

```java
WebviewParameters parameters = WebviewParameters.builder()
        .title("Docs")
        .width(1280)
        .height(800)
        .url("https://example.com")   // optional: navigate as soon as the window exists
        .build();

try (WebviewBackend webview = Webview.create(parameters)) {
    webview.run();
}
```

Every method of `WebviewBackend` is safe to call from any thread; the backend marshals calls onto
its UI thread. `run()` blocks the caller until the user closes the window or `close()` is called
from elsewhere. `WebviewBackend` is `AutoCloseable` - always open it in try-with-resources.

## Shipping the frontend inside the jar

Put the page under `src/main/resources` and load it by classpath path:

```java
webview.loadResource("app/index.html");
```

## Calling Java from the page

`bind` publishes a Java function on `window`. The page gets a promise; the handler runs on a
virtual thread, so it may block, and a handler that throws rejects the promise with its message.

```java
webview.bind("sha256", text -> hexSha256(text));
webview.bind("systemInfo", _ -> """
        {"java": "%s", "os": "%s"}""".formatted(
        System.getProperty("java.version"), System.getProperty("os.name")));
```

```js
const hash = await window.sha256("webview-jvm");
const info = JSON.parse(await window.systemInfo());
```

Handlers exchange strings. A string argument is passed through as is; any other JavaScript value
arrives serialised as JSON. Bind before the page loads to have the function available from the
first script; binding later works too and applies to the page already on screen.

## Calling the page from Java

`eval` runs a script in the current page and returns its result as a string:

```java
String title = webview.eval("document.title").get();
webview.eval("window.onHeapUsage(%d);".formatted(usedMegabytes));   // fire and forget
```

The engine can only hand back a value it can serialise - a string, number or boolean. A
`Promise` fails the future rather than being awaited; for asynchronous work, let the page store
the outcome and read it afterwards, or drive the call from the page through `bind`.

## Load events

```java
webview.onLoad(event -> {
    switch (event.state()) {
        case STARTED -> log.info("Loading {}", event.uri());
        case FAILED -> log.error("Could not load {}: {}", event.uri(), event.message());
        case FINISHED -> log.info("Loaded {}", event.uri());
        default -> { }
    }
});
```

Register the listener before navigating to see the first event. WebKit follows a `FAILED` with a
`FINISHED` for its own error page, so success is "`FINISHED` without a prior `FAILED`".

## Developing the frontend with Vite, React, Vue and more

Set `devServerUrl` and `loadResource` opens the dev server
instead of the bundled files, with the bridge intact - `window.sha256()` works on the hot-reloaded
page exactly as it will in production:

```bash
WEBVIEW_DEV_SERVER_URL=http://localhost:5173 ./gradlew :webview-jvm-example:run
```

The value also comes from `-Dwebview.devServerUrl=…` or from
`WebviewParameters.builder().devServerUrl(…)`.

## Choosing a backend

`Webview.create()` takes the highest-priority backend that supports the machine: the native one for
the platform, or - when its engine is missing and the experimental `webview-jvm-chrome` is on the classpath -
an installed Chrome, Chromium or Edge. `-Dwebview.backend=<name>` or `WEBVIEW_BACKEND` names one
explicitly (`gtk3-webkit2gtk-4.1`, `win32-webview2`, `cocoa-wkwebview`, `chromium`); `-Dwebview.chrome=<path>`
or `WEBVIEW_CHROME` points the Chrome backend at a particular executable, and `-Dwebview.chrome.args` /
`WEBVIEW_CHROME_ARGS` adds command line switches to it (for example `--no-proxy-server`).
