package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardNumbers;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.EpicService;
import org.mwolff.manban.card.application.EpicService.DerivationNodeView;
import org.mwolff.manban.card.application.EpicService.EpicView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vorhaben und Herkunft (Issue #1393, Plan #1387 E12): Vorhaben eines Boards mit Fortschritt, der
 * Herkunftsbaum eines Vorhabens, die Zuordnung einer Karte zu einem Vorhaben, ihre Herkunft, die
 * Anforderungskarte und das Eröffnen eines Vorgangs. Pfade, HTTP-Methoden, Rechte und Antworten
 * sind unverändert die aus {@link CardController}; delegiert wird an den {@link EpicService}.
 */
@RestController
class EpicController {

  private final EpicService epicService;

  EpicController(EpicService epicService) {
    this.epicService = epicService;
  }

  @GetMapping("/api/boards/{boardId}/epics")
  List<EpicView> epics(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
    return epicService.listEpics(userId, boardId);
  }

  /**
   * Herkunftsbaum eines Vorhabens — dieselbe Rechnung wie beim board-weiten Baum, angewandt auf die
   * Mitglieder dieses Vorhabens (Issue #643).
   *
   * <p>Der Pfad endet bewusst auf {@code /tree} und wiederholt den Pfadbestandteil des board-weiten
   * Endpunkts darüber nicht: Der kommt im Controller genau einmal vor, und daran bleibt sein
   * Rückbau maschinell prüfbar.
   */
  @GetMapping("/api/boards/{boardId}/epics/{epicId}/tree")
  List<DerivationNodeView> epicTree(
      @AuthenticationPrincipal Long userId, @PathVariable long boardId, @PathVariable long epicId) {
    return epicService.epicDerivationTree(userId, boardId, epicId);
  }

  /**
   * Ordnet eine Karte einem Vorhaben zu ({@code parentId}) oder löst die Zuordnung ({@code
   * parentId: null}).
   */
  @PatchMapping("/api/cards/{cardId}/parent")
  CardView assignParent(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @RequestBody AssignParentRequest request) {
    return epicService.assignParent(userId, cardId, request.parentId());
  }

  /**
   * Setzt die Herkunft einer Karte ({@code derivedFrom} als projektweite Kartennummer) oder löscht
   * sie ({@code derivedFrom: null}).
   *
   * <p>Eigener Endpunkt statt eines Feldes in {@link CardController.UpdateCardRequest}: Jener Pfad
   * ist ein Voll-Update, und ein fehlendes JSON-Feld ist in einem Jackson-Record nicht von {@code
   * null} zu unterscheiden — jeder bestehende Client haette die Herkunft bei jedem Karten-Edit
   * geloescht (Issue #607).
   */
  @PatchMapping("/api/cards/{cardId}/derived-from")
  CardView assignDerivedFrom(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody AssignDerivedFromRequest request) {
    return epicService.assignDerivedFrom(userId, cardId, request.derivedFrom());
  }

  /**
   * Eröffnet einen Vorgang an dieser Karte: Vorhaben anlegen, Karte als Anforderung setzen und ihr
   * zuordnen — in einem Aufruf. Kartenzentriert wie {@code move}, {@code transfer} und {@code
   * archive}; die Antwort ist die Sicht des <b>neuen Vorhabens</b>.
   */
  @PostMapping("/api/cards/{cardId}/open-epic")
  @ResponseStatus(HttpStatus.CREATED)
  CardView openEpic(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody OpenEpicRequest request) {
    return epicService.openEpicFromCard(userId, cardId, request.kuerzel(), request.name());
  }

  /**
   * Setzt oder löscht die Anforderungskarte eines Vorhabens.
   *
   * <p>Schmaler Endpunkt wie {@code derived-from} (#607): Ein Voll-Update kann ein fehlendes Feld
   * nicht von {@code null} unterscheiden und löschte die Zuordnung bei jedem Karten-Edit. Übergabe
   * von {@code null} löscht sie ausdrücklich.
   */
  @PatchMapping("/api/cards/{cardId}/requirement")
  CardView assignRequirement(
      @AuthenticationPrincipal Long userId,
      @PathVariable long cardId,
      @Valid @RequestBody AssignRequirementRequest request) {
    return epicService.assignRequirement(userId, cardId, request.requirementCardNumber());
  }

  record AssignParentRequest(Long parentId) {}

  // Dieselben Grenzen wie im kanbancompat-Ingest (`CreateItemRequest`), damit beide Schreibpfade
  // dieselbe Nummer akzeptieren und dieselbe ablehnen.
  record AssignDerivedFromRequest(@Nullable @Positive @Max(CardNumbers.MAX) Integer derivedFrom) {}

  record AssignRequirementRequest(
      @Nullable @Positive @Max(CardNumbers.MAX) Integer requirementCardNumber) {}

  record OpenEpicRequest(@Nullable String kuerzel, @NotBlank String name) {}
}
