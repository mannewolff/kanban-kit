package org.mwolff.manban.common.i18n;

import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Feste Texte des Servers in einer Sprache (Issue #1640, Plan #1638 A5): Schlüssel und Argumente
 * werden über die {@link MessageSource} ({@code i18n/messages_*.properties}) zum Text.
 *
 * <p><strong>Sprache nach Zugangsart.</strong> Eine Anfrage mit Token-Autorität ({@value
 * #TOKEN_AUTORITAET}, {@value #UNGEBUNDENE_TOKEN_AUTORITAET}) bekommt immer Deutsch: Werkzeuge
 * lesen die Meldungen, und deren Texte ändern sich mit der Spracheinstellung nicht (Nicht-Ziel API
 * aus Issue #1637). Session- und anonyme Anfragen bekommen die eingestellte Sprache. Die
 * Authority-Namen stehen hier als eigene Konstanten, weil {@code common} das Modul {@code
 * accesstoken} nicht kennen darf; sie gleichen {@code PatAuthenticationFilter.AUTHORITY} und {@code
 * UNBOUND_AUTHORITY}.
 */
@Component
public class Texte {

  /** Authority jedes Projekt-Tokens, gebunden wie ungebunden. */
  static final String TOKEN_AUTORITAET = "AUTH_PAT";

  /** Authority eines Projekt-Tokens ohne Board-Bindung. */
  static final String UNGEBUNDENE_TOKEN_AUTORITAET = "AUTH_PAT_UNBOUND";

  private static final Set<String> TOKEN_AUTORITAETEN =
      Set.of(TOKEN_AUTORITAET, UNGEBUNDENE_TOKEN_AUTORITAET);

  private static final Logger LOG = LoggerFactory.getLogger(Texte.class);

  private final MessageSource meldungen;
  private final SpracheQuelle quelle;

  public Texte(MessageSource meldungen, SpracheQuelle quelle) {
    this.meldungen = meldungen;
    this.quelle = quelle;
  }

  /**
   * Der Text zum Schlüssel in der Sprache.
   *
   * @throws org.springframework.context.NoSuchMessageException wenn der Schlüssel fehlt
   */
  public String text(Sprache sprache, String schluessel, Object... argumente) {
    return meldungen.getMessage(schluessel, argumente, sprache.locale());
  }

  /** Der Text einer übersetzbaren Meldung in der Sprache. */
  public String text(Sprache sprache, UebersetzbareMeldung meldung) {
    return text(sprache, meldung.schluessel(), meldung.argumente());
  }

  /**
   * Die eingestellte Sprache der Installation; Deutsch, wenn die Abfrage scheitert.
   *
   * <p>Der Port fängt Datenbankfehler schon selbst ab. Der Rückfall hier gilt jedem anderen
   * Laufzeitfehler: Gerufen wird oft aus einem Fehlerhandler, und der darf beim Bestimmen der
   * Sprache nicht ein zweites Mal werfen.
   */
  // PMD.AvoidCatchingGenericException: bewusst — jeder Fehler der Abfrage endet in Deutsch.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public Sprache eingestellte() {
    try {
      return quelle.aktuelle();
    } catch (RuntimeException e) {
      LOG.warn("Sprache der Installation nicht bestimmbar — es gilt Deutsch", e);
      return Sprache.DE;
    }
  }

  /**
   * Die Sprache der laufenden Anfrage nach Zugangsart: Deutsch bei Token-Autorität, sonst die
   * eingestellte Sprache.
   */
  public Sprache spracheDerAnfrage() {
    return mitTokenAutoritaet(SecurityContextHolder.getContext().getAuthentication())
        ? Sprache.DE
        : eingestellte();
  }

  private static boolean mitTokenAutoritaet(@Nullable Authentication authentication) {
    if (authentication == null) {
      return false;
    }
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      if (TOKEN_AUTORITAETEN.contains(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }
}
