#!/usr/bin/env bash
# Recover a hung / broken network adb connection to the Android TV box.
# Usage: ./scripts/adb-reconnect.sh [ip:port]   (default 192.168.50.207:5555)
#
# Why this exists: network adb to the TV drops often. A half-killed adb server
# leaves it in a "protocol fault / Connection reset by peer" state, and macOS has
# no `timeout`, so a hung `adb connect` must be bounded another way (perl alarm).

set -u
UDID="${1:-192.168.50.207:5555}"
HOST="${UDID%%:*}"

echo "== ping $HOST (macOS -t = total timeout sec) =="
if ping -c 3 -t 5 "$HOST" >/dev/null 2>&1; then
  echo "   reachable ✅  (box is on the network — problem is the local adb server)"
else
  echo "   NOT reachable ❌  (box asleep / IP changed / Wi-Fi down)"
  echo "   → wake it with the remote, check IP in Settings > Network, re-enable ADB debugging if needed."
fi

echo "== clean restart of adb server =="
adb kill-server 2>/dev/null
adb start-server >/dev/null 2>&1

echo "== connect $UDID (bounded to 10s) =="
perl -e 'alarm 10; exec("adb","connect",$ARGV[0])' "$UDID" 2>&1 || echo "   connect timed out"

echo "== devices =="
adb devices -l
