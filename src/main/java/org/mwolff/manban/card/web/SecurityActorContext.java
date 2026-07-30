package org.mwolff.manban.card.web;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.card.application.ActorContext;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web-Adapter des {@link ActorContext}-Ports (Issue #517): liest Herkunft und Token-Name aus dem
 * Spring-Sicherheitskontext und die Modell-Selbstauskunft aus dem Request-Header.
 *
 * <p>Die Authority-Strings entsprechen {@code SessionAuthenticationFilter.AUTHORITY} bzw. {@code
 * PatAuthenticationFilter.AUTHORITY}; als Literale gehalten, um keine Kanten auf fremde
 * web.security-Pakete zu ziehen — die Kopplung ist durch die Integrationstests abgesichert, die
 * über die echten Filter laufen. Die Kante auf {@code accesstoken.application.KanbanPrincipal}
 * (Details-Cast) ist bewusst: derselbe Vertrag, den auch {@code kanbancompat} nutzt; eine
 * accesstoken-Fassaden-Whitelist existiert nicht (nur {@code auth} ist gesperrt, #438).
 */
@Component
class SecurityActorContext implements ActorContext {

  /** Selbstauskunfts-Header des Clients; Wert wird getrimmt und auf 100 Zeichen gekappt. */
  static final String AGENT_HEADER = "X-Agent-Model";

  private static final int AGENT_MAX_LENGTH = 100;
  private static final String SESSION_AUTHORITY = "AUTH_SESSION";
  private static final String PAT_AUTHORITY = "AUTH_PAT";

  @Override
  public ActorStamp current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return ActorStamp.unknown();
    }
    if (hasAuthority(auth, PAT_AUTHORITY)) {
      String tokenName =
          auth.getDetails() instanceof KanbanPrincipal principal ? principal.tokenName() : null;
      return new ActorStamp(CardActivityOrigin.TOKEN, tokenName, agentHeader());
    }
    if (hasAuthority(auth, SESSION_AUTHORITY)) {
      return new ActorStamp(CardActivityOrigin.SESSION, null, agentHeader());
    }
    return ActorStamp.unknown();
  }

  private static boolean hasAuthority(Authentication auth, String authority) {
    return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }

  /** Selbstauskunft aus dem Header — getrimmt, gekappt; leer oder ohne Request-Kontext: null. */
  private static @Nullable String agentHeader() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return null;
    }
    HttpServletRequest request = attributes.getRequest();
    String value = request.getHeader(AGENT_HEADER);
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= AGENT_MAX_LENGTH ? trimmed : trimmed.substring(0, AGENT_MAX_LENGTH);
  }
}
