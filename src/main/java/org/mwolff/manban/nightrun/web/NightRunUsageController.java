package org.mwolff.manban.nightrun.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.EpicRef;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.CardTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageService;
import org.mwolff.manban.nightrun.application.NightRunUsageService.Coverage;
import org.mwolff.manban.nightrun.application.NightRunUsageService.EpicUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.KindSplit;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightSummary;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.PeriodFigures;
import org.mwolff.manban.nightrun.application.NightRunUsageService.PeriodUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.TotalUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.UsageSplit;
import org.mwolff.manban.nightrun.domain.NightRunPeriodType;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Verbrauchs-Auswertung an HTTP (Issue #939, Plan #933): ein Abruf je Sicht (Plan E2).
 *
 * <p><b>Die Zone kommt vom Leser</b> (Plan E4) und ist eine Regionszone. Unbekanntes weist die
 * Bindung als {@link ZoneId} selbst mit 400 ab; Offset-Zonen wie {@code +05:30}, {@code Z} oder
 * {@code GMT+2} akzeptiert {@code ZoneId.of} zwar, sie werden hier trotzdem mit 400 abgewiesen:
 * Postgres deutet POSIX-Offsets mit umgekehrtem Vorzeichen, und die Nächte lägen sonst verschoben.
 *
 * <p>Keine eigenen Exceptions: 403 und 404 liefert {@code requireOwner} im Service, 400 die
 * Bindung, die Parameter-Validierung und die Abweisung der Zone über {@link
 * ResponseStatusException}.
 */
@RestController
class NightRunUsageController {

  /**
   * Obergrenze des Rückschritts (Plan E14). Die Spanne wird mit dem Rückschritt nicht länger, nur
   * älter — die Grenze hält vor allem Werte fern, an denen die Datumsrechnung überläuft. Ein Jahr
   * in Tagen reicht über jede Aufbewahrung hinaus: Bei 190 Läufen sind das kaum mehr als drei
   * Monate.
   */
  static final int MAX_STEPS_BACK = 366;

  private final NightRunUsageService usage;

  NightRunUsageController(NightRunUsageService usage) {
    this.usage = usage;
  }

  /** Eine Nacht, adressiert über das Datum ihres Beginns (Plan E21). */
  @GetMapping("/api/projects/{projectId}/night-run-usage/night")
  NightResponse night(
      @AuthenticationPrincipal Long userId,
      @PathVariable long projectId,
      @RequestParam LocalDate date,
      @RequestParam ZoneId zone) {
    return NightResponse.of(usage.night(userId, projectId, date, regionszone(zone)));
  }

  /** Ein Zeitraum samt Vorzeitraum, Nächten und Vorhaben-Aufstellung. */
  @GetMapping("/api/projects/{projectId}/night-run-usage")
  PeriodResponse period(
      @AuthenticationPrincipal Long userId,
      @PathVariable long projectId,
      @RequestParam NightRunPeriodType type,
      @RequestParam @Min(0) @Max(MAX_STEPS_BACK) int stepsBack,
      @RequestParam ZoneId zone) {
    return PeriodResponse.of(usage.period(userId, projectId, type, stepsBack, regionszone(zone)));
  }

  /**
   * Die Summe über die ganze Laufzeit des Projekts (Plan E19). Ein eigener Abruf und kein vierter
   * Wert für {@code type}: Eine Lebenszeit hat keinen ersten Tag, keinen Vorzeitraum und keine
   * Zone, nach der sie sich gruppieren ließe — sie braucht deshalb keinen Parameter.
   */
  @GetMapping("/api/projects/{projectId}/night-run-usage/total")
  TotalResponse total(@AuthenticationPrincipal Long userId, @PathVariable long projectId) {
    return TotalResponse.of(usage.total(userId, projectId));
  }

  /** Lässt allein Zonen aus der Zonendatenbank durch — nie einen festen Offset. */
  private static ZoneId regionszone(ZoneId zone) {
    if (!ZoneId.getAvailableZoneIds().contains(zone.getId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "zone muss eine Regionszone sein, etwa Europe/Berlin");
    }
    return zone;
  }

  /** Die vier Verbrauchsangaben und der Anteil aus dem Zwischenspeicher; fehlend bleibt null. */
  record UsageResponse(
      @Nullable BigDecimal costUsd,
      @Nullable Long inputTokens,
      @Nullable Long outputTokens,
      @Nullable Long cachedInputTokens,
      @Nullable BigDecimal cachedInputSharePercent) {

    static UsageResponse of(NightRunUsage u) {
      return new UsageResponse(
          u.costUsd(),
          u.inputTokens(),
          u.outputTokens(),
          u.cachedInputTokens(),
          u.cachedInputSharePercent());
    }
  }

  /** Gesamtsumme, kartenbezogener Anteil und nicht zuordenbarer Rest (#926 AK 2). */
  record SplitResponse(UsageResponse total, UsageResponse cardShare, UsageResponse remainder) {

    static SplitResponse of(UsageSplit s) {
      return new SplitResponse(
          UsageResponse.of(s.total()),
          UsageResponse.of(s.cardShare()),
          UsageResponse.of(s.remainder()));
    }
  }

  /**
   * Dieselbe Teilung je Gattung (Issue #1013): {@code night} sind die Nachtläufe, {@code
   * interactive} die interaktiven Sitzungen. Steht neben {@code usage}, nicht an dessen Stelle —
   * die Erweiterung ist additiv.
   */
  record KindSplitResponse(SplitResponse night, SplitResponse interactive) {

    static KindSplitResponse of(KindSplit s) {
      return new KindSplitResponse(SplitResponse.of(s.night()), SplitResponse.of(s.interactive()));
    }
  }

  /** Der Verbrauch einer Kartenzeile je Gattung. */
  record KindUsageResponse(UsageResponse night, UsageResponse interactive) {

    static KindUsageResponse of(CardTotals c) {
      return new KindUsageResponse(
          UsageResponse.of(c.nightUsage()), UsageResponse.of(c.interactiveUsage()));
    }
  }

  /** Eine Kartenzeile. */
  record CardResponse(
      int cardNumber,
      long attemptCount,
      @Nullable Long durationMs,
      UsageResponse usage,
      KindUsageResponse usageByKind) {

    static CardResponse of(CardTotals c) {
      return new CardResponse(
          c.cardNumber(),
          c.attemptCount(),
          c.durationMs(),
          UsageResponse.of(c.usage()),
          KindUsageResponse.of(c));
    }
  }

  /** Eine Nacht. */
  record NightResponse(
      LocalDate night,
      long runCount,
      long durationMs,
      long cardCount,
      SplitResponse usage,
      KindSplitResponse usageByKind,
      boolean aborted,
      List<CardResponse> cards) {

    static NightResponse of(NightUsageView n) {
      return new NightResponse(
          n.night(),
          n.runCount(),
          n.durationMs(),
          n.cardCount(),
          SplitResponse.of(n.usage()),
          KindSplitResponse.of(n.usageByKind()),
          n.aborted(),
          n.cards().stream().map(CardResponse::of).toList());
    }
  }

  /**
   * Die Kennzahlen eines Zeitraums samt seiner Grenzen.
   *
   * @param nightRunCount Zahl der Nachtläufe, {@code interactiveRunCount} die der interaktiven
   *     Sitzungen; {@code runCount} ist ihre Summe (#984 AK 1)
   * @param interactiveUsageSince Erfassungsbeginn der interaktiven Sitzungen als ISO-Zeitpunkt;
   *     {@code null}, solange das Projekt keine gemeldet hat (Plan E18)
   */
  record PeriodFiguresResponse(
      NightRunPeriodType type,
      LocalDate firstDay,
      LocalDate lastDay,
      Instant from,
      Instant to,
      Coverage coverage,
      boolean noRuns,
      long runCount,
      long nightRunCount,
      long interactiveRunCount,
      long durationMs,
      long cardCount,
      SplitResponse usage,
      KindSplitResponse usageByKind,
      @Nullable Instant interactiveUsageSince) {

    static PeriodFiguresResponse of(PeriodFigures f) {
      return new PeriodFiguresResponse(
          f.period().type(),
          f.period().firstDay(),
          f.period().lastDay(),
          f.period().from(),
          f.period().to(),
          f.coverage(),
          f.noRuns(),
          f.runCount(),
          f.nightRunCount(),
          f.interactiveRunCount(),
          f.durationMs(),
          f.cardCount(),
          SplitResponse.of(f.usage()),
          KindSplitResponse.of(f.usageByKind()),
          f.interactiveUsageSince());
    }
  }

  /** Eine Nacht innerhalb eines Zeitraums. */
  record NightSummaryResponse(
      LocalDate night,
      long runCount,
      long cardCount,
      SplitResponse usage,
      KindSplitResponse usageByKind,
      boolean aborted) {

    static NightSummaryResponse of(NightSummary n) {
      return new NightSummaryResponse(
          n.night(),
          n.runCount(),
          n.cardCount(),
          SplitResponse.of(n.usage()),
          KindSplitResponse.of(n.usageByKind()),
          n.aborted());
    }
  }

  /**
   * Ein Vorhaben der Aufstellung; beim Posten „ohne Vorhaben" sind Kennung, Kürzel und Titel null.
   */
  record EpicResponse(
      @Nullable Long epicId,
      @Nullable String shortcode,
      @Nullable String title,
      long cardCount,
      UsageResponse usage) {

    static EpicResponse of(EpicUsageView e) {
      EpicRef ref = e.epic();
      return new EpicResponse(
          ref == null ? null : ref.id(),
          ref == null ? null : ref.shortcode(),
          ref == null ? null : ref.title(),
          e.cardCount(),
          UsageResponse.of(e.usage()));
    }
  }

  /**
   * Die Summe über die ganze Laufzeit.
   *
   * @param oldestRetainedRunStart Beginn des ältesten aufbewahrten Eintrags als ISO-Zeitpunkt;
   *     {@code null} ohne Eintrag. Daran ist ablesbar, ab wann die Summe abgedeckt ist.
   * @param interactiveUsageSince Erfassungsbeginn der interaktiven Sitzungen; {@code null}, solange
   *     das Projekt keine gemeldet hat (Plan E18)
   */
  record TotalResponse(
      long runCount,
      long nightRunCount,
      long interactiveRunCount,
      long cardCount,
      SplitResponse usage,
      KindSplitResponse usageByKind,
      @Nullable Instant oldestRetainedRunStart,
      @Nullable Instant interactiveUsageSince) {

    static TotalResponse of(TotalUsageView t) {
      return new TotalResponse(
          t.runCount(),
          t.nightRunCount(),
          t.interactiveRunCount(),
          t.cardCount(),
          SplitResponse.of(t.usage()),
          KindSplitResponse.of(t.usageByKind()),
          t.oldestRetainedRunStart(),
          t.interactiveUsageSince());
    }
  }

  /** Ein Zeitraum samt Vorzeitraum, Nächten und Vorhaben-Aufstellung. */
  record PeriodResponse(
      PeriodFiguresResponse current,
      PeriodFiguresResponse previous,
      List<NightSummaryResponse> nights,
      List<EpicResponse> epics,
      EpicResponse withoutEpic,
      boolean epicsOverlap) {

    static PeriodResponse of(PeriodUsageView p) {
      return new PeriodResponse(
          PeriodFiguresResponse.of(p.current()),
          PeriodFiguresResponse.of(p.previous()),
          p.nights().stream().map(NightSummaryResponse::of).toList(),
          p.epics().stream().map(EpicResponse::of).toList(),
          EpicResponse.of(p.withoutEpic()),
          p.epicsOverlap());
    }
  }
}
