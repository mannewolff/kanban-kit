package org.mwolff.manban.entscheidungskette.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Aktivitaet;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.LabelEreignis;

/** Die Freigabe zur Umsetzung am Paket (Issue #2116, Kriterium 8, Plan #2108 E11). */
final class Freigaben {

  private static final String HERKUNFT_KIT = "TOKEN";
  private static final String READY = "READY";
  private static final String IN_PROGRESS = "IN_PROGRESS";
  private static final Set<Sache> FREIGEBENDE_SACHEN =
      Set.of(Sache.ZIEL_UMSETZUNG, Sache.ZIEL_PUSH_VORBEREITET, Sache.DURCHZIEHEN);

  private final List<LabelEreignis> labelEreignisse;
  private final boolean fremdesBoard;

  /**
   * Mit den Label-Ereignissen der Kette, nach Zeitpunkt.
   *
   * @param fremdesBoard ob das Board des Projekts in einem fremden System liegt (Plan #2152 A11)
   */
  Freigaben(List<LabelEreignis> labelEreignisse, boolean fremdesBoard) {
    this.labelEreignisse = labelEreignisse;
    this.fremdesBoard = fremdesBoard;
  }

  /**
   * Der Zug, der die Umsetzung freigibt: der letzte nach Ready, der kein Rückzug aus In Progress
   * ist. Züge ohne bestimmbare Spalte stehen nicht in {@code zuege}.
   */
  static @Nullable Aktivitaet zugNachReady(List<Aktivitaet> zuege) {
    Aktivitaet freigabe = null;
    String vorher = null;
    for (Aktivitaet zug : zuege) {
      if (READY.equals(zug.spaltenSchluessel()) && !IN_PROGRESS.equals(vorher)) {
        freigabe = zug;
      }
      vorher = zug.spaltenSchluessel();
    }
    return freigabe;
  }

  /**
   * Hat ein Lauf das Paket nach Ready gezogen und liegt am Fachplan oder Plan davor ein Ziel ab
   * „Umsetzung“ oder das Durchziehen-Kennzeichen, ist das jüngste davon die Freigabe und der Zug
   * ihre Ausführung; sonst ist der Zug selbst die Freigabe. Bei fremdem Board gilt jeder Zug des
   * Kit-Kontos als Zug eines Laufs, weil das fremde System keine Laufkennung festhält (Plan #2152
   * A11).
   *
   * @param freigebende Fachplan und Plan der Kette
   */
  Glied glied(Karte paket, @Nullable Aktivitaet zug, List<Karte> freigebende) {
    if (zug == null) {
      return Glied.ohneAngaben(Gliedart.FREIGABE, Zustand.NICHT_ERFASST, Verweis.KEINER, Set.of());
    }
    Akteur ausfuehrer =
        Akteur.nachHerkunft(
            zug.herkunft(), zug.person(), zug.tokenName(), zug.agent(), zug.externAkteur());
    boolean durchLauf =
        HERKUNFT_KIT.equals(zug.herkunft()) && (zug.laufStart() != null || fremdesBoard);
    Optional<Kennzeichen> kennzeichen =
        durchLauf ? kennzeichen(zug, freigebende) : Optional.empty();
    if (kennzeichen.isEmpty()) {
      return new Glied(
          Gliedart.FREIGABE,
          Zustand.ERFASST,
          ausfuehrer,
          ausfuehrer,
          zug.zeitpunkt(),
          null,
          Kartenglieder.verweis(paket),
          Set.of(),
          null,
          new Freigabeangaben(Freigabefall.ZUG_NACH_READY, zug.zeitpunkt()));
    }
    LabelEreignis e = kennzeichen.get().ereignis();
    return new Glied(
        Gliedart.FREIGABE,
        Zustand.ERFASST,
        Akteur.nachHerkunft(e.herkunft(), e.person(), e.tokenName(), null, e.externAkteur()),
        ausfuehrer,
        e.zeitpunkt(),
        null,
        Kartenglieder.verweis(kennzeichen.get().karte()),
        Set.of(),
        null,
        new Freigabeangaben(Freigabefall.KENNZEICHEN, zug.zeitpunkt()));
  }

  private Optional<Kennzeichen> kennzeichen(Aktivitaet zug, List<Karte> freigebende) {
    Kennzeichen juengstes = null;
    for (LabelEreignis e : labelEreignisse) {
      Optional<Karte> karte = freigebende.stream().filter(k -> k.id() == e.kartenId()).findFirst();
      boolean freigebend =
          KitNamen.STANDARD.sache(e.labelName()).filter(FREIGEBENDE_SACHEN::contains).isPresent();
      if (karte.isPresent() && freigebend && !e.zeitpunkt().isAfter(zug.zeitpunkt())) {
        juengstes = new Kennzeichen(e, karte.get());
      }
    }
    return Optional.ofNullable(juengstes);
  }

  private record Kennzeichen(LabelEreignis ereignis, Karte karte) {}
}
