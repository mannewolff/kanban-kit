package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Prüft die Testhilfe {@link TestAnmeldung} selbst (Plan #1801, A15; Issue #1813). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TestAnmeldungIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private TestAnmeldung anmeldung;

  @Test
  void cookieMeldetDasNeuAngelegteKontoAn() throws Exception {
    Cookie cookie = anmeldung.anmelden("hilfe-admin@example.com", PlatformRole.ADMIN);

    assertThat(cookie.getName()).isEqualTo("manban_session");
    mvc.perform(get("/api/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("hilfe-admin@example.com"))
        .andExpect(jsonPath("$.platformRole").value("ADMIN"));
    AppUser konto = users.findByEmail("hilfe-admin@example.com").orElseThrow();
    assertThat(konto.emailVerified()).isTrue();
    assertThat(konto.effectivelyApproved()).isTrue();
    assertThat(konto.disabled()).isFalse();
  }

  @Test
  void bestehendesKontoWirdWiederverwendet() throws Exception {
    long id = anmeldung.konto("hilfe-zweimal@example.com", PlatformRole.USER).requireId();

    Cookie cookie = anmeldung.anmelden("hilfe-zweimal@example.com", PlatformRole.USER);

    assertThat(users.findAll()).hasSize(1);
    mvc.perform(get("/api/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(id));
  }

  @Test
  void codeAblaufMeldetUeberDenEchtenEndpunktAn() throws Exception {
    long id = anmeldung.konto("hilfe-code@example.com", PlatformRole.USER).requireId();

    Cookie cookie = anmeldung.perCode(mvc, "hilfe-code@example.com");

    mvc.perform(get("/api/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(id));
  }

  @Test
  void codeAblaufLiefertDieAblehnungEinesWartendenKontos() throws Exception {
    anmeldung.konto("hilfe-admin@example.com", PlatformRole.ADMIN);
    users.save(
        new AppUser(
            null,
            "hilfe-wartend@example.com",
            "unbenutzt",
            "Wartend",
            true,
            PlatformRole.USER,
            null,
            null));

    MockHttpServletResponse antwort = anmeldung.codeEinloesen(mvc, "hilfe-wartend@example.com");

    assertThat(antwort.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(antwort.getCookie("manban_session")).isNull();
  }
}
