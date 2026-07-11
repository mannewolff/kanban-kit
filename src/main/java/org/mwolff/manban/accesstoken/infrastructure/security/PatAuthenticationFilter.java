package org.mwolff.manban.accesstoken.infrastructure.security;

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
 */
@Component
public class PatAuthenticationFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Kanban-Token";
  public static final String AUTHORITY = "AUTH_PAT";

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
            new UsernamePasswordAuthenticationToken(
                p.userId(), null, List.of(new SimpleGrantedAuthority(AUTHORITY)));
        // Die Projekt-/Board-Bindung wandert in die details, damit die Kanban-Compat-API
        // (#45) das gebundene Board ohne zweiten Token-Lookup kennt.
        authentication.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }
    filterChain.doFilter(request, response);
  }
}
