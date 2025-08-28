#!/bin/sh

VERSION=${VERSION:-"1.0.3"}
ARCH=${ARCH:-$(uname -m)}
OS=${OS:-$(uname -s | tr '[:upper:]' '[:lower:]')}

if [ "$OS" = "darwin" ]; then
    OS="osx"
fi
if [ "$ARCH" = "arm64" ]; then
    ARCH="aarch_64"
fi

curl --fail-with-body -L -o /tmp/skemium \
    "https://github.com/snyk/skemium/releases/download/v${VERSION}/skemium-${VERSION}-${OS}-${ARCH}"
chmod +x /tmp/skemium
