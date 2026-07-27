package org.mwolff.manban.auth.application;

import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Port für das Ausstellen und Prüfen von Session-Tokens (Issue #438).
 *
 * <p>Adapterneutral formuliert: Ein Token ist hier eine undurchsichtige Zeichenkette, die einen
 * Benutzer für eine begrenzte Zeit ausweist. Wie sie erzeugt und verifiziert wird — signiert,
 * verschlüsselt, gegen einen Store geprüft — ist Sache des Infrastruktur-Adapters (heute {@code
 * auth.infrastructure.security.SignedSessionTokens}). Der Web-Adapter kennt nur diesen Port und
 * bleibt damit vom Krypto-Verfahren entkoppelt.
 */
public interface SessionTokens {

  /** Stellt ein für die konfigurierte Sitzungsdauer gültiges Token für den Benutzer aus. */
  String issue(long userId);

  /**
   * Prüft Echtheit und Gültigkeitsdauer des Tokens und liefert die enthaltene userId, oder leer,
   * wenn das Token fehlt, manipuliert oder abgelaufen ist. {@code null} ist bewusst zulässig und
   * liefert ein leeres Ergebnis statt einer Ausnahme.
   */
  OptionalLong verify(@Nullable String token);
}
