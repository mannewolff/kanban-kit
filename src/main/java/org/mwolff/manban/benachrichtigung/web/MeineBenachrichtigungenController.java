package org.mwolff.manban.benachrichtigung.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungen;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungen.MeineEinstellungen;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungen.PersonenSchalter;
import org.mwolff.manban.benachrichtigung.domain.Ruhezeit;
import org.mwolff.manban.benachrichtigung.web.ProjektBenachrichtigungController.SchalterRequest;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die eigenen Einstellungen der Mail-Benachrichtigung (Issue #2076, Plan #2070 E12): Schalter je
 * Projekt und Ruhezeit. Immer nur die eigene Person, darum steht keine Benutzerkennung im Pfad. Nur
 * per Session ({@code ApiZugang}); die Vorschau „Ansicht als“ weist auch das Lesen ab.
 */
@Tag(name = "Benachrichtigungen")
@RestController
@RequestMapping("/api/me/benachrichtigungen")
class MeineBenachrichtigungenController {

  /** Die Uhrzeit der Ruhezeit, minutengenau wie in einem Eingabefeld für die Uhrzeit. */
  private static final String UHRZEIT = "HH:mm";

  private final BenachrichtigungEinstellungen einstellungen;

  MeineBenachrichtigungenController(BenachrichtigungEinstellungen einstellungen) {
    this.einstellungen = einstellungen;
  }

  @Operation(
      summary = "Eigene Einstellungen der Mail-Benachrichtigung lesen",
      description =
          """
          Liefert die Projekte, für die der Aufrufer seinen Schalter bedienen darf, mit beiden \
          Schaltern, dazu seine Ruhezeit: die Projekte mit Mitgliedschaft und die, deren Läufe \
          er sehen darf. Ohne Einschalten ist ein Schalter aus.""")
  @ApiResponse(responseCode = "200", description = "Die Einstellungen.")
  @ApiResponse(
      responseCode = "403",
      description = "In der Vorschau „Ansicht als“ bleibt dieser Bereich verborgen.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping
  EinstellungenView lies(@AuthenticationPrincipal Long userId) {
    return EinstellungenView.of(einstellungen.meine(userId));
  }

  @Operation(
      summary = "Eigene Mail-Benachrichtigung für ein Projekt ein- oder ausschalten",
      description =
          """
          Schaltet die Mail-Benachrichtigung des Aufrufers für das Projekt ein oder aus. Was \
          beim Einschalten schon besteht, wird nicht nachgemeldet; erneutes Einschalten \
          verschiebt diesen Zeitpunkt nicht. Eine Mail geht nur, wenn auch das Projekt \
          eingeschaltet ist.""")
  @ApiResponse(responseCode = "204", description = "Geschaltet.")
  @ApiResponse(
      responseCode = "400",
      description = "Der Schalter fehlt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Das Projekt gibt es nicht, oder der Aufrufer ist kein Mitglied und darf seine Läufe"
              + " nicht sehen.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PutMapping("/projekte/{projectId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void schalte(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Kennung des Projekts.", example = "7") @PathVariable long projectId,
      @Valid @RequestBody SchalterRequest request) {
    einstellungen.schaltePerson(userId, projectId, request.an());
  }

  @Operation(
      summary = "Eigene Ruhezeit setzen",
      description =
          """
          Legt die Ruhezeit des Aufrufers an oder ersetzt sie. Sie gilt für alle Anlässe und \
          über alle Projekte; liegt der Beginn nach dem Ende, reicht sie über Mitternacht.""")
  @ApiResponse(responseCode = "204", description = "Gesetzt.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Beginn oder Ende fehlen oder sind keine Uhrzeit, beide sind gleich, oder die Zone"
              + " steht nicht in der IANA-Liste.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PutMapping("/ruhezeit")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void setzeRuhezeit(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RuhezeitRequest request) {
    einstellungen.setzeRuhezeit(userId, request.beginn(), request.ende(), request.zeitzone());
  }

  @Operation(
      summary = "Eigene Ruhezeit löschen",
      description = "Löscht die Ruhezeit des Aufrufers; ohne Ruhezeit geschieht nichts.")
  @ApiResponse(responseCode = "204", description = "Gelöscht.")
  @ApiResponse(
      responseCode = "403",
      description = "In der Vorschau „Ansicht als“ ist Ändern nicht möglich.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @DeleteMapping("/ruhezeit")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void loescheRuhezeit(@AuthenticationPrincipal Long userId) {
    einstellungen.loescheRuhezeit(userId);
  }

  /** Eine Ruhezeit, wie sie die Oberfläche setzt. */
  @Schema(description = "Beginn, Ende und Zeitzone einer Ruhezeit.")
  record RuhezeitRequest(
      @Schema(description = "Beginn, Uhrzeit HH:mm.", type = "string", example = "22:00")
          @NotNull
          @JsonFormat(pattern = UHRZEIT)
          LocalTime beginn,
      @Schema(description = "Ende, Uhrzeit HH:mm.", type = "string", example = "07:00")
          @NotNull
          @JsonFormat(pattern = UHRZEIT)
          LocalTime ende,
      @Schema(description = "IANA-Zeitzone.", example = "Europe/Berlin") @NotBlank
          String zeitzone) {}

  /** Die Ruhezeit in der Antwort. */
  @Schema(description = "Eine eingestellte Ruhezeit.")
  record RuhezeitView(
      @Schema(description = "Beginn, Uhrzeit HH:mm.", type = "string", example = "22:00")
          @JsonFormat(pattern = UHRZEIT)
          LocalTime beginn,
      @Schema(description = "Ende, Uhrzeit HH:mm.", type = "string", example = "07:00")
          @JsonFormat(pattern = UHRZEIT)
          LocalTime ende,
      @Schema(description = "IANA-Zeitzone.", example = "Europe/Berlin") String zeitzone) {

    static RuhezeitView of(Ruhezeit ruhezeit) {
      return new RuhezeitView(ruhezeit.beginn(), ruhezeit.ende(), ruhezeit.zone().getId());
    }
  }

  /** Ein schaltbares Projekt. */
  @Schema(description = "Ein Projekt mit beiden Schaltern.")
  record ProjektView(
      @Schema(description = "Kennung des Projekts.", example = "7") long projectId,
      @Schema(description = "Name des Projekts.", example = "Leitstand") String projectName,
      @Schema(description = "true, wenn das Projekt eingeschaltet ist.", example = "true")
          boolean projektAn,
      @Schema(description = "true, wenn der eigene Schalter an ist.", example = "false")
          boolean personAn) {

    static ProjektView of(PersonenSchalter schalter) {
      return new ProjektView(
          schalter.projectId(), schalter.projectName(), schalter.projektAn(), schalter.personAn());
    }
  }

  /** Die eigenen Einstellungen. */
  @Schema(description = "Die eigenen Einstellungen der Mail-Benachrichtigung.")
  record EinstellungenView(
      @Schema(description = "Die schaltbaren Projekte.") List<ProjektView> projekte,
      @Schema(description = "Die Ruhezeit; null, wenn keine eingestellt ist.")
          @Nullable RuhezeitView ruhezeit) {

    static EinstellungenView of(MeineEinstellungen meine) {
      Ruhezeit ruhezeit = meine.ruhezeit();
      return new EinstellungenView(
          meine.projekte().stream().map(ProjektView::of).toList(),
          ruhezeit == null ? null : RuhezeitView.of(ruhezeit));
    }
  }
}
