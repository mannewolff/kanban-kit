package org.mwolff.manban.entscheidungskette.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Aktivitaet;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Bezug;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.LabelEreignis;

/**
 * Der eigene Abschnitt der geöffneten Karte und das zuletzt erreichte Glied eines Nachfahren
 * (Issues #2115, #2116, Plan #2108 E10, E11, E15, E17, E18). Am Paket folgen auf Herkunft und
 * Prüfrunden Issue-Review, Freigabe, Umsetzung, Code-Review, Übertragung und Veröffentlichung.
 *
 * <p>Züge je Karte nach Zeitpunkt, bei Gleichstand in der Folge der Eingabe; keine Uhr.
 */
final class Paketglieder {

  private final KettenEingabe eingabe;
  private final Kartenglieder kartenglieder;
  private final Freigaben freigaben;
  private final Paketmeldungen meldungen;
  private final Auslieferungen auslieferungen;
  private final Map<Long, List<Aktivitaet>> zuege = new HashMap<>();

  Paketglieder(KettenEingabe eingabe, Kartenglieder kartenglieder) {
    this.eingabe = eingabe;
    this.kartenglieder = kartenglieder;
    this.freigaben =
        new Freigaben(
            eingabe.labelEreignisse().stream()
                .sorted(Comparator.comparing(LabelEreignis::zeitpunkt))
                .toList());
    this.meldungen = new Paketmeldungen(eingabe.kommentare());
    this.auslieferungen = new Auslieferungen(meldungen);
    eingabe.aktivitaeten().stream()
        .filter(a -> a.spaltenSchluessel() != null)
        .sorted(Comparator.comparing(Aktivitaet::zeitpunkt))
        .forEach(a -> zuege.computeIfAbsent(a.kartenId(), id -> new ArrayList<>()).add(a));
  }

  /**
   * Herkunft und Prüfrunden; am Paket dazu die Glieder nach der Planung.
   *
   * @param freigebende Fachplan und Plan der Kette, an denen ein Kennzeichen die Freigabe sein kann
   */
  Abschnitt abschnitt(Dokumentart art, Karte karte, List<Karte> freigebende) {
    Abschnitt dokument = kartenglieder.abschnitt(art, karte);
    if (art != Dokumentart.PAKET) {
      return dokument;
    }
    List<Glied> alle = new ArrayList<>(dokument.glieder());
    alle.addAll(glieder(karte, freigebende));
    return new Abschnitt(art, List.copyOf(alle));
  }

  /**
   * Das am weitesten erreichte Glied (Plan #2108 E15). Am Paket zählen ein zurückgezogener Versuch,
   * eine nicht übertragene Übertragung und eine nicht veröffentlichte Veröffentlichung nicht.
   */
  private Gliedart zuletztErreicht(Dokumentart art, Karte karte) {
    Gliedart erreicht = kartenglieder.zuletztErreicht(karte);
    if (art != Dokumentart.PAKET) {
      return erreicht;
    }
    for (Glied g : glieder(karte, List.of())) {
      if (g.erreicht() && g.art().compareTo(erreicht) > 0) {
        erreicht = g.art();
      }
    }
    return erreicht;
  }

  /**
   * Ein Nachfahre am Fachplan oder Plan mit Spalte und zuletzt erreichtem Glied (Kriterium 3); ohne
   * Karte im Bestand nur mit dem, was bekannt ist.
   */
  Nachfahre nachfahre(Bezug b) {
    Karte k = b.karte();
    if (k == null) {
      Glied ohne = Glied.ohneKarte(Gliedart.HERKUNFT, b);
      return new Nachfahre(ohne.zustand(), ohne.quelle(), null, null, null, ohne.hinweise());
    }
    return new Nachfahre(
        Zustand.ERFASST,
        Kartenglieder.verweis(k),
        k.spalte(),
        k.spaltenSchluessel(),
        zuletztErreicht(Dokumentart.von(k.art()), k),
        Set.of());
  }

  private List<Glied> glieder(Karte paket, List<Karte> freigebende) {
    List<Aktivitaet> zuegeDerKarte = zuege.getOrDefault(paket.id(), List.of());
    Aktivitaet zug = Freigaben.zugNachReady(zuegeDerKarte);
    List<Glied> glieder =
        new ArrayList<>(
            IssueReview.glieder(
                kartenglieder,
                paket,
                eingabe.reviewVorReady(),
                zug == null ? null : zug.zeitpunkt()));
    glieder.add(freigaben.glied(paket, zug, freigebende));
    glieder.addAll(
        Umsetzungsversuche.glieder(
            paket, zuegeDerKarte, meldungen.von(paket, Meldungsart.ABSCHLUSSBERICHT)));
    glieder.addAll(auslieferungen.codeReviews(paket));
    glieder.addAll(auslieferungen.uebertragungen(paket));
    glieder.addAll(auslieferungen.veroeffentlichungen(paket));
    return glieder;
  }
}
