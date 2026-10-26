package org.mwolff.manban.auth.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Der Leser liefert den {@link VorschauKontext} aus den Details der Authentifizierung und ist
 * außerhalb der Vorschau leer (Issue #1918, Plan #1914 E6).
 */
class SecurityContextVorschauKontextLeserTest {

  private static final VorschauKontext KONTEXT =
      new VorschauKontext("kennung-1", 1L, 2L, Instant.parse("2026-10-26T22:30:00Z"));

  private final SecurityContextVorschauKontextLeser leser =
      new SecurityContextVorschauKontextLeser();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static void angemeldet(Object details) {
    var authentication =
        new UsernamePasswordAuthenticationToken(
            2L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    authentication.setDetails(details);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Test
  void liefertDenKontextAusDenDetails() {
    angemeldet(KONTEXT);

    assertThat(leser.aktuell()).contains(KONTEXT);
  }

  @Test
  void istOhneAuthentifizierungLeer() {
    assertThat(leser.aktuell()).isEmpty();
  }

  @Test
  void istBeiAnderenDetailsLeer() {
    angemeldet("kein Kontext");

    assertThat(leser.aktuell()).isEmpty();
  }
}
