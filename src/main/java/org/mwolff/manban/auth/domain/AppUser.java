package org.mwolff.manban.auth.domain;

import java.time.Instant;
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
 * @param approvedAt Zeitpunkt der Admin-Freigabe; {@code null} = noch nicht freigegeben (pending)
 * @param approvedBy ID des freigebenden Plattform-Admins; {@code null} wenn nicht (durch einen
 *     Admin) freigegeben
 * @param disabledAt Zeitpunkt der Sperre; {@code null} = aktiv (nicht gesperrt)
 */
public record AppUser(
    @Nullable Long id,
    String email,
    String passwordHash,
    String displayName,
    boolean emailVerified,
    PlatformRole platformRole,
    @Nullable Instant approvedAt,
    @Nullable Long approvedBy,
    @Nullable Instant disabledAt)
    implements Identifiable {

  /**
   * Sentinel-Zeitstempel für „bereits freigegeben, ohne echten Freigabe-Zeitpunkt". Wird
   * ausschließlich vom Bequem-Konstruktor gesetzt (geseedete/importierte bzw. Test-Benutzer).
   */
  private static final Instant PRE_APPROVED = Instant.EPOCH;

  /**
   * Bequem-Konstruktor für bereits im System bestehende Benutzer (geseedet, importiert oder in
   * Tests aufgebaut): Der so erzeugte Benutzer gilt als <strong>freigegeben</strong> und aktiv. Der
   * Registrierungs-Pfad ({@code RegisterUserService}) nutzt bewusst den kanonischen Konstruktor mit
   * {@code approvedAt=null}, um einen noch nicht freigegebenen (pending) Benutzer zu erzeugen.
   */
  public AppUser(
      @Nullable Long id,
      String email,
      String passwordHash,
      String displayName,
      boolean emailVerified,
      PlatformRole platformRole) {
    this(
        id,
        email,
        passwordHash,
        displayName,
        emailVerified,
        platformRole,
        PRE_APPROVED,
        null,
        null);
  }

  /**
   * Bequem-Konstruktor ohne Sperr-Feld: erzeugt einen aktiven Benutzer ({@code disabledAt=null}).
   */
  public AppUser(
      @Nullable Long id,
      String email,
      String passwordHash,
      String displayName,
      boolean emailVerified,
      PlatformRole platformRole,
      @Nullable Instant approvedAt,
      @Nullable Long approvedBy) {
    this(
        id,
        email,
        passwordHash,
        displayName,
        emailVerified,
        platformRole,
        approvedAt,
        approvedBy,
        null);
  }

  /** Ob der Benutzer von einem Plattform-Admin (bzw. beim Seed/Import) freigegeben wurde. */
  public boolean approved() {
    return approvedAt != null;
  }

  /** Ob der Benutzer gesperrt (deaktiviert) ist. */
  public boolean disabled() {
    return disabledAt != null;
  }

  /** Kopie mit gesetztem E-Mail-Verifikations-Status. */
  public AppUser withEmailVerified(boolean verified) {
    return new AppUser(
        id,
        email,
        passwordHash,
        displayName,
        verified,
        platformRole,
        approvedAt,
        approvedBy,
        disabledAt);
  }

  /** Kopie mit neuem Passwort-Hash. */
  public AppUser withPasswordHash(String newPasswordHash) {
    return new AppUser(
        id,
        email,
        newPasswordHash,
        displayName,
        emailVerified,
        platformRole,
        approvedAt,
        approvedBy,
        disabledAt);
  }

  /** Kopie mit neuem Anzeigenamen. */
  public AppUser withDisplayName(String newDisplayName) {
    return new AppUser(
        id,
        email,
        passwordHash,
        newDisplayName,
        emailVerified,
        platformRole,
        approvedAt,
        approvedBy,
        disabledAt);
  }

  /** Kopie mit neuer Plattform-Rolle. */
  public AppUser withPlatformRole(PlatformRole newPlatformRole) {
    return new AppUser(
        id,
        email,
        passwordHash,
        displayName,
        emailVerified,
        newPlatformRole,
        approvedAt,
        approvedBy,
        disabledAt);
  }

  /** Kopie mit gesetzter Freigabe (Zeitpunkt + ID des freigebenden Admins). */
  public AppUser withApproved(Instant when, @Nullable Long by) {
    return new AppUser(
        id, email, passwordHash, displayName, emailVerified, platformRole, when, by, disabledAt);
  }

  /** Kopie mit gesetzter/aufgehobener Sperre ({@code null} = entsperrt). */
  public AppUser withDisabledAt(@Nullable Instant when) {
    return new AppUser(
        id,
        email,
        passwordHash,
        displayName,
        emailVerified,
        platformRole,
        approvedAt,
        approvedBy,
        when);
  }
}
