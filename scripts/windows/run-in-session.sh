#!/usr/bin/env bash
#
# Runs a Gradle task inside the Windows VM's interactive desktop session.
#
# Commands run over SSH land in session 0, where windows exist but no desktop shows them and
# WebView2 refuses to attach (ERROR_INVALID_WINDOW_HANDLE). A scheduled task marked "run only when
# the user is logged on" executes in the user's real session instead; this script writes the
# command to a batch file, creates such a task for it, fires it, waits and prints the log.
#
#   scripts/windows/run-in-session.sh :webview-jvm-windows:displayTest -Dwebview.requireDisplay=true
#
# WIN_HOST and WIN_SRC_DIR mean the same as for sync-to-vm.sh.
#
set -euo pipefail
host=${WIN_HOST:-win}
parent=${WIN_SRC_DIR:-'C:\src'}
remote="$parent\\$(basename "$(cd "$(dirname "$0")/../.." && pwd)")"
task=webview-jvm-gradle
script="$parent\\webview-jvm-task.cmd"
log="$parent\\webview-jvm-task.log"

# The batch file does the quoting-sensitive work; the task only has to name it.
printf '@echo off\r\ncd /d %s\r\ncall gradlew.bat %s --console=plain > %s 2>&1\r\n' "$remote" "$*" "$log" \
    | ssh "$host" "\$input | Set-Content -Path '$script' -Encoding ascii" 2>/dev/null

ssh "$host" "Remove-Item -Force '$log' -ErrorAction SilentlyContinue; schtasks /Create /F /TN $task /SC ONCE /ST 00:00 /TR '$script' 2>&1 | Out-Null; schtasks /Run /TN $task 2>&1 | Out-Null" 2>/dev/null

for _ in $(seq 1 900); do
    sleep 2
    status=$(ssh "$host" "schtasks /Query /TN $task /FO LIST 2>&1 | Select-String '^Status' | ForEach-Object { \$_.ToString() }" 2>/dev/null || true)
    case "$status" in *Running*) continue ;; *) break ;; esac
done

ssh "$host" "Get-Content '$log'" 2>/dev/null
ssh "$host" "Select-String -Path '$log' -Pattern 'BUILD SUCCESSFUL' -Quiet" 2>/dev/null | grep -q True
