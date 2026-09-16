package org.mwolff.manban.nightrun.web;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.NightRunService;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Anläufe einer Karte über Läufe hinweg (Issue #967).
 *
 * <p>Der Endpunkt liegt im Modul {@code nightrun} und nicht in {@code card}: Andersherum zeigte
 * {@code card} auf {@code nightrun}, und die Karte braucht die Läufe für nichts anderes. Die Kante
 * hält {@code ArchitectureTest} fest.
 *
 * <p>Keine eigenen Exceptions: 403 und 404 liefert {@code requireOwner} im {@link NightRunService},
 * 400 eine fehlende oder unlesbare Kartennummer.
 */
@RestController
class NightRunCardController {

  private final NightRunService runs;

  NightRunCardController(NightRunService runs) {
    this.runs = runs;
  }

  /** Jüngster Anlauf zuerst; ein verdrängter Lauf hinterlässt seine Anläufe weiterhin. */
  @GetMapping("/api/projects/{projectId}/night-runs/items")
  List<NightRunAnlaufView> anlaeufe(
      @AuthenticationPrincipal Long userId,
      @PathVariable long projectId,
      @RequestParam int cardNumber) {
    return runs.anlaeufeDerKarte(userId, projectId, cardNumber).stream()
        .map(NightRunCardController::view)
        .toList();
  }

  private static NightRunAnlaufView view(NightRunItem item) {
    return new NightRunAnlaufView(
        item.startedAt(),
        item.mode(),
        item.state(),
        item.errorClass(),
        item.durationMs(),
        item.commitHash(),
        item.usage());
  }

  /**
   * Ein Anlauf an der Karte.
   *
   * @param startedAt Startzeitpunkt des Laufs
   * @param mode Lauf-Art
   * @param state Ausgang des Anlaufs
   * @param errorClass Grund eines nicht-grünen Ausgangs
   * @param durationMs Dauer; {@code null} bei übergangenen Paketen
   * @param commitHash Commit der Session; {@code null}, wenn nichts festgeschrieben wurde
   * @param usage die vier Verbrauchszahlen; {@code null}, wenn nichts gemessen wurde — nie 0
   */
  record NightRunAnlaufView(
      Instant startedAt,
      NightRunMode mode,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable String commitHash,
      @Nullable NightRunUsage usage) {}
}
