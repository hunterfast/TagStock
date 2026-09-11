/*
 * Serveroberflaeche von TagStock. Arbeitet auf derselben Schnittstelle wie die
 * App - was hier passiert, sehen die Geraete beim naechsten Abgleich.
 * Bewusst ohne Bibliothek: eine Datei, die jeder lesen kann.
 */
(function () {
    'use strict';

    var STATUS = {
        'vorhanden': {text: 'Vorhanden', farbe: 'var(--vorhanden)'},
        'nicht vorhanden': {text: 'Nicht vorhanden', farbe: 'var(--fehlt)'},
        'verliehen': {text: 'Verliehen', farbe: 'var(--verliehen)'},
        'verbraucht': {text: 'Verbraucht', farbe: 'var(--verbraucht)'},
        'ausgelagert': {text: 'Ausgelagert', farbe: 'var(--ausgelagert)'}
    };

    var stand = {
        token: localStorage.getItem('tagstock-token') || null,
        ich: null,
        teams: [],
        teamId: localStorage.getItem('tagstock-team') || null,
        rolle: 'mitglied',
        artikel: [],
        kategorien: [],
        filterStatus: null,
        offen: null
    };

    function $(kennung) {
        return document.getElementById(kennung);
    }

    function melden(text) {
        var kasten = document.createElement('div');
        kasten.className = 'meldung';
        kasten.textContent = text;
        document.body.appendChild(kasten);
        setTimeout(function () {
            kasten.remove();
        }, 3200);
    }

    /** Ein Aufruf an die Schnittstelle; wirft mit der Meldung des Servers. */
    function api(pfad, einstellungen) {
        einstellungen = einstellungen || {};
        var kopf = einstellungen.headers || {};
        if (stand.token) {
            kopf['Authorization'] = 'Bearer ' + stand.token;
        }
        if (einstellungen.body && !kopf['Content-Type']) {
            kopf['Content-Type'] = 'application/json; charset=utf-8';
        }
        return fetch('/api/v1' + pfad, {
            method: einstellungen.method || 'GET',
            headers: kopf,
            body: einstellungen.body
        }).then(function (antwort) {
            if (antwort.status === 401) {
                abmelden();
                throw new Error('Anmeldung abgelaufen');
            }
            return antwort.text().then(function (text) {
                var daten = null;
                try {
                    daten = text ? JSON.parse(text) : null;
                } catch (fehler) {
                    daten = null;
                }
                if (!antwort.ok) {
                    throw new Error(daten && daten.fehler ? daten.fehler
                        : 'Server meldet Fehler ' + antwort.status);
                }
                return daten;
            });
        });
    }

    function darfBearbeiten() {
        return stand.rolle === 'admin' || stand.rolle === 'lagerist';
    }

    // ------------------------------------------------------------ Anmeldung

    function anmelden() {
        var email = $('anmeldeEmail').value.trim();
        var passwort = $('anmeldePasswort').value;
        if (!email || !passwort) {
            return;
        }
        $('knopfAnmelden').disabled = true;
        api('/auth/anmelden', {
            method: 'POST',
            body: JSON.stringify({email: email, passwort: passwort})
        }).then(function (antwort) {
            stand.token = antwort.token;
            localStorage.setItem('tagstock-token', stand.token);
            $('anmeldeFehler').hidden = true;
            starten();
        }).catch(function (fehler) {
            $('anmeldeFehler').textContent = fehler.message;
            $('anmeldeFehler').hidden = false;
        }).finally(function () {
            $('knopfAnmelden').disabled = false;
        });
    }

    function abmelden() {
        stand.token = null;
        localStorage.removeItem('tagstock-token');
        $('oberflaeche').hidden = true;
        $('anmeldung').hidden = false;
        apkAnbieten();
    }

    /**
     * Die App zum Laden anbieten, ohne dass jemand angemeldet ist. Vor der
     * Oberflaeche stand dieser Link auf der Startseite; mit der Anmeldemaske
     * war er weg, und damit auch der Weg, ein neues Geraet zu bestuecken.
     */
    function apkAnbieten() {
        var feld = $('apkAngebot');
        fetch('/api/v1/app').then(function (antwort) {
            return antwort.ok ? antwort.json() : null;
        }).then(function (app) {
            var fassung = app && app.aktuell;
            if (!fassung) {
                feld.hidden = true;
                return;
            }
            var name = fassung.versionName || fassung.versionCode || '';
            var mb = Math.round((fassung.groesse || 0) / 1048576);
            feld.innerHTML = '<a class="knopf" href="' + text(fassung.adresse) + '">'
                + 'App für Android laden' + (name ? ' · ' + text(name) : '')
                + '</a>' + (mb ? '<span class="leer"> ' + mb + ' MB</span>' : '');
            feld.hidden = false;
        }).catch(function () {
            feld.hidden = true;
        });
    }

    /** Nach der Anmeldung: wer bin ich, welche Lager gibt es? */
    function starten() {
        if (!stand.token) {
            $('anmeldung').hidden = false;
            $('oberflaeche').hidden = true;
            apkAnbieten();
            return;
        }
        api('/ich').then(function (antwort) {
            stand.ich = antwort.benutzer;
            $('werBinIch').textContent = stand.ich.name
                + (stand.ich.verwalter ? ' · betreut den Server' : '');
            $('anmeldung').hidden = true;
            $('oberflaeche').hidden = false;
            return stand.ich.verwalter
                ? api('/verwaltung/teams').then(function (alle) {
                    stand.teams = alle;
                    zeigeAlleLager(alle);
                })
                : Promise.resolve(antwort.teams || []).then(function (meine) {
                    stand.teams = meine;
                });
        }).then(function () {
            teamsZeichnen();
            if (stand.teamId) {
                teamLaden();
            }
        }).catch(function (fehler) {
            melden(fehler.message);
        });
    }

    function teamsZeichnen() {
        var wahl = $('teamWahl');
        wahl.innerHTML = '';
        if (!stand.teams.length) {
            var leer = document.createElement('option');
            leer.textContent = 'Kein Lager vorhanden';
            wahl.appendChild(leer);
            return;
        }
        stand.teams.forEach(function (team) {
            var eintrag = document.createElement('option');
            eintrag.value = team.id;
            eintrag.textContent = team.name;
            wahl.appendChild(eintrag);
        });
        var bekannt = stand.teams.some(function (team) {
            return team.id === stand.teamId;
        });
        if (!bekannt) {
            stand.teamId = stand.teams[0].id;
            localStorage.setItem('tagstock-team', stand.teamId);
        }
        wahl.value = stand.teamId;
    }

    function teamLaden() {
        var team = stand.teams.filter(function (eintrag) {
            return eintrag.id === stand.teamId;
        })[0];
        stand.rolle = stand.ich && stand.ich.verwalter ? 'admin'
            : (team && team.rolle ? team.rolle : 'mitglied');
        bestandLaden();
        kategorienLaden();
    }

    // -------------------------------------------------------------- Bestand

    function bestandLaden() {
        if (!stand.teamId) {
            return;
        }
        api('/teams/' + stand.teamId + '/artikel').then(function (liste) {
            stand.artikel = liste || [];
            fuelleFilter();
            bestandZeichnen();
        }).catch(function (fehler) {
            melden(fehler.message);
        });
    }

    function passt(artikel, suche) {
        if (!suche) {
            return true;
        }
        return ['name', 'beschreibung', 'kategorie', 'standort', 'lagerort', 'rfidUid',
            'verliehenAn'].some(function (feld) {
            return (artikel[feld] || '').toLowerCase().indexOf(suche) >= 0;
        });
    }

    function gefiltert() {
        var suche = $('suche').value.trim().toLowerCase();
        var kategorie = $('filterKategorie').value;
        var standort = $('filterStandort').value;
        return stand.artikel.filter(function (artikel) {
            if (stand.filterStatus && artikel.status !== stand.filterStatus) {
                return false;
            }
            if (kategorie && artikel.kategorie !== kategorie) {
                return false;
            }
            if (standort && artikel.standort !== standort) {
                return false;
            }
            var behaelter = $('filterBehaelter').value;
            if (behaelter === '#behaelter' && !artikel.istBehaelter) {
                return false;
            }
            if (behaelter === '#frei' && artikel.behaelterKennung) {
                return false;
            }
            if (behaelter && behaelter.charAt(0) !== '#'
                    && artikel.behaelterKennung !== behaelter) {
                return false;
            }
            return passt(artikel, suche);
        });
    }

    function zahlenZeichnen() {
        var flaeche = $('zahlen');
        flaeche.innerHTML = '';
        var zaehler = {};
        stand.artikel.forEach(function (artikel) {
            zaehler[artikel.status] = (zaehler[artikel.status] || 0) + 1;
        });
        Object.keys(STATUS).forEach(function (schluessel) {
            var karte = document.createElement('div');
            karte.className = 'zahl' + (stand.filterStatus === schluessel ? ' aktiv' : '');
            karte.innerHTML = '<b>' + (zaehler[schluessel] || 0) + '</b><span>'
                + STATUS[schluessel].text + '</span>';
            karte.onclick = function () {
                stand.filterStatus = stand.filterStatus === schluessel ? null : schluessel;
                bestandZeichnen();
            };
            flaeche.appendChild(karte);
        });
    }

    function fuelleFilter() {
        ['kategorie', 'standort'].forEach(function (feld) {
            var werte = [];
            stand.artikel.forEach(function (artikel) {
                var wert = artikel[feld];
                if (wert && werte.indexOf(wert) < 0) {
                    werte.push(wert);
                }
            });
            werte.sort(function (a, b) {
                return a.localeCompare(b, 'de');
            });
            var auswahl = $(feld === 'kategorie' ? 'filterKategorie' : 'filterStandort');
            var bisher = auswahl.value;
            auswahl.innerHTML = '<option value="">'
                + (feld === 'kategorie' ? 'Alle Kategorien' : 'Alle Standorte') + '</option>';
            werte.forEach(function (wert) {
                var eintrag = document.createElement('option');
                eintrag.value = wert;
                eintrag.textContent = wert;
                auswahl.appendChild(eintrag);
            });
            auswahl.value = bisher;
            var vorschlag = $(feld === 'kategorie' ? 'kategorienVorschlag' : 'standorteVorschlag');
            vorschlag.innerHTML = '';
            werte.forEach(function (wert) {
                var eintrag = document.createElement('option');
                eintrag.value = wert;
                vorschlag.appendChild(eintrag);
            });
        });

        // Jeder Behälter wird zum Filter: "zeig mir, was in dieser Box liegt".
        var behaelterWahl = $('filterBehaelter');
        var gemerkt = behaelterWahl.value;
        behaelterWahl.innerHTML = '<option value="">Überall</option>'
            + '<option value="#behaelter">Nur Behälter</option>'
            + '<option value="#frei">Nicht eingeräumt</option>';
        moeglicheBehaelter(null).forEach(function (behaelter) {
            var eintrag = document.createElement('option');
            eintrag.value = behaelter.rfidUid;
            eintrag.textContent = 'In: ' + behaelter.name;
            behaelterWahl.appendChild(eintrag);
        });
        behaelterWahl.value = gemerkt;
        if (behaelterWahl.value !== gemerkt) {
            // Der Behälter ist verschwunden - dann wieder alles zeigen.
            behaelterWahl.value = '';
        }
    }

    function ort(artikel) {
        var teile = [artikel.standort, artikel.lagerort].filter(Boolean);
        var behaelter = nachKennung(artikel.behaelterKennung);
        if (behaelter) {
            teile.push('in ' + behaelter.name);
        } else if (artikel.behaelterKennung) {
            teile.push('in ' + artikel.behaelterKennung);
        }
        return teile.join(' · ');
    }

    /** Der Artikel mit dieser Kennung - so finden sich Behälter wieder. */
    function nachKennung(kennung) {
        if (!kennung) {
            return null;
        }
        for (var i = 0; i < stand.artikel.length; i++) {
            if (stand.artikel[i].rfidUid === kennung) {
                return stand.artikel[i];
            }
        }
        return null;
    }

    /** Was liegt in diesem Behälter? */
    function inhaltVon(kennung) {
        if (!kennung) {
            return [];
        }
        return stand.artikel.filter(function (eintrag) {
            return eintrag.behaelterKennung === kennung;
        });
    }

    /**
     * Behälter, in die dieser Artikel darf: nicht er selbst, und nichts, das
     * schon in ihm liegt - sonst stäke er in sich selbst.
     */
    function moeglicheBehaelter(artikel) {
        var tabu = {};
        if (artikel && artikel.rfidUid) {
            var offen = [artikel.rfidUid];
            while (offen.length) {
                var kennung = offen.pop();
                if (tabu[kennung]) {
                    continue;
                }
                tabu[kennung] = true;
                inhaltVon(kennung).forEach(function (kind) {
                    if (kind.rfidUid) {
                        offen.push(kind.rfidUid);
                    }
                });
            }
        }
        return stand.artikel.filter(function (eintrag) {
            return eintrag.istBehaelter && eintrag.rfidUid && !tabu[eintrag.rfidUid]
                && (!artikel || eintrag.id !== artikel.id);
        }).sort(function (a, b) {
            return a.name.localeCompare(b.name, 'de');
        });
    }

    function bestandZeichnen() {
        zahlenZeichnen();
        var liste = gefiltert();
        var koerper = $('bestandsliste');
        koerper.innerHTML = '';
        $('bestandLeer').hidden = liste.length > 0;
        liste.forEach(function (artikel) {
            var zeile = document.createElement('tr');
            var status = STATUS[artikel.status] || {text: artikel.status, farbe: 'var(--gedaempft)'};
            var bild = artikel.bildUrl ? '<img alt="" data-bild="'
                + text(artikel.bildUrl) + '">' : '';
            var marke = artikel.istBehaelter
                ? '<span class="behaelterzeichen" title="Behälter">▦</span> ' : '';
            zeile.innerHTML = '<td class="bild">' + bild + '</td>'
                + '<td>' + marke + text(artikel.name)
                + (artikel.istBehaelter
                    ? ' <span class="leer">' + text(artikel.behaelterArt || 'Behälter')
                      + ' · ' + inhaltVon(artikel.rfidUid).length + '</span>' : '')
                + '</td>'
                + '<td class="optional">' + text(artikel.kategorie || '') + '</td>'
                + '<td class="optional">' + text(ort(artikel)) + '</td>'
                + '<td class="optional"><code>' + text(artikel.rfidUid || '') + '</code></td>'
                + '<td><span class="plakette" style="background:' + status.farbe + '">'
                + status.text + '</span></td>';
            zeile.onclick = function () {
                artikelOeffnen(artikel);
            };
            koerper.appendChild(zeile);
        });
        [...koerper.querySelectorAll('img[data-bild]')].forEach(function (element) {
            bildZeigen(element, element.dataset.bild);
        });
    }

    var bildSpeicher = {};

    /**
     * Bilder haengen hinter der Anmeldung; ein einfaches <img src="..."> ginge
     * also leer aus. Deshalb holen wir sie mit Schluessel und merken sie uns.
     */
    function bildZeigen(element, adresse) {
        if (!adresse) {
            return;
        }
        if (bildSpeicher[adresse]) {
            element.src = bildSpeicher[adresse];
            return;
        }
        fetch(adresse, {headers: {'Authorization': 'Bearer ' + stand.token}})
            .then(function (antwort) {
                return antwort.ok ? antwort.blob() : null;
            })
            .then(function (inhalt) {
                if (!inhalt) {
                    return;
                }
                bildSpeicher[adresse] = URL.createObjectURL(inhalt);
                element.src = bildSpeicher[adresse];
            })
            .catch(function () {
            });
    }

    function text(wert) {
        var kasten = document.createElement('span');
        kasten.textContent = wert == null ? '' : wert;
        return kasten.innerHTML;
    }

    // ------------------------------------------------------------- Artikel

    function artikelOeffnen(artikel) {
        stand.offen = artikel ? JSON.parse(JSON.stringify(artikel)) : {
            name: '', status: 'vorhanden', scanWarnung: '1j'
        };
        var neu = !artikel;
        $('artikelTitel').textContent = neu ? 'Neuer Artikel' : artikel.name;
        $('feldName').value = stand.offen.name || '';
        $('feldBeschreibung').value = stand.offen.beschreibung || '';
        $('feldKategorie').value = stand.offen.kategorie || '';
        $('feldKennung').value = stand.offen.rfidUid || '';
        $('feldStandort').value = stand.offen.standort || '';
        $('feldLagerort').value = stand.offen.lagerort || '';
        $('feldIstBehaelter').checked = !!stand.offen.istBehaelter;
        $('feldBehaelterArt').value = stand.offen.behaelterArt || '';
        behaelterFelder(artikel);
        $('feldStatus').value = stand.offen.status || 'vorhanden';
        $('feldWarnung').value = stand.offen.scanWarnung || '1j';
        $('feldVerliehenAn').value = stand.offen.verliehenAn || '';
        $('feldRueckgabe').value = stand.offen.rueckgabeDatum
            ? new Date(stand.offen.rueckgabeDatum).toISOString().substring(0, 10) : '';
        verleihFelder();

        var bild = $('artikelBild');
        bild.style.display = stand.offen.bildUrl ? 'block' : 'none';
        bild.removeAttribute('src');
        bildZeigen(bild, stand.offen.bildUrl);
        $('knopfBildWeg').hidden = !stand.offen.bildUrl;
        $('feldBild').value = '';
        $('feldBild').disabled = neu;
        $('knopfLoeschen').hidden = neu;
        $('artikelProtokoll').innerHTML = '';

        [...document.querySelectorAll('#artikelFenster input, #artikelFenster select,'
            + ' #artikelFenster textarea')].forEach(function (feld) {
            if (feld.id !== 'feldBild') {
                feld.disabled = !darfBearbeiten();
            }
        });
        $('knopfSpeichern').hidden = !darfBearbeiten();
        $('knopfLoeschen').hidden = neu || !darfBearbeiten();

        if (!neu) {
            api('/teams/' + stand.teamId + '/artikel/' + artikel.id + '/protokoll')
                .then(zeigeProtokoll).catch(function () {
            });
        }
        $('artikelFenster').showModal();
    }

    function zeigeProtokoll(eintraege) {
        if (!eintraege || !eintraege.length) {
            return;
        }
        var liste = document.createElement('ul');
        liste.className = 'liste protokoll';
        eintraege.slice(0, 30).forEach(function (eintrag) {
            var zeile = document.createElement('li');
            var wann = new Date(eintrag.zeitpunkt).toLocaleString('de-DE');
            var was = eintrag.aktion + (eintrag.neuerWert ? ': ' + eintrag.neuerWert : '');
            zeile.textContent = wann + ' – ' + was
                + (eintrag.nutzer ? ' (' + eintrag.nutzer + ')' : '');
            liste.appendChild(zeile);
        });
        var kopf = document.createElement('h3');
        kopf.textContent = 'Verlauf';
        kopf.style.fontSize = '.95rem';
        $('artikelProtokoll').appendChild(kopf);
        $('artikelProtokoll').appendChild(liste);
    }

    function verleihFelder() {
        var verliehen = $('feldStatus').value === 'verliehen';
        $('feldVerliehenAnHuelle').style.display = verliehen ? '' : 'none';
        $('feldRueckgabeHuelle').style.display = verliehen ? '' : 'none';
    }

    /**
     * Auswahl "liegt in" fuellen und, wenn der Artikel selbst ein Behälter
     * ist, zeigen was drinsteckt. Ohne eigene Kennung kann er keiner sein -
     * die klebt am Möbel und wird beim Einräumen gescannt.
     */
    function behaelterFelder(artikel) {
        var wahl = $('feldLiegtIn');
        wahl.innerHTML = '<option value="">– nirgends –</option>';
        moeglicheBehaelter(artikel).forEach(function (behaelter) {
            var eintrag = document.createElement('option');
            eintrag.value = behaelter.rfidUid;
            eintrag.textContent = (behaelter.behaelterArt
                ? behaelter.behaelterArt + ': ' : '') + behaelter.name;
            wahl.appendChild(eintrag);
        });
        var liegtIn = stand.offen.behaelterKennung || '';
        if (liegtIn && !wahl.querySelector('option[value="' + CSS.escape(liegtIn) + '"]')) {
            // Der Behälter ist weg oder gesperrt - trotzdem anzeigen, was dasteht.
            var rest = document.createElement('option');
            rest.value = liegtIn;
            rest.textContent = liegtIn + ' (nicht gefunden)';
            wahl.appendChild(rest);
        }
        wahl.value = liegtIn;

        $('feldBehaelterArtHuelle').hidden = !$('feldIstBehaelter').checked;

        var kasten = $('behaelterInhalt');
        var inhalt = artikel && artikel.istBehaelter ? inhaltVon(artikel.rfidUid) : [];
        if (!artikel || !artikel.istBehaelter) {
            kasten.hidden = true;
            return;
        }
        kasten.hidden = false;
        if (!inhalt.length) {
            kasten.innerHTML = '<h4>Inhalt</h4><p class="leer">Noch nichts eingeräumt.'
                + ' Beim jeweiligen Artikel „liegt in" auf diesen Behälter stellen.</p>';
            return;
        }
        kasten.innerHTML = '<h4>Inhalt · ' + inhalt.length + '</h4><ul class="inhaltsliste">'
            + inhalt.map(function (eintrag) {
                return '<li><button type="button" class="leise" data-zu="'
                    + text(eintrag.id) + '">' + text(eintrag.name)
                    + (eintrag.istBehaelter ? ' ▸' : '') + '</button></li>';
            }).join('') + '</ul>';
        [...kasten.querySelectorAll('button[data-zu]')].forEach(function (knopf) {
            knopf.onclick = function () {
                var ziel = stand.artikel.filter(function (e) {
                    return e.id === knopf.dataset.zu;
                })[0];
                if (ziel) {
                    $('artikelFenster').close();
                    artikelOeffnen(ziel);
                }
            };
        });
    }

    function artikelSpeichern() {
        var eingabe = {
            name: $('feldName').value.trim(),
            beschreibung: $('feldBeschreibung').value.trim() || null,
            kategorie: $('feldKategorie').value.trim() || null,
            rfidUid: $('feldKennung').value.trim() || null,
            standort: $('feldStandort').value.trim() || null,
            lagerort: $('feldLagerort').value.trim() || null,
            istBehaelter: $('feldIstBehaelter').checked,
            behaelterArt: $('feldIstBehaelter').checked
                ? ($('feldBehaelterArt').value.trim() || null) : null,
            behaelterKennung: $('feldLiegtIn').value || null,
            status: $('feldStatus').value,
            scanWarnung: $('feldWarnung').value,
            verliehenAn: $('feldStatus').value === 'verliehen'
                ? ($('feldVerliehenAn').value.trim() || null) : null,
            rueckgabeDatum: $('feldStatus').value === 'verliehen' && $('feldRueckgabe').value
                ? new Date($('feldRueckgabe').value).getTime() : null,
            bildUrl: stand.offen.bildUrl || null,
            zuletztGescannt: stand.offen.zuletztGescannt || null
        };
        if (!eingabe.name) {
            melden('Bezeichnung fehlt');
            return;
        }
        var neu = !stand.offen.id;
        var pfad = '/teams/' + stand.teamId + '/artikel' + (neu ? '' : '/' + stand.offen.id);
        api(pfad, {method: neu ? 'POST' : 'PUT', body: JSON.stringify(eingabe)})
            .then(function (gespeichert) {
                return bildHochladen(gespeichert.id);
            })
            .then(function () {
                $('artikelFenster').close();
                melden('Gespeichert');
                bestandLaden();
            })
            .catch(function (fehler) {
                melden(fehler.message);
            });
    }

    /** Gibt es eine gewaehlte Datei, geht sie gleich hinterher. */
    function bildHochladen(artikelId) {
        var datei = $('feldBild').files[0];
        if (!datei) {
            return Promise.resolve();
        }
        return fetch('/api/v1/teams/' + stand.teamId + '/artikel/' + artikelId + '/bild', {
            method: 'POST',
            headers: {'Authorization': 'Bearer ' + stand.token, 'Content-Type': datei.type},
            body: datei
        }).then(function (antwort) {
            if (!antwort.ok) {
                throw new Error('Bild konnte nicht abgelegt werden (' + antwort.status + ')');
            }
        });
    }

    function artikelLoeschen() {
        if (!stand.offen.id || !confirm('„' + stand.offen.name + '" wirklich löschen?')) {
            return;
        }
        api('/teams/' + stand.teamId + '/artikel/' + stand.offen.id, {method: 'DELETE'})
            .then(function () {
                $('artikelFenster').close();
                melden('Gelöscht');
                bestandLaden();
            }).catch(function (fehler) {
                melden(fehler.message);
            });
    }

    function bildEntfernen() {
        if (!stand.offen.id) {
            return;
        }
        api('/teams/' + stand.teamId + '/artikel/' + stand.offen.id + '/bild', {method: 'DELETE'})
            .then(function () {
                stand.offen.bildUrl = null;
                $('artikelBild').style.display = 'none';
                $('knopfBildWeg').hidden = true;
                melden('Bild entfernt');
                bestandLaden();
            }).catch(function (fehler) {
                melden(fehler.message);
            });
    }

    // ----------------------------------------------------------- Kategorien

    function kategorienLaden() {
        api('/teams/' + stand.teamId + '/kategorien').then(function (liste) {
            stand.kategorien = liste || [];
            var flaeche = $('kategorienliste');
            flaeche.innerHTML = '';
            stand.kategorien.forEach(function (kategorie) {
                var zeile = document.createElement('li');
                var name = document.createElement('span');
                name.className = 'wachsen';
                name.textContent = kategorie.name;
                zeile.appendChild(name);
                if (darfBearbeiten()) {
                    zeile.appendChild(knopf('Umbenennen', 'leise', function () {
                        var neu = prompt('Neuer Name', kategorie.name);
                        if (neu && neu.trim()) {
                            api('/teams/' + stand.teamId + '/kategorien/' + kategorie.id, {
                                method: 'PUT',
                                body: JSON.stringify({
                                    name: neu.trim(),
                                    reihenfolge: kategorie.reihenfolge
                                })
                            }).then(kategorienLaden).catch(function (fehler) {
                                melden(fehler.message);
                            });
                        }
                    }));
                    zeile.appendChild(knopf('Löschen', 'gefahr', function () {
                        if (confirm('„' + kategorie.name + '" löschen?')) {
                            api('/teams/' + stand.teamId + '/kategorien/' + kategorie.id,
                                {method: 'DELETE'}).then(kategorienLaden)
                                .catch(function (fehler) {
                                    melden(fehler.message);
                                });
                        }
                    }));
                }
                flaeche.appendChild(zeile);
            });
            $('knopfKategorieAnlegen').disabled = !darfBearbeiten();
        }).catch(function (fehler) {
            melden(fehler.message);
        });
    }

    function knopf(beschriftung, art, tun) {
        var element = document.createElement('button');
        element.className = art;
        element.textContent = beschriftung;
        element.onclick = tun;
        return element;
    }

    // ----------------------------------------------------------- Mitglieder

    function mitgliederLaden() {
        api('/teams/' + stand.teamId + '/mitglieder').then(function (liste) {
            var flaeche = $('mitgliederliste');
            flaeche.innerHTML = '';
            (liste || []).forEach(function (mitglied) {
                var zeile = document.createElement('li');
                var wer = document.createElement('span');
                wer.className = 'wachsen';
                wer.innerHTML = '<strong>' + text(mitglied.name) + '</strong><small>'
                    + text(mitglied.email) + '</small>';
                zeile.appendChild(wer);

                var rolle = document.createElement('select');
                ['admin', 'lagerist', 'mitglied'].forEach(function (wert) {
                    var eintrag = document.createElement('option');
                    eintrag.value = wert;
                    eintrag.textContent = wert;
                    rolle.appendChild(eintrag);
                });
                rolle.value = mitglied.rolle;
                rolle.disabled = stand.rolle !== 'admin';
                rolle.onchange = function () {
                    api('/teams/' + stand.teamId + '/mitglieder/' + mitglied.benutzerId, {
                        method: 'PUT',
                        body: JSON.stringify({rolle: rolle.value})
                    }).then(function () {
                        melden('Rolle geändert');
                        mitgliederLaden();
                    }).catch(function (fehler) {
                        melden(fehler.message);
                        mitgliederLaden();
                    });
                };
                zeile.appendChild(rolle);

                if (stand.rolle === 'admin') {
                    zeile.appendChild(knopf('Entfernen', 'gefahr', function () {
                        if (confirm(mitglied.name + ' aus dem Lager entfernen?')) {
                            api('/teams/' + stand.teamId + '/mitglieder/' + mitglied.benutzerId,
                                {method: 'DELETE'}).then(mitgliederLaden)
                                .catch(function (fehler) {
                                    melden(fehler.message);
                                });
                        }
                    }));
                }
                flaeche.appendChild(zeile);
            });
            $('knopfEinladung').disabled = stand.rolle !== 'admin';
        }).catch(function (fehler) {
            melden(fehler.message);
        });
    }

    function einladung() {
        api('/teams/' + stand.teamId + '/einladung', {
            method: 'POST',
            body: JSON.stringify({rolle: $('einladungRolle').value})
        }).then(function (antwort) {
            $('einladungCode').textContent = 'Code: ' + antwort.code + ' (sieben Tage gültig)';
        }).catch(function (fehler) {
            melden(fehler.message);
        });
    }

    // ------------------------------------------------------------- Anfragen

    function anfragenLaden() {
        api('/teams/' + stand.teamId + '/anfragen').then(function (liste) {
            var offen = (liste || []).filter(function (anfrage) {
                return anfrage.status === 'offen';
            });
            var flaeche = $('anfragenliste');
            flaeche.innerHTML = '';
            $('anfragenLeer').hidden = offen.length > 0;
            offen.forEach(function (anfrage) {
                var zeile = document.createElement('li');
                var was = document.createElement('span');
                was.className = 'wachsen';
                was.innerHTML = '<strong>' + text(anfrage.artikelName) + '</strong><small>'
                    + text(anfrage.antragstellerName)
                    + (anfrage.nachricht ? ' – ' + text(anfrage.nachricht) : '') + '</small>';
                zeile.appendChild(was);
                if (darfBearbeiten()) {
                    zeile.appendChild(knopf('Zusagen', '', function () {
                        entscheiden(anfrage, true);
                    }));
                    zeile.appendChild(knopf('Ablehnen', 'leise', function () {
                        entscheiden(anfrage, false);
                    }));
                }
                flaeche.appendChild(zeile);
            });
        }).catch(function (fehler) {
            melden(fehler.message);
        });
    }

    function entscheiden(anfrage, genehmigt) {
        api('/teams/' + stand.teamId + '/anfragen/' + anfrage.id + '/entscheiden', {
            method: 'POST',
            body: JSON.stringify({genehmigt: genehmigt, antwort: ''})
        }).then(function () {
            melden(genehmigt ? 'Zugesagt' : 'Abgelehnt');
            anfragenLaden();
            bestandLaden();
        }).catch(function (fehler) {
            melden(fehler.message);
        });
    }

    // --------------------------------------------------------------- System

    function systemLaden() {
        serverLaden(false);
        appLaden();
    }

    /** Stand des Servers; mit nachsehen=true fragt er bei der Quelle nach. */
    function serverLaden(nachsehen) {
        var aufruf = nachsehen
            ? api('/aktualisierung/pruefen', {method: 'POST'})
            : api('/aktualisierung');
        aufruf.then(function (stand) {
            $('serverAngaben').innerHTML =
                zeile('Version', text(stand.version || '–'))
                + zeile('Gebaut am', stand.gebautAm
                    ? new Date(stand.gebautAm).toLocaleString('de-DE') : '–')
                + zeile('Laeuft aus', stand.ausVolume
                    ? 'nachgeladener Fassung' : 'dem Abbild')
                + (stand.neuesteVersion
                    ? zeile('Verfügbar', text(stand.neuesteVersion)) : '')
                + zeile('Sieht selbst nach', stand.beobachtet
                    ? 'ja, im Minutentakt'
                    : 'nein – kein Zugangsschlüssel oder ausgeschaltet');

            var neuer = stand.neuerVorhanden === true;
            $('knopfServerEinspielen').hidden = !neuer || !stand.selbstMoeglich;
            if (neuer) {
                $('knopfServerEinspielen').textContent =
                    'Auf ' + stand.neuesteVersion + ' aktualisieren und neu starten';
            }
            $('knopfServerZurueck').hidden = !stand.rueckwegMoeglich;
            $('serverHinweis').textContent = stand.hinweis || (nachsehen && !neuer
                ? 'Der Server ist auf dem neuesten Stand.' : '');
        }).catch(function (fehler) {
            $('serverHinweis').textContent = fehler.message;
        });
    }

    /** Nach dem Neustart warten, bis der Server wieder antwortet. */
    function aufNeustartWarten(versuche) {
        if (versuche > 45) {
            $('serverHinweis').textContent =
                'Der Server meldet sich nicht zurück – bitte den Container ansehen.';
            return;
        }
        setTimeout(function () {
            fetch('/api/v1/status').then(function (antwort) {
                return antwort.ok ? antwort.json() : Promise.reject(new Error('noch nicht'));
            }).then(function (status) {
                $('serverHinweis').textContent = 'Wieder da – Version ' + status.version;
                serverLaden(false);
            }).catch(function () {
                aufNeustartWarten(versuche + 1);
            });
        }, 2000);
    }

    function serverWechseln(pfad, frage) {
        if (!confirm(frage)) {
            return;
        }
        $('knopfServerEinspielen').disabled = true;
        $('knopfServerZurueck').disabled = true;
        $('serverHinweis').textContent = 'Wird geholt …';
        api(pfad, {method: 'POST'}).then(function (antwort) {
            $('serverHinweis').textContent = antwort.hinweis || 'Neustart läuft …';
            if (antwort.gewechselt) {
                aufNeustartWarten(0);
            }
        }).catch(function (fehler) {
            $('serverHinweis').textContent = fehler.message;
        }).finally(function () {
            $('knopfServerEinspielen').disabled = false;
            $('knopfServerZurueck').disabled = false;
        });
    }

    function appLaden() {
        api('/app').then(function (app) {
            var zeilen = '';
            ['aktuell', 'vorher'].forEach(function (fach) {
                var fassung = app[fach];
                if (!fassung) {
                    zeilen += zeile(fach === 'aktuell' ? 'Aktuell' : 'Vorherige',
                        'liegt nicht bereit');
                    return;
                }
                var name = fassung.versionName || fassung.versionCode || '?';
                zeilen += zeile(fach === 'aktuell' ? 'Aktuell' : 'Vorherige',
                    text(name) + ' · ' + Math.round((fassung.groesse || 0) / 1048576)
                    + ' MB · <a class="knopf leise" href="' + text(fassung.adresse)
                    + '">Laden</a>');
            });
            zeilen += zeile('Holt sich selbst', app.holtSelbst ? 'ja'
                : 'nein – ohne Zugangsschlüssel');
            if (app.hinweis) {
                zeilen += zeile('Hinweis', text(app.hinweis));
            }
            $('appAngaben').innerHTML = zeilen;
        }).catch(function (fehler) {
            $('appAngaben').innerHTML = zeile('Fehler', text(fehler.message));
        });
    }

    function zeile(begriff, wert) {
        return '<dt>' + begriff + '</dt><dd>' + wert + '</dd>';
    }

    function zeigeAlleLager(alle) {
        $('karteLager').hidden = false;
        var flaeche = $('lagerliste');
        flaeche.innerHTML = '';
        alle.forEach(function (team) {
            var zeile = document.createElement('li');
            var was = document.createElement('span');
            was.className = 'wachsen';
            was.innerHTML = '<strong>' + text(team.name) + '</strong><small>'
                + (team.anzahlArtikel || 0) + ' Artikel · '
                + (team.anzahlMitglieder || 0) + ' Mitglieder</small>';
            zeile.appendChild(was);
            zeile.appendChild(knopf('Öffnen', 'leise', function () {
                stand.teamId = team.id;
                localStorage.setItem('tagstock-team', team.id);
                $('teamWahl').value = team.id;
                teamLaden();
                bereichWechseln('bestand');
            }));
            flaeche.appendChild(zeile);
        });
    }

    // -------------------------------------------------------------- Bereiche

    function bereichWechseln(name) {
        ['bestand', 'kategorien', 'mitglieder', 'anfragen', 'system'].forEach(function (bereich) {
            var gross = bereich.charAt(0).toUpperCase() + bereich.substring(1);
            $('bereich' + gross).hidden = bereich !== name;
        });
        [...document.querySelectorAll('nav button')].forEach(function (knopf) {
            knopf.classList.toggle('aktiv', knopf.dataset.bereich === name);
        });
        if (name === 'mitglieder') {
            mitgliederLaden();
        } else if (name === 'anfragen') {
            anfragenLaden();
        } else if (name === 'system') {
            systemLaden();
        } else if (name === 'kategorien') {
            kategorienLaden();
        }
    }

    // ---------------------------------------------------------------- Start

    document.addEventListener('DOMContentLoaded', function () {
        $('knopfAnmelden').onclick = anmelden;
        $('anmeldePasswort').onkeydown = function (ereignis) {
            if (ereignis.key === 'Enter') {
                anmelden();
            }
        };
        $('knopfAbmelden').onclick = abmelden;
        $('teamWahl').onchange = function () {
            stand.teamId = $('teamWahl').value;
            localStorage.setItem('tagstock-team', stand.teamId);
            teamLaden();
        };
        [...document.querySelectorAll('nav button')].forEach(function (knopf) {
            knopf.onclick = function () {
                bereichWechseln(knopf.dataset.bereich);
            };
        });
        $('suche').oninput = bestandZeichnen;
        $('filterKategorie').onchange = bestandZeichnen;
        $('filterStandort').onchange = bestandZeichnen;
        $('filterBehaelter').onchange = bestandZeichnen;
        $('feldIstBehaelter').onchange = function () {
            $('feldBehaelterArtHuelle').hidden = !$('feldIstBehaelter').checked;
        };
        $('knopfNeu').onclick = function () {
            artikelOeffnen(null);
        };
        $('feldStatus').onchange = verleihFelder;
        $('knopfSpeichern').onclick = artikelSpeichern;
        $('knopfLoeschen').onclick = artikelLoeschen;
        $('knopfBildWeg').onclick = bildEntfernen;
        $('knopfAbbrechen').onclick = function () {
            $('artikelFenster').close();
        };
        $('knopfKategorieAnlegen').onclick = function () {
            var name = $('neueKategorie').value.trim();
            if (!name) {
                return;
            }
            api('/teams/' + stand.teamId + '/kategorien', {
                method: 'POST',
                body: JSON.stringify({name: name, reihenfolge: stand.kategorien.length})
            }).then(function () {
                $('neueKategorie').value = '';
                kategorienLaden();
            }).catch(function (fehler) {
                melden(fehler.message);
            });
        };
        $('knopfEinladung').onclick = einladung;
        $('knopfServerPruefen').onclick = function () {
            $('serverHinweis').textContent = 'Wird nachgesehen …';
            serverLaden(true);
        };
        $('knopfServerEinspielen').onclick = function () {
            serverWechseln('/aktualisierung/einspielen',
                'Neue Serverfassung holen und neu starten?\n\n'
                + 'Vorher wird die Datenbank gesichert. Der Server ist ein paar '
                + 'Sekunden nicht erreichbar.\n\n'
                + 'Wichtig: Der Container muss auf „unless-stopped" stehen, sonst '
                + 'bleibt er nach dem Beenden aus.');
        };
        $('knopfServerZurueck').onclick = function () {
            serverWechseln('/aktualisierung/zurueck',
                'Zurück auf die vorherige Serverfassung? Der Server startet dafür neu.');
        };
        $('knopfAppPruefen').onclick = function () {
            $('knopfAppPruefen').disabled = true;
            api('/app/pruefen', {method: 'POST'}).then(function () {
                melden('Nachgesehen');
                appLaden();
            }).catch(function (fehler) {
                melden(fehler.message);
            }).finally(function () {
                $('knopfAppPruefen').disabled = false;
            });
        };
        starten();
    });
})();
