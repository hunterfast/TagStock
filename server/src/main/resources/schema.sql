-- TagStock-Server: SQLite-Schema. Wird bei jedem Start angelegt, falls es fehlt.

CREATE TABLE IF NOT EXISTS benutzer (
    id          TEXT PRIMARY KEY,
    email       TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    passwort    TEXT NOT NULL,
    -- Der Verwalter betreut den ganzen Server: er sieht jedes Lager und darf
    -- dort alles. Das erste angelegte Konto bekommt dieses Merkmal.
    verwalter   INTEGER NOT NULL DEFAULT 0,
    erstellt_am INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sitzungen (
    token_hash  TEXT PRIMARY KEY,
    benutzer_id TEXT NOT NULL,
    erstellt_am INTEGER NOT NULL,
    gueltig_bis INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sitzungen_benutzer ON sitzungen (benutzer_id);

CREATE TABLE IF NOT EXISTS teams (
    id           TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    beschreibung TEXT,
    ersteller_id TEXT NOT NULL,
    erstellt_am  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS mitglieder (
    team_id     TEXT NOT NULL,
    benutzer_id TEXT NOT NULL,
    rolle       TEXT NOT NULL,
    seit        INTEGER NOT NULL,
    PRIMARY KEY (team_id, benutzer_id)
);

CREATE TABLE IF NOT EXISTS einladungen (
    code        TEXT PRIMARY KEY,
    team_id     TEXT NOT NULL,
    rolle       TEXT NOT NULL,
    erstellt_am INTEGER NOT NULL,
    gueltig_bis INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS kategorien (
    id           TEXT PRIMARY KEY,
    team_id      TEXT NOT NULL,
    name         TEXT NOT NULL,
    reihenfolge  INTEGER NOT NULL DEFAULT 0,
    geaendert_am INTEGER NOT NULL,
    geloescht    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_kategorien_team ON kategorien (team_id);

CREATE TABLE IF NOT EXISTS artikel (
    id               TEXT PRIMARY KEY,
    team_id          TEXT NOT NULL,
    rfid_uid         TEXT,
    name             TEXT NOT NULL,
    beschreibung     TEXT,
    kategorie        TEXT,
    standort         TEXT,
    lagerort         TEXT,
    bild_url         TEXT,
    status           TEXT NOT NULL DEFAULT 'vorhanden',
    verliehen_an     TEXT,
    rueckgabe_datum  INTEGER,
    zuletzt_gescannt INTEGER,
    scan_warnung     TEXT NOT NULL DEFAULT '1j',
    -- Menge: ohne Verpackungseinheit Stueck, mit einer die vollen Packungen.
    -- Was in angebrochenen Packungen liegt, steht einzeln in behaelter-freier
    -- Form daneben ("2,3") - es koennen durchaus mehrere offen sein.
    menge            INTEGER NOT NULL DEFAULT 1,
    ist_verpackung   INTEGER NOT NULL DEFAULT 0,
    packungs_groesse INTEGER NOT NULL DEFAULT 0,
    angebrochen      TEXT,
    -- Behaelter: eine Box, Schublade, ein Regal ... - also ein Lager im Lager.
    -- Wer drin liegt, merkt sich die Kennung des Behaelters; die klebt am
    -- Moebel und ist genau das, was beim Einraeumen gescannt wird.
    ist_behaelter    INTEGER NOT NULL DEFAULT 0,
    behaelter_art    TEXT,
    behaelter_kennung TEXT,
    erstellt_am      INTEGER NOT NULL,
    geaendert_am     INTEGER NOT NULL,
    geloescht        INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_artikel_team ON artikel (team_id);
CREATE INDEX IF NOT EXISTS idx_artikel_geaendert ON artikel (team_id, geaendert_am);
-- Eine Kennung gehoert je Team zu genau einem Artikel.
CREATE UNIQUE INDEX IF NOT EXISTS idx_artikel_kennung ON artikel (team_id, rfid_uid)
    WHERE rfid_uid IS NOT NULL AND geloescht = 0;

-- Artikelbilder liegen als Datei im Bilderordner; hier stehen nur die Angaben dazu.
CREATE TABLE IF NOT EXISTS bilder (
    id              TEXT PRIMARY KEY,
    team_id         TEXT NOT NULL,
    artikel_id      TEXT NOT NULL,
    datei           TEXT NOT NULL,
    typ             TEXT NOT NULL,
    groesse         INTEGER NOT NULL,
    hochgeladen_von TEXT,
    erstellt_am     INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bilder_artikel ON bilder (team_id, artikel_id);

CREATE TABLE IF NOT EXISTS protokoll (
    id           TEXT PRIMARY KEY,
    team_id      TEXT NOT NULL,
    artikel_id   TEXT,
    artikel_name TEXT NOT NULL,
    aktion       TEXT NOT NULL,
    alter_wert   TEXT,
    neuer_wert   TEXT,
    nutzer       TEXT,
    zeitpunkt    INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_protokoll_team ON protokoll (team_id, zeitpunkt);
CREATE INDEX IF NOT EXISTS idx_protokoll_artikel ON protokoll (artikel_id);

-- Nachgeschlagene Produktdaten zu einer GTIN. Jede Nummer wird nur einmal
-- ausserhalb erfragt; danach kennt der Server sie fuer alle Geraete.
CREATE TABLE IF NOT EXISTS gtin_cache (
    gtin      TEXT PRIMARY KEY,
    gefunden  INTEGER NOT NULL DEFAULT 0,
    name      TEXT,
    marke     TEXT,
    kategorie TEXT,
    quelle    TEXT,
    geholt_am INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS anfragen (
    id                 TEXT PRIMARY KEY,
    team_id            TEXT NOT NULL,
    artikel_id         TEXT NOT NULL,
    artikel_name       TEXT NOT NULL,
    antragsteller_id   TEXT NOT NULL,
    antragsteller_name TEXT NOT NULL,
    nachricht          TEXT,
    datum_von          INTEGER,
    datum_bis          INTEGER,
    status             TEXT NOT NULL DEFAULT 'offen',
    bearbeiter         TEXT,
    antwort            TEXT,
    erstellt_am        INTEGER NOT NULL,
    geaendert_am       INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_anfragen_team ON anfragen (team_id, status);
