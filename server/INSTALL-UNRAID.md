# TagStock-Server auf Unraid einrichten

Schritt-für-Schritt-Anleitung vom leeren Unraid-Server bis zur App, die sich
verbindet. Rechne mit 20–30 Minuten, davon 10 Minuten Bauzeit für das Abbild.

Ersetze in allen Befehlen `<server-ip>` durch die IP deines Unraid-Servers
(steht in der Weboberfläche oben rechts unter *Settings → Network Settings*,
z. B. `192.168.1.50`).

---

## 0. Voraussetzungen

| Was | Prüfen |
|---|---|
| Unraid 6.10 oder neuer | *Tools → Update OS* |
| Docker läuft | *Settings → Docker* → „Enable Docker" steht auf **Yes** |
| Array gestartet | Startseite → *Main* → Array ist **Started** |
| Share `appdata` vorhanden | *Shares* → `appdata` ist gelistet |
| Etwa 1 GB freier Platz | für Abbild, Datenbank und Bilder |

Die Anleitung nutzt zweimal das Unraid-Terminal: Weboberfläche oben rechts auf
das **`>_`**-Symbol klicken. Alternativ per SSH auf den Server.

---

## 1. Quellcode auf den Server holen

Im Unraid-Terminal:

```bash
mkdir -p /mnt/user/appdata/tagstock-quelle
cd /mnt/user/appdata/tagstock-quelle
git clone https://github.com/hunterfast/TagStock.git .
```

Meldet Unraid `git: command not found`, installiere zuerst über *Apps* (Community
Applications) das Plugin **„NerdTools"** und aktiviere darin `git`. Ohne git geht
es auch ohne Plugin:

```bash
mkdir -p /mnt/user/appdata/tagstock-quelle
cd /mnt/user/appdata/tagstock-quelle
wget -O quelle.zip https://github.com/hunterfast/TagStock/archive/refs/heads/main.zip
unzip quelle.zip && mv TagStock-main/* . && rm -rf TagStock-main quelle.zip
```

Prüfen, dass der Serverordner da ist:

```bash
ls /mnt/user/appdata/tagstock-quelle/server
# build.gradle  docker-compose.yml  Dockerfile  gradle  gradlew  README.md  src
```

---

## 2. Abbild bauen

Das Abbild baut sich selbst – auf dem Server muss kein Java installiert sein.

```bash
docker build -t tagstock-server /mnt/user/appdata/tagstock-quelle/server
```

Der erste Durchlauf lädt Java und die Bibliotheken und dauert je nach Anbindung
5–15 Minuten. Am Ende steht `Successfully tagged tagstock-server:latest`.

Kontrolle:

```bash
docker images | grep tagstock-server
```

---

## 3. Ordner für Daten anlegen

Hier landen Datenbank und Bilder. `99:100` ist auf Unraid `nobody:users` – genau
die Kennung, unter der der Container läuft.

```bash
mkdir -p /mnt/user/appdata/tagstock/bilder
chown -R 99:100 /mnt/user/appdata/tagstock
```

---

## 4. Container starten

Es gibt zwei Wege. **Weg A** ist bequemer, wenn du das Compose-Plugin hast,
**Weg B** braucht kein Plugin und zeigt den Container in der gewohnten
Docker-Ansicht von Unraid.

### Weg A: Docker Compose Manager

1. *Apps* → nach **„Docker Compose Manager"** suchen → installieren.
2. *Docker* → unten **„Add New Stack"** → Name `tagstock` → anlegen.
3. Beim neuen Stack auf das Zahnrad → **„Edit Stack" → „Edit Compose File"**.
4. Den Inhalt von `/mnt/user/appdata/tagstock-quelle/server/docker-compose.yml`
   hineinkopieren und dabei die erste Zeile des Dienstes anpassen: statt
   `build: .` schreibst du `image: tagstock-server` (das Abbild hast du in
   Schritt 2 schon gebaut). Speichern.
5. **„Compose Up"** drücken.

Oder ganz ohne Klicks, direkt im Terminal:

```bash
cd /mnt/user/appdata/tagstock-quelle/server
docker compose up -d
```

### Weg B: Über die Unraid-Oberfläche

*Docker* → **Add Container** → oben rechts **„Basic View"** auf **„Advanced
View"** stellen. Dann ausfüllen:

| Feld | Wert |
|---|---|
| Name | `tagstock-server` |
| Repository | `tagstock-server` |
| Network Type | `bridge` |
| Console shell command | `Shell` |
| Restart Policy | `Unless Stopped` |

Danach viermal **„Add another Path, Port, Variable, Label or Device"**:

**Port**
| Feld | Wert |
|---|---|
| Config Type | `Port` |
| Name | `Web` |
| Container Port | `8080` |
| Host Port | `8080` |
| Connection Type | `TCP` |

**Pfad**
| Feld | Wert |
|---|---|
| Config Type | `Path` |
| Name | `Daten` |
| Container Path | `/data` |
| Host Path | `/mnt/user/appdata/tagstock` |
| Access Mode | `Read/Write` |

**Variable (Registrierungscode)**
| Feld | Wert |
|---|---|
| Config Type | `Variable` |
| Name | `Registrierungscode` |
| Key | `TAGSTOCK_REGISTRIERUNGSCODE` |
| Value | vorerst leer lassen (siehe Schritt 7) |

**Icon (damit die Kachel dein Logo zeigt)**

Im Feld *Icon URL* einfach `http://<server-ip>:8080/icon.png` eintragen – das
Logo liefert der Server selbst aus. Alternativ die Datei `server/icon.png` aus
dem Quellordner verwenden.

**Variable (Zeitzone)**
| Feld | Wert |
|---|---|
| Config Type | `Variable` |
| Key | `TZ` |
| Value | `Europe/Berlin` |

Unten **Apply** – der Container startet.

---

## 5. Prüfen, ob er läuft

```bash
curl http://<server-ip>:8080/api/v1/status
```

Erwartete Antwort:

```json
{"anwendung":"TagStock-Server","apiVersion":1,"bereit":true,"eingerichtet":false}
```

`"eingerichtet":false` heißt: es gibt noch kein Konto. Das ist an dieser Stelle
richtig so.

Kommt keine Antwort:

```bash
docker ps | grep tagstock          # läuft der Container?
docker logs tagstock-server --tail 50
```

---

## 6. App verbinden und erstes Konto anlegen

Auf dem Handy in der TagStock-App:

1. *Einstellungen* → **Server**.
2. Adresse eintragen: `http://<server-ip>:8080` → **Prüfen**. Die App meldet
   „Server erreichbar".
3. **Registrieren**: E-Mail, Name, Passwort (mindestens 8 Zeichen). Das erste
   Konto darf sich immer anlegen und wird Admin.
4. **Team anlegen**, z. B. „Werkstatt". Die acht Standardkategorien legt der
   Server dabei gleich mit an.
5. Zurück in den Bestand, Liste nach unten ziehen – der erste Abgleich läuft.

Weitere Personen holst du ins Team:

- Du (Admin): *Einstellungen → Server → Einladung erzeugen*, Rolle wählen
  (`lagerist` darf den Bestand pflegen, `mitglied` nur lesen und anfragen).
- Die andere Person: App installieren, Serveradresse eintragen, **registrieren**,
  dann **„Team beitreten"** und den Code eingeben. Der Code gilt sieben Tage.

**Rolle später ändern:** Erzeuge einen neuen Einladungscode mit der gewünschten
Rolle und lass ihn dieselbe Person noch einmal eingeben – das Team bleibt, die
Rolle wird überschrieben. Das letzte Adminkonto lässt sich nicht herabstufen.

Wer darf was:

| Rolle | Bestand ändern | Kategorien anlegen und löschen | Bilder hochladen | Anfragen entscheiden |
|---|---|---|---|---|
| `admin` | ja | ja | ja | ja |
| `lagerist` | ja | ja | ja | ja |
| `mitglied` | nein | nein | nein | nein, darf nur fragen |

---

## 7. Registrierung schließen

Solange kein Code gesetzt ist, kann jeder, der den Server erreicht, ein Konto
anlegen. Sobald deine Leute drin sind:

- **Weg A:** in der Compose-Datei `TAGSTOCK_REGISTRIERUNGSCODE: "deinCode"`
  setzen, dann `docker compose up -d`.
- **Weg B:** *Docker* → `tagstock-server` → *Edit* → Variable `Value` füllen →
  **Apply**.

Ab dann braucht jede weitere Registrierung diesen Code. Bereits angelegte Konten
bleiben unberührt.

---

## 7b. Produktdaten nachschlagen (freiwillig)

Beim Scannen eines Hersteller-Barcodes kann der Server den Produktnamen
vorschlagen. Standardmäßig ist das **aus** – der Server fragt dann nichts nach
außen. Zum Einschalten eine Variable ergänzen (Weg A in der Compose-Datei,
Weg B über *Edit* am Container):

| Key | Value |
|---|---|
| `TAGSTOCK_GTIN_DIENST` | `opengtindb` |
| `TAGSTOCK_GTIN_SCHLUESSEL` | Kennung von opengtindb.org, sonst leer |

In der App zusätzlich *Einstellungen → Produktdaten nachschlagen* einschalten.
Jede Nummer geht höchstens einmal nach draußen, danach bedient der Server sie
aus seinem Zwischenspeicher. Bei Werkzeug und Technik sind die Trefferquoten
mäßig; gut abgedeckt sind Lebensmittel und Drogerie.

---

## 8. Sicherung

Alles liegt in einem Ordner:

```
/mnt/user/appdata/tagstock/
├── tagstock.db     Konten, Teams, Bestand, Protokoll
└── bilder/         Artikelbilder, nach Team sortiert
```

Für ein sauberes Backup den Container kurz anhalten:

```bash
docker stop tagstock-server
tar czf /mnt/user/backups/tagstock-$(date +%F).tar.gz -C /mnt/user/appdata tagstock
docker start tagstock-server
```

Zurückspielen: Container stoppen, Ordner ersetzen, Container starten.

Wer das Plugin **„Appdata Backup"** nutzt, hat den Ordner ohnehin dabei.

---

## 9. Auf eine neue Version aktualisieren

> Der vollständige Ablauf – auch für die App und den Weg zurück – steht in
> [../UPDATE.md](../UPDATE.md).

Ein Befehl macht alles: neuen Stand holen, Daten sichern, Abbild bauen,
Container neu starten und prüfen, ob er wieder antwortet.

```bash
sh /mnt/user/appdata/tagstock-quelle/server/update.sh
```

Bricht der Bau ab, bleibt der laufende Container unangetastet – gewechselt wird
erst, wenn das neue Abbild fertig ist. Die Sicherung landet unter
`/mnt/user/appdata/tagstock/sicherungen/`.

Von Hand geht es genauso:

```bash
cd /mnt/user/appdata/tagstock-quelle
git pull
docker build -t tagstock-server server
docker restart tagstock-server        # Weg B
# oder: cd server && docker compose up -d --build   (Weg A)
```

Die Daten liegen im Volume und überstehen das. Neue Tabellen legt der Server
beim Start an, **neue Spalten in bestehenden Tabellen ergänzt er ebenfalls
selbst** – eine Datenbank aus einer älteren Fassung läuft also einfach weiter.

**Woher weißt du, dass es etwas Neues gibt?** Der Server sieht einmal am Tag
nach und vergleicht seinen Bauzeitpunkt mit der letzten Änderung im
Projektzweig. In der App steht das unter *Einstellungen → Version*; direkt
abfragen kannst du es so:

```bash
curl http://<server-ip>:8080/api/v1/status        # Version und Bauzeitpunkt
```

Soll der Server gar nicht nach draußen schauen, setze
`TAGSTOCK_UPDATE_PRUEFEN` auf `false`.

---

## 9b. Die App über den Server verteilen

Damit die Handys nicht einzeln bei GitHub laden müssen, hält der Server die App
bereit – die aktuelle **und** die vorherige Fassung, damit ein Gerät zurück kann,
wenn mit der neuen etwas nicht stimmt.

### Selbst holen lassen (empfohlen)

Weil das Projekt nicht öffentlich ist, braucht der Server einen Lesezugriff:

1. Auf GitHub: *Settings → Developer settings → Personal access tokens →
   Fine-grained tokens → Generate new token*.
2. *Repository access* auf **Only select repositories → hunterfast/TagStock**,
   unter *Permissions → Repository permissions* die Berechtigung
   **Contents: Read-only** setzen. Mehr braucht er nicht.
3. Token kopieren und am Container als Variable eintragen:

   | Key | Value |
   |---|---|
   | `TAGSTOCK_GITHUB_TOKEN` | `github_pat_…` |

Danach sieht der Server beim Start und alle sechs Stunden nach. Ist dort eine
höhere `versionCode` als die, die er hat, lädt er die Datei – sonst rührt er
sich nicht. Vor jedem Wechsel legt er eine Sicherung der Datenbank unter
`/mnt/user/appdata/tagstock/sicherungen/` an; die letzten zehn bleiben liegen.

Sofort nachsehen lassen (statt auf den Takt zu warten) geht aus der App heraus
über *Einstellungen → Nach Aktualisierungen sehen*.

### Von Hand ablegen

Geht genauso, etwa ohne Token:

```
/mnt/user/appdata/tagstock/app/aktuell/tagstock.apk
/mnt/user/appdata/tagstock/app/aktuell/app.json
/mnt/user/appdata/tagstock/app/vorher/tagstock.apk     (freiwillig)
/mnt/user/appdata/tagstock/app/vorher/app.json
```

Die `app.json` sieht so aus:

```json
{"versionCode": 2, "versionName": "2.0", "hinweis": "Was sich geändert hat …"}
```

Danach `chown -R 99:100 /mnt/user/appdata/tagstock/app` nicht vergessen.

### Auf dem Handy

Unter *Einstellungen → Version* steht, welche Fassung läuft und ob eine neuere
bereitliegt. Ein Tipp darauf zeigt den Änderungshinweis; nach dem Bestätigen
legt die App eine Sicherung des Bestands an, lädt die Datei vom Server und
übergibt sie dem System – installiert wird erst nach dem Systemdialog.

**Zurück auf die vorherige Fassung:** Android installiert keine ältere Fassung
über eine neuere. Die App lädt sie deshalb in den Download-Ordner, wo sie das
Deinstallieren übersteht; danach TagStock deinstallieren, die Datei im
Dateimanager öffnen und wieder am Server anmelden – der Bestand kommt vom
Server zurück.

---

## 10. Zugriff von unterwegs

Der Container spricht **HTTP ohne Verschlüsselung**. Im eigenen WLAN ist das in
Ordnung. Sobald du von außen zugreifen willst, nimm einen der beiden Wege – aber
**öffne niemals einfach Port 8080 im Router**:

- **Tailscale oder WireGuard** (in Unraid eingebaut: *Settings → VPN Manager*).
  Das Handy ist dann im Heimnetz, die Adresse bleibt `http://<server-ip>:8080`.
  Das ist der einfachste und sicherste Weg.
- **Reverse Proxy mit TLS**, etwa *Nginx Proxy Manager* oder *SWAG* aus den Apps.
  Dort eine Domain auf `http://<server-ip>:8080` zeigen lassen und ein
  Let's-Encrypt-Zertifikat holen. In der App trägst du dann
  `https://tagstock.deine-domain.de` ein. Setze in diesem Fall unbedingt den
  Registrierungscode aus Schritt 7.

---

## 11. Wenn etwas klemmt

| Symptom | Ursache und Lösung |
|---|---|
| `docker build` bricht mit Netzwerkfehler ab | Der Bau lädt Java-Bibliotheken. Nochmal starten; bei Proxy-Umgebungen `docker build --network host …` |
| Container startet und stoppt sofort | `docker logs tagstock-server --tail 80` ansehen. Meist Rechte: `chown -R 99:100 /mnt/user/appdata/tagstock` |
| `Port is already allocated` | Ein anderer Container hat 8080. Im Container-Formular den Host-Port auf z. B. `8081` ändern; in der App dann `http://<server-ip>:8081` |
| App meldet „Server nicht erreichbar" | IP und Port prüfen, `http://` nicht vergessen, Handy im selben Netz? `curl` vom Server aus testen (Schritt 5) |
| App meldet „Anmeldung erforderlich" | Der Token ist abgelaufen (Standard 180 Tage). In *Einstellungen → Server* neu anmelden |
| Bilder erscheinen nicht auf dem zweiten Gerät | Erst nach einem Abgleich: Bestandsliste nach unten ziehen. Prüfen, ob `/mnt/user/appdata/tagstock/bilder` gefüllt ist |
| „Nur Lageristen und Admins dürfen den Bestand ändern" | Die Rolle im Team ist `mitglied`. Ein Admin erzeugt einen neuen Einladungscode mit der Rolle `lagerist`; wer ihn eingibt, bekommt die neue Rolle (siehe unten) |
| Alles auf Anfang | Container stoppen, `/mnt/user/appdata/tagstock` löschen, Container starten. **Damit sind alle Konten und der ganze Bestand weg.** |

Laufende Ausgabe mitlesen:

```bash
docker logs -f tagstock-server
```
