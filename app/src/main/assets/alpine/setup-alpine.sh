#!/bin/sh

set -e

echo "[1/4] Setting DNS..."
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > /etc/resolv.conf

echo "[2/4] Trusting CrestEditor Repository Key..."
mkdir -p /etc/apk/keys
if [ -f "$PREFIX/crest@crestcode.rsa.pub" ]; then
    cp -f "$PREFIX/crest@crestcode.rsa.pub" /etc/apk/keys/
else
    cat <<'EOF' > /etc/apk/keys/crest@crestcode.rsa.pub
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv8SdTBe/lPIyzbiCmGpy
KoFQztASc+3ZGI2mXTLueAybsl8gJQ6+n7MoFcUlkeG+UuTvuFof8lned9HSKRbq
9XxYgdZbpDGrCF32EoYlXdX9KzRG2yU8NeNKYHq94YNh3SOhmGjUJ9lrv182ekSi
t9Im4CUE5VeMVmov4vx6Yd/2coGGxLB1jaXjnxyhR6JOxzvhYZA0gsmQNPiXXnSj
vx+PCwP48HojSZjESjjioqhCC47DdGw9xTfgS1tPMShc12hjEJ0ne6F8KMcLSnDU
g4ma9/JeSTDUPCnU2bRMkHahQz/IaCOPmjtHqXMApO/zwTQPUClAgxouf44E+0pE
2QIDAQAB
-----END PUBLIC KEY-----
EOF
fi

echo "[3/4] Adding Alpine & CrestEditor Repositories..."
if ! grep -q "edge/main" /etc/apk/repositories 2>/dev/null; then
    cat >> /etc/apk/repositories <<'EOF'
https://dl-cdn.alpinelinux.org/alpine/edge/main
https://dl-cdn.alpinelinux.org/alpine/edge/community
EOF
fi

if ! grep -q 'crest-editor-dev.github.io/Crest-Packages' /etc/apk/repositories; then
    echo 'https://crest-editor-dev.github.io/Crest-Packages' >> /etc/apk/repositories
fi

echo "[4/4] Updating APK indexes & installing Bash, CPM, CHSH, and JQ..."
apk update || true
apk add bash cpm chsh jq || true

echo
echo "=========================================="
echo " Crest Terminal Setup Completed "
echo "=========================================="
echo
if command -v bash >/dev/null 2>&1; then
    echo "Bash: $(bash --version | head -n 1)"
else
    echo "Fallback: Using /bin/sh"
fi

if command -v cpm >/dev/null 2>&1; then
    echo "CPM:  $(cpm --version)"
fi

if command -v chsh >/dev/null 2>&1; then
    echo "CHSH: $(chsh --version)"
fi
