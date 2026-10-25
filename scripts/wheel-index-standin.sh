#!/usr/bin/env bash
# CI helper: if GitHub Pages isn't serving the wheel index yet, serves a copy of the
# gh-pages branch on port 8000 of this host instead and prints the instrumentation
# argument that points ScienceTest at it (as seen from the device, at host address $1).
# Prints nothing when Pages is live.
#
# Usage: scripts/wheel-index-standin.sh <host address seen from the device>
set -euo pipefail
url=$(grep -o 'https://[^"]*/simple/' app/build.gradle.kts | head -1)
if curl -fsS -o /dev/null "$url"; then
    echo "wheel index live at $url" >&2
    exit 0
fi
git fetch -q --depth 1 origin gh-pages
rm -rf /tmp/wheel-site && mkdir -p /tmp/wheel-site
git archive FETCH_HEAD | tar -x -C /tmp/wheel-site
nohup python3 -m http.server 8000 --bind 0.0.0.0 -d /tmp/wheel-site >/tmp/wheel-site.log 2>&1 &
sleep 1
curl -fsS -o /dev/null http://127.0.0.1:8000/simple/
echo "::warning::$url isn't served by GitHub Pages yet; tests use a local copy of gh-pages." >&2
echo "wheelIndex=http://$1:8000/simple/"
