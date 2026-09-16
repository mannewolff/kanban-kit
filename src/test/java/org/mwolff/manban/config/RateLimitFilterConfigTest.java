package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.application.RateLimitProperties;
import org.mwolff.manban.ratelimit.application.RateLimiter;
import org.mwolff.manban.ratelimit.web.ClientOriginResolver;
import org.mwolff.manban.ratelimit.web.RateLimitFilter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Die Registrierung der beiden Filter (Issue #899, Plan #892 E2).
 *
 * <p>Der sicherheitskritische Punkt ist die <em>Reihenfolge</em>: Liefe Springs {@code
 * ForwardedHeaderFilter} zuerst, wären die {@code X-Forwarded-*}-Header für die Bremse verschwunden
 * und ihre Herkunft käme aus {@code getRemoteAddr()} — also aus dem vom Client frei wählbaren
 * ersten Eintrag. Kein Test mit {@code MockHttpServletRequest} bemerkte das; deshalb wird die
 * Reihenfolge hier am echten Kontext geprüft.
 *
 * <p>Der erste Test ist die Kontrolle: Er zeigt, dass Boots Auto-Konfiguration in diesem Kontext
 * überhaupt greift. Ohne ihn wäre „genau eine Registrierung" auch dann grün, wenn die
 * Auto-Konfiguration gar nicht geladen wäre — und der Rückzug per {@code
 * ConditionalOnMissingFilterBean} bliebe unbelegt.
 */
class RateLimitFilterConfigTest {

  private static final String LOGIN_PATH = "/api/auth/login";

  /** Kontext mit Boots Servlet-Auto-Konfiguration und der Weiterleitungs-Strategie der App. */
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ServletWebServerFactoryAutoConfiguration.class))
          .withPropertyValues("server.forward-headers-strategy=framework");

  /** Derselbe Kontext plus die Composition-Root dieses Issues samt ihrer beiden Abhängigkeiten. */
  private WebApplicationContextRunner runnerWithConfig() {
    return runner
        .withBean(RateLimiter.class, () -> mock(RateLimiter.class))
        .withBean(
            ClientOriginResolver.class,
            () ->
                new ClientOriginResolver(new RateLimitProperties(true, 1, null, null, null, null)))
        .withUserConfiguration(RateLimitFilterConfig.class);
  }

  /** Ein Endpunkt, der umleitet — belegt, ob der Schalter für relative Umleitungen ankommt. */
  private static final class RedirectingServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      response.sendRedirect("/ziel");
    }
  }

  private static List<FilterRegistrationBean<?>> registrationsOf(
      ApplicationContext context, Class<? extends Filter> filterType) {
    return Stream.of(context.getBeanNamesForType(FilterRegistrationBean.class))
        .<FilterRegistrationBean<?>>map(name -> (FilterRegistrationBean<?>) context.getBean(name))
        .filter(registration -> filterType.isInstance(registration.getFilter()))
        .toList();
  }

  private static FilterRegistrationBean<?> onlyRegistrationOf(
      ApplicationContext context, Class<? extends Filter> filterType) {
    List<FilterRegistrationBean<?>> registrations = registrationsOf(context, filterType);
    assertThat(registrations).hasSize(1);
    return registrations.get(0);
  }

  /**
   * Das Umleitungsziel hinter dem Filter.
   *
   * <p>Die Weiterleitungsheader sind nötig: Ohne sie steigt der Filter in {@code shouldNotFilter}
   * sofort aus und wrappt die Antwort gar nicht — der Unterschied zwischen relativer und absoluter
   * Umleitung wäre dann unsichtbar.
   */
  private static String redirectedUrlAfter(Filter filter) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
    request.addHeader("X-Forwarded-Host", "kanban.example.org");
    request.addHeader("X-Forwarded-Proto", "https");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain(new RedirectingServlet()));
    return String.valueOf(response.getRedirectedUrl());
  }

  @Test
  void ohneUnsereKonfigurationRegistriertBootDenWeiterleitungsfilterSelbst() {
    // Given / When — nur Boots Auto-Konfiguration.
    runner.run(
        context ->
            // Then — sie ist aktiv und setzt die höchste Priorität.
            assertThat(onlyRegistrationOf(context, ForwardedHeaderFilter.class).getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE));
  }

  @Test
  void mitUnsererKonfigurationBleibtGenauEineWeiterleitungsregistrierung() {
    // Given / When — Boot weicht per ConditionalOnMissingFilterBean zurück.
    runnerWithConfig()
        .run(
            context ->
                // Then — genau eine, und zwar unsere: Boots trüge HIGHEST_PRECEDENCE.
                assertThat(onlyRegistrationOf(context, ForwardedHeaderFilter.class).getOrder())
                    .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1));
  }

  @Test
  void dieBremseLaeuftVorDerAufloesungDerWeiterleitungsheader() {
    // Given / When
    runnerWithConfig()
        .run(
            context ->
                // Then — kleinere Order zuerst: die Bremse sieht X-Forwarded-For noch.
                assertThat(onlyRegistrationOf(context, RateLimitFilter.class).getOrder())
                    .isLessThan(
                        onlyRegistrationOf(context, ForwardedHeaderFilter.class).getOrder()));
  }

  @Test
  void dieBremseGreiftNurAnDenDreiBegrenztenPfaden() {
    // Given / When
    runnerWithConfig()
        .run(
            context ->
                // Then
                assertThat(onlyRegistrationOf(context, RateLimitFilter.class).getUrlPatterns())
                    .containsExactly(LOGIN_PATH, "/api/auth/register", "/api/auth/forgot"));
  }

  @Test
  void derWeiterleitungsfilterLaeuftAuchBeiFehlerWeiterleitungen() {
    // Given / When — Boot registriert ASYNC und ERROR mit; der Default wäre nur REQUEST.
    runnerWithConfig()
        .run(
            context ->
                // Then
                assertThat(
                        onlyRegistrationOf(context, ForwardedHeaderFilter.class)
                            .determineDispatcherTypes())
                    .containsExactlyInAnyOrder(
                        DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
  }

  @Test
  void relativeUmleitungenBleibenKonfigurierbar() {
    // Given / When — die Einstellung gehört Boot; wer den Filter selbst registriert, muss sie
    // weiterreichen, sonst wäre sie stillschweigend wirkungslos.
    runnerWithConfig()
        .withPropertyValues("server.tomcat.use-relative-redirects=true")
        .run(
            context ->
                // Then
                assertThat(
                        redirectedUrlAfter(
                            onlyRegistrationOf(context, ForwardedHeaderFilter.class).getFilter()))
                    .isEqualTo("/ziel"));
  }

  @Test
  void ohneDieEinstellungBleibenUmleitungenAbsolut() {
    // Given / When — der Vorgabewert von Boot.
    runnerWithConfig()
        .run(
            context ->
                // Then — die Umleitung trägt die Adresse, unter der der Proxy erreichbar ist.
                assertThat(
                        redirectedUrlAfter(
                            onlyRegistrationOf(context, ForwardedHeaderFilter.class).getFilter()))
                    .isEqualTo("https://kanban.example.org/ziel"));
  }

  /**
   * Ohne Servlet-Umgebung traegt die Konfiguration nichts bei.
   *
   * <p>Zehn IT-Klassen laufen bewusst mit {@code spring.main.web-application-type=none} (Outbox,
   * Nebenlaeufigkeit, reine Persistenz). Dort gibt es keine {@code ServerProperties}-Bean, und
   * beide Registrierungen waeren ohnehin wirkungslos: Ein {@code FilterRegistrationBean} braucht
   * einen Servlet-Container. Ohne Bedingung an der Klasse scheitert in diesen Kontexten nicht ein
   * einzelner Test, sondern der Kontextstart selbst — „required a bean of type ServerProperties",
   * und damit fallen ganze Klassen mit {@code APPLICATION FAILED TO START} aus.
   */
  @Test
  void ohneServletUmgebung_traegtDieKonfigurationNichtsBei() {
    new ApplicationContextRunner()
        .withBean(RateLimiter.class, () -> mock(RateLimiter.class))
        .withBean(
            ClientOriginResolver.class,
            () ->
                new ClientOriginResolver(new RateLimitProperties(true, 1, null, null, null, null)))
        .withUserConfiguration(RateLimitFilterConfig.class)
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(FilterRegistrationBean.class));
  }
}
