#!/bin/sh
# ElektorykScan AIDE-safe Gradle bootstrap.
# AIDE invokes ./gradlew. This script reuses a compatible Gradle 8.2 when present,
# otherwise downloads the official Gradle 8.2 binary distribution once.
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.2
DIST_NAME="gradle-${GRADLE_VERSION}"
DIST_ZIP="${DIST_NAME}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/${DIST_ZIP}"
DIST_SHA256="38f66cd6eef217b4c35855bb11ea4e9fbc53594ccccb5fb82dfd317ef8c2c5a3"

# Prefer a system Gradle only when it is the compatible 8.2 line.
if command -v gradle >/dev/null 2>&1; then
    SYS_VERSION=$(gradle --version 2>/dev/null | sed -n 's/^Gradle[[:space:]]\+//p' | head -n 1 || true)
    case "$SYS_VERSION" in
        8.2|8.2.*) exec gradle "$@" ;;
    esac
fi

if [ -n "${GRADLE_USER_HOME:-}" ]; then
    GUH=$GRADLE_USER_HOME
elif [ -n "${HOME:-}" ]; then
    GUH="$HOME/.gradle"
else
    GUH="$APP_HOME/.gradle"
fi

# Reuse an official-wrapper cache if AIDE already has Gradle 8.2.
for CANDIDATE in \
    "$GUH"/wrapper/dists/gradle-8.2-bin/*/gradle-8.2/bin/gradle \
    "$APP_HOME"/.gradle/wrapper/dists/gradle-8.2-bin/*/gradle-8.2/bin/gradle
 do
    if [ -f "$CANDIDATE" ]; then
        chmod +x "$CANDIDATE" 2>/dev/null || true
        exec "$CANDIDATE" "$@"
    fi
 done

BOOT_ROOT="$GUH/elektoryk-bootstrap"
GRADLE_HOME="$BOOT_ROOT/$DIST_NAME"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
ZIP_PATH="$BOOT_ROOT/$DIST_ZIP"

if [ ! -f "$GRADLE_BIN" ]; then
    mkdir -p "$BOOT_ROOT"
    TMP_ZIP="$ZIP_PATH.part"
    rm -f "$TMP_ZIP"
    echo "ElektorykScan: first build — downloading Gradle $GRADLE_VERSION..."

    if command -v curl >/dev/null 2>&1; then
        curl -fL --connect-timeout 20 --retry 3 --retry-delay 2 -o "$TMP_ZIP" "$DIST_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$TMP_ZIP" "$DIST_URL"
    elif command -v busybox >/dev/null 2>&1 && busybox wget --help >/dev/null 2>&1; then
        busybox wget -O "$TMP_ZIP" "$DIST_URL"
    else
        echo "ERROR: Gradle 8.2 is not cached and this AIDE build environment has no curl/wget." >&2
        echo "Connect AIDE to internet or install/cache Gradle 8.2, then press Build again." >&2
        exit 127
    fi

    if [ ! -s "$TMP_ZIP" ]; then
        echo "ERROR: Gradle download returned an empty file." >&2
        rm -f "$TMP_ZIP"
        exit 1
    fi

    if command -v sha256sum >/dev/null 2>&1; then
        GOT=$(sha256sum "$TMP_ZIP" | awk '{print $1}')
        if [ "$GOT" != "$DIST_SHA256" ]; then
            echo "ERROR: Gradle download checksum mismatch." >&2
            rm -f "$TMP_ZIP"
            exit 1
        fi
    fi

    mv "$TMP_ZIP" "$ZIP_PATH"
    rm -rf "$GRADLE_HOME"
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$ZIP_PATH" -d "$BOOT_ROOT"
    elif command -v jar >/dev/null 2>&1; then
        (cd "$BOOT_ROOT" && jar xf "$ZIP_PATH")
    else
        echo "ERROR: Cannot unpack Gradle: neither unzip nor JDK jar is available." >&2
        exit 127
    fi
fi

if [ ! -f "$GRADLE_BIN" ]; then
    echo "ERROR: Gradle bootstrap completed but $GRADLE_BIN is missing." >&2
    exit 1
fi
chmod +x "$GRADLE_BIN" 2>/dev/null || true
exec "$GRADLE_BIN" "$@"
