package org.mwolff.manban.fremdboard.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.fremdboard.application.VerbindungAnsicht;
import org.mwolff.manban.fremdboard.application.VerbindungEingabe;
import org.mwolff.manban.fremdboard.application.VerbindungService;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Board eines Projekts in einem fremden System einrichten und prüfen (Issue #2166, Plan #2152 A2,
 * A13, A18). Nur für den Owner des Projekts; der Zugang steht in keiner Antwort, auch nicht
 * verschlüsselt. Lösen gibt es nicht.
 */
@Tag(name = "Fremdes Board")
@RestController
@RequestMapping("/api/projects/{id}/fremdboard")
class FremdBoardController {

  private static final String PROJEKT_ID = "Interne ID des Projekts.";

  private static final String KEIN_OWNER =
      "Der Aufrufer ist Mitglied, aber nicht Owner des Projekts.";

  private static final String PROJEKT_FEHLT =
      "Das Projekt gibt es nicht, oder der Aufrufer ist kein Mitglied.";

  private final VerbindungService service;

  FremdBoardController(VerbindungService service) {
    this.service = service;
  }

  @Operation(
      summary = "Board in einem fremden System lesen",
      description =
          """
          Sagt, ob das Board des Projekts in einem fremden System liegt, und nennt dann Art, \
          Adresse, Projektpfad, Kit-Konto, Statuslabels und den Stand des letzten Abgleichs. \
          zugangLesbar sagt, ob sich der hinterlegte Zugang entschlüsseln lässt; sonst muss er \
          neu hinterlegt werden. Der Zugang selbst steht nie in der Antwort. Nur für den Owner.""")
  @ApiResponse(responseCode = "200", description = "Die Verbindung des Projekts.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_OWNER,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping
  FremdBoardView lies(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = PROJEKT_ID, example = "1") @PathVariable long id) {
    return FremdBoardView.aus(service.lesen(userId, id));
  }

  @Operation(
      summary = "Board an ein fremdes System binden oder den Zugang neu hinterlegen",
      description =
          """
          Bindet das Board des Projekts an ein Projekt in einem fremden System (zuerst GitLab). \
          Das geht nur, solange das Projekt keine einzige Karte hat — auch keine archivierte, \
          keine im Papierkorb und kein Vorhaben — und höchstens ein aktives Board, das zum \
          Spiegel-Board wird; ohne Board entsteht eines mit den fünf Standardspalten. Die Adresse \
          muss in der Erlaubnisliste stehen, und ein Probeabruf mit dem Zugang muss gelingen. Bei \
          einer bestehenden Verbindung bleiben Art, Adresse und Projektpfad fest; ein angegebener \
          Zugang ersetzt den alten, Kit-Konto und Statuslabels werden übernommen. Nur für den \
          Owner.""")
  @ApiResponse(responseCode = "200", description = "Die gespeicherte Verbindung.")
  @ApiResponse(
      responseCode = "400",
      description =
          "Pflichtangabe fehlt, Zugang fehlt beim ersten Anbinden, Adresse nicht erlaubt oder das"
              + " fremde Board ließ sich mit den Angaben nicht lesen.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = KEIN_OWNER,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = PROJEKT_FEHLT,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Das Projekt hat schon Karten oder weitere Boards, für den Zugang ist kein Schlüssel"
              + " gesetzt, oder Adresse oder Projektpfad einer bestehenden Verbindung sollen"
              + " wechseln.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PutMapping
  FremdBoardView speichere(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = PROJEKT_ID, example = "1") @PathVariable long id,
      @Valid @RequestBody FremdBoardRequest request) {
    return FremdBoardView.aus(service.speichern(userId, id, request.eingabe()));
  }

  /** Die Labelnamen, an denen das fremde System den Status trägt. */
  @Schema(
      description =
          "Labelnamen je Status im fremden System; Done hat keins, eine geschlossene Karte gilt"
              + " als Done.")
  record Statuslabels(
      @Schema(description = "Label für Backlog.", example = "Backlog") @NotBlank @Size(max = 255)
          String backlog,
      @Schema(description = "Label für Ready.", example = "Ready") @NotBlank @Size(max = 255)
          String ready,
      @Schema(description = "Label für In progress.", example = "In progress")
          @NotBlank
          @Size(max = 255)
          String inProgress,
      @Schema(description = "Label für In review.", example = "In review")
          @NotBlank
          @Size(max = 255)
          String inReview) {

    static Statuslabels aus(StatusLabels labels) {
      return new Statuslabels(
          labels.backlog(), labels.ready(), labels.inProgress(), labels.inReview());
    }

    StatusLabels zuDomain() {
      return new StatusLabels(backlog, ready, inProgress, inReview);
    }
  }

  /** Was der Owner zur Verbindung angibt. */
  @Schema(description = "Angaben zur Verbindung mit dem Board im fremden System.")
  record FremdBoardRequest(
      @Schema(description = "Das fremde System.", example = "GITLAB") @NotNull FremdBoardArt art,
      @Schema(
              description = "Adresse des fremden Systems, nur https.",
              example = "https://gitlab.com")
          @NotBlank
          @Size(max = 500)
          String basisUrl,
      @Schema(description = "Pfad des Projekts im fremden System.", example = "gruppe/projekt")
          @NotBlank
          @Size(max = 500)
          String projektPfad,
      @Schema(
              description =
                  "Lesezugang im fremden System (bei GitLab read_api); beim ersten Anbinden"
                      + " Pflicht, danach nur zum Neu-Hinterlegen. Wird nie ausgegeben.",
              example = "glpat-…")
          @Size(max = 1000)
          @Nullable String zugang,
      @Schema(description = "Benutzername des Kits im fremden System.", example = "kit-bot")
          @Size(max = 255)
          @Nullable String kitKonto,
      @Schema(description = "Labelnamen je Status; ohne Angabe die Namen der Kit-Spalten.") @Valid
          @Nullable Statuslabels statuslabels) {

    VerbindungEingabe eingabe() {
      return new VerbindungEingabe(
          art,
          basisUrl,
          projektPfad,
          zugang,
          kitKonto,
          statuslabels == null ? null : statuslabels.zuDomain());
    }

    /** Textform ohne den Zugang. */
    @Override
    public String toString() {
      return "FremdBoardRequest[" + eingabe() + "]";
    }
  }

  /** Die Verbindung, wie die Einstellung sie zeigt — ohne Zugang. */
  @Schema(description = "Die Verbindung des Projekts zu einem Board in einem fremden System.")
  record FremdBoardView(
      @Schema(description = "true, wenn das Board des Projekts in einem fremden System liegt.")
          boolean angebunden,
      @Schema(
              description =
                  "false, wenn MANBAN_SECRET_KEY fehlt; dann lässt sich kein fremdes Board"
                      + " anbinden.")
          boolean schluesselGesetzt,
      @Schema(description = "Das fremde System; leer ohne Verbindung.", example = "GITLAB")
          @Nullable FremdBoardArt art,
      @Schema(description = "Adresse des fremden Systems.", example = "https://gitlab.com")
          @Nullable String basisUrl,
      @Schema(description = "Pfad des Projekts im fremden System.", example = "gruppe/projekt")
          @Nullable String projektPfad,
      @Schema(description = "Benutzername des Kits im fremden System.", example = "kit-bot")
          @Nullable String kitKonto,
      @Schema(description = "Labelnamen je Status.") @Nullable Statuslabels statuslabels,
      @Schema(description = "Zeitpunkt der Anbindung; ab hier wird gezählt.")
          @Nullable Instant angebundenAm,
      @Schema(description = "Zeitpunkt des letzten Abgleichs; leer vor dem ersten.")
          @Nullable Instant letzterAbgleichAm,
      @Schema(description = "Fehler des letzten Abgleichs; leer, wenn er gelang.")
          @Nullable String letzterFehler,
      @Schema(
              description =
                  "false, wenn sich der hinterlegte Zugang nicht entschlüsseln lässt; dann muss er"
                      + " neu hinterlegt werden.")
          boolean zugangLesbar) {

    static FremdBoardView aus(VerbindungAnsicht ansicht) {
      StatusLabels labels = ansicht.statuslabels();
      return new FremdBoardView(
          ansicht.angebunden(),
          ansicht.schluesselGesetzt(),
          ansicht.art(),
          ansicht.basisUrl(),
          ansicht.projektPfad(),
          ansicht.kitKonto(),
          labels == null ? null : Statuslabels.aus(labels),
          ansicht.angebundenAm(),
          ansicht.letzterAbgleichAm(),
          ansicht.letzterFehler(),
          ansicht.zugangLesbar());
    }
  }
}
