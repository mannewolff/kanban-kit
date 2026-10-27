package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.AdminVorschauService;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.auth.application.VorschauKontextLeser;
import org.mwolff.manban.auth.application.VorschauLaeuftBereitsException;
import org.mwolff.manban.auth.application.VorschauStart;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.VorschauEndgrund;
import org.mwolff.manban.auth.web.security.SessionAuthenticationFilter;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.auth.web.security.VorschauCookie;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starten, Beenden und Protokoll der nur lesenden „Ansicht als XY“ (Issue #1921, Plan #1914 E9,
 * E11, E12).
 *
 * <p><strong>Start nur mit Sitzung (E11):</strong> Der Pfad liegt unter {@code /api/admin/**} und
 * ist damit schon in der Sicherheitskette auf die Sitzung beschränkt. Der Controller prüft es
 * trotzdem selbst an der Authentifizierung: Eine Vorschau erweitert die Sicht des Admins, und diese
 * Regel soll nicht allein an einer Zeile der Zugangs-Tabelle hängen.
 */
@Tag(
    name = "Vorschau",
    description =
        "Nur lesende Ansicht der App als ein anderes Konto für Plattform-Admins: starten, beenden"
            + " und die Einträge, wer wann als wen geschaut hat.")
@RestController
class AdminVorschauController {

  private static final String KEIN_ADMIN =
      "Der Aufrufer ist kein Plattform-Admin oder nicht per Sitzung angemeldet.";

  private final AdminVorschauService vorschauen;
  private final SessionCookieManager cookies;
  private final VorschauKontextLeser kontext;

  AdminVorschauController(
      AdminVorschauService vorschauen, SessionCookieManager cookies, VorschauKontextLeser kontext) {
    this.vorschauen = vorschauen;
    this.cookies = cookies;
    this.kontext = kontext;
  }

  @Operation(
      summary = "Ansicht als ein Konto starten",
      description =
          "Startet für die Anmeldung des Aufrufers eine nur lesende Ansicht der App als das"
              + " genannte Konto, höchstens 30 Minuten lang. Setzt das Cookie manban_vorschau"
              + " (Header Set-Cookie); die Oberfläche lädt danach neu. Nur mit Sitzung, nur für"
              + " einen nicht gesperrten Plattform-Admin und nur, solange in dieser Anmeldung"
              + " keine Vorschau läuft.")
  @ApiResponse(responseCode = "204", description = "Die Vorschau läuft, das Cookie ist gesetzt.")
  @ApiResponse(
      responseCode = "400",
      description = "Das Ziel ist der Aufrufer selbst oder ein Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = "Ein Benutzer mit dieser ID existiert nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "409",
      description = "In dieser Anmeldung läuft schon eine Vorschau; sie wird zuerst beendet.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @PostMapping("/api/admin/users/{id}/vorschau")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void starte(
      @Parameter(
              description = "Interne ID des Kontos, als das die App gezeigt wird.",
              example = "7")
          @PathVariable
          long id,
      HttpServletRequest request,
      HttpServletResponse response) {
    long adminId = sitzungsKonto();
    if (kontext.aktuell().isPresent()) {
      throw new VorschauLaeuftBereitsException();
    }
    VorschauStart start = vorschauen.starte(adminId, id, cookies.readVorschau(request).isPresent());
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        cookies.createVorschau(start.vorschau().kennung(), start.geheimnis()).toString());
  }

  @Operation(
      summary = "Ansicht beenden",
      description =
          "Beendet die Vorschau dieser Anmeldung und löscht das Cookie manban_vorschau (Header"
              + " Set-Cookie). Danach gilt wieder die eigene Sicht des Aufrufers. Läuft keine"
              + " Vorschau, wird nur das Cookie gelöscht.")
  @ApiResponse(responseCode = "204", description = "Die Vorschau ist beendet.")
  @ApiResponse(
      responseCode = "409",
      description =
          "Die Vorschau war schon zu Ende oder der Tab zeigt einen anderen Stand (Problem-Typ"
              + " vorschau-gewechselt); das Cookie ist gelöscht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @DeleteMapping("/api/vorschau")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void beende(HttpServletRequest request, HttpServletResponse response) {
    Optional<VorschauKontext> laufend = kontext.aktuell();
    Optional<VorschauCookie> cookie = cookies.readVorschau(request);
    if (laufend.isPresent() && cookie.isPresent()) {
      vorschauen.beende(cookie.get().kennung(), cookie.get().geheimnis(), laufend.get().adminId());
    }
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearVorschau().toString());
  }

  @Operation(
      summary = "Einträge der Ansicht als ein Konto lesen",
      description =
          "Liefert, wann welcher Admin die App als das genannte Konto angesehen hat, die zuletzt"
              + " gestartete Ansicht zuerst. Eine abgelaufene Ansicht steht mit ihrem"
              + " Ablaufzeitpunkt und dem Grund ABLAUF darin.")
  @ApiResponse(responseCode = "200", description = "Die Einträge.")
  @ApiResponse(
      responseCode = "403",
      description = KEIN_ADMIN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @GetMapping("/api/admin/users/{id}/vorschau-protokoll")
  List<VorschauEintrag> protokollFuerAdmin(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Zielkontos.", example = "7") @PathVariable long id) {
    return vorschauen.protokollFuerAdmin(userId, id).stream().map(VorschauEintrag::von).toList();
  }

  @Operation(
      summary = "Eigene Einträge der Ansicht lesen",
      description =
          "Liefert, wann welcher Admin die App als das Konto des Aufrufers angesehen hat, die"
              + " zuletzt gestartete Ansicht zuerst. Nur mit Sitzung.")
  @ApiResponse(responseCode = "200", description = "Die Einträge.")
  @ApiResponse(
      responseCode = "403",
      description =
          "Aufruf in einer laufenden Vorschau (Problem-Typ vorschau-persoenlich): Persönliches"
              + " des angesehenen Kontos zeigt die Vorschau nicht.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/me/vorschau-protokoll")
  List<VorschauEintrag> eigenesProtokoll(@AuthenticationPrincipal Long userId) {
    return vorschauen.protokoll(userId).stream().map(VorschauEintrag::von).toList();
  }

  /** Das Konto der Sitzung; ein Projekt-Token oder eine fehlende Anmeldung startet nichts. */
  private static long sitzungsKonto() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof Long userId
        && authentication.getAuthorities().stream()
            .anyMatch(g -> SessionAuthenticationFilter.AUTHORITY.equals(g.getAuthority()))) {
      return userId;
    }
    throw new AdminAccessDeniedException();
  }

  /** Ein Eintrag der Ansicht als ein Konto, ohne Kennung und Geheimnis. */
  @Schema(description = "Eine Ansicht der App als ein Konto: wer, wann, wie lange, warum beendet.")
  record VorschauEintrag(
      @Schema(description = "Anzeigename des Admins beim Start.", example = "Ada Lovelace")
          String adminName,
      @Schema(
              description =
                  "Ob das Konto des Admins inzwischen gelöscht ist; der Name bleibt dann stehen.",
              example = "false")
          boolean adminKontoGeloescht,
      @Schema(description = "Zeitpunkt des Starts.") Instant gestartetAm,
      @Schema(description = "Zeitpunkt, an dem die Ansicht spätestens endet.") Instant laeuftAbAm,
      @Schema(description = "Zeitpunkt des Endes; null, solange die Ansicht läuft.")
          @Nullable Instant beendetAm,
      @Schema(
              description =
                  "Grund des Endes: BEENDET, ABLAUF, ABMELDUNG, ROLLE_ENTZOGEN, ADMIN_GESPERRT,"
                      + " SITZUNG_UNGUELTIG, ZIEL_IST_ADMIN oder ZIEL_FEHLT; null, solange die"
                      + " Ansicht läuft.",
              example = "BEENDET")
          @Nullable VorschauEndgrund endgrund) {

    static VorschauEintrag von(AdminVorschau vorschau) {
      return new VorschauEintrag(
          vorschau.adminName(),
          vorschau.adminId() == null,
          vorschau.gestartetAm(),
          vorschau.laeuftAbAm(),
          vorschau.beendetAm(),
          vorschau.endgrund());
    }
  }
}
