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
# Source rc files if they exist
if [ -f "/etc/profile" ]; then
    source "/etc/profile"
fi

# Environment setup
export PATH=$PATH:/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin

export HOME=/public
export TERM=xterm-256color
SHELL=/bin/bash
export PIP_BREAK_SYSTEM_PACKAGES=1
export PS1='\[\033[1;32m\]\u\[\033[0m\]@localhost \[\033[1;34m\]\w\[\033[0m\] \$ '

# Enable bracketed paste in readline (stops line-by-line execution on paste)
bind 'set enable-bracketed-paste on' 2>/dev/null || true

# Replicate behaviour of termux
alias clear='reset'

if [ -f "$HOME/.bashrc" ]; then
    source "$HOME/.bashrc"
fi
EOF
    chmod +x /initrc

    # Also mirror to $PREFIX/alpine/initrc if directory exists
    if [ -d "$PREFIX/alpine" ]; then
        cp -f /initrc "$PREFIX/alpine/initrc" 2>/dev/null || true
    fi

    # Ensure /etc/profile.d has PS1 override
    if [ -d /etc/profile.d ]; then
        cat <<'EOF' > /etc/profile.d/cresteditor.sh
export PS1='\[\033[1;32m\]\u\[\033[0m\]@localhost \[\033[1;34m\]\w\[\033[0m\] \$ '
bind 'set enable-bracketed-paste on' 2>/dev/null || true
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

# Shell Login System: Bash if installed, otherwise fallback to /bin/sh
if [ -x /bin/bash ]; then
    exec /bin/bash --rcfile /initrc -i
elif [ -x /usr/bin/bash ]; then
    exec /usr/bin/bash --rcfile /initrc -i
elif [ -x "$PREFIX/alpine/bin/bash" ]; then
    exec "$PREFIX/alpine/bin/bash" --rcfile /initrc -i
elif [ -x "$PREFIX/alpine/usr/bin/bash" ]; then
    exec "$PREFIX/alpine/usr/bin/bash" --rcfile /initrc -i
else
    export ENV=/initrc
    exec /bin/sh -i
fi
