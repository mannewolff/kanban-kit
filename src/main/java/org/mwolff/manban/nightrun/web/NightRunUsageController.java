package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.EpicRef;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.CardTotals;
import org.mwolff.manban.nightrun.application.NightRunUsageService;
import org.mwolff.manban.nightrun.application.NightRunUsageService.Coverage;
import org.mwolff.manban.nightrun.application.NightRunUsageService.EpicUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.KindSplit;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightSummary;
import org.mwolff.manban.nightrun.application.NightRunUsageService.NightUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.PeriodFigures;
import org.mwolff.manban.nightrun.application.NightRunUsageService.PeriodUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.StageUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.TotalUsageView;
import org.mwolff.manban.nightrun.application.NightRunUsageService.UsageSplit;
import org.mwolff.manban.nightrun.domain.NightRunPeriodType;
import org.mwolff.manban.nightrun.domain.NightRunStage;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Verbrauchs-Auswertung an HTTP (Issue #939, Plan #933): ein Abruf je Sicht (Plan E2).
 *
 * <p><b>Die Zone kommt vom Leser</b> (Plan E4) und ist eine Regionszone — eingegrenzt von {@link
 * Regionszone}, das seit Issue #1095 auch der Plattform-Leitstand nutzt.
 *
 * <p>Keine eigenen Exceptions: 403 und 404 liefert {@code requireOwner} im Service, 400 die
 * Bindung, die Parameter-Validierung und die Abweisung der Zone über {@link
 * ResponseStatusException}.
 */
@Tag(name = "Läufe (Runner)")
@RestController
class NightRunUsageController {

  /** Was eine Nacht ist — gemeinsam für Nacht- und Zeitraumansicht. */
  private static final String NACHT =
      " Eine Nacht reicht von 12:00 bis 12:00 in der übergebenen Zone und heißt nach dem Datum"
          + " ihres Beginns.";

  /** Wie der Verbrauch geteilt wird — gemeinsam für alle drei Ansichten. */
  private static final String TEILUNG =
      " Verbrauch steht dreigeteilt: total ist der gemeldete Verbrauch der Läufe, cardShare der"
          + " Anteil ihrer Arbeitspakete (der Karten), remainder der Rest, der keiner Karte"
          + " zuzuordnen ist. usageByKind teilt dasselbe nach Nachtläufen (night) und interaktiven"
          + " Sitzungen (interactive). Ein fehlender Wert bleibt null und wird nie als 0"
          + " gezählt.";

  private static final String ZONE_FALSCH =
      "Ein Parameter fehlt oder ist ungültig; zone ist unbekannt oder ein fester Versatz statt"
          + " einer Regionszone.";

  private static final String BESCHREIBUNG_ZONE =
      "Regionszone des Lesers, in der die Grenzen um 12:00 gezogen werden; ein fester Versatz"
          + " wie +02:00 wird abgewiesen.";

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
  @Operation(
      summary = "Verbrauch einer Nacht lesen",
      description =
          "Liefert Zahl der Läufe, Dauer und Verbrauch einer Nacht, je Karte (cards) und je"
              + " Stufe einer Kette (stages). Ein Lauf ist eine Sitzung eines Agenten, die"
              + " Karten abarbeitet; eine Kette ist ein Lauf, der eine fachliche Anforderung über"
              + " die Stufen PLAN, REVIEW, PAKETE und ABDECKUNG führt."
              + NACHT
              + TEILUNG
              + " "
              + NightRunController.LESERECHT)
  @ApiResponse(responseCode = "200", description = "Die Nacht.")
  @ApiResponse(
      responseCode = "400",
      description = ZONE_FALSCH,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = NightRunController.LESEN_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = NightRunController.PROJEKT_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/night-run-usage/night")
  NightResponse night(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = NightRunController.BESCHREIBUNG_PROJEKT_ID, example = "7")
          @PathVariable
          long projectId,
      @Parameter(description = "Datum, an dem die Nacht beginnt.", example = "2026-10-04")
          @RequestParam
          LocalDate date,
      @Parameter(description = BESCHREIBUNG_ZONE, example = "Europe/Berlin") @RequestParam
          ZoneId zone) {
    return NightResponse.of(usage.night(userId, projectId, date, Regionszone.of(zone)));
  }

  /** Ein Zeitraum samt Vorzeitraum, Nächten und Vorhaben-Aufstellung. */
  @Operation(
      summary = "Verbrauch eines Zeitraums lesen",
      description =
          "Liefert die Kennzahlen eines Tages, einer Woche oder eines Monats (current) samt"
              + " dem unmittelbar vorangegangenen gleichartigen Zeitraum (previous), dazu die"
              + " Nächte des Zeitraums, die Aufstellung nach Vorhaben (epics, withoutEpic) und"
              + " die Kosten je Stufe einer Kette (stages). Eine Kette ist ein Lauf, der eine"
              + " fachliche Anforderung über die Stufen PLAN, REVIEW, PAKETE und ABDECKUNG führt."
              + " coverage sagt, ob die Aufbewahrung Läufe des Zeitraums schon verdrängt hat."
              + NACHT
              + TEILUNG
              + " "
              + NightRunController.LESERECHT)
  @ApiResponse(responseCode = "200", description = "Zeitraum und Vorzeitraum.")
  @ApiResponse(
      responseCode = "400",
      description = ZONE_FALSCH + " Ebenso: stepsBack außerhalb von 0 bis 366.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = NightRunController.LESEN_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = NightRunController.PROJEKT_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/night-run-usage")
  PeriodResponse period(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = NightRunController.BESCHREIBUNG_PROJEKT_ID, example = "7")
          @PathVariable
          long projectId,
      @Parameter(
              description = "Art des Zeitraums: DAY, WEEK (ISO-Woche ab Montag) oder MONTH.",
              example = "WEEK")
          @RequestParam
          NightRunPeriodType type,
      @Parameter(
              description =
                  "Wie viele Zeiträume zurück; 0 ist der zuletzt abgeschlossene, höchstens 366.",
              example = "0")
          @RequestParam
          @Min(0)
          @Max(MAX_STEPS_BACK)
          int stepsBack,
      @Parameter(description = BESCHREIBUNG_ZONE, example = "Europe/Berlin") @RequestParam
          ZoneId zone) {
    return PeriodResponse.of(
        usage.period(userId, projectId, type, stepsBack, Regionszone.of(zone)));
  }

  /**
   * Die Summe über die ganze Laufzeit des Projekts (Plan E19). Ein eigener Abruf und kein vierter
   * Wert für {@code type}: Eine Lebenszeit hat keinen ersten Tag, keinen Vorzeitraum und keine
   * Zone, nach der sie sich gruppieren ließe — sie braucht deshalb keinen Parameter.
   */
  @Operation(
      summary = "Verbrauch über die ganze Laufzeit lesen",
      description =
          "Liefert Zahl der Läufe und Karten und den Verbrauch über alle aufbewahrten Läufe des"
              + " Projekts, ohne Zeitraum und ohne Zone. oldestRetainedRunStart zeigt, ab wann"
              + " die Summe reicht — ältere Läufe hat die Aufbewahrung verdrängt;"
              + " interactiveUsageSince, seit wann interaktive Sitzungen überhaupt erfasst"
              + " werden."
              + TEILUNG
              + " "
              + NightRunController.LESERECHT)
  @ApiResponse(responseCode = "200", description = "Die Summe über die ganze Laufzeit.")
  @ApiResponse(
      responseCode = "403",
      description = NightRunController.LESEN_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = NightRunController.PROJEKT_UNBEKANNT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/night-run-usage/total")
  TotalResponse total(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = NightRunController.BESCHREIBUNG_PROJEKT_ID, example = "7")
          @PathVariable
          long projectId) {
    return TotalResponse.of(usage.total(userId, projectId));
  }

  /** Die vier Verbrauchsangaben und der Anteil aus dem Zwischenspeicher; fehlend bleibt null. */
  @Schema(description = "Verbrauch; ein fehlender Wert ist null — nicht gemessen, nicht 0.")
  record UsageResponse(
      @Schema(description = "Kosten in US-Dollar.", example = "1.25") @Nullable BigDecimal costUsd,
      @Schema(description = "Eingabe-Tokens.", example = "120000") @Nullable Long inputTokens,
      @Schema(description = "Ausgabe-Tokens.", example = "8000") @Nullable Long outputTokens,
      @Schema(description = "Davon aus dem Cache gelesene Eingabe-Tokens.", example = "90000")
          @Nullable Long cachedInputTokens,
      @Schema(description = "Anteil der Eingabe aus dem Cache in Prozent.", example = "75.0")
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
  @Schema(description = "Verbrauch dreigeteilt: gesamt, Anteil der Karten, Rest.")
  record SplitResponse(
      @Schema(description = "Gemeldeter Verbrauch der Läufe insgesamt.") UsageResponse total,
      @Schema(description = "Anteil der Arbeitspakete, also der Karten.") UsageResponse cardShare,
      @Schema(description = "Rest, der keiner Karte zuzuordnen ist; total minus cardShare.")
          UsageResponse remainder) {

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
  @Schema(description = "Dieselbe Teilung je Gattung.")
  record KindSplitResponse(
      @Schema(description = "Nachtläufe des Runners.") SplitResponse night,
      @Schema(description = "Interaktive Sitzungen am Rechner.") SplitResponse interactive) {

    static KindSplitResponse of(KindSplit s) {
      return new KindSplitResponse(SplitResponse.of(s.night()), SplitResponse.of(s.interactive()));
    }
  }

  /** Der Verbrauch einer Kartenzeile je Gattung. */
  @Schema(description = "Verbrauch einer Karte je Gattung.")
  record KindUsageResponse(
      @Schema(description = "Aus Nachtläufen.") UsageResponse night,
      @Schema(description = "Aus interaktiven Sitzungen.") UsageResponse interactive) {

    static KindUsageResponse of(CardTotals c) {
      return new KindUsageResponse(
          UsageResponse.of(c.nightUsage()), UsageResponse.of(c.interactiveUsage()));
    }
  }

  /** Eine Kartenzeile. */
  @Schema(description = "Eine Karte mit ihren Anläufen im betrachteten Zeitraum.")
  record CardResponse(
      @Schema(description = "Projektweite Nummer der Karte.", example = "1403") int cardNumber,
      @Schema(description = "Zahl der Anläufe an der Karte über beide Gattungen.", example = "2")
          long attemptCount,
      @Schema(
              description = "Summe der Dauern in Millisekunden; null ohne Dauer.",
              example = "1800000")
          @Nullable Long durationMs,
      @Schema(description = "Verbrauch der Karte.") UsageResponse usage,
      @Schema(description = "Verbrauch der Karte je Gattung.") KindUsageResponse usageByKind) {

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
  @Schema(description = "Eine Nacht mit ihren Karten und Stufen.")
  record NightResponse(
      @Schema(description = "Datum, an dem die Nacht beginnt.", example = "2026-10-04")
          LocalDate night,
      @Schema(description = "Zahl der Läufe der Nacht.", example = "2") long runCount,
      @Schema(description = "Summe der Laufdauern in Millisekunden.", example = "7200000")
          long durationMs,
      @Schema(description = "Zahl der verschiedenen Karten.", example = "5") long cardCount,
      @Schema(description = "Verbrauch dreigeteilt.") SplitResponse usage,
      @Schema(description = "Verbrauch je Gattung.") KindSplitResponse usageByKind,
      @Schema(
              description = "true, wenn in dieser Nacht ein Lauf abgebrochen ist.",
              example = "false")
          boolean aborted,
      @Schema(description = "Die Karten der Nacht.") List<CardResponse> cards,
      @Schema(description = "Kosten je Stufe einer Kette; leer, wenn keine Kette lief.")
          List<StageResponse> stages) {

    static NightResponse of(NightUsageView n) {
      return new NightResponse(
          n.night(),
          n.runCount(),
          n.durationMs(),
          n.cardCount(),
          SplitResponse.of(n.usage()),
          KindSplitResponse.of(n.usageByKind()),
          n.aborted(),
          n.cards().stream().map(CardResponse::of).toList(),
          n.stages().stream().map(StageResponse::of).toList());
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
  @Schema(description = "Die Kennzahlen eines Zeitraums samt seiner Grenzen.")
  record PeriodFiguresResponse(
      @Schema(description = "Art des Zeitraums.", example = "WEEK") NightRunPeriodType type,
      @Schema(description = "Erster Tag des Zeitraums.", example = "2026-09-28") LocalDate firstDay,
      @Schema(description = "Letzter Tag des Zeitraums.", example = "2026-10-04") LocalDate lastDay,
      @Schema(description = "Beginn als Zeitpunkt.", example = "2026-09-28T10:00:00Z") Instant from,
      @Schema(description = "Ende als Zeitpunkt.", example = "2026-10-05T10:00:00Z") Instant to,
      @Schema(
              description =
                  "COMPLETE: kein Lauf des Zeitraums verdrängt; PARTIAL: der Zeitraum reicht"
                      + " über die Aufbewahrungsgrenze; BEFORE_RETENTION: er endet davor.",
              example = "COMPLETE")
          Coverage coverage,
      @Schema(description = "true, wenn im Zeitraum kein Lauf liegt.", example = "false")
          boolean noRuns,
      @Schema(description = "Summe aus Nachtläufen und Sitzungen.", example = "9") long runCount,
      @Schema(description = "Zahl der Nachtläufe.", example = "7") long nightRunCount,
      @Schema(description = "Zahl der interaktiven Sitzungen.", example = "2")
          long interactiveRunCount,
      @Schema(description = "Summe der Laufdauern in Millisekunden.", example = "25200000")
          long durationMs,
      @Schema(description = "Zahl der verschiedenen Karten.", example = "14") long cardCount,
      @Schema(description = "Verbrauch dreigeteilt.") SplitResponse usage,
      @Schema(description = "Verbrauch je Gattung.") KindSplitResponse usageByKind,
      @Schema(
              description =
                  "Beginn der Erfassung interaktiver Sitzungen; null, solange keine gemeldet"
                      + " wurde.",
              example = "2026-09-01T08:00:00Z")
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
  @Schema(description = "Eine Nacht innerhalb eines Zeitraums.")
  record NightSummaryResponse(
      @Schema(description = "Datum, an dem die Nacht beginnt.", example = "2026-10-04")
          LocalDate night,
      @Schema(description = "Zahl der Läufe der Nacht.", example = "2") long runCount,
      @Schema(description = "Zahl der verschiedenen Karten.", example = "5") long cardCount,
      @Schema(description = "Verbrauch dreigeteilt.") SplitResponse usage,
      @Schema(description = "Verbrauch je Gattung.") KindSplitResponse usageByKind,
      @Schema(
              description = "true, wenn in dieser Nacht ein Lauf abgebrochen ist.",
              example = "false")
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
  @Schema(
      description =
          "Ein Vorhaben mit dem Verbrauch seiner Karten; beim Posten ohne Vorhaben sind Kennung,"
              + " Kürzel und Titel null.")
  record EpicResponse(
      @Schema(description = "Kennung des Vorhabens.", example = "12") @Nullable Long epicId,
      @Schema(description = "Kürzel des Vorhabens.", example = "API") @Nullable String shortcode,
      @Schema(description = "Titel des Vorhabens.", example = "API-Übersicht")
          @Nullable String title,
      @Schema(description = "Zahl der Karten, die beitragen.", example = "6") long cardCount,
      @Schema(description = "Summe ihrer Verbräuche.") UsageResponse usage) {

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
   * Eine Stufe der Kette in der Aufstellung (Issue #1114, #993 AK 8).
   *
   * <p>Der Verbrauch kommt als {@link UsageResponse} wie überall sonst. Modellzeit und Züge werden
   * je Stufe zwar summiert, treten hier aber nicht auf: {@code UsageResponse} behält seine heutigen
   * Felder, und ein zweiter Verbrauchs-Satz nur für die Stufen wäre eine zweite Form derselben
   * Sache.
   */
  @Schema(description = "Eine Stufe einer Kette mit ihrem Verbrauch.")
  record StageResponse(
      @Schema(description = "Die Stufe: PLAN, REVIEW, PAKETE oder ABDECKUNG.", example = "PLAN")
          NightRunStage stage,
      @Schema(
              description = "Zahl der Arbeitspakete, die die Stufe durchlaufen haben.",
              example = "3")
          long itemCount,
      @Schema(
              description = "Summe der Dauern in Millisekunden; null ohne Dauer.",
              example = "600000")
          @Nullable Long durationMs,
      @Schema(description = "Summe der Verbräuche.") UsageResponse usage) {

    static StageResponse of(StageUsageView s) {
      return new StageResponse(
          s.stage(), s.itemCount(), s.durationMs(), UsageResponse.of(s.usage()));
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
  @Schema(description = "Die Summe über die ganze Laufzeit des Projekts.")
  record TotalResponse(
      @Schema(description = "Summe aus Nachtläufen und Sitzungen.", example = "190") long runCount,
      @Schema(description = "Zahl der Nachtläufe.", example = "150") long nightRunCount,
      @Schema(description = "Zahl der interaktiven Sitzungen.", example = "40")
          long interactiveRunCount,
      @Schema(description = "Zahl der verschiedenen Karten.", example = "320") long cardCount,
      @Schema(description = "Verbrauch dreigeteilt.") SplitResponse usage,
      @Schema(description = "Verbrauch je Gattung.") KindSplitResponse usageByKind,
      @Schema(
              description =
                  "Beginn des ältesten aufbewahrten Laufs; null ohne Lauf. Ab hier reicht die"
                      + " Summe.",
              example = "2026-08-01T01:00:00Z")
          @Nullable Instant oldestRetainedRunStart,
      @Schema(
              description =
                  "Beginn der Erfassung interaktiver Sitzungen; null, solange keine gemeldet"
                      + " wurde.",
              example = "2026-09-01T08:00:00Z")
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
  @Schema(description = "Ein Zeitraum samt Vorzeitraum, Nächten und Vorhaben-Aufstellung.")
  record PeriodResponse(
      @Schema(description = "Der angefragte Zeitraum.") PeriodFiguresResponse current,
      @Schema(description = "Der unmittelbar vorangegangene gleichartige Zeitraum.")
          PeriodFiguresResponse previous,
      @Schema(description = "Die Nächte des Zeitraums.") List<NightSummaryResponse> nights,
      @Schema(description = "Die Vorhaben, absteigend nach Kosten.") List<EpicResponse> epics,
      @Schema(description = "Die Karten ohne Vorhaben.") EpicResponse withoutEpic,
      @Schema(
              description =
                  "true, wenn eine Karte zu mehreren Vorhaben gehört — dann ergeben die"
                      + " Vorhaben-Summen zusammen mehr als den Anteil der Karten.",
              example = "false")
          boolean epicsOverlap,
      @Schema(description = "Kosten je Stufe einer Kette; leer, wenn keine Kette lief.")
          List<StageResponse> stages) {

    static PeriodResponse of(PeriodUsageView p) {
      return new PeriodResponse(
          PeriodFiguresResponse.of(p.current()),
          PeriodFiguresResponse.of(p.previous()),
          p.nights().stream().map(NightSummaryResponse::of).toList(),
          p.epics().stream().map(EpicResponse::of).toList(),
          EpicResponse.of(p.withoutEpic()),
          p.epicsOverlap(),
          p.stages().stream().map(StageResponse::of).toList());
    }
  }
}
