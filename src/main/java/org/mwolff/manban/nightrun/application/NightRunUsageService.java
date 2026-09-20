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
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.LifetimeTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.NightTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.PeriodTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.RetainedByKind;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.TotalsByKind;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunPeriod;
import org.mwolff.manban.nightrun.domain.NightRunPeriodType;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.InteractiveUsageSinceReader;
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
 * <p>Wer darf: {@code requireNightRunAccess} wie jeder lesende Nachtlauf-Use-Case (Issue #1079).
 * Der echte Projekt-OWNER darf immer; ein Plattform-Admin ohne diese Rolle nur, wenn das Projekt am
 * Plattform-Leitstand teilnimmt — sonst 403. Bis dahin kam er ungebunden durch (Plan E17).
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
  private final InteractiveUsageSinceReader erfassungsbeginn;
  private final Clock clock;
  private final NightRunProperties properties;

  public NightRunUsageService(
      NightRunUsageRepository usage,
      CardService cards,
      PermissionChecker permissions,
      InteractiveUsageSinceReader erfassungsbeginn,
      Clock clock,
      NightRunProperties properties) {
    this.usage = usage;
    this.cards = cards;
    this.permissions = permissions;
    this.erfassungsbeginn = erfassungsbeginn;
    this.clock = clock;
    this.properties = properties;
  }

  /**
   * Eine Nacht, adressiert über das Datum ihres Beginns (Plan E21) — mit der Tagesgrenze 12:00
   * (Issue #969).
   */
  @Transactional(readOnly = true)
  public NightUsageView night(long userId, long projectId, LocalDate night, ZoneId zone) {
    permissions.requireNightRunAccess(userId, projectId);
    NightRunPeriod spanne = NightRunPeriod.night(night, zone);
    PeriodTotals summe = usage.totals(projectId, spanne.from(), spanne.to());
    List<NightTotals> tagesgruppen =
        usage.totalsPerNight(projectId, spanne.from(), spanne.to(), zone);
    return new NightUsageView(
        night,
        summe.runCount(),
        summe.durationMs(),
        summe.cardCount(),
        teilen(summe.runUsage(), summe.itemUsage()),
        jeGattung(summe.byKind()),
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
    permissions.requireNightRunAccess(userId, projectId);
    NightRunPeriod zeitraum = NightRunPeriod.of(type, zone, clock, stepsBack);
    Optional<Instant> grenze = aufbewahrungsgrenze(projectId);
    Instant seit = erfassungsbeginn.interactiveUsageSince(projectId).orElse(null);

    List<NightSummary> naechte =
        usage.totalsPerNight(projectId, zeitraum.from(), zeitraum.to(), zone).stream()
            .map(
                n ->
                    new NightSummary(
                        n.night(),
                        n.runCount(),
                        n.cardCount(),
                        teilen(n.runUsage(), n.itemUsage()),
                        jeGattung(n.byKind()),
                        abgebrochen(n)))
            .toList();

    List<CardTotals> karten = usage.totalsPerCard(projectId, zeitraum.from(), zeitraum.to());
    Map<Integer, Set<EpicRef>> zuordnung =
        cards.epicsByCardNumber(projectId, karten.stream().map(CardTotals::cardNumber).toList());

    return new PeriodUsageView(
        kennzahlen(projectId, zeitraum, grenze, seit),
        kennzahlen(projectId, zeitraum.previous(), grenze, seit),
        naechte,
        vorhaben(karten, zuordnung),
        ohneVorhaben(karten, zuordnung),
        zuordnung.values().stream().anyMatch(vorhaben -> vorhaben.size() > 1));
  }

  /**
   * Die Summe über die ganze Laufzeit des Projekts (#984 AK 4, Plan E19) — ohne Zeitraum und
   * deshalb ohne Abdeckungs-Einordnung: Über die Lebenszeit gibt es keinen Zeitraum, der ganz vor
   * der Aufbewahrung liegen könnte. Stattdessen kommen beide Lückenangaben mit, und sie sagen
   * Verschiedenes: Der älteste aufbewahrte Eintrag zeigt, was der Ringpuffer verdrängt hat, der
   * Erfassungsbeginn trennt „nie erfasst" von „erfasst, dann verdrängt".
   */
  @Transactional(readOnly = true)
  public TotalUsageView total(long userId, long projectId) {
    permissions.requireNightRunAccess(userId, projectId);
    LifetimeTotals summe = usage.lifetimeTotals(projectId);
    return new TotalUsageView(
        summe.byKind().night().runCount(),
        summe.byKind().interactive().runCount(),
        summe.cardCount(),
        teilen(summe.byKind().runUsage(), summe.byKind().itemUsage()),
        jeGattung(summe.byKind()),
        usage.oldestRetainedRunStart(projectId).orElse(null),
        erfassungsbeginn.interactiveUsageSince(projectId).orElse(null));
  }

  private PeriodFigures kennzahlen(
      long projectId, NightRunPeriod zeitraum, Optional<Instant> grenze, @Nullable Instant seit) {
    PeriodTotals summe = usage.totals(projectId, zeitraum.from(), zeitraum.to());
    return new PeriodFigures(
        zeitraum,
        abdeckung(zeitraum, grenze),
        summe.byKind().night().runCount(),
        summe.byKind().interactive().runCount(),
        summe.durationMs(),
        summe.cardCount(),
        teilen(summe.runUsage(), summe.itemUsage()),
        jeGattung(summe.byKind()),
        seit);
  }

  /**
   * Die Aufbewahrungsgrenze (Plan E8, Issue #1071): der späteste älteste Eintrag unter den
   * Gattungen, deren Ringpuffer <b>gefüllt</b> ist ({@code count >= properties.maxRunsFor(kind)}).
   * Hat keine Gattung ihren Ringpuffer gefüllt, ist nichts verdrängt worden — dann gibt es keine
   * Grenze, unabhängig davon, wie alt das Projekt ist.
   *
   * <p>Hält eine nicht volle Gattung ältere Einträge als die volle, bleibt die Grenze am ältesten
   * Eintrag der vollen Gattung — die nicht volle hat davon nichts verloren (E9, Plan-Review H5).
   */
  private Optional<Instant> aufbewahrungsgrenze(long projectId) {
    return usage.retentionBoundary(projectId).stream()
        .filter(r -> r.count() >= properties.maxRunsFor(r.kind()))
        .map(RetainedByKind::oldestStart)
        .filter(Objects::nonNull)
        .max(Instant::compareTo);
  }

  /**
   * Ohne Aufbewahrungsgrenze ist nichts verdrängt worden — der Zeitraum ist dann vollständig, auch
   * wenn er leer ist oder ganz vor dem ersten Lauf des Projekts liegt. Ein Lauf genau am Beginn des
   * Zeitraums lässt ihn vollständig; endet der Zeitraum genau an der Grenze, liegt er ganz davor
   * ({@code to} ist ausschließlich).
   */
  private static Coverage abdeckung(NightRunPeriod zeitraum, Optional<Instant> grenze) {
    if (grenze.isEmpty() || !zeitraum.from().isBefore(grenze.get())) {
      return Coverage.COMPLETE;
    }
    return zeitraum.to().isAfter(grenze.get()) ? Coverage.PARTIAL : Coverage.BEFORE_RETENTION;
  }

  /**
   * Dieselbe Rechnung noch einmal, jede Gattung für sich (Issue #1013, #984 AK 3): Der Rest einer
   * Gattung ist die Differenz aus <b>ihrer</b> Lauf-Summe und <b>ihrer</b> Paket-Summe — nie aus
   * den Paketen gerechnet und nie aus dem Gesamtrest verteilt.
   */
  private static KindSplit jeGattung(TotalsByKind summe) {
    return new KindSplit(
        teilen(summe.night().runUsage(), summe.night().itemUsage()),
        teilen(summe.interactive().runUsage(), summe.interactive().itemUsage()));
  }

  /** Die Rechnung aus Plan E6: Der Rest ist die Differenz, nie aus den Paketen gerechnet. */
  private static UsageSplit teilen(NightRunUsage lauf, NightRunUsage pakete) {
    return new UsageSplit(lauf, pakete, lauf.minus(pakete));
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

  /**
   * Abdeckung eines Zeitraums durch die aufbewahrten Läufe (Plan E8, Issue #1071). Die drei Werte
   * sagen etwas über <b>verdrängte</b> Läufe — nicht über das Alter des Projekts: Ein junges
   * Projekt, dessen Ringpuffer noch nicht gefüllt ist, ist für jeden Zeitraum {@code COMPLETE},
   * auch für einen, der ganz vor seinem ersten Lauf liegt.
   */
  public enum Coverage {
    /** Kein Lauf dieses Zeitraums ist verdrängt worden. */
    COMPLETE,
    /** Der Zeitraum beginnt vor der Aufbewahrungsgrenze und reicht über sie hinaus. */
    PARTIAL,
    /** Der Zeitraum endet vor der Aufbewahrungsgrenze. */
    BEFORE_RETENTION
  }

  /**
   * Die drei getrennten Zahlen (#926 AK 2): Gesamtsumme, kartenbezogener Anteil und Rest. Ein Feld
   * des Rests ist {@code null}, wenn eine der beiden Seiten fehlt.
   */
  public record UsageSplit(NightRunUsage total, NightRunUsage cardShare, NightRunUsage remainder) {}

  /**
   * Dieselbe Teilung je Gattung (Issue #1013, #984 AK 1): daneben, nicht anstelle der Gesamtzahlen.
   * Die Gesamtsumme bleibt, was sie war, und ist genau die Addition der beiden Anteile (AK 5).
   *
   * @param night Anteil der Nachtläufe
   * @param interactive Anteil der interaktiven Sitzungen
   */
  public record KindSplit(UsageSplit night, UsageSplit interactive) {}

  /** Eine Nacht mit ihren Kartenzeilen (#926 AK 1–4). */
  public record NightUsageView(
      LocalDate night,
      long runCount,
      long durationMs,
      long cardCount,
      UsageSplit usage,
      KindSplit usageByKind,
      boolean aborted,
      List<CardTotals> cards) {}

  /**
   * Die Kennzahlen eines Zeitraums.
   *
   * @param nightRunCount Zahl der Nachtläufe im Zeitraum
   * @param interactiveRunCount Zahl der interaktiven Sitzungen im Zeitraum. Beide stehen getrennt,
   *     weil die Gesamtzahl allein nicht sagt, woher sie kommt: „3 Läufe" über einem Zeitraum aus
   *     einem Lauf und zwei Sitzungen wäre eine falsche Aussage (#984 AK 1).
   * @param interactiveUsageSince Startzeitpunkt der ersten je gemeldeten interaktiven Sitzung des
   *     Projekts; {@code null}, solange keine gemeldet wurde (Plan E18). Daran unterscheidet die
   *     Anzeige „nicht erfasst" von „teilweise erfasst" — die Klassifikation selbst entsteht im
   *     Frontend, hier steht nur der Zeitpunkt.
   */
  public record PeriodFigures(
      NightRunPeriod period,
      Coverage coverage,
      long nightRunCount,
      long interactiveRunCount,
      long durationMs,
      long cardCount,
      UsageSplit usage,
      KindSplit usageByKind,
      @Nullable Instant interactiveUsageSince) {

    /** Zahl aller Einträge des Zeitraums — Läufe <b>und</b> Sitzungen. */
    public long runCount() {
      return nightRunCount + interactiveRunCount;
    }

    /** Kein Eintrag in diesem Zeitraum (#926 AK 9) — unabhängig von der Abdeckung. */
    public boolean noRuns() {
      return runCount() == 0;
    }
  }

  /**
   * Die Summe über die ganze Laufzeit des Projekts (#984 AK 4, Plan E19).
   *
   * @param nightRunCount Zahl der aufbewahrten Nachtläufe
   * @param interactiveRunCount Zahl der aufbewahrten interaktiven Sitzungen
   * @param cardCount Zahl der verschiedenen Kartennummern über beide Gattungen
   * @param oldestRetainedRunStart Beginn des ältesten aufbewahrten Eintrags; {@code null}, solange
   *     das Projekt keinen hat. Liegt er nach dem Erfassungsbeginn, hat der Ringpuffer verdrängt —
   *     die Summe ist dann unvollständig, und der abgedeckte Zeitraum beginnt hier.
   * @param interactiveUsageSince Erfassungsbeginn der interaktiven Sitzungen; {@code null}, solange
   *     keine gemeldet wurde (Plan E18). Er trennt „nie erfasst" von „erfasst, dann verdrängt" —
   *     die Einordnung selbst entsteht im Frontend, hier stehen nur die beiden Zeitpunkte.
   */
  public record TotalUsageView(
      long nightRunCount,
      long interactiveRunCount,
      long cardCount,
      UsageSplit usage,
      KindSplit usageByKind,
      @Nullable Instant oldestRetainedRunStart,
      @Nullable Instant interactiveUsageSince) {

    /** Zahl aller aufbewahrten Einträge — Läufe <b>und</b> Sitzungen. */
    public long runCount() {
      return nightRunCount + interactiveRunCount;
    }
  }

  /** Eine Nacht innerhalb eines Zeitraums, von der aus die Nachtansicht erreichbar ist (AK 8). */
  public record NightSummary(
      LocalDate night,
      long runCount,
      long cardCount,
      UsageSplit usage,
      KindSplit usageByKind,
      boolean aborted) {}

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
