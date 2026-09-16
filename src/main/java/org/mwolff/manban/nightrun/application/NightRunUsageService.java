package org.mwolff.manban.nightrun.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.EpicRef;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.CardTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.NightTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.PeriodTotals;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunPeriod;
import org.mwolff.manban.nightrun.domain.NightRunPeriodType;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Cases der Verbrauchs-Auswertung (Issue #938, Plan #933, fachlich #926): eine Nacht und ein
 * Zeitraum samt Vorzeitraum, Nächten und Vorhaben-Aufstellung.
 *
 * <p>Drei Dinge entscheiden sich hier und nirgends sonst:
 *
 * <ul>
 *   <li><b>Der nicht zuordenbare Rest</b> ist die Differenz aus gemeldeter Lauf-Summe und Summe
 *       über die Arbeitspakete — nie aus den Paketen gerechnet, sonst wäre er per Konstruktion null
 *       (Plan E6).
 *   <li><b>Der Abbruch-Hinweis</b> stützt sich allein auf die Fehlerklassen, die die Läufe
 *       mitbringen: {@code HARD_ABORT} und {@code TIME_BUDGET_EXCEEDED} (Plan E9).
 *   <li><b>Die Abdeckung</b> unterscheidet einen vollständig aufbewahrten Zeitraum von einem nur
 *       teilweise aufbewahrten und von einem, der ganz vor dem ältesten Lauf liegt (Plan E8) —
 *       getrennt vom Fall, dass im Zeitraum schlicht kein Lauf stattfand.
 * </ul>
 *
 * <p>Wer darf: {@code requireOwner} wie jeder Nachtlauf-Use-Case, der Plattform-Admin kommt mit
 * durch (Plan E17).
 */
@Service
// Die Kopplung folgt den Sichten, nicht einer Entwurfsentscheidung: Der Use-Case setzt aus Port,
// card-Fassade und Zeitraum-Rechnung sechs Antwort-Records zusammen, und jeder davon zaehlt als
// eigener Typ. Die Records in eigene Dateien zu ziehen senkte die Zahl nicht — sie blieben
// referenziert —, und die Rechnung auf zwei Services zu verteilen trennte, was #926 zusammen zeigt.
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class NightRunUsageService {

  /** Die Fehlerklassen, an denen eine abgebrochene Nacht erkannt wird (Plan E9). */
  private static final Set<NightRunErrorClass> ABBRUCH =
      EnumSet.of(NightRunErrorClass.HARD_ABORT, NightRunErrorClass.TIME_BUDGET_EXCEEDED);

  private final NightRunUsageRepository usage;
  private final CardService cards;
  private final PermissionChecker permissions;
  private final Clock clock;

  public NightRunUsageService(
      NightRunUsageRepository usage,
      CardService cards,
      PermissionChecker permissions,
      Clock clock) {
    this.usage = usage;
    this.cards = cards;
    this.permissions = permissions;
    this.clock = clock;
  }

  /**
   * Eine Nacht, adressiert über das Datum ihres Beginns (Plan E21) — mit der Tagesgrenze 12:00
   * (Issue #969).
   */
  @Transactional(readOnly = true)
  public NightUsageView night(long userId, long projectId, LocalDate night, ZoneId zone) {
    permissions.requireOwner(userId, projectId);
    NightRunPeriod spanne = NightRunPeriod.night(night, zone);
    PeriodTotals summe = usage.totals(projectId, spanne.from(), spanne.to());
    List<NightTotals> tagesgruppen =
        usage.totalsPerNight(projectId, spanne.from(), spanne.to(), zone);
    return new NightUsageView(
        night,
        summe.runCount(),
        summe.durationMs(),
        summe.cardCount(),
        aufteilung(summe),
        tagesgruppen.stream().anyMatch(NightRunUsageService::abgebrochen),
        usage.totalsPerCard(projectId, spanne.from(), spanne.to()));
  }

  /**
   * Ein Zeitraum: Rückschritt 0 ist der zuletzt abgeschlossene (Plan E14), daneben der unmittelbar
   * vorangegangene gleichartige (#926 AK 6).
   */
  @Transactional(readOnly = true)
  public PeriodUsageView period(
      long userId, long projectId, NightRunPeriodType type, int stepsBack, ZoneId zone) {
    permissions.requireOwner(userId, projectId);
    NightRunPeriod zeitraum = NightRunPeriod.of(type, zone, clock, stepsBack);
    Optional<Instant> aeltester = usage.oldestRetainedRunStart(projectId);

    List<NightSummary> naechte =
        usage.totalsPerNight(projectId, zeitraum.from(), zeitraum.to(), zone).stream()
            .map(
                n ->
                    new NightSummary(
                        n.night(),
                        n.runCount(),
                        n.cardCount(),
                        new UsageSplit(
                            n.runUsage(), n.itemUsage(), n.runUsage().minus(n.itemUsage())),
                        abgebrochen(n)))
            .toList();

    List<CardTotals> karten = usage.totalsPerCard(projectId, zeitraum.from(), zeitraum.to());
    Map<Integer, Set<EpicRef>> zuordnung =
        cards.epicsByCardNumber(projectId, karten.stream().map(CardTotals::cardNumber).toList());

    return new PeriodUsageView(
        kennzahlen(projectId, zeitraum, aeltester),
        kennzahlen(projectId, zeitraum.previous(), aeltester),
        naechte,
        vorhaben(karten, zuordnung),
        ohneVorhaben(karten, zuordnung),
        zuordnung.values().stream().anyMatch(vorhaben -> vorhaben.size() > 1));
  }

  private PeriodFigures kennzahlen(
      long projectId, NightRunPeriod zeitraum, Optional<Instant> aeltester) {
    PeriodTotals summe = usage.totals(projectId, zeitraum.from(), zeitraum.to());
    return new PeriodFigures(
        zeitraum,
        abdeckung(zeitraum, aeltester),
        summe.runCount(),
        summe.durationMs(),
        summe.cardCount(),
        aufteilung(summe));
  }

  /**
   * Ohne aufbewahrten Lauf ist nichts verdrängt worden — der Zeitraum ist dann vollständig, nur
   * leer. Ein Lauf genau am Beginn des Zeitraums lässt ihn vollständig; endet der Zeitraum genau am
   * ältesten Lauf, liegt er ganz davor ({@code to} ist ausschließlich).
   */
  private static Coverage abdeckung(NightRunPeriod zeitraum, Optional<Instant> aeltester) {
    if (aeltester.isEmpty() || !zeitraum.from().isBefore(aeltester.get())) {
      return Coverage.COMPLETE;
    }
    return zeitraum.to().isAfter(aeltester.get()) ? Coverage.PARTIAL : Coverage.BEFORE_RETENTION;
  }

  private static UsageSplit aufteilung(PeriodTotals summe) {
    return new UsageSplit(
        summe.runUsage(), summe.itemUsage(), summe.runUsage().minus(summe.itemUsage()));
  }

  private static boolean abgebrochen(NightTotals nacht) {
    return nacht.errorClasses().stream().anyMatch(ABBRUCH::contains);
  }

  /**
   * Die Kartensummen je Vorhaben, absteigend nach Kosten; ohne Kosten zuletzt, bei Gleichstand nach
   * Vorhaben-ID. Eine Karte in mehreren Vorhaben zählt in jedem (Plan E11).
   */
  private static List<EpicUsageView> vorhaben(
      List<CardTotals> karten, Map<Integer, Set<EpicRef>> zuordnung) {
    Map<EpicRef, EpicUsageView> jeVorhaben = new LinkedHashMap<>();
    for (CardTotals karte : karten) {
      for (EpicRef ref : zuordnung.getOrDefault(karte.cardNumber(), Set.of())) {
        jeVorhaben.merge(
            ref, new EpicUsageView(ref, 1L, karte.usage()), NightRunUsageService::zusammen);
      }
    }
    return jeVorhaben.values().stream()
        .sorted(
            Comparator.comparing(
                    (EpicUsageView e) -> e.usage().costUsd(),
                    Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()))
                // Vorhaben der Aufstellung tragen immer eine Kennung; null gibt es nur beim Posten
                // „ohne Vorhaben", und der steht nicht in dieser Liste.
                .thenComparingLong(e -> Objects.requireNonNull(e.epic()).id()))
        .toList();
  }

  /**
   * Der eigene Posten „ohne Vorhaben": Karten ohne Zuordnung und Kartennummern, zu denen es keine
   * Karte mehr gibt (#926 AK 11, Plan E12) — nicht weggelassen und nicht verteilt.
   */
  private static EpicUsageView ohneVorhaben(
      Collection<CardTotals> karten, Map<Integer, Set<EpicRef>> zuordnung) {
    EpicUsageView ohne = new EpicUsageView(null, 0L, new NightRunUsage(null, null, null, null));
    for (CardTotals karte : karten) {
      if (zuordnung.getOrDefault(karte.cardNumber(), Set.of()).isEmpty()) {
        ohne = zusammen(ohne, new EpicUsageView(null, 1L, karte.usage()));
      }
    }
    return ohne;
  }

  private static EpicUsageView zusammen(EpicUsageView a, EpicUsageView b) {
    return new EpicUsageView(a.epic(), a.cardCount() + b.cardCount(), a.usage().plus(b.usage()));
  }

  /** Abdeckung eines Zeitraums durch die aufbewahrten Läufe (Plan E8). */
  public enum Coverage {
    /** Kein Lauf dieses Zeitraums ist verdrängt worden. */
    COMPLETE,
    /** Der Zeitraum beginnt vor dem ältesten aufbewahrten Lauf und reicht über ihn hinaus. */
    PARTIAL,
    /** Der Zeitraum endet vor dem ältesten aufbewahrten Lauf. */
    BEFORE_RETENTION
  }

  /**
   * Die drei getrennten Zahlen (#926 AK 2): Gesamtsumme, kartenbezogener Anteil und Rest. Ein Feld
   * des Rests ist {@code null}, wenn eine der beiden Seiten fehlt.
   */
  public record UsageSplit(NightRunUsage total, NightRunUsage cardShare, NightRunUsage remainder) {}

  /** Eine Nacht mit ihren Kartenzeilen (#926 AK 1–4). */
  public record NightUsageView(
      LocalDate night,
      long runCount,
      long durationMs,
      long cardCount,
      UsageSplit usage,
      boolean aborted,
      List<CardTotals> cards) {}

  /** Die Kennzahlen eines Zeitraums. */
  public record PeriodFigures(
      NightRunPeriod period,
      Coverage coverage,
      long runCount,
      long durationMs,
      long cardCount,
      UsageSplit usage) {

    /** Kein Lauf in diesem Zeitraum (#926 AK 9) — unabhängig von der Abdeckung. */
    public boolean noRuns() {
      return runCount == 0;
    }
  }

  /** Eine Nacht innerhalb eines Zeitraums, von der aus die Nachtansicht erreichbar ist (AK 8). */
  public record NightSummary(
      LocalDate night, long runCount, long cardCount, UsageSplit usage, boolean aborted) {}

  /**
   * Ein Vorhaben mit seinen Kartensummen.
   *
   * @param epic das Vorhaben; {@code null} beim Posten „ohne Vorhaben"
   * @param cardCount Zahl der Kartennummern, die beitragen
   * @param usage Summe ihrer Verbräuche
   */
  public record EpicUsageView(@Nullable EpicRef epic, long cardCount, NightRunUsage usage) {}

  /**
   * Ein Zeitraum samt Vorzeitraum, Nächten und Vorhaben-Aufstellung.
   *
   * @param epicsOverlap {@code true}, wenn eine Karte zu mehreren Vorhaben gehört — dann ergeben
   *     die Vorhaben-Summen zusammen mehr als den kartenbezogenen Anteil (Plan E11)
   */
  public record PeriodUsageView(
      PeriodFigures current,
      PeriodFigures previous,
      List<NightSummary> nights,
      List<EpicUsageView> epics,
      EpicUsageView withoutEpic,
      boolean epicsOverlap) {}
}
