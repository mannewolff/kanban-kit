package org.mwolff.manban.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.i18n.Texte;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Die Locale jeder Anfrage nach Zugangsart (Issue #1640, Plan #1638 A5/E6): Token-Autorität ergibt
 * {@code de-DE}, Session und anonym die eingestellte Sprache — unabhängig von {@code
 * Accept-Language}.
 *
 * <p>Wirkung vor allem auf Bean Validation: Spring interpoliert die Constraint-Texte aus {@code
 * ValidationMessages_de}/{@code _en} mit der Locale dieses Resolvers. Mit Springs Vorgabe ({@code
 * AcceptHeaderLocaleResolver}) entschiede der Browser über die Sprache der Feldmeldungen.
 *
 * <p>Der Bean-Name muss {@value DispatcherServlet#LOCALE_RESOLVER_BEAN_NAME} lauten; unter diesem
 * Namen sucht ihn das {@link DispatcherServlet}, und Boots Vorgabe weicht zurück. Das Servlet fragt
 * die Locale erst bei Bedarf ab, eine Anfrage ohne Validierung liest die Spracheinstellung also
 * nicht.
 */
@Configuration(proxyBeanMethods = false)
public class SpracheLocaleConfig {

  /** Öffentlich, damit Slice- und Standalone-Tests anderer Pakete denselben Resolver einsetzen. */
  @Bean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
  public LocaleResolver localeResolver(Texte texte) {
    return new SpracheLocaleResolver(texte);
  }

  /** Locale aus der Sprache der Anfrage; ein Setzen von außen gibt es nicht. */
  static final class SpracheLocaleResolver implements LocaleResolver {

    private final Texte texte;

    SpracheLocaleResolver(Texte texte) {
      this.texte = texte;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
      return texte.spracheDerAnfrage().locale();
    }

    /**
     * Nicht unterstützt: Die Sprache stellt allein der Plattform-Admin für die ganze Installation
     * ein.
     */
    @Override
    public void setLocale(
        HttpServletRequest request,
        @Nullable HttpServletResponse response,
        @Nullable Locale locale) {
      throw new UnsupportedOperationException(
          "Die Sprache gilt für die ganze Installation und wird in der Administration eingestellt");
    }
  }
}
