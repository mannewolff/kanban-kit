package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/**
 * Die echte Rolle ohne Wurf (Issue #1967): {@code hatEchteRolle} und {@code istEchterOwner} — wie
 * {@code requireRealRole} ohne Plattform-Admin-Bypass, aber mit {@code false} statt Ausnahme.
 */
class PermissionCheckerEchteRolleTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private ProjectMembershipRepository memberships;
  private PlatformAdminChecker platformAdminChecker;
  private PermissionChecker checker;

  private static ProjectMembership membership(long projectId, long userId, ProjectRole role) {
    return new ProjectMembership(1L, projectId, userId, role, FIXED);
  }

  @BeforeEach
  void setUp() {
    memberships = mock(ProjectMembershipRepository.class);
    platformAdminChecker = mock(PlatformAdminChecker.class);
    checker =
        new PermissionChecker(
            memberships,
            mock(RolePermissionRepository.class),
            platformAdminChecker,
            mock(ProjectRepository.class),
            Clock.fixed(FIXED, ZoneOffset.UTC));
  }

  @ParameterizedTest
  @EnumSource(
      value = ProjectRole.class,
      names = {"OWNER", "ADMIN"})
  void requireEchterOwnerOderAdmin_laesstOwnerUndAdminPassieren(ProjectRole rolle) {
    when(memberships.findByProjectIdAndUserId(7L, 2L))
        .thenReturn(Optional.of(membership(7L, 2L, rolle)));

    assertThatCode(() -> checker.requireEchterOwnerOderAdmin(2L, 7L)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProjectRole.class,
      names = {"OWNER", "ADMIN"},
      mode = EnumSource.Mode.EXCLUDE)
  void requireEchterOwnerOderAdmin_weistUebrigeRollenAb(ProjectRole rolle) {
    when(memberships.findByProjectIdAndUserId(7L, 2L))
        .thenReturn(Optional.of(membership(7L, 2L, rolle)));

    assertThatThrownBy(() -> checker.requireEchterOwnerOderAdmin(2L, 7L))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  @Test
  void requireEchterOwnerOderAdmin_kenntKeinenPlattformAdminBypass() {
    when(platformAdminChecker.isPlatformAdmin(1L)).thenReturn(true);
    when(memberships.findByProjectIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> checker.requireEchterOwnerOderAdmin(1L, 7L))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void hatEchteRolle_true_forMatchingRealRole() {
    when(memberships.findByProjectIdAndUserId(7L, 2L))
        .thenReturn(Optional.of(membership(7L, 2L, ProjectRole.OWNER)));

    assertThat(checker.hatEchteRolle(2L, 7L, ProjectRole.OWNER)).isTrue();
  }

  @Test
  void hatEchteRolle_false_forOtherRole() {
    when(memberships.findByProjectIdAndUserId(7L, 2L))
        .thenReturn(Optional.of(membership(7L, 2L, ProjectRole.ADMIN)));

    assertThat(checker.hatEchteRolle(2L, 7L, ProjectRole.OWNER)).isFalse();
  }

  @Test
  void hatEchteRolle_false_forNonMember() {
    when(memberships.findByProjectIdAndUserId(7L, 2L)).thenReturn(Optional.empty());

    assertThat(checker.hatEchteRolle(2L, 7L, ProjectRole.OWNER)).isFalse();
  }

  /**
   * Wie {@code requireRealRole}: Der Admin-Status macht keinen Owner — ohne Mitgliedschaft false.
   */
  @Test
  void hatEchteRolle_false_forPlatformAdminWithoutMembership() {
    when(platformAdminChecker.isPlatformAdmin(1L)).thenReturn(true);
    when(memberships.findByProjectIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

    assertThat(checker.hatEchteRolle(1L, 7L, ProjectRole.OWNER)).isFalse();
  }

  @Test
  void istEchterOwner_true_nurFuerDieEchteOwnerRolle() {
    when(memberships.findByProjectIdAndUserId(7L, 2L))
        .thenReturn(Optional.of(membership(7L, 2L, ProjectRole.OWNER)));
    when(memberships.findByProjectIdAndUserId(7L, 3L))
        .thenReturn(Optional.of(membership(7L, 3L, ProjectRole.ADMIN)));

    assertThat(checker.istEchterOwner(2L, 7L)).isTrue();
    assertThat(checker.istEchterOwner(3L, 7L)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProjectRole.class,
      names = {"OWNER", "ADMIN"})
  void istEchterOwnerOderAdmin_true_fuerOwnerUndAdmin(ProjectRole rolle) {
    when(memberships.findByProjectIdAndUserId(7L, 2L))
        .thenReturn(Optional.of(membership(7L, 2L, rolle)));

    assertThat(checker.istEchterOwnerOderAdmin(2L, 7L)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProjectRole.class,
      names = {"OWNER", "ADMIN"},
      mode = EnumSource.Mode.EXCLUDE)
  void istEchterOwnerOderAdmin_false_fuerUebrigeRollen(ProjectRole rolle) {
    when(memberships.findByProjectIdAndUserId(7L, 2L))
        .thenReturn(Optional.of(membership(7L, 2L, rolle)));

    assertThat(checker.istEchterOwnerOderAdmin(2L, 7L)).isFalse();
  }

  @Test
  void istEchterOwnerOderAdmin_kenntKeinenPlattformAdminBypass() {
    when(platformAdminChecker.isPlatformAdmin(1L)).thenReturn(true);
    when(memberships.findByProjectIdAndUserId(7L, 1L)).thenReturn(Optional.empty());

    assertThat(checker.istEchterOwnerOderAdmin(1L, 7L)).isFalse();
  }
}
