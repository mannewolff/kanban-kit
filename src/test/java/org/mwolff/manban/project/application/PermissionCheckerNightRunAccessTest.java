package org.mwolff.manban.project.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/**
 * Wer die Nachtlauf-Auswertung eines Projekts lesen darf (Issue #1079, fachliche Quelle #1064,
 * Frage 9).
 *
 * <p>Bis hierher passierte ein Plattform-Admin {@link PermissionChecker#requireOwner} überall mit
 * (Plan #718, A6) und las Laufliste, Abbruchgründe, Anläufe und Verbrauch jedes Projekts. Die
 * fachliche Quelle zieht die Grenze enger: „beschränkt auf die Nachtlauf-Auswertungen
 * <em>teilnehmender</em> Projekte — die Teilnahme ist genau diese Einwilligung".
 *
 * <p>Eigene Klasse statt weiterer Tests in {@link PermissionCheckerTest}: Jene steht bei 26
 * Methoden, und die PMD-Schwelle {@code TooManyMethods} liegt bei 30.
 */
class PermissionCheckerNightRunAccessTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long ADMIN = 1L;
  private static final long USER = 2L;
  private static final long PROJECT = 7L;

  private ProjectMembershipRepository memberships;
  private PlatformAdminChecker platformAdminChecker;
  private ProjectRepository projects;
  private PermissionChecker checker;

  private static ProjectMembership membership(long userId, ProjectRole role) {
    return new ProjectMembership(1L, PROJECT, userId, role, FIXED);
  }

  private void mitgliedschaft(long userId, @org.jspecify.annotations.Nullable ProjectRole role) {
    when(memberships.findByProjectIdAndUserId(PROJECT, userId))
        .thenReturn(role == null ? Optional.empty() : Optional.of(membership(userId, role)));
  }

  private void teilnahme(boolean teilnehmend) {
    when(projects.findById(PROJECT))
        .thenReturn(Optional.of(new Project(PROJECT, "P", 9L, FIXED, null, teilnehmend)));
  }

  @BeforeEach
  void setUp() {
    memberships = mock(ProjectMembershipRepository.class);
    platformAdminChecker = mock(PlatformAdminChecker.class);
    projects = mock(ProjectRepository.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    checker =
        new PermissionChecker(
            memberships,
            mock(RolePermissionRepository.class),
            platformAdminChecker,
            projects,
            clock);
  }

  @Test
  void derEchteOwnerKommtDurch_ohneDassDieTeilnahmeGefragtWird() {
    mitgliedschaft(USER, ProjectRole.OWNER);

    assertThatCode(() -> checker.requireNightRunAccess(USER, PROJECT)).doesNotThrowAnyException();
    verify(projects, never()).findById(PROJECT);
  }

  /**
   * Keine Rolle unterhalb von OWNER liest die Auswertung — auch der Projekt-ADMIN nicht. Seit Issue
   * #1165 trägt die Matrix {@code NIGHT_RUN_READ}/{@code NIGHT_RUN_SUBMIT} als echte Schlüssel; sie
   * beschreiben genau diese Regel und ersetzen die Prüfung nicht. Deshalb steht hier jede der drei
   * Rollen einzeln: Ein Schlüssel, der versehentlich an VIEWER, MEMBER oder ADMIN gerät, dürfte an
   * dieser Prüfung nichts ändern.
   */
  @ParameterizedTest
  @EnumSource(
      value = ProjectRole.class,
      names = {"VIEWER", "MEMBER", "ADMIN"})
  void einMitgliedUnterhalbVonOwnerBekommt403(ProjectRole rolle) {
    mitgliedschaft(USER, rolle);

    assertThatThrownBy(() -> checker.requireNightRunAccess(USER, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  @Test
  void einNichtmitgliedOhneAdminrechteBekommt404() {
    mitgliedschaft(USER, null);

    assertThatThrownBy(() -> checker.requireNightRunAccess(USER, PROJECT))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void derPlattformAdminKommtAmTeilnehmendenProjektDurch() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    mitgliedschaft(ADMIN, null);
    teilnahme(true);

    assertThatCode(() -> checker.requireNightRunAccess(ADMIN, PROJECT)).doesNotThrowAnyException();
  }

  /**
   * Der Kern des Pakets. <b>403 und nicht 404</b>: Der Plattform-Admin sieht das Board ohnehin, und
   * {@code useLeitstandDaten} macht aus 403 den stillen Zustand „ohne Recht" — aus jedem anderen
   * Fehler eine kaputte Anzeige.
   */
  @Test
  void derPlattformAdminBekommtAmNichtTeilnehmendenProjekt403() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    mitgliedschaft(ADMIN, null);
    teilnahme(false);

    assertThatThrownBy(() -> checker.requireNightRunAccess(ADMIN, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  /** Ein unbekanntes Projekt nimmt nicht teil — der Weg endet wie beim abgehakten Projekt. */
  @Test
  void derPlattformAdminBekommtAmUnbekanntenProjekt403() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    mitgliedschaft(ADMIN, null);
    when(projects.findById(PROJECT)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> checker.requireNightRunAccess(ADMIN, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  /**
   * Eine Mitgliedschaft unterhalb von OWNER trägt den Nachtlauf-Zugriff auch heute nicht — der
   * Admin-Status macht daraus keinen Owner, sondern führt in die Teilnahme-Prüfung.
   */
  @Test
  void derPlattformAdminMitEchtemMemberBekommtAmNichtTeilnehmendenProjekt403() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    mitgliedschaft(ADMIN, ProjectRole.MEMBER);
    teilnahme(false);

    assertThatThrownBy(() -> checker.requireNightRunAccess(ADMIN, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  @Test
  void derPlattformAdminMitEchtemOwnerKommtAuchOhneTeilnahmeDurch() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    mitgliedschaft(ADMIN, ProjectRole.OWNER);

    assertThatCode(() -> checker.requireNightRunAccess(ADMIN, PROJECT)).doesNotThrowAnyException();
    verify(projects, never()).findById(PROJECT);
  }
}
