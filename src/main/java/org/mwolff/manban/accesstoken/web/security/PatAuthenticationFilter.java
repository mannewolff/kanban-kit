package org.mwolff.manban.accesstoken.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentifiziert Requests mit dem Header {@code X-Kanban-Token} (PAT), additiv neben der
 * Cookie-Auth. Setzt die Authority {@code AUTH_PAT} — damit lassen sich rein per Cookie erreichbare
 * Endpunkte (Token-Verwaltung) von PAT-Zugriffen abgrenzen.
 *
 * <p>Die Authorities sind zweigeteilt (Issue #836): {@code AUTH_PAT} trägt <em>jedes</em> Token,
 * gebunden wie ungebunden; {@code AUTH_PAT_UNBOUND} kommt nur bei einem ungebundenen Token hinzu.
 * Erst diese zweite Authority öffnet in der {@code SecurityConfig} die übrige {@code /api/**}
 * -Oberfläche — ein board-gebundenes Token bleibt damit auf {@code /api/kanban/**} seines Boards
 * beschränkt, unabhängig von den Rollen seines Erstellers.
 *
 * <p>Bewusst additiv statt einer eigenen Authority für gebundene Token: {@code AUTH_PAT} bleibt so
 * das verlässliche Merkmal „stammt von einem Token", an dem der Herkunfts-Stempel {@code TOKEN} des
 * Aktivitätsverlaufs hängt (Issue #517).
 */
@Component
public class PatAuthenticationFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Kanban-Token";
  public static final String AUTHORITY = "AUTH_PAT";

  /** Zusatz-Authority ausschließlich ungebundener Token; siehe Klassen-Javadoc. */
  public static final String UNBOUND_AUTHORITY = "AUTH_PAT_UNBOUND";

  private final AccessTokenService accessTokens;

  public PatAuthenticationFilter(AccessTokenService accessTokens) {
    this.accessTokens = accessTokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HEADER);
    if (header != null
        && !header.isBlank()
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      Optional<KanbanPrincipal> principal = accessTokens.resolveBinding(header);
      if (principal.isPresent()) {
        KanbanPrincipal p = principal.get();
        var authentication =
            new UsernamePasswordAuthenticationToken(p.userId(), null, authorities(p));
        // Die Projekt-/Board-Bindung wandert in die details, damit die Kanban-Compat-API
        // (#45) das gebundene Board ohne zweiten Token-Lookup kennt.
        authentication.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }
    filterChain.doFilter(request, response);
  }

  /** {@code AUTH_PAT} für jedes Token, {@code AUTH_PAT_UNBOUND} nur ohne Board-Bindung (#836). */
  private static List<SimpleGrantedAuthority> authorities(KanbanPrincipal principal) {
    if (principal.isBound()) {
      return List.of(new SimpleGrantedAuthority(AUTHORITY));
    }
    return List.of(
        new SimpleGrantedAuthority(AUTHORITY), new SimpleGrantedAuthority(UNBOUND_AUTHORITY));
  }
}
