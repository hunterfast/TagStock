#!/bin/sh
# TagStock-Server aktualisieren: sichern, neu bauen, neu starten.
#
#   sh /mnt/user/appdata/tagstock-quelle/server/update.sh
#
# Bricht bei jedem Fehler ab, ohne den laufenden Container anzuruehren - erst
# wenn das neue Abbild fertig gebaut ist, wird gewechselt.
set -e

QUELLE=${TAGSTOCK_QUELLE:-/mnt/user/appdata/tagstock-quelle}
DATEN=${TAGSTOCK_DATEN:-/mnt/user/appdata/tagstock}
NAME=${TAGSTOCK_CONTAINER:-tagstock-server}
ABBILD=${TAGSTOCK_ABBILD:-tagstock-server}
PORT=${TAGSTOCK_PORT:-8080}

echo "== Neuen Stand holen"
cd "$QUELLE"
git pull --ff-only

echo "== Daten sichern"
mkdir -p "$DATEN/sicherungen"
SICHERUNG="$DATEN/sicherungen/tagstock-$(date +%Y-%m-%d-%H%M).tar.gz"
tar czf "$SICHERUNG" -C "$(dirname "$DATEN")" "$(basename "$DATEN")" \
    --exclude="$(basename "$DATEN")/sicherungen"
echo "   $SICHERUNG"

echo "== Abbild bauen"
docker build -t "$ABBILD" "$QUELLE/server"

echo "== Container neu starten"
docker restart "$NAME" >/dev/null

echo "== Warten, bis der Server antwortet"
i=0
while [ $i -lt 30 ]; do
    if wget -q -O- "http://127.0.0.1:$PORT/api/v1/status" >/dev/null 2>&1; then
        echo "   läuft"
        wget -q -O- "http://127.0.0.1:$PORT/api/v1/status"
        echo
        exit 0
    fi
    i=$((i + 1))
    sleep 2
done

echo "   Der Server antwortet nicht. Log ansehen:" >&2
echo "   docker logs $NAME --tail 80" >&2
exit 1
