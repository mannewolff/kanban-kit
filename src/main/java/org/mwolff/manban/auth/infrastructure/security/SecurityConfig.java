package org.mwolff.manban.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.mwolff.manban.accesstoken.infrastructure.security.PatAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Zentrale Web-Security.
 *
 * <ul>
 *   <li>Zustandslos: kein Server-Session-Store; Authentifizierung über das signierte Session-Cookie
 *       ({@link SessionAuthenticationFilter}).
 *   <li>Default-Deny für {@code /api/**} (außer den öffentlichen Auth-Endpunkten); statische
 *       Inhalte und die React-App unter {@code /} bleiben offen.
 *   <li>Unauthentifizierte API-Zugriffe → 401 (kein Redirect auf eine Login-Seite).
 *   <li>CSRF: Der Synchronizer-Token entfällt bewusst — es gibt keine Server-Session, und das
 *       Auth-Cookie ist {@code HttpOnly; SameSite=Strict}, wird also nie cross-site gesendet. Damit
 *       ist der zustandslose Cookie-Ansatz CSRF-resistent.
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
      DisabledUserGuardFilter disabledGuard)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/register",
                        "/api/auth/verify",
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/auth/forgot",
                        "/api/auth/reset")
                    .permitAll()
                    // Token-Verwaltung nur per Cookie-Login, nicht per PAT (Least Privilege).
                    .requestMatchers("/api/access-tokens/**")
                    .hasAuthority(SessionAuthenticationFilter.AUTHORITY)
                    // Admin-Bereich (inkl. Bootstrap) nur per Session-Login, nicht per PAT.
                    // Die Admin-Autorisierung selbst erledigt der AdminService pro Endpunkt.
                    .requestMatchers("/api/admin/**")
                    .hasAuthority(SessionAuthenticationFilter.AUTHORITY)
                    // Kanban-Compat-API (tbx.mjs/board.mjs) ausschließlich per PAT.
                    .requestMatchers("/api/kanban/**")
                    .hasAuthority(PatAuthenticationFilter.AUTHORITY)
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (request, response, ex) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(patFilter, UsernamePasswordAuthenticationFilter.class)
        // Läuft nach beiden Auth-Filtern: sperrt authentifizierte Anfragen gesperrter Konten
        // (Session wie PAT), indem der Kontext geleert wird.
        .addFilterAfter(disabledGuard, PatAuthenticationFilter.class);
    return http.build();
  }
}
