package org.mwolff.manban.kanbancompat.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.card.application.CardNumbers;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Comment;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Created;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Epic;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Item;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Toolbox-Kanban-API für tbx.mjs / board.mjs (Dogfooding, #45). Vertragsgleich mit dem bestehenden
 * Toolbox-Backend: der Client sendet nur den Token ({@code X-Kanban-Token}); das Board kommt aus
 * der Token-Bindung (#44), die der {@code PatAuthenticationFilter} an die Authentication-{@code
 * details} hängt. Nur per PAT erreichbar (SecurityConfig).
 */
@RestController
@RequestMapping("/api/kanban")
class KanbanCompatController {

  private final KanbanCompatService service;

  KanbanCompatController(KanbanCompatService service) {
    this.service = service;
  }

  @GetMapping("/items")
  Map<String, List<Item>> items(@Nullable Authentication authentication) {
    return service.items(principal(authentication));
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  Created create(
      @Nullable Authentication authentication, @Valid @RequestBody CreateItemRequest request) {
    return service.create(
        principal(authentication),
        request.title(),
        request.body(),
        request.column(),
        Boolean.TRUE.equals(request.ideaStored()),
        request.externalKey(),
        Boolean.TRUE.equals(request.direct()),
        request.number());
  }

  @PutMapping("/items/{id}")
  Item update(
      @Nullable Authentication authentication,
      @PathVariable long id,
      @Valid @RequestBody UpdateRequest request) {
    return service.update(principal(authentication), id, request.title(), request.body());
  }

  @PutMapping("/items/{id}/move")
  void move(
      @Nullable Authentication authentication,
      @PathVariable long id,
      @Valid @RequestBody MoveRequest request) {
    service.move(principal(authentication), id, request.column(), request.position());
  }

  @PutMapping("/items/{id}/dependencies")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void dependencies(
      @Nullable Authentication authentication,
      @PathVariable long id,
      @Valid @RequestBody DependenciesRequest request) {
    service.replaceDependencies(principal(authentication), id, request.dependsOn());
  }

  @PostMapping("/items/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  void comment(
      @Nullable Authentication authentication,
      @PathVariable long id,
      @Valid @RequestBody CommentRequest request) {
    service.comment(principal(authentication), id, request.body());
  }

  @GetMapping("/items/{id}/comments")
  List<Comment> comments(@Nullable Authentication authentication, @PathVariable long id) {
    return service.listComments(principal(authentication), id);
  }

  @GetMapping("/epics")
  List<Epic> epics(@Nullable Authentication authentication) {
    return service.epics(principal(authentication));
  }

  /** Zieht die Token-Bindung aus den Authentication-details; ohne Bindung → 409. */
  private static KanbanPrincipal principal(@Nullable Authentication authentication) {
    if (authentication != null && authentication.getDetails() instanceof KanbanPrincipal p) {
      return p;
    }
    throw new TokenNotBoundException();
  }

  record CreateItemRequest(
      @NotBlank @Size(max = 300) String title,
      String body,
      String column,
      @Nullable Boolean ideaStored,
      // Idempotenz-Schlüssel (#534); Normalisierung (trim/Kappung) macht der Service.
      @Nullable String externalKey,
      // Opt-in-Board-Routing (#535): true = direkt aufs Token-Board statt in den Ideen-Pool.
      // Die Zielspalte kommt seit #569 aus `column` (ohne Angabe: erste Spalte, DONE abgelehnt).
      @Nullable Boolean direct,
      // Vorgegebene projektweite Nummer (#565), fuer den Import aus einem anderen Tracker.
      // Verlangt direct=true und einen externalKey; der Service lehnt beides sonst ab.
      // Obergrenze: nextCardNumber rechnet MAX(number)+1 auf einer integer-Spalte — ohne Deckel
      // legt ein einziger Import mit Integer.MAX_VALUE jede spaetere Anlage im Projekt lahm.
      @Nullable @Positive @Max(CardNumbers.MAX) Integer number) {}

  /**
   * Titel und Rumpf einer bestehenden Karte (#571). Grenzen wie in {@link CreateItemRequest} —
   * beide Schreibwege dürfen dieselbe Karte nicht unterschiedlich beschneiden.
   *
   * <p>{@code body} ist bewusst optional: Der Adapter sendet den Titel immer mit, auch wenn sich
   * nur der Rumpf ändert. Ein fehlendes Feld (und JSON-{@code null}) lässt die Beschreibung
   * unverändert, ein blanker Wert löscht sie — siehe {@code CardService.updateContent}.
   */
  record UpdateRequest(@NotBlank @Size(max = 300) String title, @Nullable String body) {}

  record MoveRequest(@NotBlank String column, @PositiveOrZero int position) {}

  /**
   * Abhaengigkeiten als projektweite Kartennummern (#566). Ersetzen-Semantik: Die Liste tritt an
   * die Stelle der vorhandenen Verweise, {@code null} oder leer loescht sie. Nummern duerfen auf
   * noch nicht importierte Karten zeigen.
   */
  // @NotNull je Element: @Positive allein laesst null durch, und der Selbstverweis-Vergleich
  // entpackt den Wert — {"dependsOn":[null]} endete sonst als NullPointerException in einem 500.
  // @Size deckelt die Transaktionsgroesse; jedes Element ist ein eigenes INSERT.
  record DependenciesRequest(
      @Nullable @Size(max = 500)
          List<@NotNull @Positive @Max(CardNumbers.MAX) Integer> dependsOn) {}

  /** Gleiche Längengrenze wie der UI-Pfad ({@code CommentController.CommentRequest}). */
  record CommentRequest(@NotBlank @Size(max = 10_000) String body) {}
}
