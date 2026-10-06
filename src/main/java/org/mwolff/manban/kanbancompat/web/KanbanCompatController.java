package org.mwolff.manban.kanbancompat.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.mwolff.manban.common.Laufkennung;
import org.mwolff.manban.common.TextLimits;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.common.web.api.Stabilitaet;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Activity;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Comment;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Created;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Epic;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Item;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Toolbox-Kanban-API für tbx.mjs / board.mjs (Dogfooding, #45). Vertragsgleich mit dem bestehenden
 * Toolbox-Backend: der Client sendet nur den Token ({@code X-Kanban-Token}); das Board kommt aus
 * der Token-Bindung (#44), die der {@code PatAuthenticationFilter} an die Authentication-{@code
 * details} hängt. Nur per PAT erreichbar (SecurityConfig).
 *
 * <p>Die API-Beschreibung (Issue #1403, Plan #1400) steht an Klasse, Methoden und Records. Die
 * Aufrufe, die Kit, {@code cli/tbx.mjs} und {@code scripts/} benutzen, tragen {@code VERLAESSLICH}
 * (E4); {@code PUT /items/{id}/dependencies} hat keinen solchen Aufrufer und bleibt änderbar. Ein
 * Vertragsschnappschuss in {@code OpenApiIT} macht jede Änderung an ihnen sichtbar.
 */
// Die OpenAPI-Annotationen heben die Importzahl über die PMD-Schwelle von 40 (Plan #1400, E14);
// die Ausnahme bleibt an dieser Klasse sichtbar, statt die Regel für alle anzuheben.
@SuppressWarnings("PMD.ExcessiveImports")
@Tag(
    name = "Einliefern (Kanban-kompatibel)",
    description =
        "Karten, Kommentare und Labels über ein board-gebundenes Projekt-Token lesen und"
            + " schreiben, ohne Anmeldung im Browser. Das Board kommt aus der Bindung des Tokens"
            + " (Header X-Kanban-Token), nicht aus dem Aufruf: Jeder Aufruf wirkt nur auf das"
            + " Board, für das das Token ausgestellt wurde, und das Token darf nie mehr als die"
            + " Person, die es angelegt hat. Spalten heißen hier mit festen Schlüsseln BACKLOG,"
            + " READY, IN_PROGRESS, IN_REVIEW und DONE.")
@RestController
@RequestMapping("/api/kanban")
class KanbanCompatController {

  /** Längengrenze eines Labelnamens; identisch mit {@code LabelController.LabelRequest}. */
  private static final int MAX_LABEL_NAME = 60;

  /** Header des Idempotenz-Schlüssels der anlegenden Befehle (Issue #1001, Plan #995 E7). */
  private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  /** Längengrenze des Schlüssels; identisch mit der Spalte {@code idempotency_record.key}. */
  private static final int MAX_IDEMPOTENCY_KEY = 200;

  private static final String TOKEN_BINDUNG =
      "Das Board kommt aus der Bindung des Projekt-Tokens; ein Token ohne Board-Bindung wird"
          + " mit 409 abgewiesen.";

  private static final String BESCHREIBUNG_IDEMPOTENCY_KEY =
      "Optionaler Idempotenz-Schlüssel (höchstens 200 Zeichen). Derselbe Schlüssel im selben"
          + " Projekt bewirkt den Befehl genau einmal; jede Wiederholung bekommt dieselbe Antwort."
          + " Wer nach einem Netzfehler nicht weiß, ob der Aufruf ankam, wiederholt ihn mit"
          + " demselben Schlüssel. Ein leerer Wert gilt als kein Schlüssel.";

  private static final String BESCHREIBUNG_LAUFKENNUNG =
      "Optionale Laufkennung: der Startzeitpunkt des Laufs (ISO-8601), der den Kommentar"
          + " schreibt. Ein Lauf ist eine Sitzung eines Agenten, die Karten abarbeitet, etwa ein"
          + " Nachtlauf; die Kennung ordnet den Kommentar diesem Lauf zu. Eine Selbstauskunft des"
          + " Aufrufers, die der Leitstand nicht prüft; ein unlesbarer Wert gilt als keine"
          + " Kennung.";

  private static final String BESCHREIBUNG_ID =
      "Interne ID der Karte (Feld id aus GET /api/kanban/items), nicht die projektweite Nummer.";

  private static final String NICHT_GEBUNDEN =
      "Das Projekt-Token ist an kein Board gebunden; ein board-gebundenes Token verwenden.";

  private static final String UNGUELTIGE_EINGABE =
      "Ungültige Eingabe: ein Feld verletzt seine Grenzen (Details in fieldErrors).";

  private static final String KARTE_FEHLT =
      "Die Karte gibt es nicht, oder sie liegt nicht auf dem gebundenen Board.";

  private final KanbanCompatService service;

  KanbanCompatController(KanbanCompatService service) {
    this.service = service;
  }

  @Operation(
      summary = "Karten des Boards lesen",
      description =
          "Liefert alle sichtbaren Karten und Vorhaben des gebundenen Boards, gruppiert nach den"
              + " Spaltenschlüsseln BACKLOG, READY, IN_PROGRESS, IN_REVIEW und DONE; jeder"
              + " Schlüssel ist vorhanden, auch ohne Karten. Bei einem Arbeitspaket ist der"
              + " Schlüssel sein Status, sonst die Spalte; eine Spalte ohne festen Schlüssel"
              + " zählt als BACKLOG. Archivierte Karten fehlen. Jede Karte trägt ihre Labels und"
              + " ihre Herkunft (derivedFrom: die Nummer der Karte, aus der sie abgeleitet wurde,"
              + " etwa ein Arbeitspaket aus seinem Plan). Projekt und Board ergeben sich aus der"
              + " Token-Bindung, nicht aus einem eigenen Aufruf.")
  @ApiResponse(responseCode = "200", description = "Die Karten je Spaltenschlüssel.")
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @GetMapping("/items")
  Map<String, List<Item>> items(@Nullable Authentication authentication) {
    return service.items(principal(authentication));
  }

  /**
   * Legt ein Item an. Der optionale Header {@code Idempotency-Key} macht die Anlage wiederholbar:
   * Derselbe Schlüssel im selben Projekt erzeugt eine Karte und beliebig viele gleiche Antworten
   * (Issue #1001). Ohne Header bleibt alles wie bisher.
   */
  @Operation(
      summary = "Karte anlegen",
      description =
          "Legt eine Karte auf dem gebundenen Board an. "
              + TOKEN_BINDUNG
              + " Ohne column landet sie in der ersten Spalte. Mit column wird der Schlüssel"
              + " aufgelöst: mit direct=true ist ein nicht auflösbarer Schlüssel ein Fehler,"
              + " ohne direct gilt dann die erste Spalte. DONE ist nie erlaubt — dass eine Karte"
              + " fertig ist, stellt ein Mensch auf dem Board fest.\n\n"
              + "Wiederholbar auf zwei Wegen: Ein externalKey ist ein dauerhafter fachlicher"
              + " Schlüssel — trifft er eine vorhandene Karte, wird nichts angelegt und created"
              + " ist false. Ohne externalKey macht der Header Idempotency-Key die Anlage"
              + " wiederholbar; ist ein externalKey gesetzt, hat er Vorrang.\n\n"
              + "derivedFrom hält die Herkunft fest: die projektweite Nummer der Karte, aus der"
              + " diese abgeleitet ist, etwa ein Arbeitspaket aus seinem Plan. number gibt die"
              + " Nummer für einen Import aus einem anderen Tracker vor und verlangt einen"
              + " externalKey.")
  @ApiResponse(
      responseCode = "201",
      description = "Die Karte ist angelegt oder lag mit diesem externalKey schon vor.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE
              + " Ebenso: unbekannte oder fehlende Spalte bei direct=true, Spalte DONE, oder"
              + " number ohne externalKey.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description =
          NICHT_GEBUNDEN
              + " Ebenso: die vorgegebene number ist schon vergeben, oder der Idempotency-Key"
              + " wurde schon für einen anderen Befehl verwendet.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  Created create(
      @Nullable Authentication authentication,
      @Valid @RequestBody CreateItemRequest request,
      @Parameter(description = BESCHREIBUNG_IDEMPOTENCY_KEY, example = "kit-1403-anlage-1")
          @RequestHeader(name = IDEMPOTENCY_KEY, required = false)
          @Nullable
          @Size(max = MAX_IDEMPOTENCY_KEY)
          String idempotencyKey) {
    // request.ideaStored() wird bewusst nicht weitergereicht — siehe CreateItemRequest.
    return service.create(
        principal(authentication),
        request.title(),
        request.body(),
        request.column(),
        request.externalKey(),
        Boolean.TRUE.equals(request.direct()),
        request.number(),
        request.derivedFrom(),
        idempotencyKey);
  }

  @Operation(
      summary = "Titel und Beschreibung einer Karte ersetzen",
      description =
          "Ersetzt Titel und Beschreibung einer Karte oder eines Vorhabens des gebundenen"
              + " Boards. Fehlt body (oder ist null), bleibt die Beschreibung unverändert; ein"
              + " leerer body löscht sie. Alle übrigen Felder — Vorhaben, Fälligkeit, Labels,"
              + " Herkunft — bleiben unangetastet. "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "200", description = "Die Karte nach der Änderung.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE,
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
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PutMapping("/items/{id}")
  Item update(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id,
      @Valid @RequestBody UpdateRequest request) {
    return service.update(principal(authentication), id, request.title(), request.body());
  }

  @Operation(
      summary = "Karte verschieben",
      description =
          "Bei einem Arbeitspaket setzt der Aufruf den Status auf den Spaltenschlüssel; die Karte"
              + " wandert ans Ende der passenden Spalte, wenn das Board eine hat, und position"
              + " hat keine Wirkung. Vorhaben und Dokumente werden in die Spalte mit diesem"
              + " Schlüssel an die angegebene Position verschoben. "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "200", description = "Die Karte ist verschoben.")
  @ApiResponse(
      responseCode = "400",
      description =
          UNGUELTIGE_EINGABE + " Ebenso: unbekannter Spaltenschlüssel oder keine passende Spalte.",
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
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PutMapping("/items/{id}/move")
  void move(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id,
      @Valid @RequestBody MoveRequest request) {
    service.move(principal(authentication), id, request.column(), request.position());
  }

  @Operation(
      summary = "Abhängigkeiten einer Karte ersetzen",
      description =
          "Ersetzt die Abhängigkeiten einer Karte des gebundenen Projekts durch die übergebenen"
              + " Kartennummern; eine leere Liste oder null löscht sie. Nummern dürfen auf noch"
              + " nicht importierte Karten zeigen. Gedacht für die Übernahme aus einem anderen"
              + " Tracker. "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "204", description = "Die Abhängigkeiten sind ersetzt.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE + " Ebenso: Verweis der Karte auf sich selbst.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = "Die Karte gibt es nicht, oder sie liegt nicht im gebundenen Projekt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PutMapping("/items/{id}/dependencies")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void dependencies(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id,
      @Valid @RequestBody DependenciesRequest request) {
    service.replaceDependencies(principal(authentication), id, request.dependsOn());
  }

  /**
   * Ergänzt genau ein Label (#574). Adressierung über die interne Karten-ID, konsistent zu {@code
   * /items/{id}/move} und {@code /items/{id}/comments}.
   */
  @Operation(
      summary = "Label an eine Karte setzen",
      description =
          "Ordnet einer Karte genau ein Label zu, das auf dem Board definiert ist; alle übrigen"
              + " Labels bleiben stehen. Ein schon gesetztes Label erneut zu setzen ist Erfolg."
              + " Ein unbekannter Name wird abgelehnt, nicht angelegt. Die Freigabe-Labels"
              + " kit:night und kit:nightrun setzt nur ein Mensch im Board, nie ein Token. "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "204", description = "Das Label ist gesetzt.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE + " Ebenso: die Karte ist ein Vorhaben (ohne Labels).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          "Das Label ist ein Freigabe-Label, das nur ein Mensch setzt, oder die Rolle reicht"
              + " nicht zum Bearbeiten von Karten.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT + " Ebenso: das Board definiert kein Label dieses Namens.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PostMapping("/items/{id}/labels")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void addLabel(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id,
      @Valid @RequestBody LabelRequest request) {
    service.addLabel(principal(authentication), id, request.name());
  }

  /**
   * Entfernt genau ein Label (#574).
   *
   * <p>Der Name steht bewusst im <strong>Query-Parameter</strong> statt im Pfad: {@code
   * LabelService} trimmt den Namen nur und lehnt Leerstrings ab — jedes andere Zeichen ist gültig,
   * auch {@code /}. Ein Pfadsegment trüge das nicht, weil Spring/Tomcat kodierte Slashes per
   * Default ablehnen.
   */
  @Operation(
      summary = "Label von einer Karte nehmen",
      description =
          "Entfernt genau ein Label von einer Karte; alle übrigen bleiben stehen. Ein nicht"
              + " gesetztes Label zu entfernen ist Erfolg. Der Name steht im Query-Parameter,"
              + " weil Labelnamen jedes Zeichen enthalten dürfen, auch '/'. Die Freigabe-Labels"
              + " kit:night und kit:nightrun nimmt nur ein Mensch im Board ab. "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "204", description = "Das Label ist entfernt.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE + " Ebenso: die Karte ist ein Vorhaben (ohne Labels).",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description =
          "Das Label ist ein Freigabe-Label, das nur ein Mensch abnimmt, oder die Rolle reicht"
              + " nicht zum Bearbeiten von Karten.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT + " Ebenso: das Board definiert kein Label dieses Namens.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @DeleteMapping("/items/{id}/labels")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeLabel(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id,
      @Parameter(
              description = "Name des Labels, höchstens 60 Zeichen; wird getrimmt.",
              example = "kit:klaeren")
          @RequestParam
          @NotBlank
          @Size(max = MAX_LABEL_NAME)
          String name) {
    service.removeLabel(principal(authentication), id, name);
  }

  /**
   * Kommentiert ein Item; {@code Idempotency-Key} wie bei {@link #create} (Issue #1001). Die
   * Laufkennung aus {@link Laufkennung#HEADER} geht an den Kommentar (Issue #1428, A7).
   */
  @Operation(
      summary = "Kommentar anlegen",
      description =
          "Hängt einen Kommentar (Markdown) an eine Karte des gebundenen Boards. Mit dem Header"
              + " Idempotency-Key entsteht der Kommentar je Projekt und Schlüssel genau einmal;"
              + " zwei verschiedene Schlüssel mit gleichem Text ergeben bewusst zwei Kommentare."
              + " Der Header X-Night-Run ordnet den Kommentar einem Lauf zu; so meldet etwa ein"
              + " laufender Lauf seinen Laufstand (Kommentar \"## Laufstand\": woran er gerade"
              + " arbeitet). "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "201", description = "Der Kommentar ist angelegt.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE,
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
  @ApiResponse(
      responseCode = "409",
      description =
          NICHT_GEBUNDEN
              + " Ebenso: der Idempotency-Key wurde schon für einen anderen Befehl"
              + " verwendet.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PostMapping("/items/{id}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  void comment(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id,
      @Valid @RequestBody CommentRequest request,
      @Parameter(description = BESCHREIBUNG_IDEMPOTENCY_KEY, example = "kit-1403-bericht-1")
          @RequestHeader(name = IDEMPOTENCY_KEY, required = false)
          @Nullable
          @Size(max = MAX_IDEMPOTENCY_KEY)
          String idempotencyKey,
      @Parameter(description = BESCHREIBUNG_LAUFKENNUNG, example = "2026-10-05T12:58:05Z")
          @RequestHeader(value = Laufkennung.HEADER, required = false)
          @Nullable String laufkennung) {
    service.comment(
        principal(authentication),
        id,
        request.body(),
        idempotencyKey,
        Laufkennung.ausHeader(laufkennung));
  }

  /**
   * Ersetzt den Text eines Kommentars an Ort und Stelle (Issue #1339); nur der Autor selbst, wie im
   * UI-Pfad. Längengrenze wie beim Anlegen. Die Laufkennung ersetzt die bisherige (Issue #1428).
   */
  @Operation(
      summary = "Kommentar ersetzen",
      description =
          "Ersetzt den Text eines Kommentars an Ort und Stelle, statt einen neuen anzuhängen —"
              + " so erneuert ein Lauf etwa seinen Laufstand oder Abschlussbericht. Ändern darf"
              + " nur, wer den Kommentar geschrieben hat; der Kommentar muss zur adressierten"
              + " Karte gehören. Die Laufkennung aus X-Night-Run ersetzt die bisherige, auch"
              + " ohne Header. "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "204", description = "Der Kommentar ist ersetzt.")
  @ApiResponse(
      responseCode = "400",
      description = UNGUELTIGE_EINGABE,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = "Den Kommentar hat jemand anderes geschrieben.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT + " Ebenso: der Kommentar gehört nicht zu dieser Karte.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @PatchMapping("/items/{id}/comments/{commentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void updateComment(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id,
      @Parameter(description = "ID des Kommentars (Feld id aus GET …/comments).", example = "815")
          @PathVariable
          long commentId,
      @Valid @RequestBody CommentRequest request,
      @Parameter(description = BESCHREIBUNG_LAUFKENNUNG, example = "2026-10-05T12:58:05Z")
          @RequestHeader(value = Laufkennung.HEADER, required = false)
          @Nullable String laufkennung) {
    service.updateComment(
        principal(authentication),
        id,
        commentId,
        request.body(),
        Laufkennung.ausHeader(laufkennung));
  }

  @Operation(
      summary = "Kommentare einer Karte lesen",
      description =
          "Liefert die Kommentare einer Karte des gebundenen Boards in zeitlicher Reihenfolge,"
              + " den ältesten zuerst. "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "200", description = "Die Kommentare der Karte.")
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @GetMapping("/items/{id}/comments")
  List<Comment> comments(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id) {
    return service.listComments(principal(authentication), id);
  }

  /**
   * Aktivitätsverlauf eines Items (#876). Adressierung über die interne Karten-ID, konsistent zu
   * {@code /items/{id}/comments}.
   */
  @Operation(
      summary = "Aktivitätsverlauf einer Karte lesen",
      description =
          "Liefert den Verlauf einer Karte des gebundenen Boards in zeitlicher Reihenfolge —"
              + " Anlage, Verschiebungen, Änderungen —, je Eintrag mit Herkunft der Änderung"
              + " (Oberfläche oder Token), Tokenname und gemeldetem Agenten. Dieselbe Auskunft"
              + " wie in der Oberfläche, beschränkt auf das gebundene Board. "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "200", description = "Die Einträge des Verlaufs.")
  @ApiResponse(
      responseCode = "404",
      description = KARTE_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
  @GetMapping("/items/{id}/activity")
  List<Activity> activity(
      @Nullable Authentication authentication,
      @Parameter(description = BESCHREIBUNG_ID, example = "4711") @PathVariable long id) {
    return service.listActivity(principal(authentication), id);
  }

  @Operation(
      summary = "Vorhaben des Boards lesen",
      description =
          "Liefert die Vorhaben des gebundenen Boards — Klammern um zusammengehörige Karten —"
              + " mit Nummer, Titel, Kürzel und Fortschritt (Zahl der Karten und der erledigten)."
              + " "
              + TOKEN_BINDUNG)
  @ApiResponse(responseCode = "200", description = "Die Vorhaben des Boards.")
  @ApiResponse(
      responseCode = "409",
      description = NICHT_GEBUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
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

  @Schema(description = "Eine neue Karte.")
  record CreateItemRequest(
      @Schema(description = "Titel, höchstens 300 Zeichen.", example = "Export als CSV")
          @NotBlank
          @Size(max = 300)
          String title,
      @Schema(description = "Beschreibung in Markdown.", example = "## Kontext\nWarum …")
          @Size(max = TextLimits.MAX_TEXT)
          String body,
      @Schema(
              description =
                  "Spaltenschlüssel BACKLOG, READY, IN_PROGRESS oder IN_REVIEW; ohne Angabe die"
                      + " erste Spalte. DONE wird abgelehnt.",
              example = "BACKLOG")
          String column,
      // Seit Issue #1203 wirkungslos und bewusst im Vertrag belassen: Jeder Ingest legt
      // board-gebunden an, es gibt keinen Ideen-Pool mehr, in den dieses Feld routen koennte.
      // Aelteren Kit-Versionen ist das Anlegen ausdruecklich zugesagt — ein 400 auf ein bekanntes
      // Feld waere der Bruch dieser Zusage. Der Service bekommt es deshalb nicht mehr zu sehen.
      @Schema(description = "Ohne Wirkung; bleibt für ältere Aufrufer zulässig.", deprecated = true)
          @Nullable Boolean ideaStored,
      // Idempotenz-Schlüssel (#534); Normalisierung (trim/Kappung) macht der Service.
      @Schema(
              description =
                  "Dauerhafter fachlicher Schlüssel, etwa die ID im Quellsystem; getrimmt und auf"
                      + " 100 Zeichen gekürzt. Trifft er eine vorhandene Karte, wird nichts"
                      + " angelegt.",
              example = "sonar:AY1x")
          @Nullable String externalKey,
      // Seit Issue #1203 entscheidet `direct` nur noch, wie streng `column` aufgeloest wird: mit
      // direct ist ein nicht auflösbarer Schluessel ein 400, ohne direct greift die erste Spalte
      // (E8a). Ohne `column` gilt auf beiden Wegen die erste Spalte, DONE wird stets abgelehnt.
      @Schema(
              description =
                  "true: ein nicht auflösbarer Spaltenschlüssel ist ein Fehler; sonst gilt dann"
                      + " die erste Spalte.",
              example = "true")
          @Nullable Boolean direct,
      // Vorgegebene projektweite Nummer (#565), fuer den Import aus einem anderen Tracker.
      // Verlangt einen externalKey; der Service lehnt sie sonst ab.
      // Obergrenze: nextCardNumber rechnet MAX(number)+1 auf einer integer-Spalte — ohne Deckel
      // legt ein einziger Import mit Integer.MAX_VALUE jede spaetere Anlage im Projekt lahm.
      @Schema(
              description =
                  "Vorgegebene projektweite Nummer für einen Import; verlangt einen externalKey.",
              example = "42")
          @Nullable
          @Positive
          @Max(CardNumbers.MAX)
          Integer number,
      // Herkunft als projektweite Kartennummer (#601-#605). Dieselbe Obergrenze wie number,
      // aus demselben Grund: Schutz der Nummernvergabe vor Ueberlauf (siehe CardNumbers).
      @Schema(
              description =
                  "Herkunft: projektweite Nummer der Karte, aus der diese abgeleitet ist, etwa"
                      + " der Plan eines Arbeitspakets.",
              example = "1400")
          @Nullable
          @Positive
          @Max(CardNumbers.MAX)
          Integer derivedFrom) {}

  /**
   * Titel und Rumpf einer bestehenden Karte (#571). Grenzen wie in {@link CreateItemRequest} —
   * beide Schreibwege dürfen dieselbe Karte nicht unterschiedlich beschneiden.
   *
   * <p>{@code body} ist bewusst optional: Der Adapter sendet den Titel immer mit, auch wenn sich
   * nur der Rumpf ändert. Ein fehlendes Feld (und JSON-{@code null}) lässt die Beschreibung
   * unverändert, ein blanker Wert löscht sie — siehe {@code CardIngestService.updateContent}.
   */
  @Schema(description = "Neuer Titel und neue Beschreibung einer Karte.")
  record UpdateRequest(
      @Schema(description = "Titel, höchstens 300 Zeichen.", example = "Export als CSV")
          @NotBlank
          @Size(max = 300)
          String title,
      @Schema(
              description =
                  "Beschreibung in Markdown; fehlt sie, bleibt die bisherige, leer löscht sie.",
              example = "## Kontext\nWarum …")
          @Nullable
          @Size(max = TextLimits.MAX_TEXT)
          String body) {}

  @Schema(description = "Ziel einer Verschiebung.")
  record MoveRequest(
      @Schema(
              description = "Spaltenschlüssel BACKLOG, READY, IN_PROGRESS, IN_REVIEW oder DONE.",
              example = "IN_REVIEW")
          @NotBlank
          String column,
      @Schema(
              description = "Position in der Zielspalte ab 0; ohne Wirkung bei Arbeitspaketen.",
              example = "0")
          @PositiveOrZero
          int position) {}

  /**
   * Abhaengigkeiten als projektweite Kartennummern (#566). Ersetzen-Semantik: Die Liste tritt an
   * die Stelle der vorhandenen Verweise, {@code null} oder leer loescht sie. Nummern duerfen auf
   * noch nicht importierte Karten zeigen.
   */
  // @NotNull je Element: @Positive allein laesst null durch, und der Selbstverweis-Vergleich
  // entpackt den Wert — {"dependsOn":[null]} endete sonst als NullPointerException in einem 500.
  // @Size deckelt die Transaktionsgroesse; jedes Element ist ein eigenes INSERT.
  @Schema(description = "Die neuen Abhängigkeiten einer Karte.")
  record DependenciesRequest(
      @Schema(
              description =
                  "Projektweite Nummern der Karten, von denen diese abhängt; höchstens 500.",
              example = "[1402]")
          @Nullable
          @Size(max = 500)
          List<@NotNull @Positive @Max(CardNumbers.MAX) Integer> dependsOn) {}

  /** Gleiche Längengrenze wie der UI-Pfad ({@code CommentController.CommentRequest}). */
  // Eigener Schemaname: CommentController hat einen gleichnamigen Record, und springdoc führt
  // beide unter dem einfachen Namen — welcher gewönne, hinge an der Reihenfolge der Scans.
  @Schema(name = "KanbanCommentRequest", description = "Text eines Kommentars.")
  record CommentRequest(
      @Schema(description = "Kommentar in Markdown.", example = "## Laufstand\nUmsetzung läuft.")
          @NotBlank
          @Size(max = TextLimits.MAX_TEXT)
          String body) {}

  /**
   * Name genau eines Labels (#574). Gleiche Grenze wie der UI-Pfad ({@code
   * LabelController.LabelRequest}) — beide Wege dürfen denselben Namen nicht unterschiedlich
   * beschneiden.
   */
  // Eigener Schemaname aus demselben Grund wie bei CommentRequest (LabelController).
  @Schema(name = "KanbanLabelRequest", description = "Name genau eines Labels.")
  record LabelRequest(
      @Schema(
              description = "Name eines auf dem Board definierten Labels, höchstens 60 Zeichen.",
              example = "kit:klaeren")
          @NotBlank
          @Size(max = MAX_LABEL_NAME)
          String name) {}
}
