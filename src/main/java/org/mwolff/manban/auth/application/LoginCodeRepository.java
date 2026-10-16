package org.mwolff.manban.auth.application;

import java.time.Instant;
import java.util.Optional;
import org.mwolff.manban.auth.domain.LoginCode;

/**
 * Ausgehender Port für die Anforderungen der Code-Anmeldung (Plan #1801, A1, A2).
 *
 * <p><strong>Warum Verbrauch und Fehlversuch je ein eigener Aufruf sind:</strong> Wie bei {@link
 * SingleUseTokenRepository#consume} (Issue #497) dürfen Prüfen und Schreiben nicht nacheinander
 * laufen. Unter READ COMMITTED sähen zwei gleichzeitige Einlösungen beide eine offene Anforderung
 * und gewännen beide, und gleichzeitige Rateversuche zählten über das Maximum hinaus. Jede der
 * Methoden {@link #consumeByCode}, {@link #consumeByLink} und {@link #recordFailure} ist deshalb
 * <strong>eine</strong> Anweisung in der Datenbank.
 *
 * <p>Offen ist eine Anforderung, solange sie weder verbraucht noch ersetzt noch abgelaufen ist
 * ({@code expiresAt > now}) und weniger als {@code maxAttempts} Fehlversuche trägt.
 */
public interface LoginCodeRepository {

  LoginCode save(LoginCode code);

  /** Zahl der Anforderungen einer Adresse mit {@code createdAt >= since}. */
  long countSince(String emailHash, Instant since);

  /**
   * Ersetzt alle weder verbrauchten noch schon ersetzten Anforderungen der Adresse — Code und Link
   * gelten danach nicht mehr.
   *
   * @return Zahl der ersetzten Anforderungen
   */
  int supersedeOpen(String emailHash, Instant now);

  /**
   * Löst die offene Anforderung mit diesem Code-HMAC <strong>atomar</strong> ein. Genau ein Aufruf
   * gewinnt; alle anderen — und falsche, verbrauchte, ersetzte, abgelaufene oder ausgereizte —
   * liefern ununterscheidbar {@link Optional#empty()}.
   *
   * @return Konto der eingelösten Anforderung; sonst leer
   */
  Optional<Long> consumeByCode(String emailHash, String codeHmac, Instant now, int maxAttempts);

  /**
   * Löst die offene Anforderung mit diesem Link-Hash <strong>atomar</strong> ein; sonst wie {@link
   * #consumeByCode}. Code und Link teilen den Verbrauch.
   *
   * @return Konto der eingelösten Anforderung; sonst leer
   */
  Optional<Long> consumeByLink(String linkHash, Instant now, int maxAttempts);

  /**
   * Zählt einen Fehlversuch an den offenen Anforderungen der Adresse <strong>atomar</strong> hoch,
   * höchstens bis {@code maxAttempts}.
   *
   * @return Zahl der hochgezählten Anforderungen
   */
  int recordFailure(String emailHash, Instant now, int maxAttempts);

  /**
   * Löscht alle Anforderungen mit {@code createdAt < cutoff}.
   *
   * @return Zahl der gelöschten Anforderungen
   */
  int deleteOlderThan(Instant cutoff);
}
