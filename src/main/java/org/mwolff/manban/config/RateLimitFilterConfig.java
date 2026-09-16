package org.mwolff.manban.config;

import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.List;
import org.mwolff.manban.ratelimit.application.RateLimitedOperation;
import org.mwolff.manban.ratelimit.application.RateLimiter;
import org.mwolff.manban.ratelimit.web.ClientOriginResolver;
import org.mwolff.manban.ratelimit.web.RateLimitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Verdrahtet die Zählbremse und erzwingt ihre Reihenfolge gegenüber Springs {@code
 * ForwardedHeaderFilter} (Issue #899, Plan #892 E2).
 *
 * <p><strong>Warum beide Filter hier registriert werden.</strong> Spring Boot registriert den
 * {@code ForwardedHeaderFilter} selbst mit {@link Ordered#HIGHEST_PRECEDENCE}. Zwei Filter gleicher
 * Order haben zueinander <em>keine</em> definierte Reihenfolge. Liefe Springs Filter zuerst, wären
 * die {@code X-Forwarded-*}-Header für die Bremse verschwunden, und {@code getRemoteAddr()}
 * lieferte den vom Client frei wählbaren ersten Eintrag — die Bremse wäre umgehbar, und zwar
 * stillschweigend: Tests mit {@code MockHttpServletRequest} blieben grün. Deshalb registriert diese
 * Klasse beide selbst; Boots Auto-Konfiguration weicht per {@code @ConditionalOnMissingFilterBean}
 * zurück ({@code ServletWebServerFactoryAutoConfiguration}).
 *
 * <p>Übernommen werden dabei beide Eigenschaften, die Boots Registrierung setzt: die
 * Dispatcher-Typen {@code REQUEST}/{@code ASYNC}/{@code ERROR} und der Schalter für relative
 * Umleitungen aus {@code server.tomcat.use-relative-redirects}. Ohne sie wäre eine bestehende
 * Konfiguration nach dieser Änderung wirkungslos, ohne dass es jemand bemerkte.
 *
 * <p>Die Bremse selbst greift nur an den drei begrenzten Pfaden ({@code urlPatterns}) und nur beim
 * {@code REQUEST}-Dispatch: Ein Fehler-Dispatch desselben Aufrufs ist kein zweiter Versuch.
 *
 * <p>Diese Klasse fasst weder Domänentyp noch Repository noch {@code RateLimitProperties} an — sie
 * steckt zusammen, was die Module anbieten, und bleibt damit innerhalb der ArchUnit-Regeln für die
 * Composition-Root (E16).
 *
 * <p><strong>Nur in der Servlet-Umgebung.</strong> Beide Beans sind Servlet-Angelegenheiten: Ein
 * {@code FilterRegistrationBean} braucht einen Container, und {@link ServerProperties} entsteht
 * erst mit Boots Web-Auto-Konfiguration. Zehn Integrationstests laufen jedoch bewusst mit {@code
 * spring.main.web-application-type=none} (Outbox, Nebenläufigkeit, reine Persistenz). Ohne diese
 * Bedingung scheitert dort nicht ein einzelner Test, sondern der Kontextstart — {@code APPLICATION
 * FAILED TO START}, und ganze Klassen fallen aus. Die Bedingung nimmt der Produktion nichts: Ohne
 * Servlet-Container gäbe es nichts zu filtern.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RateLimitFilterConfig {

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
      RateLimiter rateLimiter, ClientOriginResolver originResolver) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(new RateLimitFilter(rateLimiter, originResolver));
    registration.setUrlPatterns(limitedPaths());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Bean
  FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilterRegistration(
      ServerProperties serverProperties) {
    ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
    filter.setRelativeRedirects(serverProperties.getTomcat().isUseRelativeRedirects());
    FilterRegistrationBean<ForwardedHeaderFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setDispatcherTypes(
        DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }

  /** Die begrenzten Pfade — aus dem Vorgang, nicht aus einer zweiten Liste. */
  private static List<String> limitedPaths() {
    return Arrays.stream(RateLimitedOperation.values()).map(RateLimitedOperation::path).toList();
  }
}
