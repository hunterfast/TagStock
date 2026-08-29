# TagStock

Native Android-App (Java) zur Lagerverwaltung mit Barcode-, QR-Code- und NFC-Erfassung.
Kein WebView, keine Webanwendung – reines Android-SDK mit Views, Room und CameraX.

## Funktionen

- **Mehrere Lager**: beliebig viele Lager mit Name, Ort und Beschreibung anlegen,
  bearbeiten und löschen. Die Übersicht zeigt je Lager, wie viele Artikel vorhanden,
  verliehen oder verloren sind.
- **Artikel je Lager**: Bezeichnung, Beschreibung, Menge, Notiz und ein optionaler Code.
- **Status** je Artikel:
  - *Vorhanden*
  - *Verliehen* – mit Name des Ausleihers und Ausleihdatum
  - *Verloren*
  
  Der Status lässt sich direkt aus der Liste über das Kontextmenü umschalten.
- **Scannen** (ein Bildschirm für alles):
  - **Barcodes** (EAN-8/13, UPC-A/E, Code 39/128, ITF, PDF417, Aztec, Data Matrix …)
    und **QR-Codes** über die Kamera (CameraX + ML Kit, funktioniert offline)
  - **NFC-Tags** parallel über den NFC-Reader-Mode: gelesen werden die Tag-UID und,
    falls vorhanden, der NDEF-Text- bzw. URI-Inhalt
  - Taschenlampe und manuelle Code-Eingabe als Rückfallebene
- **Scan-Logik**: Ein gescannter Code wird über alle Lager hinweg gesucht.
  - Artikel bekannt → wird direkt geöffnet
  - Artikel in einem anderen Lager → Nachfrage „öffnen oder hierher verschieben?“
  - Code unbekannt → Artikel kann direkt mit diesem Code angelegt werden
- **Suche und Filter**: Volltextsuche über Bezeichnung, Beschreibung, Code, Ausleiher
  und Notiz sowie Filter-Chips je Status.
- NFC-Tags werden auch auf dem Startbildschirm und in der Artikelliste erkannt,
  ohne dass der Scanner geöffnet sein muss.

## Bauen

Voraussetzungen: JDK 17, Android SDK mit API 35 (Android Studio Ladybug oder neuer).

```bash
./gradlew assembleDebug      # APK unter app/build/outputs/apk/debug/
./gradlew installDebug       # auf ein angeschlossenes Gerät installieren
```

In Android Studio: Ordner öffnen, Gradle-Sync abwarten, „Run“.

## Projektstruktur

```
app/src/main/java/de/tagstock/
├── data/          Room-Datenbank
│   ├── Lager.java, Item.java          Entitäten
│   ├── ItemStatus.java, CodeType.java Enums (vorhanden/verliehen/verloren, Codeart)
│   ├── LagerDao.java, ItemDao.java    Abfragen inkl. LiveData
│   ├── LagerWithCount.java            Lager samt Artikelzahlen je Status
│   ├── Converters.java, AppDatabase.java
│   └── Repository.java                Zugriffspunkt, Hintergrund-Executor
├── ui/
│   ├── MainActivity.java              Lagerübersicht + Scan-Einstieg
│   ├── LagerDetailActivity.java       Artikelliste mit Suche und Statusfilter
│   ├── ItemEditActivity.java          Artikel anlegen/bearbeiten
│   ├── ScannerActivity.java           Kamera- und NFC-Scanner
│   ├── LagerAdapter.java, ItemAdapter.java, *ViewModel.java
│   └── LagerDialog.java, StatusActions.java
└── util/
    ├── NfcHelper.java                 Reader-Mode, UID- und NDEF-Auswertung
    ├── ScanResult.java, Dialogs.java, Formatter.java
```

## Datenmodell

`lager` (id, name, beschreibung, ort, erstelltAm)
→ `items` (id, lagerId, name, beschreibung, code, codeType, menge, status,
verliehenAn, verliehenSeit, notiz, erstelltAm, geaendertAm)

Artikel hängen per Fremdschlüssel am Lager (`ON DELETE CASCADE`), `code` ist indiziert,
damit ein Scan sofort den passenden Artikel findet.

## Berechtigungen

- `CAMERA` – wird erst beim ersten Scan abgefragt; ohne Freigabe bleibt der
  NFC-Scan nutzbar
- `NFC` – Geräte ohne NFC-Chip können die App trotzdem installieren
  (`uses-feature … required="false"`)

## Hinweis zum Stand

Die App wurde in dieser Umgebung nicht kompiliert: Der Netzwerkzugang erlaubt kein
`dl.google.com`, von dort stammen sowohl das Android SDK als auch alle
AndroidX-/Material-Artefakte. Geprüft wurden stattdessen alle XML-Dateien auf
Wohlgeformtheit sowie sämtliche Ressourcen- und ViewBinding-Referenzen aus dem
Java-Code. Der erste Build muss also lokal bzw. in Android Studio laufen.
