package org.mwolff.manban.auth.application;

import java.util.Locale;
import org.mwolff.manban.auth.domain.AppUser;
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
    if (!user.emailVerified()) {
      throw new EmailNotVerifiedException();
    }
    return user;
  }
}
