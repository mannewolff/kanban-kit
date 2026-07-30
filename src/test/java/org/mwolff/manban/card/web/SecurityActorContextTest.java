package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Verhaltenstests des {@link SecurityActorContext}: Herkunft aus dem Sicherheitskontext
 * (verifiziert), Modell-Angabe aus dem Header (Selbstauskunft, gekappt).
 */
class SecurityActorContextTest {

  private final SecurityActorContext context = new SecurityActorContext();
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @BeforeEach
  void setUp() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  private static void authenticate(String authority, Object details) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            7L, null, List.of(new SimpleGrantedAuthority(authority)));
    authentication.setDetails(details);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Test
  void current_returnsUnknown_withoutAuthentication() {
    // When / Then
    assertThat(context.current()).isEqualTo(ActorStamp.unknown());
  }

  @Test
  void current_returnsSession_forSessionAuthority() {
    // Given
    authenticate("AUTH_SESSION", null);

    // When / Then
    assertThat(context.current()).isEqualTo(new ActorStamp(CardActivityOrigin.SESSION, null, null));
  }

  @Test
  void current_returnsTokenWithName_forPatAuthority() {
    // Given
    authenticate("AUTH_PAT", new KanbanPrincipal(7L, 3L, 1L, 2L, "Nachtlauf"));

    // When / Then
    assertThat(context.current())
        .isEqualTo(new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", null));
  }

  @Test
  void current_returnsTokenWithoutName_whenDetailsAreNoPrincipal() {
    // Given: defensiv — AUTH_PAT, aber die Details sind kein KanbanPrincipal.
    authenticate("AUTH_PAT", "etwas anderes");

    // When / Then
    assertThat(context.current()).isEqualTo(new ActorStamp(CardActivityOrigin.TOKEN, null, null));
  }

  @Test
  void current_returnsUnknown_forForeignAuthority() {
    // Given: weder Session noch PAT — Herkunft nicht bestimmbar, keine Agent-Übernahme.
    authenticate("ROLE_OTHER", null);

    // When / Then
    assertThat(context.current()).isEqualTo(ActorStamp.unknown());
  }

  @Test
  void current_readsAgentHeader() {
    // Given
    authenticate("AUTH_SESSION", null);
    request.addHeader("X-Agent-Model", "  claude-opus-5  ");

    // When / Then: getrimmt übernommen.
    assertThat(context.current().agent()).isEqualTo("claude-opus-5");
  }

  @Test
  void current_capsAgentHeaderAt100Chars() {
    // Given
    authenticate("AUTH_SESSION", null);
    request.addHeader("X-Agent-Model", "x".repeat(150));

    // When / Then
    assertThat(context.current().agent()).hasSize(100);
  }

  @Test
  void current_ignoresBlankAgentHeader() {
    // Given
    authenticate("AUTH_SESSION", null);
    request.addHeader("X-Agent-Model", "   ");

    // When / Then
    assertThat(context.current().agent()).isNull();
  }

  @Test
  void current_leavesAgentEmpty_withoutRequestContext() {
    // Given: Sicherheitskontext ohne gebundenen Request (z. B. asynchroner Aufruf).
    authenticate("AUTH_SESSION", null);
    RequestContextHolder.resetRequestAttributes();

    // When / Then
    assertThat(context.current()).isEqualTo(new ActorStamp(CardActivityOrigin.SESSION, null, null));
  }
}
