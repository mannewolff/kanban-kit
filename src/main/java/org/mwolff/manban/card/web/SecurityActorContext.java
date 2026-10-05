package org.mwolff.manban.card.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.card.application.ActorContext;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.common.Laufkennung;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web-Adapter des {@link ActorContext}-Ports (Issue #517): liest Herkunft und Token-Name aus dem
 * Spring-Sicherheitskontext und die Modell-Selbstauskunft aus dem Request-Header; bei
 * Token-Herkunft dazu die Laufkennung aus {@link Laufkennung#HEADER} (Issue #1426).
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

  // AUTH_PAT trägt seit Issue #836 bewusst weiterhin JEDES Token — gebunden wie ungebunden; die
  // Board-Grenze hängt allein an der zusätzlichen Authority AUTH_PAT_UNBOUND. Andernfalls verlören
  // alle Karten board-gebundener Token hier den Herkunfts-Stempel TOKEN.
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
      return new ActorStamp(CardActivityOrigin.TOKEN, tokenName, agentHeader(), laufHeader());
    }
    if (hasAuthority(auth, SESSION_AUTHORITY)) {
      // Keine Laufkennung bei Session-Herkunft (Plan #1423, A3): Menschen gehören zu keinem Lauf.
      return new ActorStamp(CardActivityOrigin.SESSION, null, agentHeader(), null);
    }
    return ActorStamp.unknown();
  }

  private static boolean hasAuthority(Authentication auth, String authority) {
    return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
  }

  /** Selbstauskunft aus dem Header — getrimmt, gekappt; leer oder ohne Request-Kontext: null. */
  private static @Nullable String agentHeader() {
    String value = header(AGENT_HEADER);
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= AGENT_MAX_LENGTH ? trimmed : trimmed.substring(0, AGENT_MAX_LENGTH);
  }

  /** Laufkennung aus dem Header; fehlt sie, ist sie ungültig oder fehlt der Request: null. */
  private static @Nullable Instant laufHeader() {
    return Laufkennung.ausHeader(header(Laufkennung.HEADER));
  }

  private static @Nullable String header(String name) {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return null;
    }
    HttpServletRequest request = attributes.getRequest();
    return request.getHeader(name);
  }
}
