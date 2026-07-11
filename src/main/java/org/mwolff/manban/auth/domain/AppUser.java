package org.mwolff.manban.auth.domain;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Domänen-Repräsentation eines Benutzers. Frei von Persistenz- und Framework-Bezug.
 *
 * @param id technische ID; {@code null} für einen noch nicht persistierten Benutzer
 * @param email eindeutige E-Mail-Adresse (Login-Kennung)
 * @param passwordHash Argon2id-Hash des Passworts (gesetzt ab Issue A1)
 * @param displayName Anzeigename
 * @param emailVerified ob die E-Mail bestätigt wurde
 * @param platformRole plattformweite Rolle
 */
public record AppUser(
    @Nullable Long id,
    String email,
    String passwordHash,
    String displayName,
    boolean emailVerified,
    PlatformRole platformRole)
    implements Identifiable {

  /** Kopie mit gesetztem E-Mail-Verifikations-Status. */
  public AppUser withEmailVerified(boolean verified) {
    return new AppUser(id, email, passwordHash, displayName, verified, platformRole);
  }

  /** Kopie mit neuem Passwort-Hash. */
  public AppUser withPasswordHash(String newPasswordHash) {
    return new AppUser(id, email, newPasswordHash, displayName, emailVerified, platformRole);
  }

  /** Kopie mit neuer Plattform-Rolle. */
  public AppUser withPlatformRole(PlatformRole newPlatformRole) {
    return new AppUser(id, email, passwordHash, displayName, emailVerified, newPlatformRole);
  }
}
