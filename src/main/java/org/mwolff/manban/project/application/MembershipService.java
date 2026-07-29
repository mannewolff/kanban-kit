package org.mwolff.manban.project.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.UserDisplayNameWriter;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mitgliederverwaltung geteilter Projekte: Einladen (Token per E-Mail), Annehmen, Rolle ändern und
 * Entfernen. Rechteprüfung über den {@link PermissionChecker} (MEMBER_INVITE bzw. MEMBER_REMOVE).
 * Der letzte OWNER ist geschützt.
 */
@Service
public class MembershipService {

  private final ProjectRepository projects;
  private final ProjectMembershipRepository memberships;
  private final ProjectInvitationRepository invitations;
  private final PermissionChecker permissions;
  private final InvitationMailer mailer;
  private final UserLookup users;
  private final UserDisplayNameWriter displayNames;
  private final AuthProperties authProperties;
  private final ProjectProperties projectProperties;
  private final Clock clock;

  public MembershipService(
      ProjectRepository projects,
      ProjectMembershipRepository memberships,
      ProjectInvitationRepository invitations,
      PermissionChecker permissions,
      InvitationMailer mailer,
      UserLookup users,
      UserDisplayNameWriter displayNames,
      AuthProperties authProperties,
      ProjectProperties projectProperties,
      Clock clock) {
    this.projects = projects;
    this.memberships = memberships;
    this.invitations = invitations;
    this.permissions = permissions;
    this.mailer = mailer;
    this.users = users;
    this.displayNames = displayNames;
    this.authProperties = authProperties;
    this.projectProperties = projectProperties;
    this.clock = clock;
  }

  /**
   * Ordnet eine E-Mail einem Projekt zu. Gehört sie zu einem bereits registrierten und
   * freigegebenen Nutzer, wird dieser <strong>sofort</strong> Mitglied (idempotent; bestehende
   * Mitgliedschaft wird auf die neue Rolle aktualisiert) und nur per Info-Mail benachrichtigt.
   * Nicht freigegebene Nutzer werden abgelehnt ({@link MemberNotApprovedException}). Unbekannte
   * E-Mails durchlaufen den Einladungs-/Token-Pfad.
   *
   * <p><strong>Fehlersemantik seit Issue #502:</strong> Ein HTTP-Erfolg bestätigt die persistierte
   * Mitgliedschaft bzw. Einladung, <em>nicht</em> die Zustellung der Mail — die wird in derselben
   * Transaktion in der Outbox vorgemerkt und nach dem Commit mit Wiederholungen versandt. Der
   * frühere 502-Pfad bei gescheiterter Einladungs-Mail ist bewusst entfallen: Sein Zweck war, keine
   * Invitation ohne versandten Link zu hinterlassen; mit der Outbox holt der Worker den Versand
   * nach, statt dass die Einladung verworfen wird. Eine endgültig gescheiterte Zustellung bleibt
   * als FAILED-Eintrag in der Outbox sichtbar (siehe Betriebs-Doku); ein erneutes Einladen erzeugt
   * dann ein frisches Token samt neuer Mail.
   *
   * @return {@link InviteOutcome#ADDED} bei direkter Mitgliedschaft, sonst {@link
   *     InviteOutcome#INVITED}
   */
  @Transactional
  public InviteOutcome invite(long inviterUserId, long projectId, String email, ProjectRole role) {
    permissions.require(inviterUserId, projectId, Permission.MEMBER_INVITE);
    Project project = projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

    @Nullable UserSummary existing = users.findByEmail(normalizedEmail).orElse(null);
    if (existing != null) {
      if (!existing.approved()) {
        throw new MemberNotApprovedException(normalizedEmail);
      }
      addOrUpdateMembership(projectId, existing.id(), role);
      // Die Info-Mail wird in der Outbox vorgemerkt und erst nach dem Commit versandt — ein
      // Versandfehler kann die gespeicherte Mitgliedschaft strukturell nicht mehr berühren.
      mailer.sendProjectAssignedEmail(normalizedEmail, project.name(), role, projectUrl(projectId));
      return InviteOutcome.ADDED;
    }

    String plaintext = SecureTokens.newToken();
    invitations.save(
        new ProjectInvitation(
            null,
            projectId,
            normalizedEmail,
            role,
            SecureTokens.sha256Hex(plaintext),
            clock.instant().plus(projectProperties.invitationTtl()),
            null,
            inviterUserId));

    String url = authProperties.baseUrl() + "/invitations/accept?token=" + plaintext;
    // Vormerkung in derselben Transaktion: Rollt die Invitation zurück, verschwindet die Mail mit;
    // committet sie, stellt der Outbox-Worker mit Wiederholungen zu (Fehlersemantik siehe Javadoc).
    mailer.sendInvitationEmail(normalizedEmail, project.name(), url);
    return InviteOutcome.INVITED;
  }

  /**
   * Macht den Benutzer zum Mitglied des Projekts — idempotent: Eine bestehende Mitgliedschaft wird
   * auf die übergebene Rolle aktualisiert.
   *
   * <p>Diese Aktualisierung ist ein vollwertiger rollenändernder Pfad und unterliegt daher
   * demselben Aussperr-Schutz wie {@link #changeRole}: Ohne ihn degradierte eine Einladung des
   * einzigen OWNER als VIEWER das Projekt still in die Ownerlosigkeit (Issue #498). Das Anlegen
   * einer <em>neuen</em> Mitgliedschaft braucht keinen Schutz — es kann die Owner-Menge nur
   * vergrößern.
   */
  private void addOrUpdateMembership(long projectId, long userId, ProjectRole role) {
    @Nullable ProjectMembership current =
        memberships.findByProjectIdAndUserId(projectId, userId).orElse(null);
    if (current == null) {
      memberships.save(new ProjectMembership(null, projectId, userId, role, clock.instant()));
      return;
    }
    if (role != ProjectRole.OWNER) {
      requireOwnerRemains(memberships.lockOwnerUserIds(projectId), userId);
    }
    memberships.save(current.withRole(role));
  }

  private String projectUrl(long projectId) {
    return authProperties.baseUrl() + "/projects/" + projectId;
  }

  @Transactional
  public MemberView accept(long acceptingUserId, String plaintextToken) {
    Instant now = clock.instant();
    ProjectInvitation invitation =
        invitations
            .findByTokenHash(SecureTokens.sha256Hex(plaintextToken))
            .orElseThrow(InvalidInvitationException::new);
    if (invitation.isAccepted() || invitation.isExpired(now)) {
      throw new InvalidInvitationException();
    }

    UserSummary user = users.findById(acceptingUserId).orElseThrow(InvalidInvitationException::new);
    if (!user.email().equalsIgnoreCase(invitation.email())) {
      throw new InvitationEmailMismatchException();
    }

    ProjectMembership membership =
        memberships
            .findByProjectIdAndUserId(invitation.projectId(), acceptingUserId)
            .orElseGet(
                () ->
                    memberships.save(
                        new ProjectMembership(
                            null,
                            invitation.projectId(),
                            acceptingUserId,
                            invitation.role(),
                            now)));

    invitations.save(invitation.markAccepted(now));
    return toView(membership);
  }

  @Transactional(readOnly = true)
  public List<MemberView> listMembers(long userId, long projectId) {
    // Jedes Mitglied darf die Mitgliederliste sehen; Nichtmitglieder erhalten 404.
    if (memberships.findByProjectIdAndUserId(projectId, userId).isEmpty()) {
      throw new ProjectNotFoundException();
    }
    return memberships.findByProjectId(projectId).stream().map(this::toView).toList();
  }

  /**
   * Setzt die Projekt-Rolle eines Mitglieds. Der letzte OWNER kann nicht degradiert werden — auch
   * dann nicht, wenn zwei Owner das gleichzeitig füreinander versuchen (Issue #498, siehe {@link
   * #requireOwnerRemains}).
   */
  @Transactional
  public MemberView changeRole(
      long actorUserId, long projectId, long targetUserId, ProjectRole newRole) {
    permissions.require(actorUserId, projectId, Permission.MEMBER_REMOVE);
    List<Long> owners = memberships.lockOwnerUserIds(projectId);
    ProjectMembership target =
        memberships
            .findByProjectIdAndUserId(projectId, targetUserId)
            .orElseThrow(MemberNotFoundException::new);

    if (newRole != ProjectRole.OWNER) {
      requireOwnerRemains(owners, targetUserId);
    }
    return toView(memberships.save(target.withRole(newRole)));
  }

  /**
   * Ändert den Anzeigenamen eines Projekt-Mitglieds. Recht {@link Permission#MEMBER_REMOVE} (wie
   * Rolle ändern). <strong>Achtung:</strong> Es gibt kein projektspezifisches Namensfeld — dies
   * ändert den <strong>globalen</strong> Benutzernamen projektübergreifend.
   *
   * <p>Die Rechteprüfung liegt vollständig hier: Der {@link UserDisplayNameWriter} prüft bewusst
   * nichts (Issue #460) — er wird erst nach {@code permissions.require(...)} aufgerufen.
   */
  @Transactional
  public MemberView changeMemberDisplayName(
      long actorUserId, long projectId, long targetUserId, String displayName) {
    permissions.require(actorUserId, projectId, Permission.MEMBER_REMOVE);
    ProjectMembership target =
        memberships
            .findByProjectIdAndUserId(projectId, targetUserId)
            .orElseThrow(MemberNotFoundException::new);
    // Ein unbekannter Benutzer ist hier ein fehlendes Mitglied (404). Das leere Optional des
    // Schreib-Ports liefert diese Unterscheidung ohne zweiten Lookup (#472) — vorher schlug erst
    // dieser Aufrufer und dann der Port denselben Benutzer nach.
    UserSummary saved =
        displayNames
            .updateDisplayName(targetUserId, displayName)
            .orElseThrow(MemberNotFoundException::new);
    return new MemberView(saved.id(), saved.email(), saved.displayName(), target.role());
  }

  /**
   * Überträgt die Projekt-Eigentümerschaft atomar an ein bestehendes Mitglied: Das Ziel wird OWNER,
   * der aufrufende (bisherige) Owner wird ADMIN. Nur der amtierende OWNER (Recht {@link
   * Permission#PROJECT_OWNER_TRANSFER}) darf übertragen — bewusst nicht ADMIN. Ist das Ziel bereits
   * OWNER, ist der Aufruf ein No-Op. Etwaige weitere (Alt-)Owner bleiben unangetastet.
   *
   * <p><strong>Warum hier kein Aussperr-Schutz steht (Issue #498):</strong> Der Transfer ist
   * bestandserhaltend — er befördert genau eine Nicht-OWNER-Zeile (+1) und degradiert höchstens den
   * Aufrufer (−1). Auch der Transfer des <em>einzigen</em> Owners ist deshalb zulässig: Das Projekt
   * hat danach wieder genau einen Owner, nur einen anderen. Eine pauschale Ablehnung würde den
   * Regelfall der Übergabe verbieten.
   *
   * <p>Die Sperre braucht der Transfer trotzdem: Ohne sie könnte ein gleichzeitiges {@code
   * removeMember} auf das frisch beförderte Ziel zugreifen, während dieser Aufruf den Alt-Owner
   * degradiert — beide Prüfungen sähen einen Owner zu viel, und das Projekt bliebe ownerlos zurück.
   */
  @Transactional
  public void transferOwnership(long callerUserId, long projectId, long newOwnerUserId) {
    permissions.require(callerUserId, projectId, Permission.PROJECT_OWNER_TRANSFER);
    List<Long> owners = memberships.lockOwnerUserIds(projectId);
    ProjectMembership target =
        memberships
            .findByProjectIdAndUserId(projectId, newOwnerUserId)
            .orElseThrow(MemberNotFoundException::new);
    if (owners.contains(newOwnerUserId)) {
      return;
    }
    memberships.save(target.withRole(ProjectRole.OWNER));
    // Den bisherigen Owner (Aufrufer) herabstufen — nur wenn er ein echtes OWNER-Mitglied ist
    // (ein Plattform-Admin ohne Mitgliedschaft hat keine Rolle im Projekt).
    memberships
        .findByProjectIdAndUserId(projectId, callerUserId)
        .filter(m -> owners.contains(m.userId()))
        .ifPresent(m -> memberships.save(m.withRole(ProjectRole.ADMIN)));
  }

  /**
   * Entfernt ein Mitglied. Der letzte OWNER kann nicht entfernt werden — auch nicht bei
   * gleichzeitigen Aufrufen (Issue #498, siehe {@link #requireOwnerRemains}).
   */
  @Transactional
  public void removeMember(long actorUserId, long projectId, long targetUserId) {
    permissions.require(actorUserId, projectId, Permission.MEMBER_REMOVE);
    List<Long> owners = memberships.lockOwnerUserIds(projectId);
    ProjectMembership target =
        memberships
            .findByProjectIdAndUserId(projectId, targetUserId)
            .orElseThrow(MemberNotFoundException::new);

    requireOwnerRemains(owners, targetUserId);
    memberships.deleteById(target.requireId());
  }

  /**
   * Wirft {@link LastOwnerException}, wenn der Benutzer der letzte OWNER des Projekts ist.
   *
   * <p>{@code ownerUserIds} muss aus {@link ProjectMembershipRepository#lockOwnerUserIds} stammen —
   * dort liegt die Begründung, warum das Lesen der Owner-Menge die Mitgliedschaften des Projekts
   * sperrt. Nur mit dieser Sperre ist die Prüfung mehr als eine Momentaufnahme: Sie gilt bis zum
   * Commit. Die Rolle des Ziels wird bewusst <em>nicht</em> zusätzlich geprüft — die gesperrte
   * Owner-Menge ist die verlässliche Auskunft darüber, wer OWNER ist.
   */
  private static void requireOwnerRemains(List<Long> ownerUserIds, long targetUserId) {
    if (ownerUserIds.size() == 1 && ownerUserIds.get(0) == targetUserId) {
      throw new LastOwnerException();
    }
  }

  private MemberView toView(ProjectMembership m) {
    return users
        .findById(m.userId())
        .map(u -> new MemberView(u.id(), u.email(), u.displayName(), m.role()))
        .orElseGet(() -> new MemberView(m.userId(), null, null, m.role()));
  }

  /** Mitgliederdarstellung. */
  public record MemberView(
      Long userId, @Nullable String email, @Nullable String displayName, ProjectRole role) {}
}
