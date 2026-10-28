package org.mwolff.manban.auth.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminVorschauService;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.auth.application.VorschauPruefung;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.VorschauEndgrund;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit-Tests des Vorschau-Filters (Dienst gemockt, Issue #1919, Plan #1914 E1, E3, E8, E9). */
// PMD.TooManyMethods: Je Fall aus Issue #1919 ein Test, damit ein Fehlschlag seinen Fall nennt.
@SuppressWarnings("PMD.TooManyMethods")
class VorschauFilterTest {

  private static final long ADMIN = 1L;
  private static final long ZIEL = 42L;
  private static final String KENNUNG = "kennung-1";
  private static final String GEHEIMNIS = "geheimnis-1";
  private static final Instant START = Instant.parse("2026-10-26T10:00:00Z");
  private static final Instant ABLAUF = START.plus(Duration.ofMinutes(30));
  private static final String GELOESCHT = SessionCookieManager.VORSCHAU_COOKIE_NAME + "=;";

  private AdminVorschauService vorschauen;
  private Sprache eingestellt;
  private FilterChain chain;
  private VorschauFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    vorschauen = mock(AdminVorschauService.class);
    chain = mock(FilterChain.class);
    eingestellt = Sprache.DE;
    var cookies = new SessionCookieManager(new AuthProperties(null, "secret", null, true, null));
    filter = new VorschauFilter(cookies, vorschauen, SprachFixture.mit(() -> eingestellt));
    request = new MockHttpServletRequest("GET", "/api/projects");
    response = new MockHttpServletResponse();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static AdminVorschau laufende() {
    return new AdminVorschau(
        7L, KENNUNG, "hash", ADMIN, "Admin", ZIEL, 3L, START, ABLAUF, null, null);
  }

  private void angemeldet(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority(SessionAuthenticationFilter.AUTHORITY))));
  }

  private void mitVorschauCookie() {
    request.setCookies(
        new Cookie(SessionCookieManager.COOKIE_NAME, "sitzung"),
        new Cookie(SessionCookieManager.VORSCHAU_COOKIE_NAME, KENNUNG + "." + GEHEIMNIS));
  }

  private void pruefungErgibt(VorschauPruefung ergebnis) {
    when(vorschauen.pruefe(KENNUNG, GEHEIMNIS, ADMIN)).thenReturn(ergebnis);
  }

  /** Admin angemeldet, Cookie dabei, die Vorschau gilt. */
  private void inGueltigerVorschau() {
    angemeldet(ADMIN);
    mitVorschauCookie();
    pruefungErgibt(new VorschauPruefung.Gueltig(laufende()));
  }

  private static Authentication authentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }

  private String setCookie() {
    return String.join("\n", response.getHeaders(HttpHeaders.SET_COOKIE));
  }

  private void assertWeitergereicht() throws Exception {
    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  private void assertVorschauGewechselt() throws Exception {
    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(409);
  }

  // --- ohne Vorschau-Cookie --------------------------------------------------------------------

  @Test
  void ohneCookie_ohneKopfzeile_gehtUnveraendertWeiter() throws Exception {
    // Given
    angemeldet(ADMIN);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ADMIN);
    assertThat(authentication().getDetails()).isNull();
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    verifyNoInteractions(vorschauen);
  }

  @Test
  void ohneCookie_kopfzeileKeine_gehtWeiter() throws Exception {
    // Given
    angemeldet(ADMIN);
    request.addHeader(VorschauFilter.HEADER, VorschauFilter.KEINE);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
  }

  @Test
  void ohneCookie_kopfzeileMitKennung_wirdAbgewiesen() throws Exception {
    // Given: ein alter Vorschau-Tab, die Vorschau ist am Server schon weg
    angemeldet(ADMIN);
    request.setMethod("POST");
    request.addHeader(VorschauFilter.HEADER, KENNUNG);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
    assertThat(authentication().getPrincipal()).isEqualTo(ADMIN);
  }

  @Test
  void ohneCookie_ohneAnmeldung_gehtWeiter() throws Exception {
    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication()).isNull();
  }

  // --- Cookie ohne passende Sitzung (E3) -------------------------------------------------------

  @Test
  void cookieOhneSitzung_wirdGeloeschtUndNichtGeprueft() throws Exception {
    // Given
    mitVorschauCookie();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication()).isNull();
    assertThat(setCookie()).contains(GELOESCHT).contains("Max-Age=0");
    verifyNoInteractions(vorschauen);
  }

  @Test
  void cookieMitProjektToken_wirdNieZurVorschau() throws Exception {
    // Given: Authentifizierung per Projekt-Token, nicht per Sitzung (AK 6)
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                ADMIN, null, List.of(new SimpleGrantedAuthority("AUTH_PAT"))));
    mitVorschauCookie();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ADMIN);
    assertThat(authentication().getDetails()).isNull();
    assertThat(setCookie()).contains(GELOESCHT);
    verifyNoInteractions(vorschauen);
  }

  @Test
  void cookieMitSitzungEinesAnderenKontos_giltNichtUndWirdGeloescht() throws Exception {
    // Given: das Cookie gehört nicht zur Sitzung von Konto 5
    angemeldet(5L);
    mitVorschauCookie();
    when(vorschauen.pruefe(KENNUNG, GEHEIMNIS, 5L)).thenReturn(new VorschauPruefung.Unbekannt());

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(5L);
    assertThat(authentication().getDetails()).isNull();
    assertThat(setCookie()).contains(GELOESCHT);
  }

  @Test
  void fremdesCookie_mitKopfzeileDerKennung_wirdAbgewiesen() throws Exception {
    // Given
    angemeldet(ADMIN);
    mitVorschauCookie();
    pruefungErgibt(new VorschauPruefung.Unbekannt());
    request.addHeader(VorschauFilter.HEADER, KENNUNG);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
    assertThat(setCookie()).contains(GELOESCHT);
  }

  @Test
  void verformtesCookie_giltAlsKeines() throws Exception {
    // Given
    angemeldet(ADMIN);
    request.setCookies(new Cookie(SessionCookieManager.VORSCHAU_COOKIE_NAME, "ohnePunkt"));

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ADMIN);
    verifyNoInteractions(vorschauen);
  }

  // --- Endfälle (E9) ---------------------------------------------------------------------------

  @Test
  void hoehereSitzungsGeneration_beendetUndWeistAb() throws Exception {
    // Given
    angemeldet(ADMIN);
    mitVorschauCookie();
    pruefungErgibt(new VorschauPruefung.Beendet(VorschauEndgrund.SITZUNG_UNGUELTIG));
    request.addHeader(VorschauFilter.HEADER, KENNUNG);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
    assertThat(setCookie()).contains(GELOESCHT);
    verify(vorschauen).pruefe(KENNUNG, GEHEIMNIS, ADMIN);
  }

  @Test
  void endfall_weistAuchOhneKopfzeileAb() throws Exception {
    // Given: auch ein Tab, der sich für den Admin hält, sieht nach dem Ende nichts mehr
    angemeldet(ADMIN);
    mitVorschauCookie();
    pruefungErgibt(new VorschauPruefung.Beendet(VorschauEndgrund.ABLAUF));

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
    assertThat(setCookie()).contains(GELOESCHT);
  }

  @Test
  void endfall_weistAuchAufAusgenommenemPfadAb() throws Exception {
    // Given
    angemeldet(ADMIN);
    mitVorschauCookie();
    pruefungErgibt(new VorschauPruefung.Beendet(VorschauEndgrund.ZIEL_FEHLT));
    request.setRequestURI("/api/boards/3/events");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
  }

  // --- gültige Vorschau (E1, E8) ---------------------------------------------------------------

  @Test
  void gueltig_principalWirdZielUndDetailsTragenDenKontext() throws Exception {
    // Given
    inGueltigerVorschau();
    request.addHeader(VorschauFilter.HEADER, KENNUNG);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ZIEL);
    assertThat(authentication().getDetails())
        .isEqualTo(new VorschauKontext(KENNUNG, ADMIN, ZIEL, ABLAUF));
    assertThat(authentication().getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly(SessionAuthenticationFilter.AUTHORITY);
    assertThat(authentication().isAuthenticated()).isTrue();
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  void gueltig_ohneKopfzeile_wirdWieKeineAbgewiesen() throws Exception {
    // Given
    inGueltigerVorschau();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
  }

  @Test
  void gueltig_kopfzeileKeine_wirdAbgewiesen() throws Exception {
    // Given: ein alter Admin-Tab
    inGueltigerVorschau();
    request.addHeader(VorschauFilter.HEADER, VorschauFilter.KEINE);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  void gueltig_kopfzeileEinerAnderenKennung_wirdAbgewiesen() throws Exception {
    // Given
    inGueltigerVorschau();
    request.addHeader(VorschauFilter.HEADER, "andere-kennung");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
  }

  @Test
  void gueltig_boardEreignisseOhneKopfzeile_laufenAlsZiel() throws Exception {
    // Given
    inGueltigerVorschau();
    request.setRequestURI("/api/boards/3/events");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ZIEL);
  }

  @Test
  void gueltig_anhangLinkOhneKopfzeile_laeuftAlsZiel() throws Exception {
    // Given
    inGueltigerVorschau();
    request.setRequestURI("/api/attachments/9");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ZIEL);
  }

  @Test
  void gueltig_selbstauskunftNachDemNeuladenMitKopfzeileKeine_laeuftAlsZiel() throws Exception {
    // Given: der frisch geladene Tab kennt die Kennung noch nicht (Issue #1940)
    inGueltigerVorschau();
    request.setRequestURI("/api/me");
    request.addHeader(VorschauFilter.HEADER, VorschauFilter.KEINE);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ZIEL);
    assertThat(authentication().getDetails())
        .isEqualTo(new VorschauKontext(KENNUNG, ADMIN, ZIEL, ABLAUF));
  }

  @Test
  void gueltig_selbstauskunftOhneKopfzeile_laeuftAlsZiel() throws Exception {
    // Given
    inGueltigerVorschau();
    request.setRequestURI("/api/me");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ZIEL);
  }

  @Test
  void endfall_weistAuchDieSelbstauskunftAbUndLoeschtDasCookie() throws Exception {
    // Given
    angemeldet(ADMIN);
    mitVorschauCookie();
    pruefungErgibt(new VorschauPruefung.Beendet(VorschauEndgrund.ABLAUF));
    request.setRequestURI("/api/me");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
    assertThat(setCookie()).contains(GELOESCHT);
  }

  @Test
  void gueltig_spracheNachDemNeuladenMitKopfzeileKeine_wirdWeitergereicht() throws Exception {
    // Given: GET /api/language ist die erste Anfrage des frisch geladenen Tabs, noch vor der
    // Selbstauskunft (Issue #1940)
    inGueltigerVorschau();
    request.setRequestURI("/api/language");
    request.addHeader(VorschauFilter.HEADER, VorschauFilter.KEINE);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
  }

  @Test
  void endfall_weistAuchDieSpracheAbUndLoeschtDasCookie() throws Exception {
    // Given
    angemeldet(ADMIN);
    mitVorschauCookie();
    pruefungErgibt(new VorschauPruefung.Beendet(VorschauEndgrund.ABLAUF));
    request.setRequestURI("/api/language");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
    assertThat(setCookie()).contains(GELOESCHT);
  }

  @Test
  void ausnahmen_geltenNurFuerGetAufGenauDiesenPfaden() throws Exception {
    String[][] faelle = {
      {"PATCH", "/api/me"},
      {"PUT", "/api/me"},
      {"GET", "/api/me/vorschau-protokoll"},
      {"GET", "/api/me/"},
      {"PUT", "/api/language"},
      {"GET", "/api/language/"},
      {"PUT", "/api/admin/language"},
      {"DELETE", "/api/attachments/9"},
      {"POST", "/api/boards/3/events"},
      {"GET", "/api/attachments/9/mehr"},
      {"GET", "/api/attachments"},
      {"GET", "/api/boards/3/events/mehr"},
      {"GET", "/api/boards/events"},
      {"GET", "/api/cards/3/attachments"},
    };
    for (String[] fall : faelle) {
      // Given
      SecurityContextHolder.clearContext();
      setUp();
      inGueltigerVorschau();
      request.setMethod(fall[0]);
      request.setRequestURI(fall[1]);

      // When
      filter.doFilter(request, response, chain);

      // Then
      assertThat(response.getStatus()).as(fall[0] + " " + fall[1]).isEqualTo(409);
    }
  }

  @Test
  void ausnahme_beachtetDenKontextpfad() throws Exception {
    // Given
    inGueltigerVorschau();
    request.setContextPath("/app");
    request.setRequestURI("/app/api/attachments/9");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
  }

  @Test
  void ausnahme_kodiertGeschrieben_bleibtAusgenommen() throws Exception {
    // Issue #1974: Das Routing sieht den dekodierten Pfad.
    String[] pfade = {"/api/%6De", "/api/%61ttachments/9", "/api/boards/3/%65vents", "/api//me"};
    for (String pfad : pfade) {
      // Given
      SecurityContextHolder.clearContext();
      setUp();
      inGueltigerVorschau();
      request.setRequestURI(pfad);

      // When
      filter.doFilter(request, response, chain);

      // Then
      assertWeitergereicht();
      assertThat(authentication().getPrincipal()).as(pfad).isEqualTo(ZIEL);
    }
  }

  @Test
  void keineAusnahme_kodiertGeschrieben_bleibtImAbgleich() throws Exception {
    // Issue #1974: Ein kodierter Pfad darf den Abgleich nicht überspringen.
    String[] pfade = {
      "/api/me/%76orschau-protokoll",
      "/api/%61ttachments/9/mehr",
      "/api/me%2F",
      "/api/me;x=1/vorschau-protokoll"
    };
    for (String pfad : pfade) {
      // Given
      SecurityContextHolder.clearContext();
      setUp();
      inGueltigerVorschau();
      request.setRequestURI(pfad);

      // When
      filter.doFilter(request, response, chain);

      // Then
      assertThat(response.getStatus()).as(pfad).isEqualTo(409);
    }
  }

  @Test
  void apiPfadKodiertGeschrieben_laeuftDurchDenFilter() throws Exception {
    // Given: /%61pi/ erreicht dieselben Handler wie /api/ (Issue #1974)
    inGueltigerVorschau();
    request.setRequestURI("/%61pi/projects");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertVorschauGewechselt();
  }

  @Test
  void alterAdminTab_aufAusgenommenemPfadOhneKopfzeile_gehtOhneVorschauWeiter() throws Exception {
    // Given
    angemeldet(ADMIN);
    request.setRequestURI("/api/boards/3/events");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ADMIN);
  }

  // --- Problem-Format der 409 ------------------------------------------------------------------

  @Test
  void abweisung_imProblemFormatMitEigenemTyp() throws Exception {
    // Given
    inGueltigerVorschau();
    request.addHeader(VorschauFilter.HEADER, VorschauFilter.KEINE);

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"type\":\"vorschau-gewechselt\",\"title\":\"Conflict\",\"status\":409,"
                + "\"detail\":\"Die Ansicht hat gewechselt. Bitte lade die Seite neu.\"}");
  }

  @Test
  void abweisung_inDerEingestelltenSprache() throws Exception {
    // Given
    eingestellt = Sprache.EN;
    inGueltigerVorschau();

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertThat(response.getContentAsString())
        .contains("\"detail\":\"The view has changed. Please reload the page.\"");
  }

  // --- Geltungsbereich -------------------------------------------------------------------------

  @Test
  void ausserhalbDerApi_laeuftDerFilterNicht() throws Exception {
    // Given: das Laden der Seite selbst trägt nie eine Kopfzeile
    inGueltigerVorschau();
    request.setRequestURI("/projects/1");

    // When
    filter.doFilter(request, response, chain);

    // Then
    assertWeitergereicht();
    assertThat(authentication().getPrincipal()).isEqualTo(ADMIN);
    verifyNoInteractions(vorschauen);
  }
}
