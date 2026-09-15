package org.mwolff.manban.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plattform-Administration: Nutzer auflisten, Plattform-Rollen setzen und Registrierungen
 * freigeben. Alle Operationen erfordern, dass der Aufrufer selbst Plattform-Admin ist.
 *
 * <p>Über allen rollen- und sperrändernden Vorgängen steht eine Zusicherung: <strong>Mindestens ein
 * nicht gesperrter Plattform-Administrator bleibt übrig</strong> — gegen Herabstufen wie gegen
 * Sperren (Issue #881). Beide Vorgänge prüfen sie an derselben Stelle ({@link
 * #requireAnotherActiveAdminRemains}) und lehnen mit derselben {@link LastAdminException} ab.
 */
@Service
public class AdminService {

  private final AppUserRepository users;
  private final Clock clock;
  private final PlatformAdminChecker platformAdminChecker;

  public AdminService(
      AppUserRepository users, Clock clock, PlatformAdminChecker platformAdminChecker) {
    this.users = users;
    this.clock = clock;
    this.platformAdminChecker = platformAdminChecker;
  }

  @Transactional(readOnly = true)
  public boolean isPlatformAdmin(long userId) {
    return platformAdminChecker.isPlatformAdmin(userId);
  }

  @Transactional(readOnly = true)
  public List<UserView> listUsers(long actorUserId) {
    requirePlatformAdmin(actorUserId);
    return users.findAll().stream().map(AdminService::toView).toList();
  }

  /**
   * Setzt die Plattform-Rolle eines Benutzers. Eine Herabstufung darf nicht den letzten nicht
   * gesperrten Admin nehmen — und zwar auch dann nicht, wenn zwei Admins das gleichzeitig
   * füreinander versuchen: {@link AppUserRepository#lockActivePlatformAdminIds()} sperrt die
   * Admin-Zeilen, sodass der zweite Aufruf erst nach dem ersten prüft und dessen Degradierung
   * bereits sieht (Issue #498).
   */
  @Transactional
  public UserView changePlatformRole(long actorUserId, long targetUserId, PlatformRole newRole) {
    requirePlatformAdmin(actorUserId);
    // Vor dem Lesen des Ziels: Wer gerade Admin ist, entscheidet die gesperrte Menge — nicht die
    // (womöglich aus der Rechteprüfung zwischengespeicherte) Rolle am Benutzer selbst.
    List<Long> adminIds = users.lockActivePlatformAdminIds();
    AppUser target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);

    // Eine Beförderung vergrößert die Menge und kann die Zusicherung nicht verletzen.
    if (newRole != PlatformRole.ADMIN) {
      requireAnotherActiveAdminRemains(adminIds, targetUserId);
    }

    // Wer zum Plattform-Admin befördert wird, ist damit zugleich freigegeben — sonst bliebe ein
    // Admin in der Übersicht dauerhaft als „wartet auf Freigabe" stehen (Issue #557). Umgekehrt
    // gilt das bewusst nicht: eine Degradierung nimmt die Freigabe nicht zurück. Bereits
    // freigegebene Benutzer behalten Zeitpunkt und Freigebenden (idempotent, analog approve).
    AppUser updated = target.withPlatformRole(newRole);
    AppUser saved =
        users.save(
            newRole == PlatformRole.ADMIN && !updated.approved()
                ? updated.withApproved(clock.instant(), actorUserId)
                : updated);
    return toView(saved);
  }

  /** Ändert den Anzeigenamen eines beliebigen Benutzers (nur Plattform-Admin; getrimmt). */
  @Transactional
  public UserView changeDisplayName(long actorUserId, long targetUserId, String displayName) {
    requirePlatformAdmin(actorUserId);
    AppUser target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);
    return toView(users.save(target.withDisplayName(displayName.trim())));
  }

  /**
   * Gibt einen Benutzer frei. Idempotent: Ein bereits freigegebener Benutzer bleibt unverändert
   * (Zeitpunkt und freigebender Admin werden nicht überschrieben).
   */
  @Transactional
  public UserView approve(long actorUserId, long targetUserId) {
    requirePlatformAdmin(actorUserId);
    AppUser target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);
    if (target.approved()) {
      return toView(target);
    }
    AppUser saved = users.save(target.withApproved(clock.instant(), actorUserId));
    return toView(saved);
  }

  /**
   * Sperrt (deaktiviert) ein Konto. Der Aufrufer kann sich nicht selbst sperren; darüber hinaus
   * darf das Sperren nicht den letzten nicht gesperrten Plattform-Admin nehmen — dieselbe
   * Zusicherung und dieselbe Ablehnung wie beim Herabstufen (Issue #881).
   *
   * <p>Die Selbstsperre wird zuerst geprüft: Sie ist der häufigste Fehlgriff, spart so eine
   * Zeilensperre und behält ihre eigene Meldung, statt vom Aussperr-Schutz verdeckt zu werden.
   * Danach sperrt {@link AppUserRepository#lockActivePlatformAdminIds()} die Admin-Zeilen, damit
   * zwei gleichzeitige Sperren einander sehen (Issue #498). Idempotent: Ein bereits gesperrtes
   * Konto bleibt unverändert und erreicht den Schutz gar nicht — es nimmt der Plattform nichts
   * mehr.
   */
  @Transactional
  public UserView disable(long actorUserId, long targetUserId) {
    requirePlatformAdmin(actorUserId);
    if (actorUserId == targetUserId) {
      throw new CannotDisableSelfException();
    }
    List<Long> adminIds = users.lockActivePlatformAdminIds();
    AppUser target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);
    if (target.disabled()) {
      return toView(target);
    }
    requireAnotherActiveAdminRemains(adminIds, targetUserId);
    return toView(users.save(target.withDisabledAt(clock.instant())));
  }

  /**
   * Die gemeinsame Zusicherung beider Vorgänge: Nach dem Vorgang bleibt mindestens ein nicht
   * gesperrter Plattform-Administrator übrig. Verletzt ist sie genau dann, wenn das Ziel selbst in
   * der gesperrten Menge steht und diese keinen zweiten enthält.
   *
   * <p>Bewusst die <strong>einzige</strong> Stelle mit dieser Bedingung: Zwei Formulierungen
   * derselben Regel driften auseinander, und ein Vorgang bekäme stillschweigend einen anderen
   * Schutz als der andere.
   *
   * @param activeAdminIds IDs der nicht gesperrten Plattform-Admins, gesperrt gelesen
   * @param targetUserId Benutzer, den der Vorgang der Menge nähme
   */
  private static void requireAnotherActiveAdminRemains(
      List<Long> activeAdminIds, long targetUserId) {
    if (activeAdminIds.size() <= 1 && activeAdminIds.contains(targetUserId)) {
      throw new LastAdminException();
    }
  }

  /**
   * Entsperrt ein Konto. Idempotent (ein aktives Konto bleibt unverändert). Sperrt die Admin-Zeilen
   * bewusst nicht: Das Entsperren vergrößert die Menge der aktiven Admins und kann die Zusicherung
   * aus {@link #requireAnotherActiveAdminRemains} nicht verletzen.
   */
  @Transactional
  public UserView enable(long actorUserId, long targetUserId) {
    requirePlatformAdmin(actorUserId);
    AppUser target = users.findById(targetUserId).orElseThrow(UserNotFoundException::new);
    if (!target.disabled()) {
      return toView(target);
    }
    return toView(users.save(target.withDisabledAt(null)));
  }

  private void requirePlatformAdmin(long actorUserId) {
    if (!platformAdminChecker.isPlatformAdmin(actorUserId)) {
      throw new AdminAccessDeniedException();
    }
  }

  private static UserView toView(AppUser u) {
    return new UserView(
        u.requireId(),
        u.email(),
        u.displayName(),
        u.platformRole(),
        u.emailVerified(),
        u.approvedAt(),
        u.disabled());
  }

  /** Nutzerdarstellung für die Admin-Verwaltung. */
  public record UserView(
      Long id,
      String email,
      String displayName,
      PlatformRole platformRole,
      boolean emailVerified,
      @Nullable Instant approvedAt,
      boolean disabled) {}
}
