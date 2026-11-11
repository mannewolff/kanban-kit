package org.mwolff.manban.entscheidungskette.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.common.KitMeldungen.Wert;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;
import org.mwolff.manban.entscheidungskette.domain.Paketmeldungen.Gelesen;

/**
 * Code-Review, Übertragung und Veröffentlichung am Paket (Issue #2116, Kriterien 4, 10, 11, 12): je
 * Meldung ein Glied, auch bei Wiederholung; ein späterer Erfolg verdeckt keinen früheren
 * Fehlschlag.
 */
final class Auslieferungen {

  private final Paketmeldungen meldungen;

  Auslieferungen(Paketmeldungen meldungen) {
    this.meldungen = meldungen;
  }

  /** Die Code-Reviews mit der Zahl ihrer Funde. */
  List<Glied> codeReviews(Karte paket) {
    return jeMeldung(
        paket,
        Meldungsart.CODE_REVIEW,
        Gliedart.CODE_REVIEW,
        (k, meldung) ->
            new Glied(
                Gliedart.CODE_REVIEW,
                Zustand.ERFASST,
                null,
                Akteur.werkzeug(k.tokenName(), k.agent()),
                k.zeitpunkt(),
                null,
                Kartenglieder.quelle(k, paket),
                Set.of(),
                null,
                (CodeReview) meldung));
  }

  /** Die Übertragungen, je mit dem nachgetragenen Ergebnis des Build-Diensts. */
  List<Glied> uebertragungen(Karte paket) {
    List<BuildNachtrag> nachtraege =
        meldungen.von(paket, Meldungsart.BUILD_NACHTRAG).stream()
            .map(Gelesen::meldung)
            .filter(BuildNachtrag.class::isInstance)
            .map(BuildNachtrag.class::cast)
            .toList();
    return jeMeldung(
        paket,
        Meldungsart.UEBERTRAGUNG,
        Gliedart.UEBERTRAGUNG,
        (k, meldung) -> {
          Uebertragung u = nachgetragen((Uebertragung) meldung, nachtraege);
          return glied(
              Gliedart.UEBERTRAGUNG,
              k,
              paket,
              u.freigegebenVon(),
              u.ausgefuehrtVon(),
              u.ausgang(),
              u);
        });
  }

  /** Die Veröffentlichungen mit Version und wörtlichem Ausgang (Plan #2108 E15). */
  List<Glied> veroeffentlichungen(Karte paket) {
    return jeMeldung(
        paket,
        Meldungsart.VEROEFFENTLICHUNG,
        Gliedart.VEROEFFENTLICHUNG,
        (k, meldung) -> {
          Veroeffentlichung v = (Veroeffentlichung) meldung;
          return glied(
              Gliedart.VEROEFFENTLICHUNG,
              k,
              paket,
              v.freigegebenVon(),
              v.ausgefuehrtVon(),
              v.ausgang(),
              v);
        });
  }

  /**
   * {@code ausstehend} bleibt ohne Frist stehen, bis ein Nachtrag mit gleichem {@code Versuch:}
   * oder {@code Commit:} vorliegt; der jüngste gilt (Kriterium 11).
   */
  private static Uebertragung nachgetragen(Uebertragung u, List<BuildNachtrag> nachtraege) {
    if (!u.buildDienst().ist(Wert.AUSSTEHEND)) {
      return u;
    }
    BuildNachtrag passend = null;
    for (BuildNachtrag n : nachtraege) {
      if (gleich(n.versuch(), u.versuch()) || gleich(n.commit(), u.commit())) {
        passend = n;
      }
    }
    if (passend == null) {
      return u;
    }
    return new Uebertragung(
        u.versuch(),
        u.commit(),
        u.basis(),
        u.weg(),
        u.prueflauf(),
        passend.buildDienst(),
        Objects.requireNonNullElse(passend.quelle(), u.quelle()),
        u.ausgang(),
        u.freigegebenVon(),
        u.ausgefuehrtVon());
  }

  private static boolean gleich(@Nullable String a, @Nullable String b) {
    return a != null && a.equals(b);
  }

  /**
   * Freigabe durch den genannten Menschen, Ausführung durch das Modell, Ausgang wörtlich; ohne
   * Zeitpunkt der Ausführung gilt der des Kommentars (Kriterium 10).
   */
  private static Glied glied(
      Gliedart art,
      Kommentar k,
      Karte paket,
      Angabe freigegeben,
      Angabe ausgefuehrt,
      @Nullable String ausgang,
      Einzelheiten einzelheiten) {
    String freigeber = freigegeben.wert();
    String ausfuehrer = ausgefuehrt.wert();
    return new Glied(
        art,
        Zustand.ERFASST,
        freigeber == null ? null : Akteur.menschGenannt(freigeber),
        ausfuehrer == null ? null : Akteur.modell(ausfuehrer),
        Objects.requireNonNullElse(ausgefuehrt.zeitpunkt(), k.zeitpunkt()),
        ausgang,
        Kartenglieder.quelle(k, paket),
        Set.of(),
        null,
        einzelheiten);
  }

  /**
   * Je Meldung ein Glied, eine von Hand geänderte als „nicht erfasst“ mit Hinweis; ohne Meldung ein
   * Glied „nicht erfasst“.
   */
  private List<Glied> jeMeldung(
      Karte paket,
      Meldungsart art,
      Gliedart gliedart,
      BiFunction<Kommentar, KitMeldung, Glied> bau) {
    List<Gelesen> gelesen = meldungen.von(paket, art);
    if (gelesen.isEmpty()) {
      return List.of(Glied.ohneAngaben(gliedart, Zustand.NICHT_ERFASST, Verweis.KEINER, Set.of()));
    }
    return gelesen.stream()
        .map(
            g -> {
              KitMeldung meldung = g.meldung();
              return meldung == null
                  ? Glied.ohneAngaben(
                      gliedart,
                      Zustand.NICHT_ERFASST,
                      Kartenglieder.quelle(g.kommentar(), paket),
                      Set.of(Hinweis.VON_HAND_GEAENDERT))
                  : bau.apply(g.kommentar(), meldung);
            })
        .toList();
  }
}
