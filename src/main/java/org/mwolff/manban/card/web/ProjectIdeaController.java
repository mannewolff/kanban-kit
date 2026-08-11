package org.mwolff.manban.card.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardView;
import org.mwolff.manban.common.TextLimits;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projektweiter Ideen-Pool: board-lose Ideen eines Projekts auflisten und anlegen. Session-Auth
 * erforderlich; Rechte prüft der {@link CardService} (Mitglied bzw. {@code TICKET_CREATE}).
 */
@RestController
class ProjectIdeaController {

  /**
   * Obergrenze für ein Stapel-Anlegen. Gewählt wie bei den bestehenden Bulk-Endpunkten ({@code
   * bulk-archive}/{@code bulk-delete}, {@code CardController}), damit im Projekt genau eine
   * Mengen-Obergrenze gilt statt zweier divergierender Zahlen. Für den Anwendungsfall
   * „Spezifikation importieren" ist der Wert reichlich bemessen — realistische Dokumente haben
   * Dutzende Abschnitte —, und er begrenzt die Verstärkung: Ein Aufruf kann nicht mehr Karten
   * erzeugen, als ein Bulk-Aufruf heute schon verändern darf. (Ein allgemeines Rate Limiting
   * existiert im Projekt bislang nirgends; diese Grenze ersetzt es nicht, sie deckelt nur diesen
   * Endpoint.)
   */
  static final int MAX_IDEAS_PER_BATCH = 200;

  private final CardService cards;

  ProjectIdeaController(CardService cards) {
    this.cards = cards;
  }

  @GetMapping("/api/projects/{projectId}/ideas")
  List<CardView> list(@AuthenticationPrincipal Long userId, @PathVariable long projectId) {
    return cards.listProjectIdeas(userId, projectId);
  }

  @PostMapping("/api/projects/{projectId}/ideas")
  @ResponseStatus(HttpStatus.CREATED)
  CardView create(
      @AuthenticationPrincipal Long userId,
      @PathVariable long projectId,
      @Valid @RequestBody CreateIdeaRequest request) {
    return cards.createProjectIdea(
        userId, projectId, request.title(), request.description(), request.targetBoardId());
  }

  /**
   * Legt mehrere Pool-Ideen in einem Zug an (#492) — Ziel des Spezifikations-Imports, der die
   * Markdown-Datei im Browser auflöst und nur die fertigen Karten hierher schickt. Antwort: die
   * angelegten Ideen in Eingabereihenfolge, jeweils mit {@code id} und vergebener {@code number}.
   *
   * <p>Alles-oder-nichts: Verletzt ein Element die Feldgrenzen, lehnt die Bean-Validation die ganze
   * Anfrage mit 400 ab, bevor der Service läuft — es entsteht keine einzige Idee. Begründung der
   * Entscheidung im Javadoc von {@link CardService#createProjectIdeas}.
   */
  @PostMapping("/api/projects/{projectId}/ideas/batch")
  @ResponseStatus(HttpStatus.CREATED)
  List<CardView> createBatch(
      @AuthenticationPrincipal Long userId,
      @PathVariable long projectId,
      @Valid @RequestBody CreateIdeasBatchRequest request) {
    List<CardService.NewIdea> ideas =
        request.ideas().stream()
            .map(i -> new CardService.NewIdea(i.title(), i.description()))
            .toList();
    return cards.createProjectIdeas(userId, projectId, ideas, request.targetBoardId());
  }

  record CreateIdeaRequest(
      @NotBlank @Size(max = 300) String title,
      @Nullable @Size(max = TextLimits.MAX_TEXT) String description,
      @Nullable Long targetBoardId) {}

  /**
   * Ein Element des Stapels. Titelgrenze wie an allen anderen Anlegewegen (300); die
   * Beschreibungsgrenze ist seit #572 dieselbe wie an allen anderen Textwegen ({@link
   * TextLimits#MAX_TEXT}).
   */
  record BatchIdeaItem(
      @NotBlank @Size(max = 300) String title,
      @Nullable @Size(max = TextLimits.MAX_TEXT) String description) {}

  /**
   * Eine leere Liste ist eine Fehleingabe und keine leere Erfolgsantwort ({@code @NotEmpty} → 400)
   * — dasselbe Verhalten wie bei den bestehenden Bulk-Endpunkten. Das {@code targetBoardId} gilt
   * für alle Elemente.
   */
  record CreateIdeasBatchRequest(
      @NotEmpty @Size(max = MAX_IDEAS_PER_BATCH) List<@Valid @NotNull BatchIdeaItem> ideas,
      @Nullable Long targetBoardId) {}
}
