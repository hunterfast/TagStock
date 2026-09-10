# Changelog

Was sich geändert hat – getrennt nach den beiden Teilen, die getrennt
aktualisiert werden:

| Ordner | Betrifft |
|---|---|
| [`app/`](app/) | die Android-App (APK) |
| [`server/`](server/) | die Serveranwendung im Docker-Container |

## Ablage

In jedem der beiden Ordner liegt **ein Unterordner je Jahr**, darin **eine
Datei je Version**:

```
changelog/
├── app/
│   ├── 2026/
│   │   ├── 2.0.md        ← Version 2.0, alle Änderungen dieser Version
│   │   └── 2.1.md
│   └── 2027/             ← wird am 1. Januar 2027 angelegt
└── server/
    └── 2026/
        └── 2.0.0.md
```

Am 1. Januar entsteht der Ordner für das neue Jahr; die alten bleiben
unverändert stehen. Eine Datei wird **fortlaufend ergänzt**, solange an ihrer
Version noch gearbeitet wird – erst mit der nächsten Versionsnummer beginnt
eine neue Datei.

Anlegen geht am schnellsten so:

```bash
sh changelog/neu.sh app 2.1
sh changelog/neu.sh server 2.1.0
```

Das Skript legt Jahresordner und Datei an, falls sie fehlen, und trägt die
Kopfzeile ein.

Wie eine neue Version herausgegeben wird – Nummer hochzählen, Changelog
schreiben, veröffentlichen –, steht in [../UPDATE.md](../UPDATE.md).

## Aufbau einer Datei

```markdown
# App 2.1

_Stand: 2026-10-04_

## Neu
- …

## Geändert
- …

## Behoben
- …
```

Die Abschnitte, die leer bleiben, lässt man weg. Der Text landet unverändert in
der Veröffentlichung auf GitHub und – bei der App – als Hinweis in der
Aktualisierungsmeldung auf dem Handy. Er richtet sich also an die Leute, die
die App benutzen, nicht an die, die sie bauen.
