#!/usr/bin/env bash
# Starts redroid (Android 14 in a container, https://github.com/remote-android/redroid-doc)
# on an arm64 Linux host such as GitHub's ubuntu-24.04-arm runner, which has no KVM for
# the emulator. Waits for boot and leaves the device on adb as localhost:5555.
set -euo pipefail

sudo apt-get update -qq
sudo apt-get install -y -qq "linux-modules-extra-$(uname -r)" adb >/dev/null
sudo modprobe binder_linux devices=binder,hwbinder,vndbinder
docker run -itd --rm --privileged --name redroid -p 5555:5555 redroid/redroid:14.0.0-latest

for i in $(seq 1 90); do
    adb connect localhost:5555 >/dev/null 2>&1 || true
    if [ "$(adb -s localhost:5555 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then
        echo "booted after $((i * 2))s"
        break
    fi
    sleep 2
done
adb -s localhost:5555 wait-for-device
# Ready for UI tests: screen on and unlocked, no animations.
for cmd in "svc power stayon true" "input keyevent KEYCODE_WAKEUP" "wm dismiss-keyguard" \
    "settings put global window_animation_scale 0" "settings put global transition_animation_scale 0" \
    "settings put global animator_duration_scale 0"; do
    adb -s localhost:5555 shell "$cmd" || true
done
echo "abi: $(adb -s localhost:5555 shell getprop ro.product.cpu.abi | tr -d '\r')," \
    "API $(adb -s localhost:5555 shell getprop ro.build.version.sdk | tr -d '\r')"
