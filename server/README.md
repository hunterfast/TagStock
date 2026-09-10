# TagStock-Server

Eigenständige Anwendung, auf die die TagStock-App zugreift. Sie hält den Bestand
für mehrere Geräte, verwaltet Teams mit Rollen und nimmt Leih-Anfragen entgegen.
Läuft als einzelner Docker-Container – gedacht für einen Unraid-Server, geht aber
auf jedem Docker-Host.

Ein Container, eine SQLite-Datei, keine zusätzliche Datenbank.

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

Das **erste Konto** darf sich immer registrieren – danach greift der Code, falls
gesetzt. Ohne Code kann jeder, der den Server erreicht, ein Konto anlegen; setze
ihn also, sobald der Server aus dem Heimnetz heraus erreichbar ist.

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
