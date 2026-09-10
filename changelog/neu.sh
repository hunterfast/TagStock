#!/bin/sh
# Legt eine Changelog-Datei an: sh changelog/neu.sh app 2.1
set -e

TEIL=$1
VERSION=$2
if [ -z "$TEIL" ] || [ -z "$VERSION" ]; then
    echo "Aufruf: sh changelog/neu.sh <app|server> <version>" >&2
    exit 1
fi
if [ "$TEIL" != "app" ] && [ "$TEIL" != "server" ]; then
    echo "Erster Wert muss app oder server sein." >&2
    exit 1
fi

ORDNER="$(dirname "$0")/$TEIL/$(date +%Y)"
DATEI="$ORDNER/$VERSION.md"
mkdir -p "$ORDNER"

if [ -f "$DATEI" ]; then
    echo "Gibt es schon: $DATEI"
    exit 0
fi

if [ "$TEIL" = "app" ]; then
    TITEL="App $VERSION"
else
    TITEL="Server $VERSION"
fi

cat > "$DATEI" <<EOF
# $TITEL

_Stand: $(date +%Y-%m-%d)_

## Neu
-

## Geändert
-

## Behoben
-
EOF

echo "Angelegt: $DATEI"
