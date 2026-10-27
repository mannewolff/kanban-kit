package org.mwolff.manban.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminVorschauRepository;
import org.mwolff.manban.auth.application.AdminVorschauService;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.SessionGenerations;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.domain.VorschauEndgrund;
import org.mwolff.manban.auth.web.security.SessionAuthenticationFilter;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Endpunkte der „Ansicht als XY“ (Issue #1921, Plan #1914 E9, E11, E12). Der Dienst läuft echt
 * gegen gemockte Ports, damit die Zugangsfälle aus E11 bis zur Antwort durchschlagen; das Cookie
 * baut der echte {@link SessionCookieManager}.
 */
// PMD.ExcessiveImports: Der Test baut den echten Dienst über seine Ports, das echte Cookie und eine
// Anmeldung je Zugangsart auf, damit die Fälle aus E11 bis zur Antwort durchschlagen.
@SuppressWarnings("PMD.ExcessiveImports")
class AdminVorschauControllerTest {

  private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
  private static final Instant ABLAUF = NOW.plus(Duration.ofMinutes(30));
  private static final long ADMIN = 1L;
  private static final long ZIEL = 2L;
  private static final long ANDERER_ADMIN = 3L;
  private static final long GENERATION = 4L;
  private static final String KENNUNG = "kennung-a";
  private static final String GEHEIMNIS = "geheimnis-a";
  private static final String START = "/api/admin/users/{id}/vorschau";

  private AppUserRepository users;
  private AdminVorschauRepository vorschauen;
  private Optional<VorschauKontext> kontext;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    SessionGenerations generations = mock(SessionGenerations.class);
    vorschauen = mock(AdminVorschauRepository.class);
    var service = new AdminVorschauService(users, generations, vorschauen, new MutableClock(NOW));
    var cookies =
        new SessionCookieManager(
            new AuthProperties(null, "secret", Duration.ofHours(8), true, null));
    kontext = Optional.empty();
    var controller = new AdminVorschauController(service, cookies, () -> kontext);
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
            .build();
    when(users.findById(ADMIN))
        .thenReturn(
            Optional.of(
                new AppUser(ADMIN, "a@example.org", "Ada Admin", true, PlatformRole.ADMIN)));
    when(users.findById(ZIEL))
        .thenReturn(
            Optional.of(new AppUser(ZIEL, "x@example.org", "Xaver", true, PlatformRole.USER)));
    when(users.findById(ANDERER_ADMIN))
        .thenReturn(
            Optional.of(
                new AppUser(ANDERER_ADMIN, "b@example.org", "Bea", true, PlatformRole.ADMIN)));
    when(generations.current(anyLong())).thenReturn(OptionalLong.of(GENERATION));
    when(vorschauen.save(any(AdminVorschau.class))).thenAnswer(inv -> inv.getArgument(0));
    angemeldet(ADMIN, SessionAuthenticationFilter.AUTHORITY);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static void angemeldet(long userId, String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                userId, null, List.of(new SimpleGrantedAuthority(authority))));
  }

  private static AdminVorschau offen(Long adminId) {
    return new AdminVorschau(
        5L,
        KENNUNG,
        SecureTokens.sha256Hex(GEHEIMNIS),
        adminId,
        "Ada Admin",
        ZIEL,
        GENERATION,
        NOW,
        ABLAUF,
        null,
        null);
  }

  // ---------------------------------------------------------------- Starten (E11)

  @Test
  void erfolgreicherStartSetztDasVorschauCookieHttpOnlyUndSameSiteStrict() throws Exception {
    MvcResult result = mvc.perform(post(START, ZIEL)).andExpect(status().isNoContent()).andReturn();

    String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie)
        .startsWith(SessionCookieManager.VORSCHAU_COOKIE_NAME + "=")
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Path=/");
    assertThat(result.getResponse().getContentAsString()).isEmpty();
    verify(vorschauen).save(any(AdminVorschau.class));
  }

  @Test
  void dasCookieTraegtKennungUndGeheimnisDerGespeichertenVorschau() throws Exception {
    MvcResult result = mvc.perform(post(START, ZIEL)).andReturn();

    var captor = org.mockito.ArgumentCaptor.forClass(AdminVorschau.class);
    verify(vorschauen).save(captor.capture());
    String wert =
        result.getResponse().getCookie(SessionCookieManager.VORSCHAU_COOKIE_NAME).getValue();
    int trenner = wert.indexOf('.');
    assertThat(wert.substring(0, trenner)).isEqualTo(captor.getValue().kennung());
    assertThat(SecureTokens.sha256Hex(wert.substring(trenner + 1)))
        .isEqualTo(captor.getValue().geheimnisHash());
  }

  @Test
  void startDurchNichtAdminWirdMit403Abgewiesen() throws Exception {
    angemeldet(ZIEL, SessionAuthenticationFilter.AUTHORITY);

    mvc.perform(post(START, ANDERER_ADMIN + 10)).andExpect(status().isForbidden());

    verify(vorschauen, never()).save(any());
  }

  @Test
  void startAlsSichSelbstWirdAbgewiesen() throws Exception {
    mvc.perform(post(START, ADMIN)).andExpect(status().isBadRequest());

    verify(vorschauen, never()).save(any());
  }

  @Test
  void startAlsAnderenAdminWirdAbgewiesen() throws Exception {
    mvc.perform(post(START, ANDERER_ADMIN)).andExpect(status().isBadRequest());

    verify(vorschauen, never()).save(any());
  }

  @Test
  void startMitProjektTokenWirdAbgewiesen() throws Exception {
    angemeldet(ADMIN, "AUTH_PAT");

    MvcResult result = mvc.perform(post(START, ZIEL)).andExpect(status().isForbidden()).andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    verify(vorschauen, never()).save(any());
  }

  @Test
  void startOhneAnmeldungWirdAbgewiesen() throws Exception {
    SecurityContextHolder.clearContext();

    mvc.perform(post(START, ZIEL)).andExpect(status().isForbidden());

    verify(vorschauen, never()).save(any());
  }

  @Test
  void startMitAnmeldungOhneKontoIdWirdAbgewiesen() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "ada",
                null,
                List.of(new SimpleGrantedAuthority(SessionAuthenticationFilter.AUTHORITY))));

    mvc.perform(post(START, ZIEL)).andExpect(status().isForbidden());

    verify(vorschauen, never()).save(any());
  }

  @Test
  void startMitVorschauCookieWirdAbgewiesen() throws Exception {
    mvc.perform(
            post(START, ZIEL)
                .cookie(
                    new Cookie(
                        SessionCookieManager.VORSCHAU_COOKIE_NAME, KENNUNG + "." + GEHEIMNIS)))
        .andExpect(status().isConflict());

    verify(vorschauen, never()).save(any());
  }

  @Test
  void startInDerVorschauWirdAbgewiesen() throws Exception {
    kontext = Optional.of(new VorschauKontext(KENNUNG, ADMIN, ZIEL, ABLAUF));

    mvc.perform(post(START, ZIEL)).andExpect(status().isConflict());

    verify(vorschauen, never()).save(any());
  }

  // ---------------------------------------------------------------- Beenden (E9)

  @Test
  void beendenSchreibtBeendetUndLoeschtDasCookie() throws Exception {
    kontext = Optional.of(new VorschauKontext(KENNUNG, ADMIN, ZIEL, ABLAUF));
    when(vorschauen.findByKennung(KENNUNG)).thenReturn(Optional.of(offen(ADMIN)));

    MvcResult result =
        mvc.perform(
                delete("/api/vorschau")
                    .cookie(
                        new Cookie(
                            SessionCookieManager.VORSCHAU_COOKIE_NAME, KENNUNG + "." + GEHEIMNIS)))
            .andExpect(status().isNoContent())
            .andReturn();

    verify(vorschauen).beende(5L, NOW, VorschauEndgrund.BEENDET);
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .startsWith(SessionCookieManager.VORSCHAU_COOKIE_NAME + "=;")
        .contains("Max-Age=0");
  }

  @Test
  void beendenOhneLaufendeVorschauLoeschtNurDasCookie() throws Exception {
    MvcResult result =
        mvc.perform(
                delete("/api/vorschau")
                    .cookie(
                        new Cookie(
                            SessionCookieManager.VORSCHAU_COOKIE_NAME, KENNUNG + "." + GEHEIMNIS)))
            .andExpect(status().isNoContent())
            .andReturn();

    verify(vorschauen, never()).beende(anyLong(), any(), any());
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
  }

  @Test
  void beendenOhneCookieBeendetNichts() throws Exception {
    kontext = Optional.of(new VorschauKontext(KENNUNG, ADMIN, ZIEL, ABLAUF));

    mvc.perform(delete("/api/vorschau")).andExpect(status().isNoContent());

    verify(vorschauen, never()).findByKennung(any());
    verify(vorschauen, never()).beende(anyLong(), any(), any());
  }

  // ---------------------------------------------------------------- Protokoll (E12)

  @Test
  void adminLiestDieEintraegeDesZielkontos() throws Exception {
    Instant beendet = NOW.plusSeconds(60);
    when(vorschauen.findByZiel(ZIEL))
        .thenReturn(
            List.of(offen(ADMIN), offen(null).beendet(beendet, VorschauEndgrund.ABMELDUNG)));

    mvc.perform(get("/api/admin/users/{id}/vorschau-protokoll", ZIEL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].adminName").value("Ada Admin"))
        .andExpect(jsonPath("$[0].adminKontoGeloescht").value(false))
        .andExpect(jsonPath("$[0].gestartetAm").value(NOW.toString()))
        .andExpect(jsonPath("$[0].laeuftAbAm").value(ABLAUF.toString()))
        .andExpect(jsonPath("$[0].beendetAm").doesNotExist())
        .andExpect(jsonPath("$[0].endgrund").doesNotExist())
        .andExpect(jsonPath("$[0].kennung").doesNotExist())
        .andExpect(jsonPath("$[0].geheimnisHash").doesNotExist())
        .andExpect(jsonPath("$[1].adminKontoGeloescht").value(true))
        .andExpect(jsonPath("$[1].adminName").value("Ada Admin"))
        .andExpect(jsonPath("$[1].beendetAm").value(beendet.toString()))
        .andExpect(jsonPath("$[1].endgrund").value("ABMELDUNG"));
    verify(vorschauen).beendeAbgelaufene(ZIEL, NOW);
  }

  @Test
  void nichtAdminLiestKeineEintraege() throws Exception {
    angemeldet(ZIEL, SessionAuthenticationFilter.AUTHORITY);

    mvc.perform(get("/api/admin/users/{id}/vorschau-protokoll", ZIEL))
        .andExpect(status().isForbidden());

    verify(vorschauen, never()).findByZiel(anyLong());
  }

  @Test
  void xyLiestDieEigenenEintraege() throws Exception {
    angemeldet(ZIEL, SessionAuthenticationFilter.AUTHORITY);
    when(vorschauen.findByZiel(ZIEL)).thenReturn(List.of(offen(ADMIN)));

    mvc.perform(get("/api/me/vorschau-protokoll"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].adminName").value("Ada Admin"));
    verify(vorschauen).beendeAbgelaufene(ZIEL, NOW);
  }
}
