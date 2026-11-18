#!/system/bin/sh
export LD_LIBRARY_PATH=$PREFIX

ROOTFS_DIR="$PREFIX/rootfs"
if [ ! -d "$ROOTFS_DIR" ] && [ -d "$PREFIX/alpine" ]; then
    ROOTFS_DIR="$PREFIX/alpine"
fi

mkdir -p "$PREFIX/tmp"
chmod 700 "$PREFIX/tmp" 2>/dev/null || true
export TMPDIR="$PREFIX/tmp"
mkdir -p "$ROOTFS_DIR/tmp"
mkdir -p "$ROOTFS_DIR/tmp/shm"
mkdir -p "$PREFIX/public"

FAILSAFE=false
INSTALLING=false

for arg in "$@"; do
    case "$arg" in
        --failsafe)
            FAILSAFE=true
            ;;
        --installing)
            INSTALLING=true
            ;;
    esac
done

# Locate crest-chroot binary
CREST_CHROOT=""
if [ -n "$CREST_CHROOT_BIN" ] && [ -x "$CREST_CHROOT_BIN" ]; then
    CREST_CHROOT="$CREST_CHROOT_BIN"
elif [ -n "$CREST_CHROOT_BIN" ] && [ -f "$CREST_CHROOT_BIN" ]; then
    CREST_CHROOT="$CREST_CHROOT_BIN"
elif [ -f "$NATIVE_DIR/libcrest-chroot.so" ]; then
    CREST_CHROOT="$NATIVE_DIR/libcrest-chroot.so"
elif [ -f "$PREFIX/libcrest-chroot.so" ]; then
    CREST_CHROOT="$PREFIX/libcrest-chroot.so"
fi

if [ -z "$CREST_CHROOT" ]; then
    echo "[CrestEditor] Fatal: crest-chroot binary not found in $NATIVE_DIR or $PREFIX" >&2
    exit 1
fi

chmod 755 "$CREST_CHROOT" 2>/dev/null || true

CHROOT_ARGS="-u -l"

# Bind host system mounts if they exist
for system_mnt in /apex /odm /product /system /system_ext /vendor /linkerconfig/ld.config.txt; do
    if [ -e "$system_mnt" ]; then
        resolved=$(readlink -f "$system_mnt" 2>/dev/null || echo "$system_mnt")
        CHROOT_ARGS="$CHROOT_ARGS -b ${resolved}:${system_mnt}"
    fi
done

# Bind storage paths if they exist
if [ -d "/storage/emulated/0" ]; then
    CHROOT_ARGS="$CHROOT_ARGS -b /storage/emulated/0:/sdcard -b /storage/emulated/0:/storage/emulated/0"
elif [ -e "/sdcard" ]; then
    CHROOT_ARGS="$CHROOT_ARGS -b /sdcard:/sdcard"
fi
if [ -d "/storage" ]; then
    CHROOT_ARGS="$CHROOT_ARGS -b /storage:/storage"
fi

# Bind app paths
if [ -d "$PREFIX" ]; then
    CHROOT_ARGS="$CHROOT_ARGS -b $PREFIX:$PREFIX -b $PREFIX:/files"
    CHROOT_ARGS="$CHROOT_ARGS -b $PREFIX/public:/public -b $PREFIX/public:/home -b $PREFIX/public:/root"
fi
if [ -n "$NATIVE_DIR" ] && [ -d "$NATIVE_DIR" ]; then
    CHROOT_ARGS="$CHROOT_ARGS -b $NATIVE_DIR:$NATIVE_DIR"
fi
if [ -d "$ROOTFS_DIR/tmp/shm" ]; then
    CHROOT_ARGS="$CHROOT_ARGS -b $ROOTFS_DIR/tmp/shm:/dev/shm"
fi

# Determine guest work directory
CWD_TARGET="/public"
if [ -n "$INITIAL_CWD" ] && [ -d "$INITIAL_CWD" ]; then
    case "$INITIAL_CWD" in
        /storage/emulated/0*)
            CWD_TARGET="${INITIAL_CWD#/storage/emulated/0}"
            CWD_TARGET="/sdcard$CWD_TARGET"
            ;;
        *)
            CWD_TARGET="$INITIAL_CWD"
            ;;
    esac
fi
CHROOT_ARGS="$CHROOT_ARGS -w $CWD_TARGET"

# Pass guest environment variables
CHROOT_ARGS="$CHROOT_ARGS -E PREFIX=$PREFIX"
CHROOT_ARGS="$CHROOT_ARGS -E HOME=/public"
CHROOT_ARGS="$CHROOT_ARGS -E TERM=${TERM:-xterm-256color}"
CHROOT_ARGS="$CHROOT_ARGS -E COLORTERM=${COLORTERM:-truecolor}"
CHROOT_ARGS="$CHROOT_ARGS -E TMPDIR=/tmp"
CHROOT_ARGS="$CHROOT_ARGS -E PATH=/usr/local/bin:/usr/local/sbin:/bin:/sbin:/usr/bin:/usr/sbin:/system/bin"
if [ -n "$ANDROID_TZ" ]; then
    CHROOT_ARGS="$CHROOT_ARGS -E ANDROID_TZ=$ANDROID_TZ"
fi
if [ -n "$CREST_BRIDGE_PORT" ]; then
    CHROOT_ARGS="$CHROOT_ARGS -E CREST_BRIDGE_PORT=$CREST_BRIDGE_PORT"
fi
if [ -n "$PREFERRED_SHELL" ]; then
    CHROOT_ARGS="$CHROOT_ARGS -E PREFERRED_SHELL=$PREFERRED_SHELL"
fi

if [ "$FAILSAFE" = true ] && [ "$INSTALLING" != true ]; then
    echo "$$" > "$PREFIX/pid" 2>/dev/null || true
    exec "$CREST_CHROOT" $CHROOT_ARGS "$ROOTFS_DIR" /bin/sh
else
    exec "$CREST_CHROOT" $CHROOT_ARGS "$ROOTFS_DIR" /bin/sh "$PREFIX/init-alpine.sh" "$@"
fi
