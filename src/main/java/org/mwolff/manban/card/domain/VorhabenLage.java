package org.mwolff.manban.card.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Durchsatzermittlung.Durchsatz;

/**
 * Lage, Zählung und ausstehende Dokumente je Vorhaben für die Leitstand-Platte „Vorhaben“ (Plan
 * #1819, A3, A6 bis A8). Die Rangfolge der Lagen aus AK 11 steht nur hier; die Texte setzt das
 * Frontend zusammen. Rein, ohne Datenbank.
 *
 * <p>Gezählt wird über die Zugehörigkeit ohne Archiv ({@code EpicMembership.compute}, E6), und nur
 * Arbeitspakete mit Status zählen (A6): offen außerhalb von In Review und Done, fertig darin (AK
 * 13, AK 16). Ob überhaupt Pakete geschnitten sind und welche Dokumente noch ausstehen, entscheidet
 * dagegen die Zugehörigkeit samt Archiv ({@code EpicMembership.computeMitArchiv}, E6, E8): Ein
 * archiviertes Paket ist umgesetzt, nicht ungeschnitten.
 */
public final class VorhabenLage {

  /** Mehr offene Pakete rechnet die Prognose nicht; das Vorhaben erhält zu wenig Durchsatz (A3). */
  public static final int HOECHSTE_PAKETZAHL = 1000;

  /** Die Lage eines Vorhabens in der Rangfolge von AK 11. */
  public enum Lage {
    /** Noch keine Pakete geschnitten, auch keine archivierten. */
    NO_PACKAGES,
    /** Kein offenes Paket (AK 12). */
    ALL_DONE,
    /**
     * Zu wenig Durchsatz (AK 5), zu viele offene Pakete oder kein Tag bis zur Schutzgrenze (A3).
     */
    INSUFFICIENT_THROUGHPUT,
    /** Die Daten der Prognose. */
    FORECAST
  }

  /**
   * Ein Dokument, aus dem noch Pakete oder ein Plan folgen (AK 6, E8).
   *
   * @param nummer board-weite Nummer der Karte
   * @param titel Titel der Karte
   */
  public record Dokument(int nummer, String titel) {}

  /**
   * Die Lage eines Vorhabens.
   *
   * @param vorhaben die Vorhaben-Karte
   * @param lage Lage nach AK 11
   * @param total Arbeitspakete ohne Archiv
   * @param finished davon in In Review oder Done
   * @param open davon offen
   * @param tage die Tage der Prognose ab dem laufenden Tag, nur bei {@link Lage#FORECAST}
   * @param earliest ob die Prognose „frühestens“ gilt — nur bei {@link Lage#FORECAST} mit
   *     ausstehendem Dokument
   * @param ausstehend ausstehende Dokumente, aufsteigend nach Nummer (E7)
   * @param otherEpicsSharing andere sichtbare Vorhaben mit mindestens einem offenen Paket (E12)
   */
  public record Ergebnis(
      Card vorhaben,
      Lage lage,
      int total,
      int finished,
      int open,
      Fertigprognose.@Nullable Tage tage,
      boolean earliest,
      List<Dokument> ausstehend,
      int otherEpicsSharing) {}

  /** Die Zählung eines Vorhabens vor der Lage. */
  private record Zaehlung(Card vorhaben, int total, int finished, boolean geschnitten) {
    int open() {
      return total - finished;
    }
  }

  private VorhabenLage() {}

  /**
   * Die Lage jedes sichtbaren Vorhabens — jedes Vorhabens, das nicht archiviert ist (AK 18) — in
   * der Reihenfolge der Karten.
   *
   * @param boardCards Karten des Boards ohne Papierkorb
   * @param zugehoerige je Vorhaben-ID die Zugehörigen ohne Archiv ({@code EpicMembership.compute})
   * @param zugehoerigeMitArchiv je Vorhaben-ID die Zugehörigen samt Archiv ({@code
   *     EpicMembership.computeMitArchiv})
   * @param spaltennamen Name je Spalten-ID, für die Done-Spalte eines Dokuments
   * @param durchsatz Durchsatz des Boards
   */
  public static List<Ergebnis> ermittle(
      List<Card> boardCards,
      Map<Long, Set<Card>> zugehoerige,
      Map<Long, Set<Card>> zugehoerigeMitArchiv,
      Map<Long, String> spaltennamen,
      Durchsatz durchsatz) {
    List<Zaehlung> zaehlungen =
        boardCards.stream()
            .filter(c -> c.type() == CardType.EPIC && !c.archived())
            .map(
                epic ->
                    zaehle(
                        epic,
                        zugehoerige.getOrDefault(epic.requireId(), Set.of()),
                        zugehoerigeMitArchiv.getOrDefault(epic.requireId(), Set.of())))
            .toList();
    Map<Integer, Optional<Fertigprognose.Tage>> prognose = prognose(zaehlungen, durchsatz);
    int mitOffenen = (int) zaehlungen.stream().filter(z -> z.open() > 0).count();

    List<Ergebnis> ergebnisse = new ArrayList<>(zaehlungen.size());
    for (Zaehlung z : zaehlungen) {
      Fertigprognose.Tage tage = prognose.getOrDefault(z.open(), Optional.empty()).orElse(null);
      Lage lage = lage(z, tage);
      List<Dokument> ausstehend =
          ausstehend(
              zugehoerigeMitArchiv.getOrDefault(z.vorhaben().requireId(), Set.of()), spaltennamen);
      ergebnisse.add(
          new Ergebnis(
              z.vorhaben(),
              lage,
              z.total(),
              z.finished(),
              z.open(),
              lage == Lage.FORECAST ? tage : null,
              lage == Lage.FORECAST && !ausstehend.isEmpty(),
              ausstehend,
              mitOffenen - (z.open() > 0 ? 1 : 0)));
    }
    return List.copyOf(ergebnisse);
  }

  private static Zaehlung zaehle(Card epic, Set<Card> zugehoerige, Set<Card> mitArchiv) {
    int total = 0;
    int finished = 0;
    for (Card c : zugehoerige) {
      if (istPaket(c)) {
        total++;
        if (fertig(c)) {
          finished++;
        }
      }
    }
    boolean geschnitten = mitArchiv.stream().anyMatch(VorhabenLage::istPaket);
    return new Zaehlung(epic, total, finished, geschnitten);
  }

  /**
   * Ein Durchlauf der Fertigprognose für alle n, die eine Prognose bekommen können (A3); ohne genug
   * Durchsatz keiner.
   */
  private static Map<Integer, Optional<Fertigprognose.Tage>> prognose(
      List<Zaehlung> zaehlungen, Durchsatz durchsatz) {
    if (durchsatz.tageMitPaketen() < Durchsatzermittlung.MINDESTTAGE) {
      return Map.of();
    }
    Set<Integer> anzahlen = new TreeSet<>();
    for (Zaehlung z : zaehlungen) {
      if (z.open() > 0 && z.open() <= HOECHSTE_PAKETZAHL) {
        anzahlen.add(z.open());
      }
    }
    return Fertigprognose.rechne(durchsatz.tageswerte(), anzahlen);
  }

  /** Die Rangfolge von AK 11. */
  private static Lage lage(Zaehlung z, Fertigprognose.@Nullable Tage tage) {
    if (!z.geschnitten()) {
      return Lage.NO_PACKAGES;
    }
    if (z.open() == 0) {
      return Lage.ALL_DONE;
    }
    return tage == null ? Lage.INSUFFICIENT_THROUGHPUT : Lage.FORECAST;
  }

  /**
   * Die Pläne ohne abgeleitetes Arbeitspaket und die Fachpläne ohne abgeleiteten Plan, jeweils
   * nicht archiviert und nicht in einer Done-Spalte (E8); nur unmittelbare Herkunft zählt.
   */
  private static List<Dokument> ausstehend(
      Collection<Card> mitArchiv, Map<Long, String> spaltennamen) {
    return mitArchiv.stream()
        .filter(c -> !c.archived() && !Arbeitspaket.effektivDone(c, spaltennamen.get(c.columnId())))
        .filter(c -> fehltNachfahr(c, mitArchiv))
        .sorted(Comparator.comparing(Card::number))
        .map(c -> new Dokument(c.number(), c.title()))
        .toList();
  }

  /** Ob das Dokument ein Plan ohne Paket oder ein Fachplan ohne Plan ist. */
  private static boolean fehltNachfahr(Card dokument, Collection<Card> mitArchiv) {
    Kartenart art = Kartenart.von(dokument.type(), dokument.title());
    boolean plan = art == Kartenart.PLAN;
    if (!plan && art != Kartenart.FACHLICH) {
      return false;
    }
    return mitArchiv.stream()
        .noneMatch(
            c ->
                stammtAus(c, dokument)
                    && (plan
                        ? Arbeitspaket.istArbeitspaket(c.type(), c.title())
                        : Kartenart.von(c.type(), c.title()) == Kartenart.PLAN));
  }

  private static boolean stammtAus(Card kind, Card dokument) {
    return dokument.requireId().equals(kind.derivedFromCardId());
  }

  /** Ein Arbeitspaket mit Status (A6). */
  private static boolean istPaket(Card c) {
    return c.status() != null && Arbeitspaket.istArbeitspaket(c.type(), c.title());
  }

  private static boolean fertig(Card c) {
    return c.status() == CardStatus.IN_REVIEW || c.status() == CardStatus.DONE;
  }
}
