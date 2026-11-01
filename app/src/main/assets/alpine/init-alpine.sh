export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin:$PREFIX/local/bin
export PS1="\[\033[1;32m\]\u\[\033[0m\]@localhost \[\033[1;34m\]\w\[\033[0m\] \$ "
export HOME=/public
export TERM=xterm-256color

INSTALLING=false
FAILSAFE=false

# Parse internal flags
while [ $# -gt 0 ]; do
    case "$1" in
        --installing)
            INSTALLING=true
            shift
            ;;
        --failsafe)
            FAILSAFE=true
            shift
            ;;
        --)
            shift
            break
            ;;
        *)
            break
            ;;
    esac
done

# If a command was supplied, execute it and exit
# without it Executor will break
if [ "$INSTALLING" != true ] && [ $# -gt 0 ] && [ "${1#--}" = "$1" ]; then
    exec "$@"
fi

if [ ! -f /linkerconfig/ld.config.txt ]; then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi


if [ "$INSTALLING" = true ]; then
    echo "Configuring timezone..."

    if [ -n "$ANDROID_TZ" ] && [ -f "/usr/share/zoneinfo/$ANDROID_TZ" ]; then
        ln -sf "/usr/share/zoneinfo/$ANDROID_TZ" /etc/localtime
        echo "$ANDROID_TZ" > /etc/timezone
        echo "Timezone set to: $ANDROID_TZ"
    else
        echo "Failed to detect timezone"
    fi

    mkdir -p "$PREFIX/.configured"

    if [ ! -f "$HOME/.bashrc" ]; then
       touch "$HOME/.bashrc" && chmod 644 "$HOME/.bashrc"
    fi

    echo "Installation completed."
    exit 0
fi



    echo "$$" > "$PREFIX/pid"

    # Create initrc if it doesn't exist or needs update
    cat <<'EOF' > /initrc
# Source rc files if they exist (POSIX compliant)
if [ -f "/etc/profile" ]; then
    . "/etc/profile"
fi

# Environment setup
export PATH=$PATH:/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin

export HOME=/public
export TERM=xterm-256color
export PIP_BREAK_SYSTEM_PACKAGES=1
export PS1='\[\033[1;32m\]\u\[\033[0m\]@localhost \[\033[1;34m\]\w\[\033[0m\] \$ '

# Enable bracketed paste in readline if supported
bind 'set enable-bracketed-paste on' 2>/dev/null || true

alias clear='reset'

if [ -f "$HOME/.bashrc" ]; then
    . "$HOME/.bashrc"
fi
EOF
    chmod +x /initrc

    # Also mirror to $PREFIX/alpine/initrc if directory exists
    if [ -d "$PREFIX/alpine" ]; then
        cp -f /initrc "$PREFIX/alpine/initrc" 2>/dev/null || true
    fi

    # Ensure /etc/profile.d has PS1 override and updated repository URL
    if [ -d /etc/profile.d ]; then
        cat <<'EOF' > /etc/profile.d/cresteditor.sh
export PS1='\[\033[1;32m\]\u\[\033[0m\]@localhost \[\033[1;34m\]\w\[\033[0m\] \$ '
bind 'set enable-bracketed-paste on' 2>/dev/null || true
if [ -f /etc/apk/repositories ] && grep -q "heysaish\.github\.io" /etc/apk/repositories 2>/dev/null; then
    sed -i 's|heysaish\.github\.io/Crest-Packages|cresteditor\.github\.io/Crest-Packages|g' /etc/apk/repositories 2>/dev/null || true
fi
EOF
    fi

# First-time terminal open script execution
if [ ! -f "$PREFIX/.first_boot_script_run" ]; then
    if [ -f "$PREFIX/setup-alpine.sh" ]; then
        /bin/sh "$PREFIX/setup-alpine.sh" 2>/dev/null || true
    elif [ -f /setup-alpine.sh ]; then
        /bin/sh /setup-alpine.sh 2>/dev/null || true
    fi
    touch "$PREFIX/.first_boot_script_run" 2>/dev/null || true
fi

# Shell Login System: Dynamic Resolution & Execution
# Priority 1: PREFERRED_SHELL (Android Settings / App Environment)
# Priority 2: ~/.config/crest/shell (User container configuration)
# Priority 3: Fallback (bash -> sh)

resolve_shell_path() {
    candidate="$1"
    [ -z "$candidate" ] && return 1

    # Check if direct absolute or relative path was provided and is executable
    if [ -x "$candidate" ]; then
        echo "$candidate"
        return 0
    fi

    # Lookup via command -v
    resolved="$(command -v "$candidate" 2>/dev/null)"
    if [ -n "$resolved" ] && [ -x "$resolved" ]; then
        echo "$resolved"
        return 0
    fi

    return 1
}

# Locate settings.json (Single Source of Truth)
SETTINGS_FILE=""
if [ -n "$PREFIX" ] && [ -f "$PREFIX/settings.json" ]; then
    SETTINGS_FILE="$PREFIX/settings.json"
elif [ -f "/data/data/com.cresteditor/files/settings.json" ]; then
    SETTINGS_FILE="/data/data/com.cresteditor/files/settings.json"
fi

CANDIDATE_SHELL=""

if [ -n "$PREFERRED_SHELL" ]; then
    CANDIDATE_SHELL="$PREFERRED_SHELL"
elif [ -n "$SETTINGS_FILE" ] && [ -f "$SETTINGS_FILE" ]; then
    if command -v jq >/dev/null 2>&1; then
        CANDIDATE_SHELL="$(jq -r '.["terminal.integrated.shell"] // empty' "$SETTINGS_FILE" 2>/dev/null || true)"
    else
        CANDIDATE_SHELL="$(grep '"terminal.integrated.shell"' "$SETTINGS_FILE" 2>/dev/null | head -n 1 | sed -E 's/.*:[[:space:]]*"([^"]*)".*/\1/' || true)"
    fi
fi

# Clean up legacy config file if present
rm -f "$HOME/.config/crest/shell" 2>/dev/null || true

TARGET_SHELL=""

if [ -n "$CANDIDATE_SHELL" ]; then
    TARGET_SHELL="$(resolve_shell_path "$CANDIDATE_SHELL")"
    if [ -z "$TARGET_SHELL" ]; then
        echo "[CrestEditor] Warning: Preferred shell '$CANDIDATE_SHELL' not found or not executable." >&2
        echo "[CrestEditor] Falling back to default shell..." >&2
    fi
fi

# Fallback sequence: try bash first, then sh
if [ -z "$TARGET_SHELL" ]; then
    TARGET_SHELL="$(resolve_shell_path "bash")"
fi

if [ -z "$TARGET_SHELL" ]; then
    TARGET_SHELL="$(resolve_shell_path "sh")"
fi

# Fatal check: prevent infinite loop if even /bin/sh is unavailable
if [ -z "$TARGET_SHELL" ]; then
    echo "[CrestEditor] Fatal Error: No usable shell (/bin/sh) found in system." >&2
    exit 1
fi

export SHELL="$TARGET_SHELL"
SHELL_NAME="$(basename "$TARGET_SHELL")"

case "$SHELL_NAME" in
    bash)
        if [ -f /initrc ]; then
            exec "$TARGET_SHELL" --rcfile /initrc -i
        else
            exec "$TARGET_SHELL" -i
        fi
        ;;
    zsh)
        exec "$TARGET_SHELL" -i
        ;;
    fish)
        exec "$TARGET_SHELL" -i
        ;;
    sh|ash|dash)
        export ENV=/initrc
        exec "$TARGET_SHELL" -i
        ;;
    *)
        # Unknown / custom shell: default to interactive mode
        exec "$TARGET_SHELL" -i
        ;;
esac
