package org.mwolff.manban.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
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
 *   <li>Zustandslos: kein Server-Session-Store; Authentifizierung über das signierte
 *       Session-Cookie ({@link SessionAuthenticationFilter}).</li>
 *   <li>Default-Deny für {@code /api/**} (außer den öffentlichen Auth-Endpunkten);
 *       statische Inhalte und die React-App unter {@code /} bleiben offen.</li>
 *   <li>Unauthentifizierte API-Zugriffe → 401 (kein Redirect auf eine Login-Seite).</li>
 *   <li>CSRF: Der Synchronizer-Token entfällt bewusst — es gibt keine Server-Session,
 *       und das Auth-Cookie ist {@code HttpOnly; SameSite=Strict}, wird also nie
 *       cross-site gesendet. Damit ist der zustandslose Cookie-Ansatz CSRF-resistent.</li>
 * </ul>
 * 2FA-Vorbereitung: Der zweite Faktor hängt im Login-Flow (SessionController /
 * LoginService), nicht hier — die Filterkette bleibt unverändert.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SessionAuthenticationFilter sessionFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/verify",
                                "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
