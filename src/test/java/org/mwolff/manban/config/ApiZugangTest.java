package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.manban.config.ApiZugang.Zugangsart;

/**
 * Die geordnete Zugangs-Tabelle (Issue #1402, Plan #1400): Aus ihr baut {@code SecurityConfig} die
 * Regeln, und die API-Beschreibung liest daraus den Zugang je Pfad. Zugeordnet wird wie in Spring
 * Security nach dem ersten Treffer.
 */
class ApiZugangTest {

  @ParameterizedTest
  @CsvSource({
    "/api/auth/register, OEFFENTLICH",
    "/api/auth/verify, OEFFENTLICH",
    "/api/auth/login, OEFFENTLICH",
    "/api/auth/logout, OEFFENTLICH",
    "/api/auth/forgot, OEFFENTLICH",
    "/api/auth/reset, OEFFENTLICH",
    "/api/access-tokens, NUR_SESSION",
    "/api/access-tokens/{id}, NUR_SESSION",
    "/api/admin/users, NUR_SESSION",
    "/api/admin/bootstrap, NUR_SESSION",
    "/api/openapi, NUR_SESSION",
    "/api/openapi.yaml, NUR_SESSION",
    "/api/kanban/items, NUR_PROJEKT_TOKEN",
    "/api/kanban/night-runs, NUR_PROJEKT_TOKEN",
    "/api/me, SESSION_ODER_UNGEBUNDENES_TOKEN",
    "/api/cards/{cardId}, SESSION_ODER_UNGEBUNDENES_TOKEN",
    "/api/auth/me, SESSION_ODER_UNGEBUNDENES_TOKEN",
    "/index.html, OEFFENTLICH",
    "/, OEFFENTLICH"
  })
  void ordnetJedenPfadSeinerGruppeZu(String pfad, Zugangsart erwartet) {
    assertThat(ApiZugang.zugangFuer(pfad)).isEqualTo(erwartet);
  }

  @Test
  void beiZweiPassendenMusternGewinntDasErste() {
    // /api/kanban/items passt auf /api/kanban/** und auf /api/**; Spring Security nimmt die
    // erste Regel, und die Beschreibung muss dasselbe sagen.
    String pfad = "/api/kanban/items";
    List<ApiZugang.Pfadgruppe> treffer =
        ApiZugang.GRUPPEN.stream()
            .filter(gruppe -> gruppe.muster().stream().anyMatch(m -> passt(m, pfad)))
            .toList();

    assertThat(treffer)
        .extracting(ApiZugang.Pfadgruppe::zugang)
        .containsExactly(Zugangsart.NUR_PROJEKT_TOKEN, Zugangsart.SESSION_ODER_UNGEBUNDENES_TOKEN);
    assertThat(ApiZugang.zugangFuer(pfad)).isEqualTo(Zugangsart.NUR_PROJEKT_TOKEN);
  }

  @Test
  void dieAuffangregelFuerDieUebrigeApiStehtAmEnde() {
    ApiZugang.Pfadgruppe letzte = ApiZugang.GRUPPEN.getLast();

    assertThat(letzte.muster()).containsExactly("/api/**");
    assertThat(letzte.zugang()).isEqualTo(Zugangsart.SESSION_ODER_UNGEBUNDENES_TOKEN);
  }

  @Test
  void jedeZugangsartTraegtEineKennung() {
    assertThat(Zugangsart.OEFFENTLICH.kennung()).isEqualTo("oeffentlich");
    assertThat(Zugangsart.NUR_SESSION.kennung()).isEqualTo("nur-session");
    assertThat(Zugangsart.NUR_PROJEKT_TOKEN.kennung()).isEqualTo("nur-projekt-token");
    assertThat(Zugangsart.SESSION_ODER_UNGEBUNDENES_TOKEN.kennung())
        .isEqualTo("session-oder-ungebundenes-token");
  }

  private static boolean passt(String muster, String pfad) {
    return new org.springframework.util.AntPathMatcher().match(muster, pfad);
  }
}
