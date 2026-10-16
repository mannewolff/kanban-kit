package org.mwolff.manban.auth.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Eine Anforderung der Code-Anmeldung: der sechsstellige Code und der Link derselben Mail (Plan
 * #1801, A1). Beide gelten gemeinsam und werden gemeinsam verbraucht. Persistiert werden nur HMAC
 * des Codes und Hash des Link-Tokens; den Klartext erhält der Nutzer einmalig per E-Mail.
 *
 * <p>Für eine unbekannte Adresse entsteht eine Anforderung ohne Konto, ohne Code und ohne Link
 * (A5), damit die Grenze je Adresse für beide Fälle gleich zählt.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param emailHash SHA-256 der normalisierten E-Mail-Adresse
 * @param userId zugehöriges Konto; {@code null} bei unbekannter Adresse
 * @param codeHash HMAC-SHA256 des Codes; {@code null} genau dann, wenn kein Konto
 * @param linkHash SHA-256 des Link-Tokens; {@code null} genau dann, wenn kein Konto
 * @param createdAt Zeitpunkt der Anforderung
 * @param expiresAt Ablaufzeitpunkt, nach {@code createdAt}
 * @param failedAttempts Zahl der falschen Eingaben, nicht negativ
 * @param consumedAt Zeitpunkt der Einlösung; {@code null} solange offen
 * @param supersededAt Zeitpunkt, zu dem eine neuere Anforderung diese ersetzt hat
 */
public record LoginCode(
    @Nullable Long id,
    String emailHash,
    @Nullable Long userId,
    @Nullable String codeHash,
    @Nullable String linkHash,
    Instant createdAt,
    Instant expiresAt,
    int failedAttempts,
    @Nullable Instant consumedAt,
    @Nullable Instant supersededAt)
    implements Identifiable {

  public LoginCode {
    if (emailHash.isBlank()) {
      throw new IllegalArgumentException("emailHash darf nicht leer sein");
    }
    if ((codeHash == null) != (linkHash == null)) {
      throw new IllegalArgumentException("Code und Link gibt es nur gemeinsam");
    }
    if ((codeHash == null) != (userId == null)) {
      throw new IllegalArgumentException(
          "Code und Link gibt es genau dann, wenn es ein Konto gibt");
    }
    if (failedAttempts < 0) {
      throw new IllegalArgumentException("failedAttempts darf nicht negativ sein");
    }
    if (!expiresAt.isAfter(createdAt)) {
      throw new IllegalArgumentException("expiresAt muss nach createdAt liegen");
    }
  }
}
