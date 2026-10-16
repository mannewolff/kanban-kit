package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft Anmeldedaten und liefert bei Erfolg den authentifizierten Benutzer.
 *
 * <p>2FA-Vorbereitung: Der Login ist so geschnitten, dass ein optionaler zweiter Faktor später hier
 * andockt — statt {@link AppUser} könnte künftig ein {@code LoginOutcome} zurückgegeben werden
 * (authentifiziert vs. MFA-Challenge), ohne die aufrufende {@code SessionController}-Logik
 * grundlegend zu ändern.
 *
 * <p><strong>Code-Anmeldung (Plan #1801, A8, E5, E6):</strong> {@link #loginWithCode} und {@link
 * #loginWithLink} lösen eine Anforderung atomar ein und prüfen danach in fester Reihenfolge:
 * Adresse bestätigen, Sperre, Freigabe, erst dann das Konto zurückgeben. Bestätigung, Fehlversuch
 * und vorgemerkte Admin-Mail überstehen die Abweisung ({@code noRollbackFor}): Ein Rollback nähme
 * dem gesperrten oder wartenden Konto die Bestätigung, den Admins die Nachricht und dem Fehlversuch
 * seine Zählung.
 */
@Service
public class LoginService {

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final LoginCodeRepository codes;
  private final LoginCodeCrypto crypto;
  private final AdminNotificationMailer adminNotificationMailer;
  private final AuthProperties properties;
  private final Clock clock;

  public LoginService(
      AppUserRepository users,
      PasswordEncoder passwordEncoder,
      LoginCodeRepository codes,
      LoginCodeCrypto crypto,
      AdminNotificationMailer adminNotificationMailer,
      AuthProperties properties,
      Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.codes = codes;
    this.crypto = crypto;
    this.adminNotificationMailer = adminNotificationMailer;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Löst den Code der jüngsten offenen Anforderung der Adresse ein. Nach {@code supersedeOpen} gibt
   * es je Adresse höchstens eine offene Anforderung (E6); ein falscher Code zählt dort als
   * Fehlversuch.
   *
   * @throws InvalidLoginCodeException bei falschem, abgelaufenem, verbrauchtem oder ersetztem Code
   *     und bei unbekannter Adresse — ununterscheidbar
   */
  @Transactional(
      noRollbackFor = {
        InvalidLoginCodeException.class,
        UserDisabledException.class,
        UserNotApprovedException.class
      })
  public AppUser loginWithCode(String email, String code) {
    String emailHash = SecureTokens.sha256Hex(email.trim().toLowerCase(Locale.ROOT));
    Instant now = clock.instant();
    int maxAttempts = properties.loginCode().maxAttempts();
    Optional<Long> userId = codes.consumeByCode(emailHash, crypto.codeHmac(code), now, maxAttempts);
    if (userId.isEmpty()) {
      codes.recordFailure(emailHash, now, maxAttempts);
      throw new InvalidLoginCodeException();
    }
    return admit(userId.get());
  }

  /**
   * Löst den Link einer offenen Anforderung ein; Code und Link teilen den Verbrauch.
   *
   * @throws InvalidLoginCodeException wenn das Token zu keiner offenen Anforderung gehört
   */
  @Transactional(
      noRollbackFor = {
        InvalidLoginCodeException.class,
        UserDisabledException.class,
        UserNotApprovedException.class
      })
  public AppUser loginWithLink(String token) {
    Long userId =
        codes
            .consumeByLink(
                crypto.linkHash(token), clock.instant(), properties.loginCode().maxAttempts())
            .orElseThrow(InvalidLoginCodeException::new);
    return admit(userId);
  }

  /** Prüfreihenfolge nach erfolgreichem Verbrauch (A8). */
  private AppUser admit(Long userId) {
    AppUser user = users.findById(userId).orElseThrow(InvalidLoginCodeException::new);
    // (1) Der Code beweist das Postfach — auch bei gesperrtem Konto (E5); Zugang gibt das nicht.
    boolean newlyVerified = !user.emailVerified();
    if (newlyVerified) {
      user = users.save(user.withEmailVerified(true));
    }
    // (2) Sperre.
    if (user.disabled()) {
      throw new UserDisabledException();
    }
    // (3) Freigabe, mit den Ausnahmen aus login(). Benachrichtigt wird nur beim Übergang
    // unbestätigt → bestätigt, damit wiederholte Versuche die Admins nicht fluten.
    if (!user.effectivelyApproved()) {
      if (newlyVerified) {
        notifyAdminsOfPendingApproval(user);
      }
      if (anyAdminExists()) {
        throw new UserNotApprovedException();
      }
    }
    // (4) Sitzung stellt der Aufrufer aus.
    return user;
  }

  // Aus VerifyEmailService übernommen; dort bleibt sie bis zum Entfernungspaket (Plan #1801).
  private void notifyAdminsOfPendingApproval(AppUser pendingUser) {
    for (AppUser admin : users.findByPlatformRole(PlatformRole.ADMIN)) {
      adminNotificationMailer.sendNewUserPendingApproval(
          admin.email(), pendingUser.email(), pendingUser.displayName());
    }
  }

  @Transactional(readOnly = true)
  public AppUser login(String email, String rawPassword) {
    AppUser user =
        users
            .findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(InvalidCredentialsException::new);

    if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
      throw new InvalidCredentialsException();
    }
    if (user.disabled()) {
      throw new UserDisabledException();
    }
    if (!user.emailVerified()) {
      throw new EmailNotVerifiedException();
    }
    // Freigabe-Gate (Issue #0097). Zwei Ausnahmen, beide gegen das Aussperren:
    // 1. Solange noch kein Plattform-Admin existiert, darf sich der (noch nicht freigegebene) erste
    //    Nutzer einloggen, um sich per Bootstrap-Token zum ersten Admin zu erheben — sonst gäbe es
    //    niemanden, der freigeben könnte.
    // 2. Ein Plattform-Admin braucht keine Freigabe (Issue #556): ein „auf Freigabe wartender
    //    Admin" ist sinnlos, weil niemand über ihm steht. Ohne diese Ausnahme kippt Ausnahme 1
    //    genau in dem Moment, in dem der Nutzer selbst zum Admin wird — er blockierte damit seine
    //    eigene Anmeldung. Die Regel steckt seit Issue #561 in AppUser.effectivelyApproved() und
    //    gilt damit überall gleich, auch bei Projektanlage und Einladung; sie sitzt in der Domäne
    //    und nicht nur im Rollenwechsel-Service, damit sie auch beim rohen SQL-UPDATE greift, das
    //    jeden Service umgeht.
    if (!user.effectivelyApproved() && anyAdminExists()) {
      throw new UserNotApprovedException();
    }
    return user;
  }

  private boolean anyAdminExists() {
    return users.findAll().stream().anyMatch(u -> u.platformRole() == PlatformRole.ADMIN);
  }
}
