package org.mwolff.manban.entscheidungskette.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Aktivitaet;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;

/**
 * Die Glieder eines Dokuments, das im Bestand liegt: Herkunft und Prüfrunden mit Einarbeitung (Plan
 * #2108 E3, E4, E9). Kommentare je Karte nach Zeitpunkt und ID, keine Uhr (E17).
 */
final class Kartenglieder {

  private static final String HERKUNFT_KIT = "TOKEN";
  private static final String TYP_ANLAGE = "CREATED";
  private static final Comparator<Kommentar> FOLGE =
      Comparator.comparing(Kommentar::zeitpunkt).thenComparingLong(Kommentar::id);

  private final Map<Long, Aktivitaet> anlagen = new HashMap<>();
  private final Map<Long, List<Dokumentmeldungen.Gelesen>> meldungen = new HashMap<>();

  Kartenglieder(List<Aktivitaet> aktivitaeten, List<Kommentar> kommentare) {
    for (Aktivitaet a : aktivitaeten) {
      if (TYP_ANLAGE.equals(a.typ())) {
        anlagen.merge(
            a.kartenId(), a, (alt, neu) -> neu.zeitpunkt().isBefore(alt.zeitpunkt()) ? neu : alt);
      }
    }
    kommentare.stream()
        .sorted(FOLGE)
        .forEach(
            k ->
                Dokumentmeldungen.lies(k)
                    .ifPresent(
                        g ->
                            meldungen
                                .computeIfAbsent(k.kartenId(), id -> new ArrayList<>())
                                .add(g)));
  }

  /** Herkunft und Prüfrunden des Dokuments; am Paket ohne das Issue-Review. */
  Abschnitt abschnitt(Dokumentart art, Karte karte) {
    List<Glied> glieder = new ArrayList<>();
    glieder.add(anlage(Gliedart.HERKUNFT, karte, null));
    glieder.addAll(runden(karte));
    return new Abschnitt(art, List.copyOf(glieder));
  }

  /** Ein Glied aus der Anlage der Karte: Idee oder Herkunft. */
  Glied anlage(Gliedart art, Karte karte, @Nullable String ergebnis) {
    Aktivitaet anlage = anlagen.get(karte.id());
    return new Glied(
        art,
        Zustand.ERFASST,
        null,
        anlage == null ? null : akteur(anlage, karte),
        anlage == null ? null : anlage.zeitpunkt(),
        ergebnis,
        verweis(karte),
        Set.of(),
        null,
        null);
  }

  /** Das letzte erfasste Glied des Dokuments (Plan #2108 E15). */
  Gliedart zuletztErreicht(Karte karte) {
    Gliedart erreicht = Gliedart.HERKUNFT;
    for (Glied runde : runden(karte)) {
      Pruefung pruefung = Objects.requireNonNull(runde.pruefung());
      if (runde.zustand() == Zustand.ERFASST) {
        erreicht = Gliedart.PRUEFRUNDE;
      }
      if (pruefung.einarbeitung().zustand() == Zustand.ERFASST) {
        erreicht = Gliedart.EINARBEITUNG;
      }
    }
    return erreicht;
  }

  static Verweis verweis(Karte karte) {
    return new Verweis(karte.id(), karte.nummer(), karte.titel(), !karte.archiviert(), null);
  }

  /** Die Runden des Issue-Reviews am Paket, je mit ihrer Einarbeitung (Issue #2116). */
  List<Glied> issueReviews(Karte karte) {
    return runden(karte, Gliedart.ISSUE_REVIEW, Set.of(Meldungsart.ISSUE_REVIEW));
  }

  private List<Glied> runden(Karte karte) {
    return runden(karte, Gliedart.PRUEFRUNDE, Dokumentmeldungen.RUNDENARTEN);
  }

  private List<Glied> runden(Karte karte, Gliedart gliedart, Set<Meldungsart> arten) {
    List<Dokumentmeldungen.Gelesen> alle = meldungen.getOrDefault(karte.id(), List.of());
    return alle.stream()
        .filter(g -> arten.contains(g.art()))
        .map(g -> runde(gliedart, g, einarbeitung(alle, g.runde(), karte), karte))
        .toList();
  }

  private static Glied runde(
      Gliedart gliedart, Dokumentmeldungen.Gelesen g, Glied einarbeitung, Karte karte) {
    Pruefrunde gelesen = g.pruefrunde();
    if (gelesen == null) {
      return new Glied(
          gliedart,
          Zustand.NICHT_ERFASST,
          null,
          null,
          null,
          null,
          quelle(g.kommentar(), karte),
          Set.of(Hinweis.VON_HAND_GEAENDERT),
          new Pruefung(g.art(), g.runde(), List.of(), null, einarbeitung),
          null);
    }
    return meldung(
        gliedart,
        g.kommentar(),
        karte,
        new Pruefung(g.art(), g.runde(), gelesen.reviewer(), gelesen.funde(), einarbeitung));
  }

  private static Glied einarbeitung(List<Dokumentmeldungen.Gelesen> alle, int runde, Karte karte) {
    return alle.stream()
        .filter(g -> g.art() == Meldungsart.EINARBEITUNG && g.runde() == runde)
        .findFirst()
        .map(
            g ->
                g.vonHand()
                    ? Glied.ohneAngaben(
                        Gliedart.EINARBEITUNG,
                        Zustand.NICHT_ERFASST,
                        quelle(g.kommentar(), karte),
                        Set.of(Hinweis.VON_HAND_GEAENDERT))
                    : meldung(Gliedart.EINARBEITUNG, g.kommentar(), karte, null))
        .orElseGet(
            () ->
                Glied.ohneAngaben(
                    Gliedart.EINARBEITUNG, Zustand.NICHT_ERFASST, Verweis.KEINER, Set.of()));
  }

  private static Glied meldung(
      Gliedart art, Kommentar k, Karte karte, @Nullable Pruefung pruefung) {
    return new Glied(
        art,
        Zustand.ERFASST,
        null,
        Akteur.werkzeug(k.tokenName(), k.agent()),
        k.zeitpunkt(),
        null,
        quelle(k, karte),
        Set.of(),
        pruefung,
        null);
  }

  static Verweis quelle(Kommentar k, Karte karte) {
    return new Verweis(karte.id(), karte.nummer(), null, !karte.archiviert(), k.id());
  }

  /**
   * Nur über ein Token angelegte Karten nennen ihr Modell (Plan #2108 E6); fehlt die Zeile, steht
   * das Werkzeug da. In der Oberfläche angelegte Karten nennen die Person.
   */
  private static @Nullable Akteur akteur(Aktivitaet anlage, Karte karte) {
    if (HERKUNFT_KIT.equals(anlage.herkunft())) {
      return Dokumentzeilen.modell(karte.beschreibung())
          .map(Akteur::modell)
          .orElseGet(() -> Akteur.werkzeug(anlage.tokenName(), anlage.agent()));
    }
    Long person = anlage.person();
    return person == null ? null : Akteur.mensch(person);
  }
}
