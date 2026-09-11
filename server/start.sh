#!/bin/sh
# Startet den Server - und zwar die neueste Fassung, die vorliegt.
#
# Im Abbild steckt eine Fassung (/app/app.jar). Hat sich der Server selbst
# aktualisiert, liegt eine neuere im Volume; die hat Vorrang. Damit genuegt zum
# Aktualisieren ein Neustart des Containers, ohne dass das Abbild neu gebaut
# werden muss.
#
# Startet die neue Fassung dreimal hintereinander nicht, wird sie beiseite
# gelegt und wieder die aus dem Abbild genommen. Ein misslungenes Update legt
# den Server also nicht lahm.
set -e

LAGER=${TAGSTOCK_SERVER_ORDNER:-/data/server}
ABBILD_JAR=/app/app.jar
NEUE_JAR="$LAGER/tagstock-server.jar"
ZAEHLER="$LAGER/startversuche"
GRENZE=3

mkdir -p "$LAGER" 2>/dev/null || true

JAR="$ABBILD_JAR"
if [ -f "$NEUE_JAR" ]; then
    VERSUCHE=$(cat "$ZAEHLER" 2>/dev/null || echo 0)
    case "$VERSUCHE" in
        ''|*[!0-9]*) VERSUCHE=0 ;;
    esac
    if [ "$VERSUCHE" -ge "$GRENZE" ]; then
        echo "TagStock: die nachgeladene Fassung startet nicht ($VERSUCHE Versuche)." >&2
        echo "TagStock: sie wird beiseitegelegt, es laeuft wieder die aus dem Abbild." >&2
        mv "$NEUE_JAR" "$NEUE_JAR.startet-nicht" 2>/dev/null || rm -f "$NEUE_JAR"
        rm -f "$ZAEHLER"
    else
        echo $((VERSUCHE + 1)) > "$ZAEHLER" 2>/dev/null || true
        JAR="$NEUE_JAR"
    fi
fi

echo "TagStock startet aus $JAR"
# shellcheck disable=SC2086
exec java $JAVA_OPTS -jar "$JAR"
