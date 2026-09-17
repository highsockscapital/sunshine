#!/data/data/com.termux/files/usr/bin/bash
# Managed bash only: control/inspection/bridge IPC must bypass this guard.
# Usage: bash guard.sh <deadline_seconds> <command_path> <lock_path> <max_output_bytes> <run_dir>
# Not a sandbox: the memory check is preflight only, and a process deliberately
# escaping the job's session (e.g. setsid) is outside process-group cleanup.
set -euo pipefail
set +m
worker=false
if [ "${1:-}" = '--worker' ]; then worker=true; shift; fi
fail() { printf 'Sunshine safety guard: %s\n' "$*" >&2; exit 125; }
[ "$#" -eq 5 ] || fail 'invalid arguments.'
deadline=$1
command_path=$2
lock_path=$3
max_bytes=$4
run_dir=$5
[[ "$deadline" =~ ^[0-9]+([.][0-9]+)?$ ]] && [[ "$deadline" =~ [1-9] ]] || fail 'deadline must be positive seconds.'
[[ "$max_bytes" =~ ^[1-9][0-9]{0,8}$ ]] || fail 'output limit must be a positive integer (at most 9 digits).'
[ -f "$command_path" ] && [ -r "$command_path" ] || fail 'command file is unreadable.'
[ -d "$run_dir" ] || fail 'run directory is missing.'

if [ "$worker" = false ]; then
    for dependency in timeout flock setsid awk head wc cat; do
        command -v "$dependency" >/dev/null || fail "$dependency is unavailable; rerun Termux setup."
    done
    # Supervise from OUTSIDE the isolated group so timeout's SIGKILL cannot kill
    # the supervisor before it returns an exit code to the managed runner.
    cancelled=0
    trap 'cancelled=1' TERM INT HUP
    [ ! -e "$run_dir/cancel_requested" ] || exit 143
    setsid timeout --signal=TERM --kill-after=5s "${deadline}s" \
        bash "$0" --worker "$@" &
    group_pid=$!
    printf '%s' "$group_pid" > "$run_dir/guard_pgid"
    set +e
    if [ "$cancelled" -eq 0 ] && [ ! -e "$run_dir/cancel_requested" ]; then
        wait "$group_pid"
        result=$?
    else
        result=143
    fi
    # Clean up descendants even if their parent exited normally. The command
    # never inherits the lock FD, so detached descendants cannot retain it.
    if kill -0 -- "-$group_pid" 2>/dev/null; then
        kill -TERM -- "-$group_pid" 2>/dev/null
        sleep 0.2
        kill -KILL -- "-$group_pid" 2>/dev/null
    fi
    wait "$group_pid" 2>/dev/null
    if [ "$cancelled" -ne 0 ] || [ -e "$run_dir/cancel_requested" ]; then
        result=143
    fi
    if [ "$result" -eq 124 ] || [ "$result" -eq 137 ]; then
        printf '\n[Sunshine: command deadline reached or job killed (exit %s).]\n' "$result"
    fi
    exit "$result"
fi

# timeout encloses lock acquisition as well as execution: queued work has a
# deadline too. Only this worker owns FD 9, never the command or log collector.
exec 9>"$lock_path"
flock -x 9
[ ! -e "$run_dir/cancel_requested" ] || exit 143
min_available_kib=262144
available_kib=$(awk '/^MemAvailable:/ {print $2; exit}' /proc/meminfo 2>/dev/null) || available_kib=''
[[ "$available_kib" =~ ^[0-9]+$ ]] || fail 'cannot read available memory; command not started.'
if [ "$available_kib" -lt "$min_available_kib" ]; then
    fail "less than $((min_available_kib / 1024)) MiB available; command not started."
fi
# Retain a bounded prefix, then drain excess without buffering or SIGPIPE-ing
# the producer. Probe with head/wc, not read, which can skip binary NUL bytes.
set +e
bash "$command_path" 9>&- 2>&1 | {
    exec 9>&-
    head -c "$max_bytes" || exit $?
    extra_bytes=$(head -c 1 | wc -c) || exit $?
    if [ "$extra_bytes" -gt 0 ]; then
        printf '\n[Sunshine: output truncated at %s bytes; remaining output discarded.]\n' "$max_bytes"
        cat >/dev/null
    fi
}
statuses=("${PIPESTATUS[@]}")
if [ "${statuses[0]}" -ne 0 ]; then exit "${statuses[0]}"; fi
exit "${statuses[1]}"
