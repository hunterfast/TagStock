# TagStock

Native Android-App (Java) zur Lagerverwaltung mit Barcode-, QR-Code- und NFC-Erfassung.
Kein WebView, keine Webanwendung – reines Android-SDK mit Views, Room und CameraX.

## Funktionen

### Lager und Artikel
- Beliebig viele Lager mit Name, Ort und Beschreibung. Die Übersicht zeigt je Lager
  die Stückzahlen nach Zustand.
- Artikel mit Bezeichnung, Beschreibung, Menge, Notiz und Foto.
- **Suche über alle Lager** hinweg: Bezeichnung, Beschreibung, Notiz und Codes,
  kombinierbar mit einem Zustandsfilter.

### Zustände mit Mengen
Der Zustand hängt nicht am ganzen Artikel, sondern an Stückzahlen:

```
vorhanden = Menge − verliehen − verloren
```

Fünf Bohrer, zwei davon verliehen, einer verloren: die App zeigt
„2 vorhanden · 2 verliehen · 1 verloren“. Die Filter *Vorhanden*, *Verliehen* und
*Verloren* greifen, sobald mindestens ein Stück im jeweiligen Zustand ist.

### Verleih mit Historie
Jede Ausleihe ist ein eigener Vorgang mit Person, Stückzahl, Ausleih- und
Rückgabedatum. Zurückgegebene Vorgänge bleiben als Historie am Artikel stehen –
so lässt sich nachvollziehen, wer etwas zuletzt hatte.

### Codes
- Ein Artikel kann **mehrere Codes** haben, etwa den Herstellerbarcode *und* einen
  selbst aufgeklebten NFC-Tag.
- Codes sind **geräteweit eindeutig**: Ein bereits vergebener Code wird abgelehnt,
  und die App zeigt, zu welchem Artikel er gehört.
- **NFC-Tags beschreiben**: Artikelname und Nummer werden als Text auf leere Tags
  geschrieben, die Tag-Kennung wird als Code hinterlegt.

### Scannen
- **Barcodes** (EAN-8/13, UPC-A/E, Code 39/128, ITF, PDF417, Aztec, Data Matrix …)
  und **QR-Codes** über die Kamera (CameraX + ML Kit, funktioniert offline).
- **NFC-Tags** parallel über den Reader-Mode: gelesen werden Tag-UID und, falls
  vorhanden, der NDEF-Inhalt. Beides wird beim Suchen berücksichtigt.
- Ein gescannter Code wird über alle Lager gesucht: bekannt → Artikel öffnen;
  in einem anderen Lager → „öffnen oder hierher verschieben?“; unbekannt →
  direkt mit diesem Code anlegen.
- Taschenlampe und manuelle Eingabe als Rückfallebene.

### Inventur
Dauerscan: Der Scanner bleibt offen, jeder Treffer wandert in eine Liste.
Die Auswertung zeigt danach

- **nicht gefunden** – auswählbar als verloren melden,
- **aus anderem Lager** – auswählbar hierher verschieben,
- **unbekannte Codes** – direkt als neuen Artikel anlegen.

Vollständig verliehene oder verlorene Stücke zählen nicht als „fehlend“, sie können
im Regal gar nicht liegen.

### Sicherung
- **JSON-Export** über den System-Dateidialog: vollständiger Bestand, wieder
  einlesbar (wahlweise ergänzend oder ersetzend).
- **CSV-Export** für die Tabellenkalkulation, mit Stückzahlen je Zustand.
- Fotos bleiben außen vor, sie liegen nur auf dem Gerät.

## Bauen

Der Debug-Build läuft bei jedem Push auf GitHub Actions
(`.github/workflows/android.yml`) und legt die APK als Artefakt `tagstock-debug-apk`
am jeweiligen Lauf ab – ohne lokale Android-Installation.

Lokal, mit JDK 17 und Android SDK (API 35):

```bash
./gradlew testDebugUnitTest    # Tests
./gradlew lintDebug            # Lint
./gradlew assembleDebug        # APK unter app/build/outputs/apk/debug/
./gradlew installDebug         # auf ein angeschlossenes Gerät
```

### Release signieren

Der Release-Build wird mit R8 verkleinert und signiert, sobald im Projektwurzel-
verzeichnis eine `keystore.properties` liegt (sie ist von der Versionierung
ausgeschlossen):

```properties
storeFile=/pfad/zum/schluessel.jks
storePassword=…
keyAlias=tagstock
keyPassword=…
```

Ohne diese Datei entsteht ein unsigniertes Release-APK.

## Tests

`app/src/test` läuft mit Robolectric auf der JVM:

- **MigrationTest** legt eine echte Datenbank im Format von Version 1 an, führt die
  Migration aus und prüft, dass Codes, Ausleihen und Verluste korrekt übernommen
  werden. Room prüft dabei zusätzlich, ob das Schema exakt zu den Entities passt.
- **BestandsLogikTest** rechnet Bestand, Zustand und Filter nach.
- **SicherungTest** prüft JSON im Rundlauf und die CSV-Ausgabe samt Maskierung.

## Datenmodell

```
lager   (id, name, beschreibung, ort, erstelltAm)
  └── items    (id, lagerId, name, beschreibung, menge, mengeVerloren,
                fotoPfad, notiz, erstelltAm, geaendertAm)
        ├── codes   (id, itemId, wert UNIQUE, typ, erfasstAm)
        └── verleih (id, itemId, person, menge, ausgeliehenAm, zurueckAm, notiz)
```

Alles hängt per Fremdschlüssel mit `ON DELETE CASCADE` zusammen. Schemaänderungen
laufen über echte Room-Migrationen (aktuell Version 2), damit ein Update den
Bestand nicht verliert.

## Projektstruktur

```
app/src/main/java/de/tagstock/
├── data/     Entities, DAOs, Migration, Repository
├── ui/       Activities, Adapter, ViewModels, Dialoge
└── util/     NFC, Fotos, Sicherung, Formatierung
```

## Berechtigungen

- `CAMERA` – wird erst beim ersten Scan abgefragt; ohne Freigabe bleibt der
  NFC-Scan nutzbar.
- `NFC` – Geräte ohne NFC-Chip können die App trotzdem installieren.
- Für Sicherung und Fotos werden keine Speicherberechtigungen gebraucht: Dateien
  laufen über den System-Dateidialog, Fotos liegen im privaten App-Verzeichnis.
