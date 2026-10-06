package org.mwolff.manban.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.mwolff.manban.accesstoken.web.security.PatAuthenticationFilter;
import org.mwolff.manban.auth.web.security.ApiAusprobierFilter;
import org.mwolff.manban.auth.web.security.DisabledUserGuardFilter;
import org.mwolff.manban.auth.web.security.SessionAuthenticationFilter;
import org.mwolff.manban.ratelimit.web.ThroughputFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Zentrale Web-Security und zugleich die anwendungsweite Composition-Root der Filterkette.
 *
 * <p>Die Klasse liegt bewusst außerhalb der Fachmodule (Issue #438): Sie verdrahtet Adapter aus
 * {@code auth} und {@code accesstoken}, und genau diese Verdrahtung schloss zuvor — als Teil des
 * {@code auth}-Moduls — den Modulzyklus {@code auth → accesstoken → board → project → auth}. Als
 * modulfreie Composition-Root darf sie beide Seiten kennen, ohne dass die Fachmodule einander
 * kennen müssen.
 *
 * <ul>
 *   <li>Zustandslos: kein Server-Session-Store; Authentifizierung über das signierte Session-Cookie
 *       ({@link SessionAuthenticationFilter}).
 *   <li>Default-Deny für {@code /api/**} (außer den öffentlichen Auth-Endpunkten); statische
 *       Inhalte und die React-App unter {@code /} bleiben offen. Die Pfadregeln stehen geordnet in
 *       {@link ApiZugang} (Issue #1402).
 *   <li>Unauthentifizierte API-Zugriffe → 401 (kein Redirect auf eine Login-Seite).
 *   <li>Board-Bindung (Issue #836): Ein an ein Board gebundenes Zugriffs-Token erreicht
 *       ausschließlich {@code /api/kanban/**}; die übrige {@code /api/**}-Oberfläche verlangt die
 *       Session-Authority oder {@code AUTH_PAT_UNBOUND} und antwortet einem gebundenen Token mit
 *       403 — unabhängig von den Rollen des Erstellers, einschließlich Plattform-Admin.
 *   <li>CSRF: Der Synchronizer-Token entfällt bewusst — es gibt keine Server-Session, und das
 *       Auth-Cookie ist {@code HttpOnly; SameSite=Strict}, wird also nie cross-site gesendet. Damit
 *       ist der zustandslose Cookie-Ansatz CSRF-resistent.
 *   <li>Ausprobieren aus der API-Übersicht (Issue #1366): Ein Aufruf mit dem Kennzeichen {@code
 *       X-Api-Ausprobieren} kommt nur für aktive Plattform-Admins durch ({@link
 *       ApiAusprobierFilter}, vor den Auth-Filtern); trägt er ein Projekt-Token, gilt das Token.
 * </ul>
 *
 * <p>2FA-Vorbereitung: Der zweite Faktor hängt im Login-Flow (SessionController / LoginService),
 * nicht hier — die Filterkette bleibt unverändert.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

  // CSRF bewusst deaktiviert (Sonar java:S4502): das Auth-Cookie ist HttpOnly + SameSite=Strict +
  // Secure (SessionCookieManager) und wird daher nie cross-site gesendet — der zustandslose
  // Cookie-Ansatz ist von sich aus CSRF-resistent, ein Synchronizer-Token wäre wirkungslose
  // Zusatzkomplexität. Siehe CLAUDE-security.md, Abschnitt "Session-Cookie — Sicherheitsmodell".
  @SuppressWarnings("java:S4502")
  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      SessionAuthenticationFilter sessionFilter,
      PatAuthenticationFilter patFilter,
      DisabledUserGuardFilter disabledGuard,
      ThroughputFilter throughputFilter,
      ApiAusprobierFilter ausprobierFilter)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              // Interne Container-Re-Dispatches (ASYNC bei SseEmitter-Streams, ERROR bei der
              // Fehlerseite) nicht erneut autorisieren: dort ist kein SecurityContext gesetzt,
              // sonst 403 auf dem bereits committeten SSE-Stream (Reconnect-Sturm). Der REQUEST-
              // Dispatch bleibt voll autorisiert; DispatcherType setzt der Container, nicht der
              // Client — nicht fälschbar.
              auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll();
              // Die Pfadregeln kommen aus der geordneten Zugangs-Tabelle (Issue #1402), in
              // ihrer Reihenfolge — der erste Treffer gilt. Die Erläuterung je Gruppe steht
              // dort; dieselbe Tabelle nennt der API-Beschreibung den Zugang je Pfad.
              for (ApiZugang.Pfadgruppe gruppe : ApiZugang.GRUPPEN) {
                regelFuer(
                    gruppe.zugang(), auth.requestMatchers(gruppe.muster().toArray(String[]::new)));
              }
              auth.anyRequest().permitAll();
            })
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (request, response, ex) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(patFilter, UsernamePasswordAuthenticationFilter.class)
        // Ausprobieren aus der API-Übersicht (Issue #1438): vor den Auth-Filtern, denn bei
        // Kennzeichen plus Projekt-Token nimmt er das Session-Cookie aus der Anfrage, damit das
        // Token gilt.
        .addFilterBefore(ausprobierFilter, SessionAuthenticationFilter.class)
        // Läuft nach beiden Auth-Filtern: sperrt authentifizierte Anfragen gesperrter Konten
        // (Session wie PAT), indem der Kontext geleert wird.
        .addFilterAfter(disabledGuard, PatAuthenticationFilter.class)
        // Durchsatzbremse (Issue #1002): nach der Sperrprüfung, denn sie braucht die Person —
        // anders als die Auth-Bremse, die vor der Kette an der Herkunft zählt
        // (RateLimitFilterConfig).
        // Ein gesperrtes Konto kommt hier ohne Authentifizierung an und wird nicht gezählt.
        .addFilterAfter(throughputFilter, DisabledUserGuardFilter.class);
    return http.build();
  }

  /**
   * Übersetzt die Zugangsart einer Gruppe in die Autorisierungsregel von Spring Security. Als
   * Switch-Ausdruck, damit der Compiler eine neue Zugangsart ohne Regel ablehnt.
   */
  private static AuthorizeHttpRequestsConfigurer<HttpSecurity>
          .AuthorizationManagerRequestMatcherRegistry
      regelFuer(
          ApiZugang.Zugangsart zugang,
          AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl regel) {
    return switch (zugang) {
      case OEFFENTLICH -> regel.permitAll();
      case NUR_SESSION -> regel.hasAuthority(SessionAuthenticationFilter.AUTHORITY);
      case NUR_PROJEKT_TOKEN -> regel.hasAuthority(PatAuthenticationFilter.AUTHORITY);
      case SESSION_ODER_UNGEBUNDENES_TOKEN ->
          regel.hasAnyAuthority(
              SessionAuthenticationFilter.AUTHORITY, PatAuthenticationFilter.UNBOUND_AUTHORITY);
    };
  }

  /**
   * Hält den {@link ThroughputFilter} aus der Servlet-Filterkette heraus (Issue #1002).
   *
   * <p>Spring Boot registriert jede Filter-Bean zusätzlich direkt im Container. Dort liefe dieser
   * Filter ein zweites Mal, außerhalb der Security-Kette — und gerade die Reihenfolge nach der
   * Authentifizierung ist sein Zweck. Er gehört ausschließlich in die Kette oben.
   */
  @Bean
  FilterRegistrationBean<ThroughputFilter> throughputFilterOutsideTheServletChain(
      ThroughputFilter throughputFilter) {
    FilterRegistrationBean<ThroughputFilter> registration =
        new FilterRegistrationBean<>(throughputFilter);
    registration.setEnabled(false);
    return registration;
  }

  /**
   * Hält den {@link ApiAusprobierFilter} aus der Servlet-Filterkette heraus (Issue #1438), aus
   * demselben Grund wie beim {@link ThroughputFilter}: Er gehört ausschließlich vor die Auth-Filter
   * der Kette oben.
   */
  @Bean
  FilterRegistrationBean<ApiAusprobierFilter> ausprobierFilterOutsideTheServletChain(
      ApiAusprobierFilter ausprobierFilter) {
    FilterRegistrationBean<ApiAusprobierFilter> registration =
        new FilterRegistrationBean<>(ausprobierFilter);
    registration.setEnabled(false);
    return registration;
  }
}
