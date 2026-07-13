package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Reine Logiktests des Bootstraps (Mockito), ohne Spring-Kontext. */
class BootstrapServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private static AppUser user(long id, PlatformRole role) {
    return new AppUser(id, "u" + id + "@x.de", "hash", "U", true, role);
  }

  /** Noch nicht freigegebener Benutzer (kanonischer Konstruktor, {@code approvedAt=null}). */
  private static AppUser pendingUser(long id) {
    return new AppUser(id, "u" + id + "@x.de", "hash", "U", true, PlatformRole.USER, null, null);
  }

  @Test
  void disabledWhenNoTokenConfigured() {
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
    BootstrapService svc = new BootstrapService(users, new BootstrapProperties(null), CLOCK);

    assertThatThrownBy(() -> svc.bootstrap(1, "irgendwas"))
        .isInstanceOf(InvalidBootstrapTokenException.class);
  }

  /**
   * Drei strukturell identische Fälle (falscher Token, exakt getroffener Blank-Token, null-Token)
   * parametrisiert statt als separate Tests (Sonar S5976).
   */
  private static Stream<Arguments> rejectedTokenCombinations() {
    return Stream.of(
        // Umgehen des Token-Guards (Mutant) schlägt sonst in Erfolg statt Ausnahme um.
        Arguments.of("secret", "falsch"),
        // Blank-Guard muss auch bei identischen Strings greifen (kein Umgehen durch Gleichheit).
        Arguments.of("   ", "   "),
        Arguments.of("secret", null));
  }

  @ParameterizedTest(name = "[{index}] configuredToken=\"{0}\", presentedToken={1}")
  @MethodSource("rejectedTokenCombinations")
  void rejectsInvalidToken(String configuredToken, String presentedToken) {
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.USER)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
    BootstrapService svc =
        new BootstrapService(users, new BootstrapProperties(configuredToken), CLOCK);

    assertThatThrownBy(() -> svc.bootstrap(1, presentedToken))
        .isInstanceOf(InvalidBootstrapTokenException.class);
  }

  @Test
  void rejectsBlankConfiguredToken() {
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
    BootstrapService svc = new BootstrapService(users, new BootstrapProperties("   "), CLOCK);

    assertThatThrownBy(() -> svc.bootstrap(1, "irgendwas"))
        .isInstanceOf(InvalidBootstrapTokenException.class);
  }

  @Test
  void inertWhenAdminExists() {
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.ADMIN)));
    BootstrapService svc = new BootstrapService(users, new BootstrapProperties("secret"), CLOCK);

    assertThatThrownBy(() -> svc.bootstrap(2, "secret"))
        .isInstanceOf(BootstrapUnavailableException.class);
  }

  @Test
  void throwsUserNotFoundWhenCallerMissing() {
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
    when(users.findById(1L)).thenReturn(Optional.empty());
    BootstrapService svc = new BootstrapService(users, new BootstrapProperties("secret"), CLOCK);

    assertThatThrownBy(() -> svc.bootstrap(1, "secret")).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void elevatesCallerWithCorrectToken() {
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.USER)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
    BootstrapService svc = new BootstrapService(users, new BootstrapProperties("secret"), CLOCK);

    AdminService.UserView view = svc.bootstrap(1, "secret");
    assertThat(view.platformRole()).isEqualTo(PlatformRole.ADMIN);
    // Bereits freigegebener Nutzer: Freigabe-Zeitpunkt wird NICHT überschrieben (idempotent).
    assertThat(view.approvedAt()).isEqualTo(Instant.EPOCH);
  }

  @Test
  void selfApprovesPendingCaller_soLoginGateDoesNotLockOutFirstAdmin() {
    // Given: der erste Nutzer ist verifiziert, aber noch nicht freigegeben (pending).
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findAll()).thenReturn(List.of(pendingUser(1)));
    when(users.findById(1L)).thenReturn(Optional.of(pendingUser(1)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
    BootstrapService svc = new BootstrapService(users, new BootstrapProperties("secret"), CLOCK);

    // When
    AdminService.UserView view = svc.bootstrap(1, "secret");

    // Then: zum Admin erhoben UND selbst freigegeben.
    assertThat(view.platformRole()).isEqualTo(PlatformRole.ADMIN);
    assertThat(view.approvedAt()).isEqualTo(NOW);
  }
}
