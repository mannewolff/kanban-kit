package org.mwolff.manban.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.common.web.api.Stabilitaet;
import org.mwolff.manban.config.ApiZugang.Zugangsart;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * Reichert die von springdoc erzeugte API-Beschreibung an (Issue #1402, Plan #1400).
 *
 * <ul>
 *   <li>Je Aufruf aus {@link ApiVertrag}: {@code x-stabilitaet}, {@code x-erforderliches-recht},
 *       {@code deprecated} und eine vorangestellte Zeile zur Stabilität bzw. Abkündigung. Ohne
 *       Annotation gilt {@link Stabilitaet#AENDERBAR}.
 *   <li>Je Pfad aus {@link ApiZugang}: {@code x-zugang}, das Security-Requirement und — außer bei
 *       öffentlichen Pfaden — die Antworten 401 und 403, sofern die Methode sie nicht selbst
 *       beschreibt.
 *   <li>Das gemeinsame Fehlerschema {@link ApiSchemas#PROBLEM_DETAIL}.
 * </ul>
 *
 * <p>{@code @AuthenticationPrincipal}-Parameter blendet springdoc selbst aus ({@code
 * SpringDocSecurityConfiguration}); dafür braucht es hier nichts (Plan #1400, E13).
 */
class ApiBeschreibungsAnreicherung implements OperationCustomizer, OpenApiCustomizer {

  static final String X_STABILITAET = "x-stabilitaet";
  static final String X_ERFORDERLICHES_RECHT = "x-erforderliches-recht";
  static final String X_ZUGANG = "x-zugang";

  /** Security-Scheme des Session-Cookies (in {@link OpenApiConfig} definiert). */
  static final String SESSION_COOKIE = "sessionCookie";

  /** Security-Scheme des Projekt-Tokens (in {@link OpenApiConfig} definiert). */
  static final String PROJEKT_TOKEN = "projektToken";

  static final String UNAUTHORIZED = "401";
  static final String FORBIDDEN = "403";

  static final String BESCHREIBUNG_401 =
      "Nicht angemeldet: Session-Cookie bzw. Projekt-Token fehlt, ist ungültig oder widerrufen.";

  static final String BESCHREIBUNG_403 =
      "Angemeldet, aber nicht berechtigt: falsche Zugangsart für diesen Pfad (etwa ein"
          + " board-gebundenes Token außerhalb von /api/kanban/**) oder das verlangte Recht fehlt.";

  private static final String TYP_STRING = "string";
  private static final Pattern MAJOR_MINOR = Pattern.compile("(\\d+)\\.(\\d+)");

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    ApiVertrag vertrag = handlerMethod.getMethodAnnotation(ApiVertrag.class);
    Stabilitaet stabilitaet = vertrag == null ? Stabilitaet.AENDERBAR : vertrag.stabilitaet();
    operation.addExtension(X_STABILITAET, stabilitaet.kennung());
    if (vertrag == null) {
      return operation;
    }
    if (!vertrag.recht().isEmpty()) {
      operation.addExtension(X_ERFORDERLICHES_RECHT, vertrag.recht());
    }
    if (!vertrag.abgekuendigtSeit().isEmpty()) {
      operation.setDeprecated(true);
      voranstellen(operation, abkuendigung(vertrag.abgekuendigtSeit()));
    } else if (stabilitaet == Stabilitaet.VERLAESSLICH) {
      voranstellen(operation, "Stabilität: verlässlich.");
    }
    return operation;
  }

  @Override
  public void customise(OpenAPI openApi) {
    if (openApi.getPaths() != null) {
      openApi
          .getPaths()
          .forEach(
              (pfad, item) -> {
                Zugangsart zugang = ApiZugang.zugangFuer(pfad);
                for (Operation operation : item.readOperations()) {
                  zugangBeschreiben(operation, zugang);
                }
              });
    }
    if (openApi.getComponents() == null) {
      openApi.setComponents(new Components());
    }
    openApi.getComponents().addSchemas(ApiSchemas.PROBLEM_DETAIL, problemDetail());
  }

  private static void zugangBeschreiben(Operation operation, Zugangsart zugang) {
    operation.addExtension(X_ZUGANG, zugang.kennung());
    // Mehrere Requirements sind Alternativen: Session-Cookie ODER Token.
    operation.setSecurity(
        switch (zugang) {
          case OEFFENTLICH -> List.of();
          case NUR_SESSION -> List.of(new SecurityRequirement().addList(SESSION_COOKIE));
          case NUR_PROJEKT_TOKEN -> List.of(new SecurityRequirement().addList(PROJEKT_TOKEN));
          case SESSION_ODER_UNGEBUNDENES_TOKEN ->
              List.of(
                  new SecurityRequirement().addList(SESSION_COOKIE),
                  new SecurityRequirement().addList(PROJEKT_TOKEN));
        });
    if (zugang == Zugangsart.OEFFENTLICH) {
      return;
    }
    if (operation.getResponses() == null) {
      operation.setResponses(new ApiResponses());
    }
    operation
        .getResponses()
        .putIfAbsent(UNAUTHORIZED, new ApiResponse().description(BESCHREIBUNG_401));
    operation
        .getResponses()
        .putIfAbsent(FORBIDDEN, new ApiResponse().description(BESCHREIBUNG_403));
  }

  private static String abkuendigung(String seit) {
    Matcher version = MAJOR_MINOR.matcher(seit);
    if (!version.matches()) {
      throw new IllegalArgumentException(
          "@ApiVertrag(abgekuendigtSeit) verlangt <major>.<minor>, nicht '" + seit + "'");
    }
    int folgendeMinor = Integer.parseInt(version.group(2)) + 1;
    return "Abgekündigt seit "
        + seit
        + " — entfällt frühestens mit "
        + version.group(1)
        + "."
        + folgendeMinor
        + ".";
  }

  private static void voranstellen(Operation operation, String zeile) {
    @Nullable String beschreibung = operation.getDescription();
    operation.setDescription(beschreibung == null ? zeile : zeile + "\n\n" + beschreibung);
  }

  /** RFC 9457 Problem Details, wie sie {@code GlobalExceptionHandler} schreibt. */
  private static Schema<?> problemDetail() {
    return feld(
            "object",
            "Fehlerantwort nach RFC 9457 (Problem Details, application/problem+json), bei"
                + " Validierungs- und feldbezogenen Fehlern ergänzt um fieldErrors.")
        .addProperty(
            "type",
            feld(TYP_STRING, "Kennung der Fehlerart (URI); ohne eigene Art about:blank.")
                .format("uri"))
        .addProperty("title", feld(TYP_STRING, "Kurzbezeichnung der Fehlerart."))
        .addProperty("status", feld("integer", "HTTP-Statuscode.").format("int32"))
        .addProperty("detail", feld(TYP_STRING, "Lesbare Erklärung dieses Fehlers."))
        .addProperty(
            "instance",
            feld(TYP_STRING, "Pfad der Anfrage, die den Fehler auslöste.").format("uri"))
        .addProperty(
            "fieldErrors",
            feld("object", "Nur bei feldbezogenen Fehlern: Feldname → Meldung.")
                .additionalProperties(feld(TYP_STRING, "Meldung zum Feld.")));
  }

  /**
   * Ein Schema mit Typ und Beschreibung. {@link JsonSchema} mit {@code types}, weil springdoc 2.8
   * OpenAPI 3.1 schreibt und dort ein nur über {@code type(…)} gesetzter Typ entfiele.
   */
  private static Schema<?> feld(String typ, String beschreibung) {
    return new JsonSchema().types(Set.of(typ)).description(beschreibung);
  }
}
