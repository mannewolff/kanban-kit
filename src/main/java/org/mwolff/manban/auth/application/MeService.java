package org.mwolff.manban.auth.application;

import java.util.List;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Liefert die Selbstauskunft ({@code /api/me}): Benutzer + Plattform-Rolle + Mitgliedschaften. */
@Service
public class MeService {

  private final AppUserRepository users;
  private final ProjectMembershipReader memberships;

  public MeService(AppUserRepository users, ProjectMembershipReader memberships) {
    this.users = users;
    this.memberships = memberships;
  }

  @Transactional(readOnly = true)
  public MeView load(long userId) {
    return toView(users.findById(userId).orElseThrow(InvalidCredentialsException::new));
  }

  /** Ändert den Anzeigenamen des angemeldeten Benutzers (getrimmt) und liefert die neue Sicht. */
  @Transactional
  public MeView updateDisplayName(long userId, String displayName) {
    AppUser user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
    return toView(users.save(user.withDisplayName(displayName.trim())));
  }

  private MeView toView(AppUser user) {
    return new MeView(
        user.requireId(),
        user.email(),
        user.displayName(),
        user.platformRole(),
        memberships.findByUserId(user.requireId()));
  }

  /** Selbstauskunft eines angemeldeten Benutzers. */
  public record MeView(
      Long userId,
      String email,
      String displayName,
      PlatformRole platformRole,
      List<ProjectMembershipReader.Membership> memberships) {}
}
