package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.card.application.CardNumbers;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.EpicService;
import org.mwolff.manban.card.application.InvalidDerivedFromException;
import org.mwolff.manban.card.application.LabelAction;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.common.TextLimits;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Karten- und Vorhaben-Verwaltung eines Boards (Anlegen, Bearbeiten, Zuordnen). Archiv und
 * Papierkorb liegen seit Issue #1394 im {@link CardArchiveController}, Verschieben, Status und
 * Umzug seit Issue #1395 im {@link CardMoveController}.
 */
// PMD.CouplingBetweenObjects entfiel mit Verschieben, Status und Umzug, die seit Issue #1395 im
// CardMoveController liegen: Die Kopplung liegt wieder unter der Schwelle (Plan #1387, E12).
@Tag(
    name = "Karten",
    description =
        "Karten und Vorhaben eines Boards anlegen, lesen und bearbeiten, Zuständige und Labels"
            + " setzen, den Aktivitätsverlauf lesen. Eine Karte trägt eine projektweite Nummer"
            + " (number) und eine interne ID (id); die Pfade verwenden die ID. Ein Vorhaben ist"
            + " eine Karte vom Typ EPIC, die andere Karten bündelt (parentId der Karten). Lesen"
            + " verlangt die Mitgliedschaft im Projekt, Schreiben das genannte Projekt-Recht;"
            + " einem Nichtmitglied antwortet der Leitstand mit 404, damit nicht erkennbar ist,"
            + " ob es Projekt oder Karte gibt.")
@RestController
class CardController {

  /**
   * Obergrenze für ein Stapel-Anlegen an einer Board-Spalte. Dieselbe Zahl wie an den bestehenden
   * Bulk-Endpunkten dieser Klasse, damit im Projekt genau eine Mengen-Obergrenze gilt statt
   * mehrerer divergierender.
   */
  static final int MAX_CARDS_PER_BATCH = 200;

  private final CardService cards;
  private final EpicService epics;

  private static final String BESCHREIBUNG_BOARD_ID = "Interne ID des Boards.";

  private static final String BESCHREIBUNG_CARD_ID =
      "Interne ID der Karte (Feld id), nicht die projektweite Nummer.";

  private static final String UNGUELTIGE_EINGABE =
      "Ungültige Eingabe: ein Feld verletzt seine Grenzen (Details in fieldErrors).";

  private static final String VERBOTEN =
      "Die Projekt-Rolle des Aufrufers umfasst das verlangte Recht nicht.";

  private static final String BOARD_FEHLT =
      "Das Board gibt es nicht, oder der Aufrufer ist kein Mitglied seines Projekts.";

  private static final String KARTE_FEHLT =
      "Die Karte gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts.";

  private static final String FREIGABE_LABELS =
      " Die Freigabe-Labels des Kits ändert ein Projekt-Token nur in einer Richtung: kit:night"
          + " und kit:nightrun setzt nur ein Mensch im Board, kit:klaeren und kit:geschuetzt"
          + " nimmt nur ein Mensch ab — sonst 403.";

  CardController(CardService cards, EpicService epics) {
    this.cards = cards;
    this.epics = epics;
  }

  @Operation(
      summary = "Karte oder Vorhaben anlegen",
      description =
          "Legt eine Karte in der angegebenen Spalte des Boards an, am Ende der Spalte. Mit"
              + " type=EPIC entsteht stattdessen ein Vorhaben in der ersten Spalte; columnId ist"
              + " dann ohne Wirkung. Recht: TICKET_CREATE für Karten, EPIC_CREATE für Vorhaben.\n\n"
              + "Zuständige, Labels und Fälligkeit werden mit der Anlage in einem Zug übernommen."
              + " dependencies nennt die projektweiten Nummern der Karten, von denen diese"
              + " abhängt; parentId ordnet die Karte einem Vorhaben desselben Boards zu.\n\n"
              + "derivedFrom hält die Herkunft fest: die projektweite Nummer der Karte, aus der"
              + " diese abgeleitet ist, etwa ein Arbeitspaket aus seinem Plan. Ein Vorhaben trägt"
              + " keine Herkunft.")
  @ApiResponse(responseCode = "201", description = "Die angelegte Karte.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE
              + " Ebenso: unbekannte Abhängigkeit, parentId ist kein Vorhaben dieses Boards,"
              + " Zuständiger ist kein Projektmitglied, Label gehört nicht zum Board, ungültige"
              + " oder zyklische Herkunft, Herkunft an einem Vorhaben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN + FREIGABE_LABELS,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT + " Ebenso: die Spalte fehlt oder gehört nicht zum Board.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "TICKET_CREATE")
  @PostMapping("/api/boards/{boardId}/cards")
  @ResponseStatus(HttpStatus.CREATED)
  CardView create(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId,
      @Valid @RequestBody CreateCardRequest request) {
    // Getter je einmal in eine lokale Variable ziehen: der Null-Check verengt dann den (nun
    // @Nullable) Typ, statt bei einem zweiten Aufruf erneut als potenziell null zu gelten.
    CardType requestedType = request.type();
    CardType type = requestedType == null ? CardType.CARD : requestedType;
    if (type == CardType.EPIC) {
      // Vorhaben tragen keine Herkunft: `parentId` (Mitgliedschaft) und `derivedFrom` (Abstammung)
      // sind zwei getrennte Relationen (Plandokument #606, E9), und ein Vorhaben mit Herkunft
      // haette im Herkunftsbaum kein definiertes Verhalten. Ohne diese Ablehnung wuerde
      // `createEpic` das Feld still verschlucken.
      if (request.derivedFrom() != null) {
        throw new InvalidDerivedFromException("Ein Vorhaben kann keine Herkunft tragen");
      }
      return epics.createEpic(
          userId, boardId, request.title(), request.description(), request.shortcode());
    }
    Long columnId = request.columnId();
    if (columnId == null) {
      throw new ColumnNotFoundException();
    }
    return cards.create(
        userId,
        boardId,
        columnId,
        request.title(),
        request.description(),
        request.dependencies(),
        request.parentId(),
        request.dueDate(),
        request.assigneeIds(),
        request.labelIds(),
        request.derivedFrom());
  }

  /**
   * Legt mehrere Karten in einem Zug am Ende einer Spalte dieses Boards an (Issue #1200) — der
   * board-gebundene Weg des Spezifikations-Imports. Antwort: die angelegten Karten in
   * Eingabereihenfolge, jeweils mit {@code id} und vergebener {@code number}.
   *
   * <p>Alles-oder-nichts: Verletzt ein Element die Feldgrenzen, lehnt die Bean-Validation die ganze
   * Anfrage mit 400 ab, bevor der Service läuft — es entsteht keine einzige Karte. Begründung der
   * Entscheidung im Javadoc von {@link CardService#createCardsBatch}.
   */
  @Operation(
      summary = "Mehrere Karten anlegen",
      description =
          "Legt bis zu 200 Karten mit Titel und optionaler Beschreibung in einem Zug am Ende"
              + " einer Spalte des Boards an, in Eingabereihenfolge. Alles oder nichts: Verletzt"
              + " ein Element seine Grenzen oder scheitert eine Karte, entsteht keine.")
  @ApiResponse(
      responseCode = "201",
      description = "Die angelegten Karten in Eingabereihenfolge, je mit id und number.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE + " Ebenso: eine leere Liste oder mehr als 200 Karten.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT + " Ebenso: die Spalte fehlt oder gehört nicht zum Board.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "TICKET_CREATE")
  @PostMapping("/api/boards/{boardId}/cards/batch")
  @ResponseStatus(HttpStatus.CREATED)
  List<CardView> createBatch(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId,
      @Valid @RequestBody CreateCardsBatchRequest request) {
    List<CardService.NewCard> neueKarten =
        request.cards().stream()
            .map(c -> new CardService.NewCard(c.title(), c.description()))
            .toList();
    return cards.createCardsBatch(userId, boardId, request.columnId(), neueKarten);
  }

  @Operation(
      summary = "Karten eines Boards lesen",
      description =
          "Liefert alle Karten des Boards ohne Vorhaben, archivierte eingeschlossen. Statt der"
              + " vollen Beschreibung trägt jede Karte nur einen Auszug (excerpt, die ersten 200"
              + " Zeichen); die volle Beschreibung liefert GET /api/cards/{cardId}.")
  @ApiResponse(responseCode = "200", description = "Die Karten des Boards.")
  @ApiResponse(
      responseCode = "404",
      description = BOARD_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/cards")
  List<CardView> list(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_BOARD_ID, example = "3") @PathVariable long boardId) {
    return cards.listByBoard(userId, boardId);
  }

  @Operation(
      summary = "Karte lesen",
      description = "Liefert eine Karte oder ein Vorhaben mit voller Beschreibung.")
  @ApiResponse(responseCode = "200", description = "Die Karte.")
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/{cardId}")
  CardView get(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    return cards.getCard(userId, cardId);
  }

  @Operation(
      summary = "Karte oder Vorhaben bearbeiten",
      description =
          "Ersetzt Titel, Beschreibung und Abhängigkeiten. Bei einer Karte setzt der Aufruf"
              + " zusätzlich Vorhaben-Zuordnung (parentId; fehlt sie, wird die Zuordnung gelöst)"
              + " und Fälligkeit, bei einem Vorhaben sein Kürzel (shortcode). Fehlen"
              + " dependencies, bleiben die Abhängigkeiten unverändert. Recht: TICKET_UPDATE für"
              + " Karten, EPIC_UPDATE für Vorhaben.")
  @ApiResponse(responseCode = "200", description = "Die bearbeitete Karte.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE
              + " Ebenso: unbekannte Abhängigkeit oder parentId ist kein Vorhaben dieses"
              + " Boards.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "TICKET_UPDATE")
  @PatchMapping("/api/cards/{cardId}")
  CardView update(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @Valid @RequestBody UpdateCardRequest request) {
    return cards.update(
        userId,
        cardId,
        request.title(),
        request.description(),
        request.dependencies(),
        request.shortcode(),
        request.parentId(),
        request.dueDate());
  }

  /**
   * Setzt ein Label an mehreren Karten oder nimmt es ihnen ab, in einer Transaktion
   * (alles-oder-nichts). Die übrigen Labels jeder Karte bleiben unberührt.
   */
  @Operation(
      summary = "Ein Label an mehreren Karten setzen oder abnehmen",
      description =
          "Setzt ein Label an bis zu 200 Karten (action=ADD) oder nimmt es ihnen ab"
              + " (action=REMOVE); die übrigen Labels jeder Karte bleiben unberührt. Alles oder"
              + " nichts: Scheitert eine Karte, ändert sich keine. Trägt eine Karte das Label"
              + " schon (bzw. schon nicht), bleibt sie unverändert."
              + FREIGABE_LABELS)
  @ApiResponse(responseCode = "200", description = "Die Karten nach der Änderung.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE
              + " Ebenso: eine Karte ist ein Vorhaben, oder das Label gehört nicht zum Board der"
              + " Karte.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "TICKET_UPDATE")
  @PostMapping("/api/cards/bulk-labels")
  List<CardView> bulkLabels(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkLabelsRequest request) {
    return cards.bulkLabels(userId, request.cardIds(), request.labelId(), request.action());
  }

  /** Ersetzt die Zuständigen der Karte (leere/fehlende Liste = keine Zuständigen). */
  @Operation(
      summary = "Zuständige einer Karte ersetzen",
      description =
          "Ersetzt die Zuständigen der Karte durch die übergebenen Benutzer-IDs; eine leere oder"
              + " fehlende Liste entfernt alle. Zuständig sein können nur Mitglieder des"
              + " Projekts. Vorhaben haben keine Zuständigen.")
  @ApiResponse(responseCode = "200", description = "Die Karte mit den neuen Zuständigen.")
  @ApiResponse(
      responseCode = "400",
      description = "Ein Benutzer ist kein Projektmitglied, oder die Karte ist ein Vorhaben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "TICKET_UPDATE")
  @PutMapping("/api/cards/{cardId}/assignees")
  CardView setAssignees(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @RequestBody AssigneesRequest request) {
    List<Long> ids = request.assignees() == null ? List.of() : request.assignees();
    return cards.setAssignees(userId, cardId, ids);
  }

  /** Ersetzt die Labels der Karte (leere/fehlende Liste = keine Labels). */
  @Operation(
      summary = "Labels einer Karte ersetzen",
      description =
          "Ersetzt die Labels der Karte durch die übergebenen Label-IDs; eine leere oder"
              + " fehlende Liste entfernt alle. Zulässig sind nur Labels des Boards der Karte."
              + " Vorhaben haben keine Labels."
              + FREIGABE_LABELS)
  @ApiResponse(responseCode = "200", description = "Die Karte mit den neuen Labels.")
  @ApiResponse(
      responseCode = "400",
      description = "Ein Label gehört nicht zum Board, oder die Karte ist ein Vorhaben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "TICKET_UPDATE")
  @PutMapping("/api/cards/{cardId}/labels")
  CardView setLabels(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId,
      @RequestBody LabelsRequest request) {
    List<Long> ids = request.labels() == null ? List.of() : request.labels();
    return cards.setLabels(userId, cardId, ids);
  }

  /** Aktivitätsverlauf einer Karte (chronologisch, Leserecht wie Board-Ansicht). */
  @Operation(
      summary = "Aktivitätsverlauf einer Karte lesen",
      description =
          "Liefert die Einträge des Aktivitätsverlaufs der Karte in zeitlicher Reihenfolge:"
              + " Anlage, Bearbeitung, Verschieben, Zuordnungen. origin sagt, über welchen Zugang"
              + " die Änderung kam (Anmeldung oder Projekt-Token), tokenName nennt dann das"
              + " Token; beides prüft der Leitstand. agent ist die Selbstauskunft des Aufrufers,"
              + " welcher Agent schrieb, und wird nicht geprüft.")
  @ApiResponse(responseCode = "200", description = "Die Einträge, ältester zuerst.")
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/{cardId}/activity")
  List<ActivityView> activity(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_CARD_ID, example = "812") @PathVariable long cardId) {
    return cards.listActivityViews(userId, cardId).stream()
        .map(CardController::activityView)
        .toList();
  }

  // Die Domänen-Abbildung (Aufzählungen -> Namen) liegt seit #876 in der Fassade; hier bleibt die
  // reine Umhüllung in die Web-Form, damit die Antwortform unverändert an dieser Stelle steht.
  private static ActivityView activityView(CardService.ActivityView a) {
    return new ActivityView(
        a.id(),
        a.actorUserId(),
        a.type(),
        a.detail(),
        a.createdAt(),
        a.origin(),
        a.tokenName(),
        a.agent());
  }

  // Nur `title` ist Pflicht. Die übrigen Felder sind optional: Jackson lässt sie bei fehlendem
  // JSON-Feld `null`, weshalb sie unter @NullMarked als @Nullable deklariert sein müssen (sonst
  // hält der Nullness-Dataflow die Null-Prüfungen in `create` für tot — java:S2583).
  @Schema(description = "Eine neue Karte oder ein neues Vorhaben.")
  record CreateCardRequest(
      @Schema(
              description =
                  "Interne ID der Zielspalte; Pflicht für Karten, ohne Wirkung bei Vorhaben.",
              example = "17")
          @Nullable Long columnId,
      @Schema(description = "Titel, höchstens 300 Zeichen.", example = "Export als CSV")
          @NotBlank
          @Size(max = 300)
          String title,
      @Schema(description = "Beschreibung in Markdown.", example = "## Kontext\nWarum …")
          @Nullable
          @Size(max = TextLimits.MAX_TEXT)
          String description,
      @Schema(
              description = "Projektweite Nummern der Karten, von denen diese abhängt.",
              example = "[41, 42]")
          @Nullable List<Integer> dependencies,
      @Schema(description = "CARD (Vorgabe) oder EPIC für ein Vorhaben.", example = "CARD")
          @Nullable CardType type,
      @Schema(description = "Interne ID des Vorhabens, dem die Karte angehört.", example = "640")
          @Nullable Long parentId,
      @Schema(
              description = "Kürzel eines Vorhabens, höchstens 16 Zeichen; nur bei EPIC.",
              example = "API")
          @Nullable
          @Size(max = 16)
          String shortcode,
      @Schema(description = "Fälligkeit (ISO-8601).", example = "2026-10-31T00:00:00Z")
          @Nullable Instant dueDate,
      @Schema(description = "Benutzer-IDs der Zuständigen.", example = "[5]")
          @Nullable List<Long> assigneeIds,
      @Schema(description = "IDs der Labels des Boards.", example = "[9]")
          @Nullable List<Long> labelIds,
      @Schema(
              description =
                  "Herkunft: projektweite Nummer der Karte, aus der diese abgeleitet ist, etwa"
                      + " der Plan eines Arbeitspakets. Nicht bei Vorhaben.",
              example = "1400")
          @Nullable
          @Positive
          @Max(CardNumbers.MAX)
          Integer derivedFrom) {}

  /**
   * Ein Element des Stapels (Issue #1200). Titel- und Beschreibungsgrenze wie am anderen Anlegeweg
   * ({@link CreateCardRequest}).
   */
  @Schema(description = "Eine Karte des Stapels.")
  record BatchCardItem(
      @Schema(description = "Titel, höchstens 300 Zeichen.", example = "Abschnitt 1: Anmeldung")
          @NotBlank
          @Size(max = 300)
          String title,
      @Schema(description = "Beschreibung in Markdown.", example = "Text des Abschnitts …")
          @Nullable
          @Size(max = TextLimits.MAX_TEXT)
          String description) {}

  /**
   * Eine leere Liste ist eine Fehleingabe und keine leere Erfolgsantwort ({@code @NotEmpty} → 400)
   * — dasselbe Verhalten wie bei den bestehenden Bulk-Endpunkten. Die {@code columnId} gilt für
   * alle Elemente: Der Stapel füllt genau eine Spalte.
   */
  @Schema(description = "Ein Stapel neuer Karten für genau eine Spalte.")
  record CreateCardsBatchRequest(
      @Schema(description = "Interne ID der Zielspalte für alle Karten.", example = "17") @NotNull
          Long columnId,
      @Schema(description = "Die Karten, 1 bis 200, in der gewünschten Reihenfolge.")
          @NotEmpty
          @Size(max = MAX_CARDS_PER_BATCH)
          List<@Valid @NotNull BatchCardItem> cards) {}

  @Schema(description = "Der neue Inhalt einer Karte oder eines Vorhabens.")
  record UpdateCardRequest(
      @Schema(description = "Titel, höchstens 300 Zeichen.", example = "Export als CSV")
          @NotBlank
          @Size(max = 300)
          String title,
      @Schema(description = "Beschreibung in Markdown.", example = "## Kontext\nWarum …")
          @Size(max = TextLimits.MAX_TEXT)
          String description,
      @Schema(
              description =
                  "Projektweite Nummern der Karten, von denen diese abhängt; fehlt die Liste,"
                      + " bleiben die Abhängigkeiten unverändert.",
              example = "[41, 42]")
          List<Integer> dependencies,
      @Schema(
              description = "Kürzel eines Vorhabens, höchstens 16 Zeichen; nur bei Vorhaben.",
              example = "API")
          @Size(max = 16)
          String shortcode,
      @Schema(
              description =
                  "Interne ID des Vorhabens; fehlt sie, wird die Zuordnung gelöst. Nur bei"
                      + " Karten.",
              example = "640")
          Long parentId,
      @Schema(
              description = "Fälligkeit (ISO-8601); nur bei Karten.",
              example = "2026-10-31T00:00:00Z")
          @Nullable Instant dueDate) {}

  @Schema(description = "Ein Label, das an mehreren Karten gesetzt oder abgenommen wird.")
  record BulkLabelsRequest(
      @Schema(description = "Interne IDs der Karten, 1 bis 200.", example = "[812, 813]")
          @NotEmpty
          @Size(max = 200)
          List<Long> cardIds,
      @Schema(description = "ID des Labels.", example = "9") @NotNull Long labelId,
      @Schema(description = "ADD setzt das Label, REMOVE nimmt es ab.", example = "ADD") @NotNull
          LabelAction action) {}

  @Schema(description = "Die neuen Zuständigen einer Karte.")
  record AssigneesRequest(
      @Schema(description = "Benutzer-IDs; leer oder fehlend entfernt alle.", example = "[5, 7]")
          @Nullable List<Long> assignees) {}

  @Schema(description = "Die neuen Labels einer Karte.")
  record LabelsRequest(
      @Schema(
              description = "Label-IDs des Boards; leer oder fehlend entfernt alle.",
              example = "[9]")
          @Nullable List<Long> labels) {}

  /**
   * Ein Eintrag des Aktivitätsverlaufs. Die Felder {@code origin} und {@code tokenName} sind
   * serverseitig verifiziert, {@code agent} ist dagegen eine Selbstauskunft des Clients — siehe
   * Issue #517. Bei Alt-Einträgen aus der Zeit vor Migration V23 sind alle drei Felder null.
   */
  @Schema(description = "Ein Eintrag des Aktivitätsverlaufs einer Karte.")
  record ActivityView(
      @Schema(description = "ID des Eintrags.", example = "5120") @Nullable Long id,
      @Schema(description = "Benutzer-ID dessen, der die Änderung auslöste.", example = "5")
          @Nullable Long actorUserId,
      @Schema(
              description = "Art des Eintrags, etwa CREATED, UPDATED oder MOVED.",
              example = "MOVED")
          String type,
      @Schema(description = "Lesbare Beschreibung der Änderung.", example = "Karte bearbeitet")
          String detail,
      @Schema(description = "Zeitpunkt (ISO-8601).", example = "2026-10-05T13:35:19Z")
          Instant createdAt,
      @Schema(
              description =
                  "Zugang, über den die Änderung kam (etwa SESSION oder TOKEN), vom Leitstand"
                      + " geprüft; leer bei alten Einträgen.",
              example = "TOKEN")
          @Nullable String origin,
      @Schema(
              description =
                  "Name des Projekt-Tokens bei Änderungen per Token, vom Leitstand geprüft.",
              example = "nachtlauf")
          @Nullable String tokenName,
      @Schema(
              description = "Selbstauskunft des Aufrufers, welcher Agent schrieb; nicht geprüft.",
              example = "claude-opus-5-5")
          @Nullable String agent) {}
}
