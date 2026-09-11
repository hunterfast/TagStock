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
ZWEIG=${TAGSTOCK_ZWEIG:-claude/android-lager-app-barcode-nfc-qe3kev}
PROJEKT=${TAGSTOCK_PROJEKT:-hunterfast/TagStock}

echo "== Neuen Stand holen"
cd "$QUELLE"

if [ -d .git ]; then
    git pull --ff-only
elif [ -n "$TAGSTOCK_GITHUB_TOKEN" ]; then
    # Von Hand ausgepackt statt geklont: dann holen wir den Stand als Archiv.
    # Das Projekt ist nicht oeffentlich, deshalb mit Zugangsschluessel.
    echo "   Kein Git-Ordner - Archiv wird geladen"
    ARCHIV=$(mktemp -d)
    wget -q --header="Authorization: Bearer $TAGSTOCK_GITHUB_TOKEN" \
        -O "$ARCHIV/quelle.tar.gz" \
        "https://api.github.com/repos/$PROJEKT/tarball/$ZWEIG"
    tar xzf "$ARCHIV/quelle.tar.gz" -C "$ARCHIV"
    NEU=$(find "$ARCHIV" -maxdepth 1 -type d -name '*-*' | head -1)
    [ -d "$NEU/server" ] || { echo "   Archiv sieht nicht wie das Projekt aus" >&2; exit 1; }
    # Nur den Inhalt ersetzen; der Ordner selbst bleibt, damit Pfade stimmen.
    rm -rf "$QUELLE"/* 
    cp -a "$NEU"/. "$QUELLE"/
    rm -rf "$ARCHIV"
else
    cat >&2 <<'HINWEIS'
   Hier liegt kein Git-Ordner, und TAGSTOCK_GITHUB_TOKEN ist nicht gesetzt.
   Zwei Wege:
     a) Einmalig einen Klon anlegen (danach laeuft update.sh von allein):
          cd /mnt/user/appdata
          mv tagstock-quelle tagstock-quelle-alt
          git clone -b <zweig> https://<token>@github.com/hunterfast/TagStock.git tagstock-quelle
     b) Oder das Token setzen und dieses Skript erneut starten:
          TAGSTOCK_GITHUB_TOKEN=github_pat_... sh .../server/update.sh
HINWEIS
    exit 1
fi

echo "== Daten sichern"
mkdir -p "$DATEN/sicherungen"
SICHERUNG="$DATEN/sicherungen/tagstock-$(date +%Y-%m-%d-%H%M).tar.gz"
# --exclude gehoert vor die Pfade; dahinter wirkt es nicht, und GNU tar bricht
# dann mit Fehlerstatus ab. Die Sicherungen selbst kommen nicht mit hinein.
if ! tar czf "$SICHERUNG" --exclude="$(basename "$DATEN")/sicherungen" \
        -C "$(dirname "$DATEN")" "$(basename "$DATEN")"; then
    echo "   Sicherung fehlgeschlagen - es wurde nichts veraendert." >&2
    rm -f "$SICHERUNG"
    exit 1
fi
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
