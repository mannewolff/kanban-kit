package org.mwolff.manban.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Liest das Session-Cookie, verifiziert das signierte Token und setzt bei Gültigkeit
 * die Authentifizierung (Principal = userId). Ungültige/fehlende Tokens lassen den
 * Kontext leer — die Autorisierung entscheidet dann über 401/403.
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionCookieManager cookies;
    private final SignedSessionTokens tokens;

    public SessionAuthenticationFilter(SessionCookieManager cookies, SignedSessionTokens tokens) {
        this.cookies = cookies;
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            cookies.readToken(request).ifPresent(token -> {
                OptionalLong userId = tokens.verify(token);
                if (userId.isPresent()) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userId.getAsLong(), null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            });
        }
        filterChain.doFilter(request, response);
    }
}
