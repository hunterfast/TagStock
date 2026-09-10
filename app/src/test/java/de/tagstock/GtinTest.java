package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.tagstock.util.Gtin;

/** Pruefziffer und Praefix - reine Rechnerei, laeuft ohne Android. */
public class GtinTest {

    @Test
    public void echteNummernGehenDurch() {
        assertTrue(Gtin.pruefzifferStimmt("4006381333931"));   // EAN-13
        assertTrue(Gtin.pruefzifferStimmt("96385074"));        // EAN-8
        assertTrue(Gtin.pruefzifferStimmt("036000291452"));    // UPC-A
        assertTrue(Gtin.pruefzifferStimmt("10614141000415"));  // GTIN-14
    }

    @Test
    public void vertippteNummernFallenAuf() {
        assertFalse(Gtin.pruefzifferStimmt("4006381333932"));
        assertFalse(Gtin.pruefzifferStimmt("4006381333"));
        assertFalse(Gtin.pruefzifferStimmt("TS-42"));
        assertFalse(Gtin.pruefzifferStimmt(null));
    }

    @Test
    public void artUndHerkunftStehenDran() {
        assertEquals("EAN-13", Gtin.art("4006381333931"));
        assertEquals("Deutschland", Gtin.herkunft("4006381333931"));
        assertEquals("UPC-A", Gtin.art("036000291452"));
        assertEquals("USA und Kanada", Gtin.herkunft("036000291452"));
        assertEquals("Buch (ISBN)", Gtin.herkunft("9783161484100"));
        assertNull(Gtin.art("04E1F2A3"));
    }

    @Test
    public void hausinterneNummernSindErkennbar() {
        assertTrue(Gtin.intern("2000000000008"));
        assertFalse(Gtin.intern("4006381333931"));
    }
}
