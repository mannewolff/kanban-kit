package org.mwolff.manban.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.web.security.PatAuthenticationFilter;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kopf der API-Beschreibung (Issue #1402, Plan #1400, E8/E16): Titel, Version der laufenden
 * Anwendung, Einführungstext und die beiden Security-Schemes. Reines Bean-Wiring; die Logik der
 * Anreicherung liegt in {@link ApiBeschreibungsAnreicherung}.
 */
@Configuration
class OpenApiConfig {

  static final String TITEL = "kanban-kit — API des Leitstands";

  /** Version, wenn {@code build-info.properties} fehlt (etwa beim Start aus der IDE). */
  static final String VERSION_UNBEKANNT = "unbekannt";

  static final String EINFUEHRUNG =
      """
      Die vollständige HTTP-Schnittstelle des Leitstands, erzeugt aus dem Code der laufenden \
      Version. Alle Pfade liegen unter `/api`, Anfragen und Antworten sind JSON.
      """;

  static final String ZUGANGSARTEN =
      """
      ## Zugang

      Jeder Aufruf nennt seinen Zugang in `x-zugang` und im Security-Requirement:

      - **oeffentlich** — ohne Anmeldung (Registrierung, Anmeldung, Passwort-Reset).
      - **nur-session** — nur mit dem Session-Cookie `manban_session` einer Anmeldung im \
      Browser, nicht mit einem Token (Token-Verwaltung, Administration, diese Beschreibung).
      - **nur-projekt-token** — nur mit einem Projekt-Token im Header `X-Kanban-Token` \
      (Kanban-kompatible Schnittstelle unter `/api/kanban`). Ein an ein Board gebundenes Token \
      erreicht ausschließlich diese Pfade.
      - **session-oder-ungebundenes-token** — mit Session-Cookie oder einem Token, das an kein \
      Board gebunden ist (alle übrigen Pfade).

      Fehlt die Anmeldung oder ist das Token ungültig oder widerrufen, antwortet der Leitstand \
      mit 401; passt die Zugangsart nicht oder fehlt ein Recht, mit 403. Welches Recht ein \
      Aufruf verlangt, steht in `x-erforderliches-recht`: der Name einer Projekt-Berechtigung \
      oder `PLATTFORM_ADMIN`. Die Beschreibung zeigt jedem Angemeldeten die gesamte API, auch \
      Aufrufe, die er selbst nicht verwenden darf.
      """;

  static final String FEHLERFORMAT =
      """
      ## Fehlerformat

      Fachliche Fehler antworten mit `application/problem+json` nach RFC 9457 (Schema \
      `ProblemDetail`): `status`, `title`, `detail` und bei feldbezogenen Fehlern `fieldErrors` \
      (Feldname → Meldung).
      """;

  static final String STABILITAET =
      """
      ## Stabilität

      Jeder Aufruf trägt in `x-stabilitaet` seine Einstufung:

      - **verlaesslich** — eine fremde Anbindung kann sich darauf stützen. Ein verlässlicher \
      Aufruf wird nicht ohne Vorlauf geändert oder entfernt: Er wird zuerst als abgekündigt \
      (`deprecated`) gekennzeichnet und ändert sich oder entfällt frühestens eine \
      Minor-Version später. Jede solche Änderung steht als API-Änderung im Changelog, und \
      verlangt sie eine Anpassung, nennt UPGRADING.md die Schritte.
      - **aenderbar** — kann sich mit jeder Version ändern.
      """;

  static final String GLOSSAR =
      """
      ## Begriffe

      - **Lauf** — eine Sitzung eines Agenten, die Karten abarbeitet, etwa ein Nachtlauf; der \
      Leitstand nimmt sein Ergebnis über die Lauf-Schnittstelle entgegen.
      - **Kette** — ein Lauf, der eine Anforderung über mehrere Stufen bis zur Umsetzung führt.
      - **Stufe** — ein Abschnitt einer Kette, etwa Fachplan, Plan oder Umsetzung.
      - **Laufstand** — der Kommentar `## Laufstand`, mit dem ein laufender Lauf an der Karte \
      meldet, woran er gerade arbeitet.
      - **Herkunft** (`derivedFrom`) — die Karte, aus der eine Karte abgeleitet wurde, etwa ein \
      Arbeitspaket aus seinem Plan.
      - **Idempotency-Key** — optionaler Header, der eine Anlage wiederholbar macht: Derselbe \
      Schlüssel im selben Projekt erzeugt genau eine Karte bzw. einen Kommentar und beliebig \
      viele gleiche Antworten.
      """;

  @Bean
  OpenAPI manbanOpenApi(ObjectProvider<BuildProperties> buildProperties) {
    @Nullable BuildProperties build = buildProperties.getIfAvailable();
    String version = build == null ? VERSION_UNBEKANNT : build.getVersion();
    return new OpenAPI()
        .info(
            new Info()
                .title(TITEL)
                .version(version)
                .description(
                    String.join(
                        "\n", EINFUEHRUNG, ZUGANGSARTEN, FEHLERFORMAT, STABILITAET, GLOSSAR)))
        .components(
            new Components()
                .addSecuritySchemes(
                    ApiBeschreibungsAnreicherung.SESSION_COOKIE,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(SessionCookieManager.COOKIE_NAME)
                        .description("Session-Cookie einer Anmeldung im Browser."))
                .addSecuritySchemes(
                    ApiBeschreibungsAnreicherung.PROJEKT_TOKEN,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(PatAuthenticationFilter.HEADER)
                        .description("Projekt-Token aus der Token-Verwaltung.")));
  }

  @Bean
  ApiBeschreibungsAnreicherung apiBeschreibungsAnreicherung() {
    return new ApiBeschreibungsAnreicherung();
  }
}
