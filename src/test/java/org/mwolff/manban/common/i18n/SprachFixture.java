package org.mwolff.manban.common.i18n;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sprache für Unit-Tests (Issue #1640): {@link Texte} mit denselben Meldungsdateien und Schaltern
 * wie die Auto-Konfiguration aus {@code spring.messages}, dazu die Zugangsart der Anfrage.
 */
public final class SprachFixture {

  private SprachFixture() {}

  /**
   * Die Meldungsdateien {@code i18n/messages_*.properties}, ohne Rückfall auf die System-Locale.
   */
  public static MessageSource meldungen() {
    ResourceBundleMessageSource meldungen = new ResourceBundleMessageSource();
    meldungen.setBasename("i18n/messages");
    meldungen.setDefaultEncoding(StandardCharsets.UTF_8.name());
    meldungen.setFallbackToSystemLocale(false);
    return meldungen;
  }

  /** Texte bei eingestellter Sprache {@code sprache}. */
  public static Texte mit(Sprache sprache) {
    return mit(() -> sprache);
  }

  /** Texte mit der gegebenen Sprachquelle. */
  public static Texte mit(SpracheQuelle quelle) {
    return new Texte(meldungen(), quelle);
  }

  /** Texte, deren Sprachabfrage scheitert wie bei nicht erreichbarer Datenbank. */
  public static Texte mitScheiternderQuelle() {
    return mit(
        () -> {
          throw new DataAccessResourceFailureException("Datenbank weg");
        });
  }

  /** Meldet die laufende Anfrage mit den gegebenen Authorities an. */
  public static void angemeldetMit(String... authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                "user", null, AuthorityUtils.createAuthorityList(List.of(authorities))));
  }

  /** Räumt die Anmeldung der laufenden Anfrage ab. */
  public static void abmelden() {
    SecurityContextHolder.clearContext();
  }
}
