set -e
set -x
if [[ ! -f /usr/bin/boot ]]; then
    wget https://github.com/boot-clj/boot-bin/releases/download/latest/boot.sh -O /usr/bin/boot;
    chmod +x /usr/bin/boot;
fi
