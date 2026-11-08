#!/bin/sh

set -e

echo "[1/2] Setting DNS resolver..."
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > /etc/resolv.conf

if command -v xbps-install >/dev/null 2>&1; then
    echo "[2/2] Void Linux (Glibc) environment detected."
    if [ ! -e /usr/share/terminfo/x/xterm-256color ]; then
        echo "Configuring terminal support (ncurses)..."
        xbps-install -Sy ncurses-base ncurses-term || true
    fi
elif command -v apk >/dev/null 2>&1; then
    echo "[2/4] Trusting CrestEditor Repository Key..."
    mkdir -p /etc/apk/keys
    if [ -f "$PREFIX/crest@crestcode.rsa.pub" ]; then
        cp -f "$PREFIX/crest@crestcode.rsa.pub" /etc/apk/keys/
    fi

    echo "[3/4] Adding Alpine & CrestEditor Repositories..."
    if ! grep -q "edge/main" /etc/apk/repositories 2>/dev/null; then
        cat >> /etc/apk/repositories <<'EOF'
https://dl-cdn.alpinelinux.org/alpine/edge/main
https://dl-cdn.alpinelinux.org/alpine/edge/community
EOF
    fi

    if ! grep -q 'crest-editor-dev.github.io/Crest-Packages' /etc/apk/repositories 2>/dev/null; then
        echo 'https://crest-editor-dev.github.io/Crest-Packages' >> /etc/apk/repositories
    fi

    echo "[4/4] Updating APK indexes & installing Bash..."
    apk update || true
    apk add bash jq || true
fi

echo
echo "=========================================="
echo "      Crest Terminal Setup Completed      "
echo "=========================================="
echo
if command -v bash >/dev/null 2>&1; then
    echo "Shell: $(bash --version | head -n 1)"
else
    echo "Fallback: Using /bin/sh"
fi

if command -v xbps-install >/dev/null 2>&1; then
    echo "Package Manager: XBPS (Void Glibc)"
elif command -v apk >/dev/null 2>&1; then
    echo "Package Manager: APK (Alpine Musl)"
fi
