package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.ReadyBewertung;
import org.mwolff.manban.nightrun.domain.Wartegrund;
import org.mwolff.manban.nightrun.domain.Wartegrund.Laufgrund;
import org.mwolff.manban.nightrun.domain.Wartegrund.LaufgrundArt;
import org.mwolff.manban.nightrun.domain.Wartegrund.Vermerk;
import org.mwolff.manban.nightrun.domain.Wartegrund.VermerkArt;

/**
 * Antwort von {@code /night-wait}: warum eine Karte in Ready wartet (Issue #1728).
 *
 * <p>Eine eigene Datei wie {@link RunnerNowView}.
 */
@Schema(description = "Warum eine Karte in Ready wartet.")
record NightWaitView(
    @Schema(description = "Gründe aus den Läufen des Projekts.") List<RunReasonView> runReasons,
    @Schema(description = "Gründe aus der jüngsten Bewertung des Runners, wie gemeldet.")
        List<CardReasonView> cardReasons,
    @Schema(
            description = "Startbare Pakete vor der Karte (position − 1); null ohne Position.",
            example = "2")
        @Nullable Integer packagesAhead,
    @Schema(description = "Vermerke über das Alter der Bewertung — keine Wartegründe.")
        List<NoteView> notes,
    @Schema(description = "Kein Hinderungsgrund festgestellt.", example = "false")
        boolean startable) {

  static NightWaitView of(Wartegrund w) {
    return new NightWaitView(
        w.laufgruende().stream().map(RunReasonView::of).toList(),
        w.kartengruende().stream().map(CardReasonView::of).toList(),
        w.paketeDavor(),
        w.vermerke().stream().map(NoteView::of).toList(),
        w.startbereit());
  }

  /** Ein Grund aus den Läufen (Plan #1724 E8). */
  @Schema(description = "Ein Grund aus dem Laufzustand.")
  record RunReasonView(
      @Schema(description = "KEINE_UMSETZUNGSNACHT, VERSTUMMT oder BELEGT.", example = "BELEGT")
          LaufgrundArt code,
      @Schema(
              description = "Bei VERSTUMMT die letzte Meldung des Laufs; sonst null.",
              example = "2026-10-13T23:05:00Z")
          @Nullable Instant since,
      @Schema(
              description =
                  """
                  Bei VERSTUMMT die zuletzt begonnene Stufe, bei BELEGT die belegende; null, \
                  wenn keine gemeldet ist.""",
              example = "VORBEREITUNG")
          @Nullable String stage,
      @Schema(
              description = "Art des Laufs: CHAIN oder IMPLEMENTATION; null ohne Lauf.",
              example = "CHAIN")
          @Nullable NightRunMode runMode,
      @Schema(description = "Bei BELEGT die Karte der belegenden Stufe.", example = "1665")
          @Nullable Integer cardNumber) {

    static RunReasonView of(Laufgrund g) {
      return new RunReasonView(g.art(), g.seit(), g.stufe(), g.laufArt(), g.karte());
    }
  }

  /** Ein Grund der Karte aus der Bewertung (Vertrag V2). */
  @Schema(description = "Ein Grund des Runners, wie gemeldet.")
  record CardReasonView(
      @Schema(description = "Code des Grundes.", example = "DEPENDENCY") String code,
      @Schema(description = "Bei NOT_A_WORK_PACKAGE die Art der Karte.", example = "PLAN")
          @Nullable String kind,
      @Schema(description = "Bei DEPENDENCY die Karten, auf die gewartet wird.")
          List<Integer> cardNumbers,
      @Schema(description = "Bei PROTECTED_FILE die geschützten Pfade.") List<String> paths,
      @Schema(description = "Wortlaut des Runners.", example = "Abhaengigkeit #1698 nicht erfuellt")
          String text) {

    static CardReasonView of(ReadyBewertung.Grund g) {
      return new CardReasonView(g.code(), g.kind(), g.cardNumbers(), g.paths(), g.text());
    }
  }

  /** Ein Vermerk über das Alter der Bewertung (Plan #1724 E9). */
  @Schema(description = "Ein Vermerk über das Wissen des Boards.")
  record NoteView(
      @Schema(
              description = "NICHT_BEWERTET oder SEIT_BEWERTUNG_GEAENDERT.",
              example = "NICHT_BEWERTET")
          VermerkArt code,
      @Schema(
              description = "Zeitpunkt der jüngsten Bewertung; null, wenn es keine gibt.",
              example = "2026-10-13T22:40:00Z")
          @Nullable Instant assessedAt) {

    static NoteView of(Vermerk v) {
      return new NoteView(v.art(), v.zeit());
    }
  }
}
