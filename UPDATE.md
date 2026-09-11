# Aktualisieren

TagStock besteht aus zwei Teilen, die sich **getrennt** aktualisieren lassen:
die App auf den Handys und der Server im Docker-Container. Beide vertragen
einen Versprung – die Schnittstelle ist versioniert (`apiVersion` in
`/api/v1/status`), und solange die große Nummer gleich bleibt, arbeiten alte
App und neuer Server zusammen und umgekehrt.

| Ich will … | Abschnitt |
|---|---|
| den Server auf den neuesten Stand bringen | [Server aktualisieren](#server-aktualisieren) |
| die App auf den Handys aktualisieren | [App aktualisieren](#app-aktualisieren) |
| eine App-Fassung rückgängig machen | [Zurück auf die vorherige Fassung](#zurück-auf-die-vorherige-fassung) |
| Daten zurückholen | [Sicherungen](#sicherungen) |
| selbst eine neue Version herausgeben | [Neue Version veröffentlichen](#neue-version-veröffentlichen) |
| dem Server Zugang zum Projekt geben | [Zugangsschlüssel anlegen](#zugangsschlüssel-anlegen) |

---

## Server aktualisieren

### Der kurze Weg

Im Unraid-Terminal (Weboberfläche, `>_`-Symbol oben rechts) oder per SSH:

```bash
sh /mnt/user/appdata/tagstock-quelle/server/update.sh
```

Das Skript macht der Reihe nach:

1. **Neuen Stand holen** – `git pull --ff-only` im Quellordner.
2. **Daten sichern** – Datenbank und Bilder landen als
   `tagstock-<datum>.tar.gz` unter `/mnt/user/appdata/tagstock/sicherungen/`.
3. **Abbild bauen** – dauert je nach Anbindung ein bis fünf Minuten.
4. **Container erneuern** – erst jetzt; bricht der Bau vorher ab, läuft der
   alte Stand unberührt weiter. Wichtig: Ein bloßes `docker restart` genügt
   **nicht**, denn der vorhandene Container hängt weiter am alten Abbild. Das
   Skript legt ihn deshalb neu an und übernimmt dabei Ports, Ordner,
   Variablen und Neustartverhalten des bisherigen. Die Daten liegen im Volume
   und bleiben unberührt.
5. **Nachsehen, ob er antwortet** – und prüfen, ob wirklich die erwartete
   Version läuft. Passt sie nicht, sagt das Skript es deutlich.

Am Ende steht die Antwort des Servers auf dem Schirm:

```json
{"anwendung":"TagStock-Server","apiVersion":1,"bereit":true,"eingerichtet":true,
 "version":"2.0.0","gebautAm":"2026-09-10T16:35:12Z"}
```

Meldet `tar` „options were used after non-option arguments", stammt das Skript
noch von vor dem 11. September 2026: Damals stand `--exclude` an der falschen
Stelle und das Skript hörte vor dem Bauen auf. Einmal den neuen Stand von Hand
holen, danach läuft es wieder von allein:

```bash
cd /mnt/user/appdata/tagstock-quelle && git pull
sh server/update.sh
```

Kommt stattdessen „Der Server antwortet nicht", hilft:

```bash
docker logs tagstock-server --tail 80
```

### Von Hand

```bash
cd /mnt/user/appdata/tagstock-quelle
git pull
docker build -t tagstock-server server
docker restart tagstock-server
```

### Wenn du die erste Fassung von Hand installiert hast

Also ohne `git clone` – etwa weil du das Archiv im Browser geladen und
entpackt hast. Dann findet `update.sh` keinen Git-Ordner. Erst nachsehen, was
vorliegt:

```bash
ls -d /mnt/user/appdata/tagstock-quelle/.git 2>/dev/null && echo "Klon" || echo "nur Dateien"
```

**Fall „Klon":** Nichts weiter zu tun, `sh .../server/update.sh` läuft.

**Fall „nur Dateien"** – zwei Wege, beide brauchen einen Zugangsschlüssel, weil
das Projekt nicht öffentlich ist (dasselbe Token wie für die App-Verteilung,
siehe [App aktualisieren](#app-aktualisieren)):

*a) Einmal einen Klon anlegen – danach läuft alles wie beschrieben:*

```bash
cd /mnt/user/appdata
mv tagstock-quelle tagstock-quelle-alt
git clone -b claude/android-lager-app-barcode-nfc-qe3kev \
    https://<token>@github.com/hunterfast/TagStock.git tagstock-quelle
sh tagstock-quelle/server/update.sh
```

Läuft das durch, kann `tagstock-quelle-alt` weg. **Deine Daten sind davon nicht
betroffen** – die liegen in `/mnt/user/appdata/tagstock`, nicht im Quellordner.

*b) Ohne Klon bleiben:* Das Skript holt den Stand dann als Archiv, wenn du ihm
das Token mitgibst:

```bash
TAGSTOCK_GITHUB_TOKEN=github_pat_... sh /mnt/user/appdata/tagstock-quelle/server/update.sh
```

Es ersetzt den Inhalt des Quellordners durch den frischen Stand und macht
danach normal weiter. Eigene Änderungen im Quellordner gehen dabei verloren –
im Datenordner passiert nichts.

*c) Ganz ohne Token:* Archiv im Browser laden
(`github.com/hunterfast/TagStock` → Zweig wählen → *Code → Download ZIP*), auf
den Server kopieren, den alten Quellordner ersetzen und dann:

```bash
docker build -t tagstock-server /mnt/user/appdata/tagstock-quelle/server
docker restart tagstock-server
```

### Heißt dein Container anders?

Das Skript nimmt die Namen aus der Anleitung an. Weichen sie ab, gib sie mit:

```bash
TAGSTOCK_CONTAINER=mein-tagstock \
TAGSTOCK_ABBILD=mein-abbild \
TAGSTOCK_QUELLE=/mnt/user/appdata/quelle \
TAGSTOCK_DATEN=/mnt/user/appdata/daten \
TAGSTOCK_PORT=8081 \
sh /mnt/user/appdata/quelle/server/update.sh
```

Nachsehen, wie sie tatsächlich heißen:

```bash
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}' | grep -i tagstock
```

Mit dem Compose-Plugin stattdessen:

```bash
cd /mnt/user/appdata/tagstock-quelle/server
docker compose up -d --build
```

### Was mit den Daten passiert

Nichts, was du tun müsstest. Die Datenbank liegt im Volume
(`/mnt/user/appdata/tagstock/tagstock.db`), die Bilder daneben in `bilder/`.

- **Neue Tabellen** legt `schema.sql` beim Start an.
- **Neue Spalten in bestehenden Tabellen** ergänzt der Server selbst – das ist
  der Punkt, an dem SQLite sonst stolpert, denn `CREATE TABLE IF NOT EXISTS`
  fasst eine vorhandene Tabelle nicht an. Zuständig ist `Schemapflege`; sie
  fügt nur hinzu und baut nie um.
- **Entfernt wird nichts.** Auch eine ältere Serverfassung kann mit der
  Datenbank noch etwas anfangen; sie ignoriert die Spalten, die sie nicht kennt.

### Woher weiß ich, dass es etwas Neues gibt?

Der Server sieht einmal am Tag nach und vergleicht seinen Bauzeitpunkt mit der
letzten Änderung im Projektzweig. Das Ergebnis steht

- in der App unter *Einstellungen → Version*,
- oder direkt: `curl http://<server-ip>:8080/api/v1/aktualisierung` (mit
  Anmeldung) beziehungsweise `…/api/v1/status` für Version und Bauzeitpunkt.

Aktualisiert wird **nie von selbst**. Soll er gar nicht erst nachsehen, setze
die Variable `TAGSTOCK_UPDATE_PRUEFEN` auf `false`.

Weil das Projekt nicht öffentlich ist, braucht die Abfrage denselben Lesezugriff
wie das Holen der App – siehe unten. Ohne ihn bleibt die Anzeige leer, sonst
ändert sich nichts.

### Wenn eine Serverfassung Ärger macht

```bash
cd /mnt/user/appdata/tagstock-quelle
git log --oneline -5                 # den letzten guten Stand suchen
git checkout <kennung>               # dorthin zurück
docker build -t tagstock-server server
docker restart tagstock-server
```

Danach mit `git checkout claude/android-lager-app-barcode-nfc-qe3kev` wieder auf
den Zweig zurück, sobald das Problem behoben ist. Stimmt auch mit den Daten
etwas nicht, spiel die Sicherung ein (siehe [Sicherungen](#sicherungen)).

---

## App aktualisieren

### Über den Server (der bequeme Weg)

**Einmalig einrichten:** Der Server holt sich die App selbst, dafür braucht er
Lesezugriff auf das Projekt – siehe
[Zugangsschlüssel anlegen](#zugangsschlüssel-anlegen).

Prüfen, ob er etwas gefunden hat:

```bash
curl -s http://<server-ip>:8080/api/v1/app
```

```json
{"aktuell":{"adresse":"/api/v1/app/aktuell/tagstock.apk","versionCode":2,
            "versionName":"2.0","groesse":30252723},
 "vorher":null,"holtSelbst":true}
```

**Auf dem Handy:** *Einstellungen → Version*. Liegt auf dem Server eine höhere
Nummer als die installierte, erscheint der Knopf „Version 2.1 installieren".
Ein Tipp darauf zeigt, was sich geändert hat. Nach dem Bestätigen

1. legt die App eine Sicherung des Bestands an (der Pfad steht in der Meldung),
2. lädt die Datei vom Server,
3. und übergibt sie dem System – **installiert wird erst nach dem
   Systemdialog**, den Android selbst zeigt.

Der Server sieht alle sechs Stunden nach neuen Fassungen. Sofort nachsehen
lassen: derselbe Bildschirm, Knopf *Nach Aktualisierungen sehen*.

### Ohne Server

Die APK liegt an zwei Stellen:

- **Veröffentlichungen:** `github.com/hunterfast/TagStock/releases` – dort die
  `tagstock.apk` der obersten Marke laden.
- **Bauläufe:** *Actions → der gewünschte Lauf → Artifacts →
  `tagstock-debug-apk`* (kommt als ZIP, muss entpackt werden).

Datei auf dem Handy öffnen und installieren. Die App wird über die vorhandene
installiert; **Daten bleiben erhalten**, solange die Version größer ist.

### Erstinstallation

Beim ersten Mal fragt Android nach der Erlaubnis, Apps aus dieser Quelle zu
installieren („Unbekannte Quellen"). Die Freigabe gilt für die App, aus der du
die Datei öffnest – also Browser oder Dateimanager, nicht für TagStock selbst.

Ist der Server schon eingerichtet, geht es am schnellsten so: im Browser des
Handys `http://<server-ip>:8080/` öffnen und unten auf `tagstock.apk` tippen.

---

## Zugangsschlüssel anlegen

Das Projekt ist **privat**. Ohne Zugangsschlüssel kommt der Server nicht an die
Dateien – weder an die App noch an die Auskunft, ob es einen neueren Stand gibt.
Beides bleibt dann einfach aus; alles andere funktioniert.

Gebraucht wird ein **fein abgestuftes Token** (fine-grained), das genau eine
Sache darf: dieses eine Projekt lesen. Kein Schreiben, keine anderen Projekte.

### Schritt 1 – Token erzeugen

1. Bei GitHub anmelden, oben rechts aufs Profilbild, **Settings**. Das sind die
   Einstellungen des Kontos, nicht die des Projekts.
2. Ganz unten links **Developer settings**.
3. **Personal access tokens → Fine-grained tokens**, dann rechts oben
   **Generate new token**.
4. Ausfüllen:

   | Feld | Wert |
   |---|---|
   | Token name | `TagStock-Server Unraid` |
   | Expiration | z. B. **1 Jahr**. Danach hört das Holen auf, bis du ein neues einträgst – ein „No expiration" ist bequem, aber ein Schlüssel, der nie abläuft, bleibt auch nach einem Leck gültig. |
   | Description | frei, etwa „liest die App-Veröffentlichungen" |
   | Resource owner | dein Konto (`hunterfast`) |
   | Repository access | **Only select repositories** → in der Auswahl **TagStock** anhaken |

5. Weiter unten **Permissions → Repository permissions**. Die Liste ist lang;
   du brauchst genau eine Zeile:

   | Berechtigung | Wert |
   |---|---|
   | **Contents** | **Read-only** |

   *Metadata: Read-only* setzt GitHub dabei von selbst – das muss so.
   **Alles andere bleibt auf „No access".** Contents deckt sowohl den Quellcode
   als auch die Veröffentlichungen samt der APK ab.

6. Unten **Generate token**, im Nachfragefenster bestätigen.
7. Der Schlüssel steht **genau einmal** da (`github_pat_…`). Jetzt kopieren –
   danach zeigt GitHub ihn nie wieder. Verlierst du ihn, machst du einfach
   einen neuen und wirfst den alten weg.

### Schritt 2 – Am Server eintragen

**Weg B (Unraid-Formular):**

1. *Docker* → beim Container `tagstock-server` auf das Symbol → **Edit**.
2. Unten **Add another Path, Port, Variable, Label or Device**.
3. Ausfüllen und **Add**:

   | Feld | Wert |
   |---|---|
   | Config Type | `Variable` |
   | Name | `GitHub-Token` |
   | Key | `TAGSTOCK_GITHUB_TOKEN` |
   | Value | der kopierte Schlüssel `github_pat_…` |

4. Unten **Apply**. Unraid legt den Container neu an und startet ihn.

**Weg A (Compose):** Der Wert gehört nicht in die `docker-compose.yml`, sondern
in eine `.env` daneben – die Vorlage liegt als `.env.beispiel` bereit:

```bash
cd /mnt/user/appdata/tagstock-quelle/server
cp .env.beispiel .env
nano .env            # TAGSTOCK_GITHUB_TOKEN=github_pat_... eintragen
docker compose up -d
```

### Schritt 3 – Nachsehen, ob es wirkt

```bash
curl -s http://<server-ip>:8080/api/v1/app
```

- `"holtSelbst": true` – der Schlüssel ist angekommen.
- `"holtSelbst": false` – die Variable fehlt oder ist leer.
- `"hinweis": "Nicht erreichbar: Antwort 401 …"` – der Schlüssel stimmt nicht
  (vertippt, abgelaufen oder für das falsche Projekt).
- `"aktuell": { "versionCode": 2, … }` – die App liegt bereit. Bis dahin können
  ein paar Sekunden vergehen; er holt sie beim Start.

Direkt am Schlüssel prüfen geht auch:

```bash
curl -s -H "Authorization: Bearer github_pat_..." \
     https://api.github.com/repos/hunterfast/TagStock | grep full_name
```

Kommt `"full_name": "hunterfast/TagStock"`, ist alles richtig. Kommt
`"message": "Not Found"`, greift der Schlüssel nicht auf dieses Projekt.

### Denselben Schlüssel für `git pull`

Wenn der Quellordner ein Klon ist, braucht auch `git pull` den Zugang:

```bash
cd /mnt/user/appdata/tagstock-quelle
git remote set-url origin https://<token>@github.com/hunterfast/TagStock.git
```

Damit steht der Schlüssel im Klartext in `.git/config` – auf einem Server im
eigenen Netz ist das üblich, aber es sollte dir bewusst sein. Wieder entfernen:

```bash
git remote set-url origin https://github.com/hunterfast/TagStock.git
```

### Was du sonst wissen solltest

- **Wo der Schlüssel landet:** in der Container-Konfiguration von Unraid
  (`/boot/config/plugins/dockerMan/templates-user/`) beziehungsweise in der
  `.env`. Beides steckt in Backups des Systems – behandle solche Sicherungen
  entsprechend.
- **Wenn er abläuft:** Der Server holt nichts mehr und schreibt einen Hinweis
  in `/api/v1/app`. Neues Token erzeugen, den Wert austauschen, Container neu
  starten. Sonst passiert nichts – Bestand, Konten und Bilder bleiben.
- **Wenn er abhandenkommt:** *Settings → Developer settings → Fine-grained
  tokens →* beim Eintrag **Revoke**. Er gilt sofort nicht mehr. Mit reinem
  Lesezugriff auf ein Projekt ohne Betriebsdaten ist der Schaden überschaubar –
  in der Datenbank des Servers steckt nichts davon.
- **Ohne Schlüssel geht es auch:** Dann legst du die APK von Hand ab (siehe
  unten) und aktualisierst den Server über das heruntergeladene Archiv. Nur
  bequemer ist es mit.
- **Die Alternative wäre, das Projekt öffentlich zu machen** – dann bräuchte es
  gar keinen Schlüssel. Damit läge allerdings der gesamte Quellcode offen; die
  Daten deines Lagers wären davon nicht betroffen, die liegen ausschließlich auf
  deinem Server.

---

## Zurück auf die vorherige Fassung

Der Server behält immer **zwei** Fassungen: die aktuelle und die davor.

Android installiert allerdings grundsätzlich **keine ältere Fassung über eine
neuere** – das lässt sich nicht umgehen, der Weg zurück führt über das
Deinstallieren. Deshalb macht die App es so:

1. *Einstellungen → Version → „Zurück auf Version 2.0"*.
2. **Bestand sichern**: Der Bestand liegt ohnehin auf dem Server; zusätzlich
   unter *Einstellungen → Daten → JSON-Export* eine Datei ablegen, die das
   Deinstallieren übersteht (etwa im Download-Ordner oder auf einer Freigabe).
3. Die App lädt die alte Fassung in den **Download-Ordner** – dort bleibt sie
   liegen, auch wenn TagStock verschwindet.
4. **TagStock deinstallieren.**
5. Die geladene Datei im Dateimanager öffnen und installieren.
6. Am Server anmelden – **der Bestand kommt beim ersten Abgleich zurück.**

Ohne Server: in Schritt 6 stattdessen die JSON-Sicherung aus Schritt 2 über
*Einstellungen → Daten → Import* einlesen.

---

## Sicherungen

| Was | Wo | Wann entsteht sie |
|---|---|---|
| Alles (Datenbank + Bilder) | `/mnt/user/appdata/tagstock/sicherungen/tagstock-<datum>.tar.gz` | bei jedem Lauf von `update.sh` |
| Nur die Datenbank | `/mnt/user/appdata/tagstock/sicherungen/vor-app-<version>-<datum>.db` | bevor der Server eine neue App-Fassung übernimmt (die letzten zehn bleiben) |
| Bestand als JSON | auf dem Handy, Pfad steht in der Meldung | bevor die App sich selbst aktualisiert |
| Bestand als JSON oder CSV | wohin du willst | *Einstellungen → Daten → Export* |

### Zurückspielen

**Ganzer Server:**

```bash
docker stop tagstock-server
cd /mnt/user/appdata
mv tagstock tagstock-kaputt
tar xzf tagstock-kaputt/sicherungen/tagstock-2026-09-10-1635.tar.gz
chown -R 99:100 tagstock
docker start tagstock-server
```

**Nur die Datenbank:**

```bash
docker stop tagstock-server
cp /mnt/user/appdata/tagstock/sicherungen/vor-app-2-2026-09-10-1635.db \
   /mnt/user/appdata/tagstock/tagstock.db
chown 99:100 /mnt/user/appdata/tagstock/tagstock.db
docker start tagstock-server
```

Die Bilder liegen getrennt in `bilder/`; sie gehören zu Artikeln über die
Adresse im Datensatz. Spielst du eine ältere Datenbank ein, bleiben neuere
Bilddateien als Karteileichen liegen – sie stören nicht.

**Auf dem Handy:** *Einstellungen → Daten → Import*, wahlweise ergänzend oder
ersetzend. Hängt das Gerät an einem Server, gewinnt beim nächsten Abgleich der
jüngere Zeitstempel – ein Import einer alten Datei wird also gegebenenfalls
wieder überschrieben. Wer wirklich zurück will, spielt die Sicherung **auf dem
Server** ein.

---

## Neue Version veröffentlichen

Für den Fall, dass du selbst etwas änderst oder ändern lässt.

### App

1. **Versionsnummer hochzählen** in `app/build.gradle`:

   ```groovy
   versionCode 3          // muss steigen, sonst sehen die Geräte kein Update
   versionName "2.1"
   ```

2. **Changelog anlegen und füllen:**

   ```bash
   sh changelog/neu.sh app 2.1
   ```

   Der Text landet unverändert in der Veröffentlichung **und** als Hinweis im
   Aktualisierungsdialog auf dem Handy – er richtet sich also an die Leute, die
   die App benutzen.

3. **Pushen.** Die CI baut, testet und legt die Veröffentlichung
   `app-v2.1+3` mit `tagstock.apk` und `app.json` an. Läuft der Bau erneut
   für dieselbe Nummer, wird die Datei dort ersetzt.

4. **Nichts weiter.** Der Server holt sich die neue Fassung binnen sechs
   Stunden, schiebt die bisherige nach `vorher` und legt vorher eine Sicherung
   an. Ungeduldig? *Einstellungen → Nach Aktualisierungen sehen*.

### Server

1. Version in `server/build.gradle` anheben (`version = '2.1.0'`).
2. `sh changelog/neu.sh server 2.1.0`, Text eintragen.
3. Pushen – und auf dem Host `sh server/update.sh` laufen lassen.

Kommt eine **neue Spalte** in einer bestehenden Tabelle dazu, gehört sie in die
Liste in `Schemapflege`; sonst fehlt sie allen, die schon eine Datenbank haben.

---

## Wenn etwas klemmt

| Symptom | Ursache und Lösung |
|---|---|
| Handy zeigt kein Update, obwohl es eins gibt | Hat der Server es schon? `curl http://<server-ip>:8080/api/v1/app`. Steht dort `holtSelbst: false`, fehlt `TAGSTOCK_GITHUB_TOKEN`. Wurde die `versionCode` überhaupt hochgezählt? |
| „App nicht installiert" beim Installieren | Die Nummer ist gleich oder kleiner als die installierte, oder die Datei stammt aus einer anderen Signatur (Debug ≠ Release). Für den Rückweg: erst deinstallieren. |
| Nach dem Update läuft weiter die alte Version | Der Container wurde nur neu gestartet statt neu angelegt. Auf Unraid: Docker-Reiter → Container anklicken → *Edit* → unten **Apply**. Oder `sh server/update.sh` erneut laufen lassen, das erledigt es jetzt selbst. |
| Nach dem Serverupdate meldet die App „Server nicht erreichbar" | `docker logs tagstock-server --tail 80`. Meist Rechte am Volume: `chown -R 99:100 /mnt/user/appdata/tagstock` |
| `update.sh` bricht bei `git pull` ab | Auf dem Host wurde etwas verändert. `git status` zeigt was; `git checkout -- <datei>` verwirft es. |
| Der Bau bricht mit Netzwerkfehler ab | Nochmal starten; der alte Container läuft derweil weiter. |
| Nach dem Zurückgehen fehlt der Bestand | Am Server anmelden und einmal abgleichen – oder die JSON-Sicherung importieren. |
| Sicherungen fressen Platz | Die `.db`-Sicherungen begrenzt der Server auf zehn; die `.tar.gz` aus `update.sh` räumst du selbst auf. |
