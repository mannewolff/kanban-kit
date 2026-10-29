package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.web.Regionszone;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.nightrun.application.MobileLaeufeService;
import org.mwolff.manban.nightrun.application.MobileLaeufeService.LaufDetail;
import org.mwolff.manban.nightrun.application.MobileLaeufeService.LaufZeile;
import org.mwolff.manban.nightrun.domain.Meldegrund.Zustand;
import org.mwolff.manban.nightrun.domain.MobilerLauf.LetzteMeldung;
import org.mwolff.manban.nightrun.domain.MobilerLauf.MeldungsArt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Läufe auf dem Handy (Issue #1994, Plan #1989 E19): die Liste der laufenden, hängenden und
 * gescheiterten Nachtläufe und ein einzelner Lauf. Nur lesen; nur per Session ({@code
 * /api/mobil/**} in {@code ApiZugang}).
 */
@Tag(name = "Mobil")
@RestController
@RequestMapping("/api/mobil/laeufe")
class MobileLaeufeController {

  /** Wer welche Läufe sieht — für beide Aufrufe gleich. */
  private static final String SICHT =
      """
      Gezeigt werden die Nachtläufe der Projekte, in denen der Aufrufer die Rolle Owner hat, \
      als Plattform-Admin zusätzlich die der Projekte, die am Plattform-Leitstand teilnehmen. \
      Interaktive Sitzungen erscheinen nicht.""";

  /** Was die Zustände heißen. */
  private static final String ZUSTAENDE =
      """
      LAEUFT: arbeitet ohne Auffälligkeit. HAENGT: ein Paket wartet auf jemanden oder ist an \
      einer Prüfung festgefahren. GESCHEITERT: ein Paket ist gescheitert oder hat sein \
      Zeitbudget erschöpft, oder der Lauf meldet sich länger als die Stillefrist (90 Minuten) \
      nicht. Ein Paket zählt schon, während der Lauf weiterarbeitet.""";

  private final MobileLaeufeService service;

  MobileLaeufeController(MobileLaeufeService service) {
    this.service = service;
  }

  @Operation(
      summary = "Läufe für das Handy lesen",
      description =
          """
          Liefert die Nachtläufe der laufenden und der vorigen Nacht, die laufen, hängen oder \
          gescheitert sind, jüngster zuoberst. Eine Nacht reicht von 12:00 bis 12:00 in der \
          übergebenen Zone. Gelungene und ohne Befund beendete Läufe fehlen.\s"""
              + ZUSTAENDE
              + " "
              + SICHT)
  @ApiResponse(responseCode = "200", description = "Die Läufe.")
  @ApiResponse(
      responseCode = "400",
      description =
          "zone fehlt, ist unbekannt oder ist ein fester Versatz statt einer Regionszone.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping
  List<LaufView> laeufe(
      @AuthenticationPrincipal Long userId,
      @Parameter(
              description =
                  "Regionszone des Lesers, in der die Nachtgrenze um 12:00 gezogen wird; ein"
                      + " fester Versatz wie +02:00 wird abgewiesen.",
              example = "Europe/Berlin")
          @RequestParam
          ZoneId zone) {
    return service.laeufe(userId, Regionszone.of(zone)).stream().map(LaufView::of).toList();
  }

  @Operation(
      summary = "Einen Lauf für das Handy lesen",
      description =
          """
          Liefert einen Nachtlauf mit Zustand, Dauer und letzter Meldung, auch einen \
          gelungenen oder älteren: Dann heißt der Zustand BEENDET. Die letzte Meldung ist der \
          Abbruchgrund (ABBRUCHGRUND), sonst der Auszug des Pakets, das den Zustand trägt \
          (PAKET), sonst beim verstummten Lauf sein letztes Lebenszeichen (STILLE), sonst die \
          zuletzt begonnene Stufe (STUFE); ohne all das fehlt sie.\s"""
              + ZUSTAENDE
              + " "
              + SICHT)
  @ApiResponse(responseCode = "200", description = "Der Lauf.")
  @ApiResponse(
      responseCode = "404",
      description =
          "Den Nachtlauf gibt es nicht, oder der Aufrufer darf die Läufe seines Projekts nicht"
              + " sehen.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/{laufId}")
  LaufDetailView lauf(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Kennung des Laufs.", example = "4711") @PathVariable long laufId) {
    return LaufDetailView.of(service.lauf(userId, laufId));
  }

  /** Eine Zeile der Liste. */
  @Schema(description = "Ein Nachtlauf in der Liste des Handys.")
  record LaufView(
      @Schema(description = "Kennung des Laufs.", example = "4711") long laufId,
      @Schema(description = "Kennung des Projekts.", example = "7") long projectId,
      @Schema(description = "Name des Projekts.", example = "Leitstand") String projectName,
      @Schema(
              description =
                  "Board, über dessen Zugangsschlüssel der Lauf gemeldet wird; null, wenn keines"
                      + " erkennbar ist.",
              example = "40")
          @Nullable Long boardId,
      @Schema(description = "Name dieses Boards; null ohne Board.", example = "Produkt")
          @Nullable String boardName,
      @Schema(description = "LAEUFT, HAENGT oder GESCHEITERT.", example = "GESCHEITERT")
          Zustand zustand,
      @Schema(description = "Beginn des Laufs.", example = "2026-10-28T22:00:00Z")
          Instant startedAt,
      @Schema(
              description =
                  "Dauer in Millisekunden: beim laufenden Lauf bis jetzt, beim verstummten bis"
                      + " zu seinem letzten Lebenszeichen, sonst die gemeldete.",
              example = "3600000")
          long durationMs) {

    static LaufView of(LaufZeile zeile) {
      return new LaufView(
          zeile.laufId(),
          zeile.projectId(),
          zeile.projectName(),
          zeile.boardId(),
          zeile.boardName(),
          zeile.zustand(),
          zeile.startedAt(),
          zeile.dauer().toMillis());
    }
  }

  /** Die letzte Meldung als Daten; den Satz bildet die Oberfläche. */
  @Schema(description = "Die letzte Meldung eines Laufs.")
  record MeldungView(
      @Schema(description = "ABBRUCHGRUND, PAKET, STILLE oder STUFE.", example = "PAKET")
          MeldungsArt art,
      @Schema(
              description = "Abbruchgrund oder Auszug des Pakets; sonst null.",
              example = "Pruefung rot: mvn verify")
          @Nullable String text,
      @Schema(description = "Karte des Pakets oder der Stufe; sonst null.", example = "1975")
          @Nullable Integer cardNumber,
      @Schema(description = "Name der Stufe bei STUFE; sonst null.", example = "umsetzung")
          @Nullable String stage,
      @Schema(
              description =
                  "Bei STILLE das letzte Lebenszeichen, bei STUFE der Beginn der Stufe; sonst"
                      + " null.",
              example = "2026-10-28T23:30:00Z")
          @Nullable Instant seit) {

    static @Nullable MeldungView of(@Nullable LetzteMeldung meldung) {
      return meldung == null
          ? null
          : new MeldungView(
              meldung.art(), meldung.text(), meldung.cardNumber(), meldung.stage(), meldung.seit());
    }
  }

  /** Ein einzelner Lauf. */
  @Schema(description = "Ein Nachtlauf mit seiner letzten Meldung.")
  record LaufDetailView(
      @Schema(description = "Der Lauf; zustand kann hier auch BEENDET sein.") LaufView lauf,
      @Schema(description = "Die letzte Meldung; null, wenn der Lauf keine hat.")
          @Nullable MeldungView meldung) {

    static LaufDetailView of(LaufDetail detail) {
      return new LaufDetailView(LaufView.of(detail.zeile()), MeldungView.of(detail.meldung()));
    }
  }
}
