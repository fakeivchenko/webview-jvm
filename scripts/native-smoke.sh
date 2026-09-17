#!/usr/bin/env bash
#
# Smoke test for the native example binary: a local page calls two Java handlers through the
# bridge and reports the answers back over http.
#
#   xvfb-run -a scripts/native-smoke.sh path/to/webview-jvm-example
#
set -euo pipefail

binary=${1:?usage: native-smoke.sh <binary>}
port=${PORT:-5199}
workdir=$(mktemp -d)
trap 'kill "${app:-}" "${server:-}" "${logger:-}" 2>/dev/null || true; rm -rf "$workdir"' EXIT

cat > "$workdir/index.html" <<'HTML'
<!DOCTYPE html><html><body><script>
(async () => {
  try {
    const info = JSON.parse(await window.systemInfo());
    const hash = await window.sha256("webview-jvm");
    await fetch(`/report?backend=${info.backend}&hash=${hash}`);
  } catch (error) {
    await fetch(`/report?error=${encodeURIComponent(error.message)}`);
  }
})();
window.onHeapUsage = (megabytes) => fetch(`/heap?mb=${megabytes}`);
</script></body></html>
HTML

python3 -m http.server "$port" --bind 127.0.0.1 --directory "$workdir" > "$workdir/server.log" 2>&1 &
server=$!
sleep 1

if command -v log >/dev/null && [ "$(uname)" = Darwin ]; then
    log stream --style compact --predicate 'process CONTAINS "webview-jvm-example" OR process CONTAINS "com.apple.WebKit"' \
        > "$workdir/system.log" 2>&1 &
    logger=$!
    sleep 2
fi

WEBVIEW_DEV_SERVER_URL="http://127.0.0.1:$port" "$binary" > "$workdir/app.log" 2>&1 &
app=$!

for _ in $(seq 1 30); do
    grep -q 'GET /report' "$workdir/server.log" && grep -q 'GET /heap' "$workdir/server.log" && break
    sleep 1
done

echo "--- application ---"; cat "$workdir/app.log"
echo "--- page reported ---"; grep -oE 'GET /(report|heap)[^ ]*' "$workdir/server.log" || true
echo "--- server ---"; cat "$workdir/server.log"
if [ -n "${logger:-}" ]; then
    sleep 2
    echo "--- system log ---"; grep -E 'WebKit:(Loading|Process|Network|ProcessSuspension)|Networking|RunningBoard| E  ' "$workdir/system.log" \
        | grep -vE 'Sandbox|appintents|linkd|DisplayLink|Layer|ActivityState|xpc:connection|ProcessSuspension' | tail -120
fi

expected_hash=$(printf 'webview-jvm' | sha256sum | cut -d' ' -f1)
grep -qE "GET /report\?backend=(gtk3-webkit2gtk-4.1|win32-webview2|cocoa-wkwebview|chromium)&hash=$expected_hash" "$workdir/server.log" \
    || { echo "FAIL: page -> Java bridge did not answer correctly"; exit 1; }
grep -q 'GET /heap?mb=' "$workdir/server.log" \
    || { echo "FAIL: Java -> page eval never arrived"; exit 1; }
echo "OK: native bridge works in both directions"
