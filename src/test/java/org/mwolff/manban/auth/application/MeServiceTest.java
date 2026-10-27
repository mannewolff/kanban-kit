package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.ProjectMembershipReader.Membership;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.ColorScheme;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Verhaltenstests der Selbstauskunft {@code /api/me} (Mockito an den Ports). */
class MeServiceTest {

  private static final Instant ABLAUF = Instant.parse("2026-10-27T10:30:00Z");

  private AppUserRepository users;
  private ProjectMembershipReader memberships;
  private Optional<VorschauKontext> kontext;
  private MeService service;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    memberships = mock(ProjectMembershipReader.class);
    kontext = Optional.empty();
    service = new MeService(users, memberships, () -> kontext);
  }

  @Test
  void load_returnsUserIdentity_whenUserExists() {
    // Given
    when(users.findById(2L))
        .thenReturn(Optional.of(new AppUser(2L, "a@x.de", "Ada", true, PlatformRole.USER)));
    when(memberships.findByUserId(2L)).thenReturn(List.of());

    // When
    MeService.MeView view = service.load(2L);

    // Then
    assertThat(view.email()).isEqualTo("a@x.de");
  }

  @Test
  void load_includesMemberships_fromReader() {
    // Given
    when(users.findById(2L))
        .thenReturn(Optional.of(new AppUser(2L, "a@x.de", "Ada", true, PlatformRole.USER)));
    when(memberships.findByUserId(2L)).thenReturn(List.of(new Membership(7L, "OWNER")));

    // When
    MeService.MeView view = service.load(2L);

    // Then
    assertThat(view.memberships()).containsExactly(new Membership(7L, "OWNER"));
  }

  @Test
  void load_throwsUserNotFound_whenUserUnknown() {
    // Given
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.load(2L)).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void updateDisplayName_trimsPersistsAndReturnsUpdatedView() {
    // Given
    when(users.findById(2L))
        .thenReturn(Optional.of(new AppUser(2L, "a@x.de", "Alt", true, PlatformRole.USER)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
    when(memberships.findByUserId(2L)).thenReturn(List.of());

    // When
    MeService.MeView view = service.updateDisplayName(2L, "  Neuer Name  ");

    // Then
    assertThat(view.displayName()).isEqualTo("Neuer Name");
    assertThat(view.email()).isEqualTo("a@x.de");
    ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().displayName()).isEqualTo("Neuer Name");
  }

  @Test
  void updateDisplayName_throwsUserNotFound_whenUserUnknown() {
    // Given
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.updateDisplayName(2L, "Neu"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void load_returnsColorSchemeOfAccount() {
    // Given
    when(users.findById(2L))
        .thenReturn(
            Optional.of(
                new AppUser(2L, "a@x.de", "Ada", true, PlatformRole.USER)
                    .withColorScheme(ColorScheme.LIGHT)));
    when(memberships.findByUserId(2L)).thenReturn(List.of());

    // When
    MeService.MeView view = service.load(2L);

    // Then
    assertThat(view.colorScheme()).isEqualTo(ColorScheme.LIGHT);
  }

  @Test
  void updateColorScheme_persistsAndReturnsUpdatedView() {
    // Given
    when(users.findById(2L))
        .thenReturn(Optional.of(new AppUser(2L, "a@x.de", "Ada", true, PlatformRole.USER)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
    when(memberships.findByUserId(2L)).thenReturn(List.of());

    // When
    MeService.MeView view = service.updateColorScheme(2L, ColorScheme.DARK);

    // Then
    assertThat(view.colorScheme()).isEqualTo(ColorScheme.DARK);
    assertThat(view.displayName()).isEqualTo("Ada");
    ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().colorScheme()).isEqualTo(ColorScheme.DARK);
  }

  @Test
  void updateColorScheme_throwsUserNotFound_whenUserUnknown() {
    // Given
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.updateColorScheme(2L, ColorScheme.DARK))
        .isInstanceOf(UserNotFoundException.class);
  }

  // ------------------------------------------------- Vorschau „Ansicht als XY“ (Issue #1921, E14)

  @Test
  void load_outsideVorschau_hasNoVorschau() {
    // Given
    when(users.findById(2L))
        .thenReturn(Optional.of(new AppUser(2L, "a@x.de", "Ada", true, PlatformRole.USER)));
    when(memberships.findByUserId(2L)).thenReturn(List.of());

    // When
    MeService.MeView view = service.load(2L);

    // Then
    assertThat(view.vorschau()).isNull();
  }

  @Test
  void load_inVorschau_describesTargetAndNamesVorschau() {
    // Given: Der Filter hat XY (7) zum Principal gemacht; Admin ist 1.
    kontext = Optional.of(new VorschauKontext("kennung-a", 1L, 7L, ABLAUF));
    when(users.findById(7L))
        .thenReturn(
            Optional.of(
                new AppUser(7L, "xy@x.de", "Xaver", true, PlatformRole.USER)
                    .withColorScheme(ColorScheme.LIGHT)));
    when(users.findById(1L))
        .thenReturn(
            Optional.of(
                new AppUser(1L, "admin@x.de", "Ada Admin", true, PlatformRole.ADMIN)
                    .withColorScheme(ColorScheme.DARK)));
    when(memberships.findByUserId(7L)).thenReturn(List.of(new Membership(3L, "VIEWER")));

    // When
    MeService.MeView view = service.load(7L);

    // Then
    assertThat(view.userId()).isEqualTo(7L);
    assertThat(view.email()).isEqualTo("xy@x.de");
    assertThat(view.platformRole()).isEqualTo(PlatformRole.USER);
    assertThat(view.memberships()).containsExactly(new Membership(3L, "VIEWER"));
    assertThat(view.vorschau())
        .isEqualTo(new MeService.VorschauView("kennung-a", "Xaver", "Ada Admin", ABLAUF));
  }

  @Test
  void load_inVorschau_deliversColorSchemeOfAdminNotOfTarget() {
    // Given
    kontext = Optional.of(new VorschauKontext("kennung-a", 1L, 7L, ABLAUF));
    when(users.findById(7L))
        .thenReturn(
            Optional.of(
                new AppUser(7L, "xy@x.de", "Xaver", true, PlatformRole.USER)
                    .withColorScheme(ColorScheme.LIGHT)));
    when(users.findById(1L))
        .thenReturn(
            Optional.of(
                new AppUser(1L, "admin@x.de", "Ada Admin", true, PlatformRole.ADMIN)
                    .withColorScheme(ColorScheme.DARK)));
    when(memberships.findByUserId(7L)).thenReturn(List.of());

    // When
    MeService.MeView view = service.load(7L);

    // Then
    assertThat(view.colorScheme()).isEqualTo(ColorScheme.DARK);
  }

  @Test
  void load_inVorschau_throwsWhenAdminAccountVanished() {
    // Given
    kontext = Optional.of(new VorschauKontext("kennung-a", 1L, 7L, ABLAUF));
    when(users.findById(7L))
        .thenReturn(Optional.of(new AppUser(7L, "xy@x.de", "Xaver", true, PlatformRole.USER)));
    when(users.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.load(7L)).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void load_withKontextOfOtherPrincipal_hasNoVorschau() {
    // Given: Ein Kontext, dessen Ziel nicht das geladene Konto ist, beschreibt es nicht.
    kontext = Optional.of(new VorschauKontext("kennung-a", 1L, 7L, ABLAUF));
    when(users.findById(2L))
        .thenReturn(
            Optional.of(
                new AppUser(2L, "a@x.de", "Ada", true, PlatformRole.USER)
                    .withColorScheme(ColorScheme.LIGHT)));
    when(memberships.findByUserId(2L)).thenReturn(List.of());

    // When
    MeService.MeView view = service.load(2L);

    // Then
    assertThat(view.vorschau()).isNull();
    assertThat(view.colorScheme()).isEqualTo(ColorScheme.LIGHT);
  }
}
