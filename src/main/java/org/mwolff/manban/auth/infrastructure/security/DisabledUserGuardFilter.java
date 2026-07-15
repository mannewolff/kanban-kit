package org.mwolff.manban.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Weist authentifizierte Anfragen gesperrter Konten ab — unabhängig davon, ob die Authentifizierung
 * per Session-Cookie oder per PAT gesetzt wurde. Läuft nach den beiden Auth-Filtern: ist der
 * aufgelöste Benutzer gesperrt, wird der Sicherheitskontext geleert (die Anfrage gilt als
 * unauthentifiziert → 401/403). So werden bestehende Sessions und Tokens gesperrter Nutzer sofort
 * ungültig, ohne dass das accesstoken-Modul auf das auth-Modul zugreifen müsste.
 */
@Component
public class DisabledUserGuardFilter extends OncePerRequestFilter {

  private final AppUserRepository users;

  public DisabledUserGuardFilter(AppUserRepository users) {
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
      boolean disabled = users.findById(userId).map(AppUser::disabled).orElse(false);
      if (disabled) {
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }
}
