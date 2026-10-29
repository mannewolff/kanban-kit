package org.mwolff.manban.push.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import org.mwolff.manban.auth.application.SitzungsEnde;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.push.application.PushAboService;
import org.mwolff.manban.push.application.PushAboService.BoardSchalter;
import org.mwolff.manban.push.application.PushNichtEingerichtetException;
import org.mwolff.manban.push.application.PushOhneSitzungException;
import org.mwolff.manban.push.application.PushProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Benachrichtigungen auf dem Handy (Issue #1997, Plan #1989 E13, E14, E16): der öffentliche
 * Schlüssel des Servers, das Abo dieses Geräts und seine Board-Schalter. Nur per Session ({@code
 * /api/push/**} in {@code ApiZugang}); die Schreibaufrufe weist die Vorschau „Ansicht als“ ab.
 *
 * <p>Ein Abo wird über den Endpunkt des Push-Dienstes angesprochen, den der Browser beim Abonnieren
 * liefert — er steht für das Gerät.
 */
@Tag(name = "Push")
@RestController
@RequestMapping("/api/push")
class PushController {

  /** Höchstlänge des Endpunkts; die Push-Dienste bleiben weit darunter. */
  static final int MAX_ENDPOINT = 2048;

  /**
   * Endpunkt eines Push-Dienstes: {@code https}, Rechnername, optional ein Port, dann ein Pfad.
   * Ohne Anmeldedaten vor dem Rechnernamen und ohne Leerzeichen.
   */
  static final String ENDPOINT_MUSTER = "^https://[A-Za-z0-9.-]+(:\\d{1,5})?/\\S*$";

  /** Öffentlicher P-256-Schlüssel des Browsers: 65 Bytes als base64url. */
  static final String P256DH_MUSTER = "^[A-Za-z0-9_-]{87}=?$";

  /** Authentifizierungsgeheimnis des Browsers: 16 Bytes als base64url. */
  static final String AUTH_MUSTER = "^[A-Za-z0-9_-]{22}(==)?$";

  private static final String BESCHREIBUNG_ENDPUNKT =
      "Endpunkt des Push-Dienstes, wie ihn der Browser beim Abonnieren liefert.";

  private static final String BEISPIEL_ENDPUNKT = "https://fcm.googleapis.com/fcm/send/abc:def";

  /** Beispiel eines unkomprimierten P-256-Punkts als base64url. */
  private static final String BEISPIEL_SCHLUESSEL =
      "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-"
          + "AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";

  private static final String OHNE_ABO =
      "Zum Endpunkt gibt es kein laufendes Abo des Aufrufers — auch ein fremdes oder abgelaufenes"
          + " Abo antwortet so.";

  private final PushAboService service;
  private final PushProperties properties;

  PushController(PushAboService service, PushProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  @Operation(
      summary = "Öffentlichen Schlüssel für Push lesen",
      description =
          """
          Liefert den öffentlichen VAPID-Schlüssel des Servers, mit dem der Browser ein Abo \
          anlegt (applicationServerKey).""")
  @ApiResponse(responseCode = "200", description = "Der Schlüssel.")
  @ApiResponse(
      responseCode = "404",
      description = "Benachrichtigungen sind auf diesem Server nicht eingerichtet.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/schluessel")
  SchluesselView schluessel() {
    if (!properties.aktiv()) {
      throw new PushNichtEingerichtetException();
    }
    return new SchluesselView(Objects.requireNonNull(properties.vapidPublicKey()));
  }

  @Operation(
      summary = "Abo dieses Geräts anlegen oder auffrischen",
      description =
          """
          Legt das Abo für den Endpunkt an oder frischt es auf. Es gilt bis zum Ende der \
          Sitzung, mit der dieser Aufruf kommt; die Oberfläche ruft ihn darum bei jedem Öffnen \
          auf. Ein neues Abo ist für alle Boards eingeschaltet, ein abgelaufenes beginnt neu. \
          Was vor dem Anlegen schon bestand, wird nicht nachgemeldet.""")
  @ApiResponse(responseCode = "204", description = "Angelegt oder aufgefrischt.")
  @ApiResponse(
      responseCode = "400",
      description = "Endpunkt oder Schlüssel fehlen oder haben nicht die erwartete Form.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Der Endpunkt ist für ein anderes Konto abonniert; das Gerät legt ein neues Abo an.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PutMapping("/abo")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void abonniere(
      @AuthenticationPrincipal Long userId,
      Authentication authentication,
      @Valid @RequestBody AboRequest request) {
    if (!(authentication.getDetails() instanceof SitzungsEnde sitzungsEnde)) {
      throw new PushOhneSitzungException();
    }
    service.abonniere(userId, request.endpoint(), request.p256dh(), request.auth(), sitzungsEnde);
  }

  @Operation(
      summary = "Abo dieses Geräts beenden",
      description =
          """
          Beendet das Abo für den Endpunkt samt seinen Board-Schaltern. Die Oberfläche ruft \
          das beim Abmelden auf, bevor die Sitzung endet.""")
  @ApiResponse(responseCode = "204", description = "Beendet.")
  @ApiResponse(
      responseCode = "400",
      description = "Der Endpunkt fehlt.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = OHNE_ABO,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @DeleteMapping("/abo")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void beende(@AuthenticationPrincipal Long userId, @Valid @RequestBody EndpunktRequest request) {
    service.beende(userId, request.endpoint());
  }

  @Operation(
      summary = "Board-Schalter dieses Geräts lesen",
      description =
          """
          Liefert die Boards, zu deren Läufen Nachrichten kommen können, und ob sie auf diesem \
          Gerät eingeschaltet sind: die nicht archivierten Boards der Projekte, deren Läufe der \
          Aufrufer sehen darf — als Owner, als Plattform-Admin bei teilnehmendem Projekt.""")
  @ApiResponse(responseCode = "200", description = "Die Boards.")
  @ApiResponse(
      responseCode = "404",
      description = OHNE_ABO,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/boards")
  List<BoardSchalterView> boards(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = BESCHREIBUNG_ENDPUNKT, example = BEISPIEL_ENDPUNKT) @RequestParam
          String endpoint) {
    return service.schaltbareBoards(userId, endpoint).stream().map(BoardSchalterView::of).toList();
  }

  @Operation(
      summary = "Board auf diesem Gerät ein- oder abschalten",
      description =
          """
          Schaltet Nachrichten zu Läufen des Boards auf diesem Gerät ein oder ab. Ein Lauf \
          ohne erkennbares Board meldet, solange mindestens ein Board seines Projekts \
          eingeschaltet ist.""")
  @ApiResponse(responseCode = "204", description = "Geschaltet.")
  @ApiResponse(
      responseCode = "400",
      description = "Endpunkt oder Schalter fehlen.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          OHNE_ABO
              + " Oder das Board gibt es nicht, es ist archiviert, oder der Aufrufer darf die"
              + " Läufe seines Projekts nicht sehen.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @PutMapping("/boards/{boardId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void schalte(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Kennung des Boards.", example = "40") @PathVariable long boardId,
      @Valid @RequestBody SchalterRequest request) {
    service.schalte(userId, request.endpoint(), boardId, request.an());
  }

  /** Der öffentliche Schlüssel des Servers. */
  @Schema(description = "Der öffentliche VAPID-Schlüssel des Servers.")
  record SchluesselView(
      @Schema(
              description = "Unkomprimierter P-256-Punkt als base64url, für applicationServerKey.",
              example = BEISPIEL_SCHLUESSEL)
          String publicKey) {}

  /** Das Abo, wie der Browser es liefert ({@code PushSubscription.toJSON()}, flach). */
  @Schema(description = "Das Abo eines Geräts.")
  record AboRequest(
      @Schema(description = BESCHREIBUNG_ENDPUNKT, example = BEISPIEL_ENDPUNKT)
          @NotBlank
          @Size(max = MAX_ENDPOINT)
          @Pattern(regexp = ENDPOINT_MUSTER)
          String endpoint,
      @Schema(
              description = "Öffentlicher Schlüssel des Browsers (keys.p256dh), base64url.",
              example = BEISPIEL_SCHLUESSEL)
          @NotNull
          @Pattern(regexp = P256DH_MUSTER)
          String p256dh,
      @Schema(
              description = "Authentifizierungsgeheimnis des Browsers (keys.auth), base64url.",
              example = "BTBZMqHH6r4Tts7J_aSIgg")
          @NotNull
          @Pattern(regexp = AUTH_MUSTER)
          String auth) {}

  /** Nur der Endpunkt, zum Beenden. */
  @Schema(description = "Der Endpunkt eines Abos.")
  record EndpunktRequest(
      @Schema(description = BESCHREIBUNG_ENDPUNKT, example = BEISPIEL_ENDPUNKT)
          @NotBlank
          @Size(max = MAX_ENDPOINT)
          String endpoint) {}

  /** Ein Board-Schalter. */
  @Schema(description = "Schalter eines Boards auf einem Gerät.")
  record SchalterRequest(
      @Schema(description = BESCHREIBUNG_ENDPUNKT, example = BEISPIEL_ENDPUNKT)
          @NotBlank
          @Size(max = MAX_ENDPOINT)
          String endpoint,
      @Schema(description = "true schaltet Nachrichten ein, false ab.", example = "false") @NotNull
          Boolean an) {}

  /** Ein schaltbares Board. */
  @Schema(description = "Ein Board und ob Nachrichten zu seinen Läufen auf diesem Gerät kommen.")
  record BoardSchalterView(
      @Schema(description = "Kennung des Boards.", example = "40") long boardId,
      @Schema(description = "Name des Boards.", example = "Produkt") String boardName,
      @Schema(description = "Kennung des Projekts.", example = "7") long projectId,
      @Schema(description = "Name des Projekts.", example = "Leitstand") String projectName,
      @Schema(description = "true, wenn Nachrichten kommen.", example = "true") boolean an) {

    static BoardSchalterView of(BoardSchalter schalter) {
      return new BoardSchalterView(
          schalter.boardId(),
          schalter.boardName(),
          schalter.projectId(),
          schalter.projectName(),
          schalter.an());
    }
  }
}
