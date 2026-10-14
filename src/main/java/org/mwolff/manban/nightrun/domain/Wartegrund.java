package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.ReadyBewertung.Eintrag;
import org.mwolff.manban.nightrun.domain.ReadyBewertung.Grund;
import org.mwolff.manban.nightrun.domain.RunnerLage.AktiverLauf;
import org.mwolff.manban.nightrun.domain.RunnerLage.LaufZustand;

/**
 * Warum ein Paket in Ready wartet (Issue #1728, Plan #1724 E7 bis E11).
 *
 * <p>Zwei Quellen, keine eigene Regel: Die Gründe der Karte sind die der jüngsten Bewertung des
 * Runners (E7) — das Board bildet seine Gates nicht nach, sonst gäbe es zwei Wahrheiten (#191). Was
 * das Board selbst beiträgt, ist der Laufzustand aus der {@link RunnerLage} (E8): Er ändert sich
 * während eines Laufs, die Bewertung nicht.
 *
 * @param laufgruende die Gründe aus dem Laufzustand, in der Reihenfolge der Läufe und ihrer Stufen
 * @param kartengruende die Gründe der Karte aus der Bewertung, wie gemeldet
 * @param paketeDavor wie viele startbare Pakete vor der Karte stehen ({@code position − 1}, E11);
 *     {@code null}, wenn die Karte keine Position trägt oder nicht bewertet ist
 * @param vermerke Aussagen über das Alter der Bewertung (E9) — keine Wartegründe
 * @param startbereit ob kein Hinderungsgrund festgestellt ist (E10)
 */
public record Wartegrund(
    List<Laufgrund> laufgruende,
    List<Grund> kartengruende,
    @Nullable Integer paketeDavor,
    List<Vermerk> vermerke,
    boolean startbereit) {

  public Wartegrund {
    laufgruende = List.copyOf(laufgruende);
    kartengruende = List.copyOf(kartengruende);
    vermerke = List.copyOf(vermerke);
  }

  /** Die Gründe, die das Board aus dem Laufzustand ableitet (E8). */
  public enum LaufgrundArt {

    /** Keine Umsetzungsnacht läuft und keine ist verstummt. */
    KEINE_UMSETZUNGSNACHT,

    /** Eine Umsetzungsnacht ist verstummt, oder ein verstummter Lauf hielt die Umsetzung. */
    VERSTUMMT,

    /** Ein laufender Lauf hält die Umsetzung für eine andere Karte. */
    BELEGT
  }

  /** Aussagen über das Wissen des Boards (E9). */
  public enum VermerkArt {

    /** Die Karte steht nicht in der jüngsten Bewertung, oder es gibt keine. */
    NICHT_BEWERTET,

    /** Die Karte wurde nach der jüngsten Bewertung geändert. */
    SEIT_BEWERTUNG_GEAENDERT
  }

  /**
   * Ein Grund aus dem Laufzustand.
   *
   * @param art welcher Grund
   * @param seit bei {@link LaufgrundArt#VERSTUMMT} die letzte Meldung des Laufs; sonst {@code null}
   * @param stufe bei {@link LaufgrundArt#VERSTUMMT} die zuletzt begonnene Stufe, bei {@link
   *     LaufgrundArt#BELEGT} die belegende Stufe; {@code null}, wenn keine gemeldet ist — die
   *     Anzeige setzt dann „nicht gemeldet“ ein
   * @param laufArt Art des Laufs, der den Grund trägt; {@code null} bei {@link
   *     LaufgrundArt#KEINE_UMSETZUNGSNACHT}
   * @param karte bei {@link LaufgrundArt#BELEGT} die Karte der belegenden Stufe; sonst {@code null}
   */
  public record Laufgrund(
      LaufgrundArt art,
      @Nullable Instant seit,
      @Nullable String stufe,
      @Nullable NightRunMode laufArt,
      @Nullable Integer karte) {}

  /**
   * Ein Vermerk über das Alter der Bewertung.
   *
   * @param art welcher Vermerk
   * @param zeit Zeitpunkt der jüngsten Bewertung; {@code null}, wenn es keine gibt
   */
  public record Vermerk(VermerkArt art, @Nullable Instant zeit) {}

  /**
   * Leitet ab, warum die Karte wartet.
   *
   * @param cardNumber projektweite Nummer der Karte
   * @param lage was der Runner des Projekts jetzt tut
   * @param bewertung die jüngste Bewertung des Projekts; leer, wenn der Runner nie bewertet hat
   * @param karteGeaendert der letzte Änderungszeitpunkt der Karte
   */
  public static Wartegrund ableiten(
      int cardNumber, RunnerLage lage, Optional<ReadyBewertung> bewertung, Instant karteGeaendert) {
    List<Laufgrund> laufgruende = laufgruende(cardNumber, lage.laeufe());
    if (bewertung.isEmpty()) {
      return nichtBewertet(laufgruende, null);
    }
    Instant bewertetAm = bewertung.get().assessedAt();
    Optional<Eintrag> eintrag =
        bewertung.get().entries().stream().filter(e -> e.cardNumber() == cardNumber).findFirst();
    if (eintrag.isEmpty()) {
      return nichtBewertet(laufgruende, bewertetAm);
    }
    Eintrag e = eintrag.get();
    boolean geaendert = karteGeaendert.isAfter(bewertetAm);
    Integer position = e.position();
    return new Wartegrund(
        laufgruende,
        e.reasons(),
        position == null ? null : position - 1,
        geaendert
            ? List.of(new Vermerk(VermerkArt.SEIT_BEWERTUNG_GEAENDERT, bewertetAm))
            : List.of(),
        e.reasons().isEmpty() && laufgruende.isEmpty() && !geaendert);
  }

  /**
   * Die Karte steht nicht in der Bewertung: Über ihre eigenen Gründe weiß das Board nichts, und
   * startbereit kann sie darum nicht heißen (E9, E10).
   */
  private static Wartegrund nichtBewertet(
      List<Laufgrund> laufgruende, @Nullable Instant bewertetAm) {
    return new Wartegrund(
        laufgruende,
        List.of(),
        null,
        List.of(new Vermerk(VermerkArt.NICHT_BEWERTET, bewertetAm)),
        false);
  }

  /**
   * Die Gründe des Laufzustands nach E8.
   *
   * <p>Eine verstummte Umsetzungsnacht gilt als laufend — wie im Fachplan: „gilt als laufend, hat
   * sich aber länger als die Schweigefrist nicht gemeldet“. Sie ergibt darum „verstummt“ und nicht
   * zusätzlich „keine Umsetzungsnacht aktiv“.
   */
  private static List<Laufgrund> laufgruende(int cardNumber, List<AktiverLauf> laeufe) {
    List<Laufgrund> gruende = new ArrayList<>();
    if (laeufe.stream().noneMatch(l -> l.mode() == NightRunMode.IMPLEMENTATION)) {
      gruende.add(new Laufgrund(LaufgrundArt.KEINE_UMSETZUNGSNACHT, null, null, null, null));
    }
    for (AktiverLauf lauf : laeufe) {
      if (lauf.zustand() == LaufZustand.VERSTUMMT) {
        if (lauf.mode() == NightRunMode.IMPLEMENTATION || haeltUmsetzung(lauf)) {
          RunnerStufe letzte = lauf.letzteStufe();
          gruende.add(
              new Laufgrund(
                  LaufgrundArt.VERSTUMMT,
                  lauf.letzteMeldung(),
                  letzte == null ? null : letzte.stage(),
                  lauf.mode(),
                  null));
        }
      } else {
        lauf.stufen().stream()
            .filter(s -> s.holdsImplementation() && s.cardNumber() != cardNumber)
            .forEach(
                s ->
                    gruende.add(
                        new Laufgrund(
                            LaufgrundArt.BELEGT, null, s.stage(), lauf.mode(), s.cardNumber())));
      }
    }
    return gruende;
  }

  private static boolean haeltUmsetzung(AktiverLauf lauf) {
    return lauf.stufen().stream().anyMatch(RunnerStufe::holdsImplementation);
  }
}
