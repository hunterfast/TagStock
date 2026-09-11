# TagStock-Server

Eigenständige Anwendung, auf die die TagStock-App zugreift. Sie hält den Bestand
für mehrere Geräte, verwaltet Teams mit Rollen und nimmt Leih-Anfragen entgegen.
Läuft als einzelner Docker-Container – gedacht für einen Unraid-Server, geht aber
auf jedem Docker-Host.

Ein Container, eine SQLite-Datei, keine zusätzliche Datenbank. Dazu eine
**Oberfläche im Browser** unter `http://<server-ip>:8080/`, mit der sich der
Bestand auch ohne Handy pflegen lässt.

> **Schritt für Schritt:** Eine ausführliche Anleitung mit allen Klicks, Feldern
> und Prüfungen steht in [INSTALL-UNRAID.md](INSTALL-UNRAID.md).

## Auf Unraid einrichten

**Mit Docker Compose** (Plugin „Docker Compose Manager"):

```bash
cd /mnt/user/appdata/tagstock-quelle
git clone https://github.com/hunterfast/TagStock.git .
cd server
docker compose up -d
```

**Ohne Compose**, direkt über die Unraid-Oberfläche → *Docker* → *Add Container*:

| Feld | Wert |
|---|---|
| Name | `tagstock-server` |
| Repository | selbst gebautes Abbild, siehe unten |
| Port | Container `8080` → Host `8080` |
| Pfad | Container `/data` → Host `/mnt/user/appdata/tagstock` |
| Variable | `TAGSTOCK_REGISTRIERUNGSCODE` (optional) |

Abbild selbst bauen:

```bash
docker build -t tagstock-server /mnt/user/appdata/tagstock-quelle/server
```

Danach ist der Server unter `http://<server-ip>:8080` erreichbar. Genau diese
Adresse trägst du in der App unter *Einstellungen → Server* ein.

Prüfen, ob er läuft:

```bash
curl http://<server-ip>:8080/api/v1/status
# {"anwendung":"TagStock-Server","apiVersion":1,"bereit":true,"eingerichtet":false}
```

## Oberfläche im Browser

`http://<server-ip>:8080/` – Anmeldung mit denselben Zugangsdaten wie in der
App, danach bleibt der Browser angemeldet.

- **Bestand:** suchen, nach Status, Kategorie und Standort filtern, Artikel
  anlegen, bearbeiten, löschen, Bilder ansehen und hochladen, Verlauf lesen.
- **Kategorien, Mitglieder, Anfragen:** dasselbe wie in der App, nach Rolle.
- **System:** Serverversion und Bauzeitpunkt, die bereitliegenden App-Fassungen
  mit Downloadlink und ein Knopf, sofort nach einer neueren zu sehen.

Gescannt wird nur mit der App – NFC und Kamera hat der Browser nicht. Kennungen
lassen sich in der Oberfläche aber von Hand eintragen.

**Das erste angelegte Konto betreut den Server:** Es sieht jedes Lager, auch
die, in denen es kein Mitglied ist, und darf dort arbeiten. Alle anderen sehen
weiterhin nur ihre eigenen.

Die Oberfläche gehört nicht ins offene Internet; davor gehört ein Reverse Proxy
mit TLS oder ein VPN.

## Einstellungen

| Variable | Bedeutung | Standard |
|---|---|---|
| `TAGSTOCK_DB` | Pfad der Datenbankdatei | `/data/tagstock.db` |
| `TAGSTOCK_BILDER` | Ordner für die Artikelbilder | `/data/bilder` |
| `TAGSTOCK_BILD_MAX_MB` | Größtes erlaubtes Bild in MB | `8` |
| `TAGSTOCK_PORT` | Port im Container | `8080` |
| `TAGSTOCK_REGISTRIERUNGSCODE` | Wenn gesetzt, braucht jede Registrierung nach der ersten diesen Code | leer |
| `TAGSTOCK_TOKEN_TAGE` | Gültigkeit einer Anmeldung in Tagen | `180` |
| `TAGSTOCK_GTIN_DIENST` | Nachschlagedienst für Produktdaten: `aus`, `opengtindb` oder `eansearch` | `aus` |
| `TAGSTOCK_GTIN_SCHLUESSEL` | Zugangsschlüssel des Dienstes, falls nötig | leer |
| `TAGSTOCK_GTIN_FEHLSCHLAG_TAGE` | So lange gilt ein Fehlschlag, bevor erneut gefragt wird | `7` |
| `TAGSTOCK_UPDATE_PRUEFEN` | Täglich nachsehen, ob es einen neueren Stand gibt | `true` |
| `TAGSTOCK_UPDATE_QUELLE` | Womit verglichen wird; leer heißt: der Projektzweig auf GitHub | leer |
| `TAGSTOCK_APP_ORDNER` | Hier liegen die App-Fassungen (`aktuell/`, `vorher/`) | `/data/app` |
| `TAGSTOCK_APP_HOLEN` | Neueste App selbst herunterladen | `true` |
| `TAGSTOCK_APP_QUELLE` | Woher; leer heißt: die Veröffentlichungen dieses Projekts | leer |
| `TAGSTOCK_GITHUB_TOKEN` | Lesezugriff aufs Projekt – ohne ihn holt der Server nichts | leer |
| `TAGSTOCK_SICHERUNGEN` | Sicherungen vor jedem Wechsel der Fassung | `/data/sicherungen` |
| `TAGSTOCK_SERVER_ORDNER` | Hier liegt eine selbst nachgeladene Serverfassung | `/data/server` |
| `TAGSTOCK_SELBST_AKTUALISIEREN` | Darf der Server sich selbst aktualisieren? | `true` |
| `TAGSTOCK_SERVER_QUELLE` | Woher die Serverfassungen kommen; leer heißt: dieses Projekt | leer |

Das **erste Konto** darf sich immer registrieren – danach greift der Code, falls
gesetzt. Ohne Code kann jeder, der den Server erreicht, ein Konto anlegen; setze
ihn also, sobald der Server aus dem Heimnetz heraus erreichbar ist.

## Aktualisieren

**Am einfachsten in der Oberfläche:** *System → Nach neuer Fassung sehen →
Aktualisieren und neu starten*. Der Server sichert die Datenbank, lädt die neue
Fassung ins Volume und startet neu; der Start nimmt eine nachgeladene Fassung
der aus dem Abbild vor. Startet sie dreimal nicht, wird sie beiseitegelegt.
Voraussetzung: Neustart-Regel `unless-stopped` und ein Zugangsschlüssel.

Ausführlich – mit Rückweg, Sicherungen und dem Weg für die App – steht das in
[../UPDATE.md](../UPDATE.md). Der Weg über den Host:

```bash
sh /mnt/user/appdata/tagstock-quelle/server/update.sh
```

Das Skript holt den neuen Stand, sichert Datenbank und Bilder, baut das Abbild
und startet den Container neu; erst wenn der Bau durchläuft, wird gewechselt.

Der Server **erkennt selbst, wenn es etwas Neues gibt**: Einmal am Tag
vergleicht er seinen Bauzeitpunkt mit der letzten Änderung im Projektzweig und
meldet das über `/api/v1/aktualisierung`; in der App steht es unter
*Einstellungen → Version*. Aktualisiert wird nie von allein.

Eine bestehende Datenbank läuft ohne Zutun weiter: neue Tabellen legt
`schema.sql` an, **fehlende Spalten in bestehenden Tabellen ergänzt der Server
beim Start** (siehe `Schemapflege`). Spalten werden dabei nur hinzugefügt.

### Die App verteilen

Der Server hält die App für die Geräte bereit und **holt sie sich selbst**:

```
/data/app/aktuell/tagstock.apk + app.json     ← die neueste Fassung
/data/app/vorher/tagstock.apk  + app.json     ← die davor, für den Rückweg
```

Jede Minute fragt er nach, ob sich an den Veröffentlichungen etwas getan hat –
mit der Kennung der letzten Antwort, sodass ein unveränderter Stand nichts
kostet und nicht gegen das Anfragekonto zählt. Sobald sich etwas geändert hat,
sieht er sofort richtig nach; abschalten lässt sich das mit
`TAGSTOCK_QUELLE_BEOBACHTEN=false`, den Abstand regelt
`TAGSTOCK_QUELLE_TAKT_SEKUNDEN`.

Zusätzlich alle sechs Stunden (und beim Start) sieht er bei den Veröffentlichungen des
Projekts nach. Ist die dortige `versionCode` höher als die eigene, lädt er die
Datei – sonst nicht. Vor jedem Wechsel legt er eine Sicherung der Datenbank an
(`/data/sicherungen/vor-app-<version>-<zeit>.db`, die letzten zehn bleiben
liegen), damit zu jeder Fassung auch passende Daten bereitstehen.

Weil das Projekt nicht öffentlich ist, braucht er dafür einen **Lesezugriff**:
ein fein abgestuftes Token mit der Berechtigung *Contents: Read-only* auf dieses
Repository, eingetragen als `TAGSTOCK_GITHUB_TOKEN`. Ohne Token holt er nichts
und sagt das in der Auskunft; die Dateien lassen sich dann von Hand ablegen.

Die App fragt unter *Einstellungen → Version* nach, vergleicht die Nummer mit
ihrer eigenen, zeigt den Änderungshinweis und installiert nach Zustimmung. Für
den Rückweg lädt sie die vorherige Fassung in den Download-Ordner – Android
lässt eine ältere Fassung nur nach dem Deinstallieren zu, deshalb liegt die
Datei dort, wo sie das überlebt.

## Sicherung

Alles liegt unter `/mnt/user/appdata/tagstock`: die Datenbank `tagstock.db` und
der Ordner `bilder`. Für ein Backup den Container kurz stoppen und den Ordner
kopieren.

## Rollen

| Rolle | Darf |
|---|---|
| `admin` | alles, dazu Mitglieder und Rollen verwalten |
| `lagerist` | Bestand pflegen, Anfragen entscheiden |
| `mitglied` | lesen und Anfragen stellen |

Neue Mitglieder kommen über einen **Einladungscode** ins Team: Ein Admin erzeugt
ihn in der App, der andere gibt ihn dort ein. Der Code gilt sieben Tage.

## Schnittstelle

Alle Antworten sind JSON, die Anmeldung läuft über
`Authorization: Bearer <token>`.

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/api/v1/status` | Erreichbarkeit und Version, ohne Anmeldung |
| `POST` | `/api/v1/auth/registrieren` | Konto anlegen |
| `POST` | `/api/v1/auth/anmelden` | Anmelden, liefert Token |
| `POST` | `/api/v1/auth/abmelden` | Token entwerten |
| `GET` | `/api/v1/ich` | eigenes Konto und Teams |
| `GET/POST` | `/api/v1/teams` | Teams auflisten und anlegen |
| `POST` | `/api/v1/teams/{id}/einladung` | Einladungscode erzeugen |
| `POST` | `/api/v1/teams/beitreten` | Code einlösen |
| `GET/PUT/DELETE` | `/api/v1/teams/{id}/mitglieder/…` | Mitglieder und Rollen |
| `GET/POST/PUT/DELETE` | `/api/v1/teams/{id}/artikel/…` | Bestand |
| `GET` | `/api/v1/teams/{id}/artikel/suche?kennung=…` | Artikel zu einem Scan |
| `GET/POST/PUT/DELETE` | `/api/v1/teams/{id}/kategorien/…` | Kategorien |
| `POST/DELETE` | `/api/v1/teams/{id}/artikel/{id}/bild` | Artikelbild ablegen oder entfernen |
| `GET` | `/api/v1/teams/{id}/bilder/{id}` | Artikelbild abholen |
| `GET` | `/api/v1/gtin/{nummer}` | Produktdaten zu einer Handelsnummer |
| `GET` | `/api/v1/verwaltung/teams` | Alle Lager des Servers – nur für die Betreuung |
| `GET` | `/api/v1/aktualisierung` | Läuft hier der neueste Stand? |
| `GET` | `/api/v1/app` | Welche App liegt bereit – aktuell und vorher? |
| `POST` | `/api/v1/app/pruefen` | Sofort bei der Quelle nachsehen |
| `POST` | `/api/v1/aktualisierung/einspielen` | Neue Serverfassung holen und neu starten |
| `POST` | `/api/v1/aktualisierung/zurueck` | Zurück auf die vorherige Serverfassung |
| `GET` | `/api/v1/app/aktuell/tagstock.apk` | Die App herunterladen, ohne Anmeldung |
| `GET` | `/api/v1/app/vorher/tagstock.apk` | Die vorherige Fassung, für den Rückweg |
| `GET/POST` | `/api/v1/teams/{id}/sync` | Abgleich mit der App |
| `GET/POST` | `/api/v1/teams/{id}/anfragen/…` | Leih-Anfragen |

### Artikelbilder

Bilder liegen auf dem Server, nicht auf dem Gerät. Die App schickt die Aufnahme
als Körper der Anfrage (`Content-Type: image/jpeg`) an
`POST /api/v1/teams/{id}/artikel/{artikelId}/bild` und bekommt die Adresse
zurück, die dann am Artikel hängt:

```json
{"id":"…","bildUrl":"/api/v1/teams/…/bilder/…","typ":"image/jpeg","groesse":184320}
```

Ein neues Bild ersetzt das vorherige, und mit dem Artikel verschwindet auch
seine Datei. Auf den Geräten bleibt nur ein Zwischenspeicher, den Android
jederzeit leeren darf.

### Produktdaten zu einer GTIN

Scannt jemand einen Hersteller-Barcode, kann die App den Produktnamen
vorschlagen. Gefragt wird **der Server**, nicht der Anbieter direkt: so bleibt
ein etwaiger Zugangsschlüssel hier, und jede Nummer geht höchstens einmal nach
draußen – danach steht sie in der Tabelle `gtin_cache` und gilt für alle Geräte.
Auch ein Fehlschlag wird gemerkt (Standard sieben Tage), damit dieselbe Nummer
nicht bei jedem Scan wieder abgefragt wird.

Ohne `TAGSTOCK_GTIN_DIENST` fragt der Server **nichts** nach außen und antwortet
mit `503`; die App bietet das Nachschlagen dann gar nicht erst an (`/status`
meldet `gtinDienst: false`). Eingebaut sind zwei Anbieter:

- `opengtindb` – [opengtindb.org](https://opengtindb.org/), frei nach dem
  Wiki-Prinzip. Für den Dauerbetrieb eine eigene Kennung eintragen.
- `eansearch` – [ean-search.org](https://www.ean-search.org/), größere Abdeckung,
  braucht einen kostenpflichtigen Zugangsschlüssel.

Rechne bei Werkzeug und Technik mit mäßigen Trefferquoten – frei zugängliche
Datenbanken decken vor allem Lebensmittel und Drogerie ab. Ein Vorschlag
ersetzt nie eine Eingabe, er steht nur zur Übernahme bereit.

### Abgleich

`GET /sync?seit=<zeitstempel>` liefert alles, was sich seither geändert hat –
auch gelöschte Artikel, erkennbar am Merkmal `geloescht`. `POST /sync` nimmt die
Änderungen der App entgegen und meldet je Artikel zurück, ob er angenommen wurde:

```json
{"zuordnungen":[{"lokaleId":7,"serverId":"…","angenommen":true,"grund":null}]}
```

Bei gleichzeitigen Änderungen gewinnt der jüngere Zeitstempel; abgelehnte Fälle
kommen mit dem Serverstand zurück, den die App dann übernimmt. Kennungen sind je
Team eindeutig – ein zweiter Artikel mit derselben Kennung wird abgelehnt.

## Entwicklung

```bash
./gradlew test                                   # Tests
./gradlew bootRun --args='--spring.datasource.url=jdbc:sqlite:lokal.db'
```

Die Tests unter `src/test` fahren den Server hoch und gehen die Schnittstelle
durch: Konto, Team, Bestand, Abgleich mit Konflikten, Rollen und Anfragen.

## Was der Server bewusst nicht tut

- **Keine Verschlüsselung.** Der Container spricht HTTP. Wer ihn aus dem Internet
  erreichbar macht, stellt einen Reverse Proxy mit TLS davor (auf Unraid etwa
  Nginx Proxy Manager oder SWAG).
- **Keine E-Mails.** Benachrichtigungen über neue Anfragen gibt es nicht; die App
  zeigt offene Anfragen beim Abgleich.
- **Keine Bildbearbeitung.** Der Server nimmt JPEG, PNG und WebP entgegen, prüft
  den Inhalt und legt die Datei ab – mehr nicht. Die App verkleinert Aufnahmen
  vor dem Hochladen auf 1600 Pixel Kantenlänge.
