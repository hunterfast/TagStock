package de.tagstock.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein Artikel im Bestand. Aufbau und Felder entsprechen der Weboberflaeche:
 * genau ein Status je Artikel, Standort und Lagerort als freie Angaben.
 */
@Entity(
        tableName = "artikel",
        indices = {@Index(value = "rfidUid", unique = true), @Index("serverId")})
public class Artikel {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** ID auf dem Server, solange nicht synchronisiert null. */
    @ColumnInfo(name = "serverId")
    public String serverId;

    /** Team auf dem Server, zu dem der Artikel gehoert. */
    @ColumnInfo(name = "teamId")
    public String teamId;

    /** NFC-Kennung oder QR-Inhalt; eindeutig, damit ein Scan genau trifft. */
    @ColumnInfo(name = "rfidUid")
    public String rfidUid;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @ColumnInfo(name = "beschreibung")
    public String beschreibung;

    @ColumnInfo(name = "kategorie")
    public String kategorie;

    /** Uebergeordneter Standort, z. B. "Werkstatt". */
    @ColumnInfo(name = "standort")
    public String standort;

    /** Genaue Position, z. B. "Regal A3, Fach 2". */
    @ColumnInfo(name = "lagerort")
    public String lagerort;

    /** Dateiname des Fotos im App-Verzeichnis. */
    @ColumnInfo(name = "fotoPfad")
    public String fotoPfad;

    /** Bildadresse vom Server, falls das Foto dort liegt. */
    @ColumnInfo(name = "bildUrl")
    public String bildUrl;

    @NonNull
    @ColumnInfo(name = "status")
    public ArtikelStatus status = ArtikelStatus.VORHANDEN;

    @ColumnInfo(name = "verliehenAn")
    public String verliehenAn;

    /** Geplante Rueckgabe, Tagesbeginn in Millisekunden. */
    @ColumnInfo(name = "rueckgabeDatum")
    public Long rueckgabeDatum;

    @ColumnInfo(name = "zuletztGescannt")
    public Long zuletztGescannt;

    @NonNull
    @ColumnInfo(name = "scanWarnung")
    public ScanWarnung scanWarnung = ScanWarnung.JAHR;

    /**
     * Wie viele. Ohne Verpackungseinheit die Stueckzahl, mit einer die Zahl
     * der <b>vollen, ungeoeffneten</b> Packungen.
     */
    @ColumnInfo(name = "menge", defaultValue = "1")
    public int menge = 1;

    /** true, wenn dieser Artikel in Packungen kommt - etwa ein 4er-Pack. */
    @ColumnInfo(name = "istVerpackung", defaultValue = "0")
    public boolean istVerpackung;

    /** Stueck je Packung. Nur bei einer Verpackungseinheit belegt. */
    @ColumnInfo(name = "packungsGroesse", defaultValue = "0")
    public int packungsGroesse;

    /**
     * Reste der angebrochenen Packungen, etwa "2,3". Einzeln, weil durchaus
     * mehr als eine Packung offen sein kann - versehentlich oder nicht.
     */
    @ColumnInfo(name = "angebrochen")
    public String angebrochen;

    /**
     * true, wenn dieser Eintrag selbst etwas aufnimmt - eine Box, eine
     * Schublade, ein Regal. Also ein Lager im Lager.
     */
    @ColumnInfo(name = "istBehaelter")
    public boolean istBehaelter;

    /** Was fuer einer, z. B. "Box" oder "Schublade" - nur zur Anzeige. */
    @ColumnInfo(name = "behaelterArt")
    public String behaelterArt;

    /**
     * Kennung des Behaelters, in dem dieser Artikel liegt. Bewusst die
     * Kennung und keine laufende Nummer: Sie klebt am Moebel, wird beim
     * Einraeumen gescannt und ueberlebt jeden Abgleich unveraendert.
     */
    @ColumnInfo(name = "behaelterKennung")
    public String behaelterKennung;

    @ColumnInfo(name = "erstelltAm")
    public long erstelltAm = System.currentTimeMillis();

    @ColumnInfo(name = "geaendertAm")
    public long geaendertAm = System.currentTimeMillis();

    /** true, solange die Aenderung noch nicht zum Server gewandert ist. */
    @ColumnInfo(name = "offen")
    public boolean offen = true;

    /** true, wenn der Artikel im Regal fehlen wuerde und nie gescannt wurde. */
    public boolean istVermisst() {
        if (scanWarnung == ScanWarnung.NIEMALS) {
            return false;
        }
        if (zuletztGescannt == null) {
            return true;
        }
        return zuletztGescannt < scanWarnung.grenze();
    }

    /** true, wenn der Artikel verliehen ist und die Rueckgabe ueberfaellig. */
    public boolean istUeberfaellig() {
        return status == ArtikelStatus.VERLIEHEN
                && rueckgabeDatum != null
                && rueckgabeDatum < System.currentTimeMillis();
    }

    /** true, wenn die Rueckgabe in den naechsten drei Tagen faellig ist. */
    public boolean istBaldFaellig() {
        return status == ArtikelStatus.VERLIEHEN
                && rueckgabeDatum != null
                && !istUeberfaellig()
                && rueckgabeDatum <= System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000;
    }

    /** Stueck insgesamt - volle Packungen und angebrochene zusammengerechnet. */
    public int gesamtStueck() {
        return Packungen.gesamt(this);
    }

    /** Wert, der im QR-Code steht: die Kennung, ersatzweise die laufende Nummer. */
    public String qrWert() {
        return rfidUid != null && !rfidUid.isEmpty() ? rfidUid : ("TS-" + id);
    }

    public Artikel kopie() {
        Artikel kopie = new Artikel();
        kopie.id = id;
        kopie.serverId = serverId;
        kopie.teamId = teamId;
        kopie.rfidUid = rfidUid;
        kopie.name = name;
        kopie.beschreibung = beschreibung;
        kopie.kategorie = kategorie;
        kopie.standort = standort;
        kopie.lagerort = lagerort;
        kopie.fotoPfad = fotoPfad;
        kopie.bildUrl = bildUrl;
        kopie.status = status;
        kopie.verliehenAn = verliehenAn;
        kopie.rueckgabeDatum = rueckgabeDatum;
        kopie.zuletztGescannt = zuletztGescannt;
        kopie.scanWarnung = scanWarnung;
        kopie.menge = menge;
        kopie.istVerpackung = istVerpackung;
        kopie.packungsGroesse = packungsGroesse;
        kopie.angebrochen = angebrochen;
        kopie.istBehaelter = istBehaelter;
        kopie.behaelterArt = behaelterArt;
        kopie.behaelterKennung = behaelterKennung;
        kopie.erstelltAm = erstelltAm;
        kopie.geaendertAm = geaendertAm;
        kopie.offen = offen;
        return kopie;
    }

    /** Vergleich fuer die Listenaktualisierung. */
    public boolean gleichAnzeige(@Nullable Artikel andere) {
        if (andere == null) {
            return false;
        }
        return geaendertAm == andere.geaendertAm
                && name.equals(andere.name)
                && status == andere.status
                && gleich(kategorie, andere.kategorie)
                && gleich(standort, andere.standort)
                && gleich(lagerort, andere.lagerort)
                && gleich(verliehenAn, andere.verliehenAn)
                && gleich(fotoPfad, andere.fotoPfad)
                && gleich(rfidUid, andere.rfidUid)
                && menge == andere.menge
                && istVerpackung == andere.istVerpackung
                && packungsGroesse == andere.packungsGroesse
                && gleich(angebrochen, andere.angebrochen)
                && istBehaelter == andere.istBehaelter
                && gleich(behaelterArt, andere.behaelterArt)
                && gleich(behaelterKennung, andere.behaelterKennung);
    }

    private boolean gleich(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
