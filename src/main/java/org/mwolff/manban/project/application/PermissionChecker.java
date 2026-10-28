package org.mwolff.manban.project.application;

import java.time.Clock;
import java.util.Optional;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.auth.application.VorschauKontextLeser;
import org.mwolff.manban.auth.application.VorschauVerborgenException;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zentrale Rechteprüfung: Hat der Benutzer das Recht X im Projekt Y? Grundlage ist die
 * Projekt-Mitgliedschaft (Rolle) plus die feste Rollen-Rechte-Matrix.
 *
 * <p>Ein <b>Plattform-Admin</b> ({@link org.mwolff.manban.auth.domain.PlatformRole#ADMIN}) ist
 * Super-User: er passiert jede Prüfung, auch ohne Mitgliedschaft im Projekt. Für Nicht-Admins gilt:
 * alle projekt-/board-/karten-verändernden Use-Cases rufen {@link #require} auf. Nichtmitglieder
 * erhalten 404 (kein Existenz-Leak), Mitglieder ohne das Recht 403.
 */
@Component
public class PermissionChecker {

  private final ProjectMembershipRepository memberships;
  private final RolePermissionRepository rolePermissions;
  private final PlatformAdminChecker platformAdminChecker;
  private final ProjectRepository projects;
  private final Clock clock;
  private final VorschauKontextLeser vorschauKontext;

  /** Ohne Vorschau „Ansicht als XY“: Jede Anfrage gilt als eigene Sicht des Principals. */
  public PermissionChecker(
      ProjectMembershipRepository memberships,
      RolePermissionRepository rolePermissions,
      PlatformAdminChecker platformAdminChecker,
      ProjectRepository projects,
      Clock clock) {
    this(memberships, rolePermissions, platformAdminChecker, projects, clock, Optional::empty);
  }

  @Autowired
  public PermissionChecker(
      ProjectMembershipRepository memberships,
      RolePermissionRepository rolePermissions,
      PlatformAdminChecker platformAdminChecker,
      ProjectRepository projects,
      Clock clock,
      VorschauKontextLeser vorschauKontext) {
    this.memberships = memberships;
    this.rolePermissions = rolePermissions;
    this.platformAdminChecker = platformAdminChecker;
    this.projects = projects;
    this.clock = clock;
    this.vorschauKontext = vorschauKontext;
  }

  /** Ob der Benutzer plattformweit Admin (Super-User) ist. */
  @Transactional(readOnly = true)
  public boolean isPlatformAdmin(long userId) {
    return platformAdminChecker.isPlatformAdmin(userId);
  }

  /**
   * Prüft (ohne zu werfen), ob der Benutzer das Recht im Projekt hat. Ein Plattform-Admin hat immer
   * Zugriff; ansonsten entscheidet die Projekt-Mitgliedschaft plus Rollen-Rechte-Matrix.
   * Nichtmitglieder erhalten {@code false} (kein Werfen, kein Existenz-Leak) — der Aufrufer
   * entscheidet über die Reaktion (z. B. 403 statt 404 bei der Token-Bindung).
   */
  @Transactional(readOnly = true)
  public boolean hasPermission(long userId, long projectId, Permission permission) {
    return platformAdminChecker.isPlatformAdmin(userId)
        || memberships
            .findByProjectIdAndUserId(projectId, userId)
            .map(m -> rolePermissions.isGranted(m.role(), permission))
            .orElse(false);
  }

  /**
   * Ob der Benutzer <em>echtes</em> Mitglied des Projekts ist — reine Mitgliedschaftsprüfung ohne
   * Rechte-Matrix und bewusst <b>ohne</b> Plattform-Admin-Bypass. Gedacht für Prüfungen über eine
   * dritte Person (z. B. „darf dieser Benutzer Zuständiger einer Karte sein?"), nicht für den
   * Zugriff des Aufrufers — dafür {@link #requireMembership}, das den Super-User passieren lässt.
   */
  @Transactional(readOnly = true)
  public boolean isRealProjectMember(long userId, long projectId) {
    return memberships.findByProjectIdAndUserId(projectId, userId).isPresent();
  }

  /**
   * Stellt sicher, dass der Benutzer das Recht im Projekt hat.
   *
   * @return die Mitgliedschaft des Benutzers (u. a. für die Rolle in Responses); für einen
   *     Plattform-Admin ohne echte Mitgliedschaft eine synthetische OWNER-Rolle
   * @throws ProjectNotFoundException wenn der Benutzer kein Mitglied und kein Admin ist (404)
   * @throws ProjectAccessDeniedException wenn die Rolle das Recht nicht umfasst (403)
   */
  @Transactional(readOnly = true)
  public ProjectMembership require(long userId, long projectId, Permission permission) {
    Optional<ProjectMembership> membership =
        memberships.findByProjectIdAndUserId(projectId, userId);
    if (platformAdminChecker.isPlatformAdmin(userId)) {
      return membership.orElseGet(() -> adminMembership(projectId, userId));
    }
    ProjectMembership m = membership.orElseThrow(ProjectNotFoundException::new);
    if (!rolePermissions.isGranted(m.role(), permission)) {
      throw new ProjectAccessDeniedException();
    }
    return m;
  }

  /**
   * Stellt sicher, dass der Benutzer im Projekt die Rolle OWNER hat (oder Plattform-Admin ist).
   * Nichtmitglied → 404 (kein Existenz-Leak); Mitglied ohne OWNER-Rolle → 403.
   */
  @Transactional(readOnly = true)
  public void requireOwner(long userId, long projectId) {
    if (platformAdminChecker.isPlatformAdmin(userId)) {
      return;
    }
    ProjectMembership m =
        memberships
            .findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(ProjectNotFoundException::new);
    if (m.role() != ProjectRole.OWNER) {
      throw new ProjectAccessDeniedException();
    }
  }

  /**
   * Stellt nur die Projekt-Mitgliedschaft sicher (für Lesezugriffe, die keiner speziellen
   * Berechtigung bedürfen). Nichtmitglied → 404 (kein Existenz-Leak); ein Plattform-Admin passiert
   * immer.
   */
  @Transactional(readOnly = true)
  public ProjectMembership requireMembership(long userId, long projectId) {
    Optional<ProjectMembership> membership =
        memberships.findByProjectIdAndUserId(projectId, userId);
    if (platformAdminChecker.isPlatformAdmin(userId)) {
      return membership.orElseGet(() -> adminMembership(projectId, userId));
    }
    return membership.orElseThrow(ProjectNotFoundException::new);
  }

  /**
   * Stellt sicher, dass der Benutzer die Nachtlauf-Auswertung des Projekts lesen darf (Issue #1079,
   * fachliche Quelle #1064, Frage 9).
   *
   * <p>Der echte Projekt-{@code OWNER} darf immer. Ein <b>Plattform-Admin ohne</b> echte
   * OWNER-Rolle darf nur, wenn das Projekt am Plattform-Leitstand <b>teilnimmt</b> — die Teilnahme
   * ist genau diese Einwilligung. Eine Mitgliedschaft unterhalb von OWNER trägt den
   * Nachtlauf-Zugriff auch sonst nicht; der Admin-Status macht daraus keinen Owner.
   *
   * <p><b>403 und nicht 404, wenn die Teilnahme fehlt.</b> Der Plattform-Admin sieht das Board des
   * Projekts ohnehin — 404 wäre eine Lüge über die Existenz, die er längst kennt. Wichtiger noch:
   * {@code frontend/src/lib/useLeitstandDaten.ts} macht in {@code useLadenWenn} aus 403 den stillen
   * Zustand {@code ohneRecht} und aus jedem anderen Fehler {@code fehler}. Bei 404 zeigte der
   * Board-Leitstand für „Letzter Lauf", „Abbruchgründe" und Verbrauch eine kaputte Anzeige statt
   * einer Aussage.
   *
   * <p>Die Teilnahme wird nur gelesen, wenn sie überhaupt entscheidet — ein echter OWNER kommt
   * durch, ohne dass das Projekt dafür geladen wird.
   *
   * @throws ProjectNotFoundException wenn der Benutzer weder Mitglied noch Plattform-Admin ist
   *     (404)
   * @throws ProjectAccessDeniedException wenn die Rolle nicht OWNER ist und die Teilnahme fehlt
   *     (403)
   * @throws VorschauVerborgenException wenn in der Vorschau der handelnde Admin selbst die
   *     Auswertung nicht sähe (403, Issue #1918)
   */
  @Transactional(readOnly = true)
  public void requireNightRunAccess(long userId, long projectId) {
    requireEigenerNachtlaufZugriff(userId, projectId);
    vorschauKontext
        .aktuell()
        .ifPresent(vorschau -> requireAdminSiehtNachtlauf(vorschau, projectId));
  }

  /** Die Regel aus {@link #requireNightRunAccess} für den Principal selbst. */
  private void requireEigenerNachtlaufZugriff(long userId, long projectId) {
    Optional<ProjectMembership> membership =
        memberships.findByProjectIdAndUserId(projectId, userId);
    if (istOwner(membership)) {
      return;
    }
    if (!platformAdminChecker.isPlatformAdmin(userId)) {
      // Ohne Admin-Status entscheidet allein die Mitgliedschaft — wie in requireOwner:
      // Nichtmitglied 404 (kein Existenz-Leak), Mitglied ohne OWNER-Rolle 403.
      if (membership.isEmpty()) {
        throw new ProjectNotFoundException();
      }
      throw new ProjectAccessDeniedException();
    }
    if (!nimmtTeil(projectId)) {
      throw new ProjectAccessDeniedException();
    }
  }

  /**
   * In der Vorschau „Ansicht als XY“ zeigt die Nachtlauf-Auswertung nie mehr, als der handelnde
   * Admin selbst sähe (Issue #1918, Plan #1914 E6): eigene OWNER-Rolle oder Teilnahme des Projekts.
   * Sonst gilt sie als verborgen — 403 mit dem Problem-Typ {@code vorschau-verborgen}.
   */
  private void requireAdminSiehtNachtlauf(VorschauKontext vorschau, long projectId) {
    if (istOwner(memberships.findByProjectIdAndUserId(projectId, vorschau.adminId()))) {
      return;
    }
    if (!nimmtTeil(projectId)) {
      throw new VorschauVerborgenException();
    }
  }

  private static boolean istOwner(Optional<ProjectMembership> membership) {
    return membership.map(m -> m.role() == ProjectRole.OWNER).orElse(false);
  }

  /** Ob das Projekt am Plattform-Leitstand teilnimmt; ein unbekanntes Projekt nimmt nicht teil. */
  private boolean nimmtTeil(long projectId) {
    return projects.findById(projectId).map(Project::dashboardParticipation).orElse(false);
  }

  /**
   * Stellt sicher, dass der Benutzer im Projekt eine der erlaubten Rollen hat — <b>als einzige
   * werfende Prüfung dieser Klasse ohne Plattform-Admin-Bypass</b> (ohne Wurf: {@link
   * #hatEchteRolle}).
   *
   * <p><strong>Das ist kein Versehen, sondern der Zweck (Issue #1077, AK 16).</strong> Überall
   * sonst ist der Plattform-Admin Super-User und passiert jede Prüfung. Beim Schalten der Teilnahme
   * am Plattform-Leitstand darf er das nicht: Die Teilnahme ist die Einwilligung des Projekts in
   * die Einsicht durch den Betreiber, und wer sie einholt, erteilt sie sich nicht selbst. Ein
   * Plattform-Admin ohne eigene Mitgliedschaft ist hier also ein Nichtmitglied wie jedes andere —
   * {@link PlatformAdminChecker} wird gar nicht erst befragt. Hat er im Projekt eine <em>echte</em>
   * Mitgliedschaft in einer erlaubten Rolle, darf er wie jedes andere Mitglied.
   *
   * <p>Auch die Rollen-Rechte-Matrix bleibt außen vor: Geprüft wird gegen die aufgezählten Rollen,
   * nicht gegen ein {@link Permission}. Ein Recht in der Matrix wäre eine Zeile, die für den
   * Plattform-Admin anders gelten müsste als für alle anderen — genau die Sonderregel, die diese
   * Methode vermeidet.
   *
   * @param erlaubt die zulässigen Rollen; leer bedeutet, dass keine Rolle genügt
   * @throws ProjectNotFoundException wenn der Benutzer keine echte Mitgliedschaft hat (404, kein
   *     Existenz-Leak)
   * @throws ProjectAccessDeniedException wenn die echte Rolle nicht aufgezählt ist (403)
   */
  @Transactional(readOnly = true)
  public void requireRealRole(long userId, long projectId, ProjectRole... erlaubt) {
    ProjectMembership m =
        memberships
            .findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(ProjectNotFoundException::new);
    for (ProjectRole rolle : erlaubt) {
      if (m.role() == rolle) {
        return;
      }
    }
    throw new ProjectAccessDeniedException();
  }

  /**
   * Ob der Benutzer im Projekt <em>echt</em> die Rolle hat — dieselbe Semantik wie {@link
   * #requireRealRole}, aber ohne zu werfen (Issue #1967). Ein Plattform-Admin ohne eigene
   * Mitgliedschaft ist auch hier ein Nichtmitglied; {@link PlatformAdminChecker} wird nicht
   * befragt. Gedacht für Antworten, die sagen, ob jemand etwas ändern darf, ohne ihm das Lesen zu
   * verwehren.
   */
  @Transactional(readOnly = true)
  public boolean hatEchteRolle(long userId, long projectId, ProjectRole rolle) {
    return memberships
        .findByProjectIdAndUserId(projectId, userId)
        .map(m -> m.role() == rolle)
        .orElse(false);
  }

  /**
   * {@link #hatEchteRolle} mit der Rolle OWNER — für Module außerhalb von {@code project}, die
   * {@link ProjectRole} nicht sehen dürfen ({@code ArchitectureTest.projectDomainIstModulintern}).
   */
  @Transactional(readOnly = true)
  public boolean istEchterOwner(long userId, long projectId) {
    return hatEchteRolle(userId, projectId, ProjectRole.OWNER);
  }

  /**
   * Synthetische Mitgliedschaft für einen Plattform-Admin ohne echte Mitgliedschaft (Vollzugriff).
   */
  private ProjectMembership adminMembership(long projectId, long userId) {
    return new ProjectMembership(null, projectId, userId, ProjectRole.OWNER, clock.instant());
  }
}
