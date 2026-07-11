package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Reine Logiktests des Bootstraps (Mockito), ohne Spring-Kontext. */
class BootstrapServiceTest {

    private static AppUser user(long id, PlatformRole role) {
        return new AppUser(id, "u" + id + "@x.de", "hash", "U", true, role);
    }

    @Test
    void disabledWhenNoTokenConfigured() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
        BootstrapService svc = new BootstrapService(users, new BootstrapProperties(null));

        assertThatThrownBy(() -> svc.bootstrap(1, "irgendwas"))
                .isInstanceOf(InvalidBootstrapTokenException.class);
    }

    @Test
    void rejectsWrongToken() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
        BootstrapService svc = new BootstrapService(users, new BootstrapProperties("secret"));

        assertThatThrownBy(() -> svc.bootstrap(1, "falsch"))
                .isInstanceOf(InvalidBootstrapTokenException.class);
    }

    @Test
    void rejectsBlankConfiguredToken() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
        BootstrapService svc = new BootstrapService(users, new BootstrapProperties("   "));

        assertThatThrownBy(() -> svc.bootstrap(1, "irgendwas"))
                .isInstanceOf(InvalidBootstrapTokenException.class);
    }

    @Test
    void rejectsNullToken() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
        BootstrapService svc = new BootstrapService(users, new BootstrapProperties("secret"));

        assertThatThrownBy(() -> svc.bootstrap(1, null))
                .isInstanceOf(InvalidBootstrapTokenException.class);
    }

    @Test
    void inertWhenAdminExists() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.ADMIN)));
        BootstrapService svc = new BootstrapService(users, new BootstrapProperties("secret"));

        assertThatThrownBy(() -> svc.bootstrap(2, "secret"))
                .isInstanceOf(BootstrapUnavailableException.class);
    }

    @Test
    void throwsUserNotFoundWhenCallerMissing() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
        when(users.findById(1L)).thenReturn(Optional.empty());
        BootstrapService svc = new BootstrapService(users, new BootstrapProperties("secret"));

        assertThatThrownBy(() -> svc.bootstrap(1, "secret"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void elevatesCallerWithCorrectToken() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.USER)));
        when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.USER)));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        BootstrapService svc = new BootstrapService(users, new BootstrapProperties("secret"));

        AdminService.UserView view = svc.bootstrap(1, "secret");
        assertThat(view.platformRole()).isEqualTo(PlatformRole.ADMIN);
    }
}
