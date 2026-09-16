# Design notes

- **One UI thread per process.** GTK may only be touched from the thread that initialised it.
  `UiDispatcher` owns that thread, runs the toolkit's event loop on it and marshals work from
  everywhere else through `g_idle_add`, the one GLib entry point that is thread safe. Backends
  for other toolkits reuse it and supply four hooks.
- **Static callbacks, id-keyed.** A C callback carries no closure, only a `void*`. Objects are
  registered under generated ids that travel as that pointer (`CallbackRegistry`), so one upcall
  stub serves every window - and the stub set stays fixed for `native-image`.
- **Static `MethodHandle`s.** `invokeExact` compiles to a direct call only when the JIT sees the
  handle as a constant, so bindings are `static final` and the binding classes are utility
  classes.
- **Nothing unwinds into C.** Every callback and listener invocation catches `Throwable` and
  reports it; letting a Java exception cross into GTK is undefined behaviour.
- **The default `WebKitWebContext` is retained forever.** WebKit's `atexit` handler unrefs it;
  if the last web view is already gone, that drops the refcount to zero and the process aborts
  while disposing the website data manager. One extra reference keeps the process-wide singleton
  alive, which is where it belongs anyway.
- **The bridge is a string protocol.** `id␟name␟payload` with an ASCII unit separator - not NUL,
  which would truncate the C string - and no JSON dependency on either side. Only the transport
  expression differs between engines: `window.webkit.messageHandlers.<name>.postMessage` on
  WebKitGTK and WKWebView, `window.chrome.webview.postMessage` on WebView2.
- **COM without a compiler.** A COM object is a pointer to a vtable, and a method call is a C call
  through slot *n* with the object first. So the Windows backend keeps one `MethodHandle` per
  method *shape* and calls slot numbers taken from `WebView2.h`. Callbacks WebView2 makes into
  Java are Java-built COM objects: two shared vtables (completion and event `Invoke`) over a
  40-byte struct holding an id, a reference count and the IID it answers to.
- **No `WebView2Loader.dll`.** The loader is thin: it finds the runtime in the registry and calls
  `CreateWebViewEnvironmentWithOptionsInternal` in `EmbeddedBrowserWebView.dll`. The backend does
  the same, which keeps the "no native artifacts" rule intact on Windows too.
