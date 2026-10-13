package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.web.security.PatAuthenticationFilter;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

/** Der {@code LocaleResolver} nach Zugangsart (Issue #1640, Plan #1638 A5/E6). */
class SpracheLocaleConfigTest {

  private static final Locale DEUTSCH = Locale.forLanguageTag("de-DE");
  private static final Locale ENGLISCH = Locale.forLanguageTag("en-US");

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static LocaleResolver resolver(Sprache eingestellt) {
    return new SpracheLocaleConfig().localeResolver(SprachFixture.mit(eingestellt));
  }

  private static MockHttpServletRequest anfrage(String acceptLanguage) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
    request.addHeader("Accept-Language", acceptLanguage);
    return request;
  }

  private static void angemeldetMit(String... authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                "user", null, AuthorityUtils.createAuthorityList(List.of(authorities))));
  }

  @Test
  void anonymBekommtDieEingestellteSpracheStattAcceptLanguage() {
    assertThat(resolver(Sprache.EN).resolveLocale(anfrage("de-DE"))).isEqualTo(ENGLISCH);
    assertThat(resolver(Sprache.DE).resolveLocale(anfrage("en-US"))).isEqualTo(DEUTSCH);
  }

  @Test
  void sessionBekommtDieEingestellteSprache() {
    angemeldetMit("ROLE_USER");

    assertThat(resolver(Sprache.EN).resolveLocale(anfrage("de-DE"))).isEqualTo(ENGLISCH);
  }

  @Test
  void tokenBekommtDeutschAuchBeiEnglisch() {
    angemeldetMit(PatAuthenticationFilter.AUTHORITY);

    assertThat(resolver(Sprache.EN).resolveLocale(anfrage("en-US"))).isEqualTo(DEUTSCH);
  }

  @Test
  void ungebundenesTokenBekommtDeutschAuchBeiEnglisch() {
    angemeldetMit(PatAuthenticationFilter.UNBOUND_AUTHORITY);

    assertThat(resolver(Sprache.EN).resolveLocale(anfrage("en-US"))).isEqualTo(DEUTSCH);
  }

  @Test
  void setzenVonAussenIstNichtVorgesehen() {
    LocaleResolver resolver = resolver(Sprache.DE);
    MockHttpServletRequest request = anfrage("de");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> resolver.setLocale(request, response, ENGLISCH))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void beanTraegtDenNamenDenDasDispatcherServletSucht() {
    new WebApplicationContextRunner()
        .withBean(Texte.class, () -> SprachFixture.mit(Sprache.EN))
        .withUserConfiguration(SpracheLocaleConfig.class)
        .run(
            context ->
                assertThat(
                        context
                            .getBean(
                                DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class)
                            .resolveLocale(anfrage("de")))
                    .isEqualTo(ENGLISCH));
  }
}
