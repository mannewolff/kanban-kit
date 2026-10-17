package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registriert einen neuen Benutzer: legt ihn unverifiziert an und fordert im selben Zug einen
 * Anmeldecode an (Plan #1801, E3). Eine eigene Bestätigungsmail gibt es nicht mehr — die erste
 * Anmeldung per Code bestätigt die Adresse.
 *
 * <p><strong>Grenze je Adresse:</strong> Die Code-Anforderung zählt auf die Grenze der Adresse. Ist
 * sie schon erreicht, wirft sie {@link LoginCodeLimitExceededException}, und die ganze
 * Registrierung wird zurückgerollt — ein Konto ohne Code entsteht nicht.
 *
 * <p><strong>Fehlersemantik seit Issue #502:</strong> Ein HTTP-Erfolg bestätigt die persistierte
 * Registrierung, <em>nicht</em> die Zustellung der Mail mit dem Code — die wird in derselben
 * Transaktion in der Outbox vorgemerkt und nach dem Commit mit Wiederholungen versandt.
 */
@Service
public class RegisterUserService {

  private final AppUserRepository users;
  private final RequestLoginCodeService loginCodes;
  private final Clock clock;
  private final RegistrationApprovalPolicy approvalPolicy;

  public RegisterUserService(
      AppUserRepository users,
      RequestLoginCodeService loginCodes,
      Clock clock,
      RegistrationApprovalPolicy approvalPolicy) {
    this.users = users;
    this.loginCodes = loginCodes;
    this.clock = clock;
    this.approvalPolicy = approvalPolicy;
  }

  /**
   * Legt das Konto an und fordert einen Anmeldecode an.
   *
   * @throws EmailAlreadyRegisteredException wenn die Adresse schon registriert ist
   * @throws LoginCodeLimitExceededException wenn die Grenze je Adresse erreicht ist
   */
  @Transactional
  public AppUser register(String email, String displayName) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(normalizedEmail)) {
      throw new EmailAlreadyRegisteredException();
    }

    // Per Token eingeladene E-Mails werden sofort freigegeben (Issue #0099, „Einladung =
    // Freigabe"); alle anderen Selbst-Registrierer warten auf die Admin-Freigabe (Issue #0097).
    Instant approvedAt = approvalPolicy.shouldAutoApprove(normalizedEmail) ? clock.instant() : null;

    AppUser user =
        users.save(
            new AppUser(
                null,
                normalizedEmail,
                displayName.trim(),
                false,
                PlatformRole.USER,
                approvedAt,
                null));

    loginCodes.requestCode(normalizedEmail);
    return user;
  }
}
