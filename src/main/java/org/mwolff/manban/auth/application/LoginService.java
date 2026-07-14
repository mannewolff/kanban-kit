package org.mwolff.manban.auth.application;

import java.util.Locale;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
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
 */
@Service
public class LoginService {

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;

  public LoginService(AppUserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
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
    // Freigabe-Gate (Issue #0097). Ausnahme: Solange noch kein Plattform-Admin existiert, darf sich
    // der (noch nicht freigegebene) erste Nutzer einloggen, um sich per Bootstrap-Token zum ersten
    // Admin zu erheben — sonst gäbe es niemanden, der freigeben könnte (kein Aussperren).
    if (!user.approved() && anyAdminExists()) {
      throw new UserNotApprovedException();
    }
    return user;
  }

  private boolean anyAdminExists() {
    return users.findAll().stream().anyMatch(u -> u.platformRole() == PlatformRole.ADMIN);
  }
}
