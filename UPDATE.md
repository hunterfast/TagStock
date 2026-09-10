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
4. **Container neu starten** – erst jetzt; bricht der Bau vorher ab, läuft der
   alte Stand unberührt weiter.
5. **Nachsehen, ob er antwortet** – und die Statusmeldung ausgeben.

Am Ende steht die Antwort des Servers auf dem Schirm:

```json
{"anwendung":"TagStock-Server","apiVersion":1,"bereit":true,"eingerichtet":true,
 "version":"2.0.0","gebautAm":"2026-09-10T16:35:12Z"}
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
Lesezugriff auf das Projekt.

1. Auf GitHub: *Settings → Developer settings → Personal access tokens →
   Fine-grained tokens → Generate new token*.
2. *Repository access*: **Only select repositories → hunterfast/TagStock**.
   Unter *Permissions → Repository permissions* nur **Contents: Read-only**.
3. Token kopieren, am Container als Variable `TAGSTOCK_GITHUB_TOKEN` eintragen
   (*Docker → tagstock-server → Edit → Add another Variable*), **Apply**.

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
| Nach dem Serverupdate meldet die App „Server nicht erreichbar" | `docker logs tagstock-server --tail 80`. Meist Rechte am Volume: `chown -R 99:100 /mnt/user/appdata/tagstock` |
| `update.sh` bricht bei `git pull` ab | Auf dem Host wurde etwas verändert. `git status` zeigt was; `git checkout -- <datei>` verwirft es. |
| Der Bau bricht mit Netzwerkfehler ab | Nochmal starten; der alte Container läuft derweil weiter. |
| Nach dem Zurückgehen fehlt der Bestand | Am Server anmelden und einmal abgleichen – oder die JSON-Sicherung importieren. |
| Sicherungen fressen Platz | Die `.db`-Sicherungen begrenzt der Server auf zehn; die `.tar.gz` aus `update.sh` räumst du selbst auf. |
