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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mitgliederverwaltung geteilter Projekte: Einladen (Token per E-Mail), Annehmen, Rolle ändern und
 * Entfernen. Rechteprüfung über den {@link PermissionChecker} (MEMBER_INVITE bzw. MEMBER_REMOVE).
 * Der letzte OWNER ist geschützt.
 */
// PMD.CouplingBetweenObjects: Schwellwert um eins überschritten (21), seit der breite
// AppUserRepository-Zugriff durch die getrennten auth-Ports UserLookup/UserDisplayNameWriter samt
// UserSummary ersetzt ist (Issue #460). Die Trennung von Lesen und Schreiben ist genau der Gewinn
// dieses Umbaus — sie hier wieder zu einem Kombi-Port zusammenzuziehen, nur um einen Zähler zu
// senken, wäre eine Verschlechterung. Die verbleibende Kopplung ist fachlich begründet
// (Projekte, Mitgliedschaften, Einladungen, Rechte, Mailversand, Benutzer) und kein
// God-Class-Smell.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
public class MembershipService {

  private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

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
      long userId = existing.id();
      @Nullable ProjectMembership current =
          memberships.findByProjectIdAndUserId(projectId, userId).orElse(null);
      if (current != null) {
        // Idempotent: bestehende Mitgliedschaft auf die neue Rolle aktualisieren.
        memberships.save(current.withRole(role));
      } else {
        memberships.save(new ProjectMembership(null, projectId, userId, role, clock.instant()));
      }
      // Info-Mail ist ein Nebeneffekt: Ein Versandfehler darf die bereits gespeicherte
      // Mitgliedschaft nicht zurückrollen — daher fangen und nur loggen (kein 500).
      try {
        mailer.sendProjectAssignedEmail(
            normalizedEmail, project.name(), role, projectUrl(projectId));
      } catch (MailException e) {
        log.warn(
            "Zuordnungs-Mail an {} fehlgeschlagen; Mitgliedschaft bleibt bestehen",
            normalizedEmail,
            e);
      }
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
    // Einladungs-Mail ist essenziell (der Link kommt nur per Mail): Bei Versandfehler klaren 502
    // melden statt generischem 500; die Transaktion rollt zurück, sodass keine unbrauchbare
    // Invitation ohne versandten Link zurückbleibt.
    try {
      mailer.sendInvitationEmail(normalizedEmail, project.name(), url);
    } catch (MailException e) {
      throw new MailDeliveryException(e);
    }
    return InviteOutcome.INVITED;
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

  @Transactional
  public MemberView changeRole(
      long actorUserId, long projectId, long targetUserId, ProjectRole newRole) {
    permissions.require(actorUserId, projectId, Permission.MEMBER_REMOVE);
    ProjectMembership target =
        memberships
            .findByProjectIdAndUserId(projectId, targetUserId)
            .orElseThrow(MemberNotFoundException::new);

    if (target.role() == ProjectRole.OWNER
        && newRole != ProjectRole.OWNER
        && isLastOwner(projectId, targetUserId)) {
      throw new LastOwnerException();
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
    // Erst nachschlagen: Ein unbekannter Benutzer ist hier ein fehlendes Mitglied (404), nicht die
    // auth-eigene UserNotFoundException des Schreib-Ports.
    UserSummary user = users.findById(targetUserId).orElseThrow(MemberNotFoundException::new);
    UserSummary saved = displayNames.updateDisplayName(user.id(), displayName);
    return new MemberView(saved.id(), saved.email(), saved.displayName(), target.role());
  }

  /**
   * Überträgt die Projekt-Eigentümerschaft atomar an ein bestehendes Mitglied: Das Ziel wird OWNER,
   * der aufrufende (bisherige) Owner wird ADMIN. Nur der amtierende OWNER (Recht {@link
   * Permission#PROJECT_OWNER_TRANSFER}) darf übertragen — bewusst nicht ADMIN. Ist das Ziel bereits
   * OWNER, ist der Aufruf ein No-Op. Etwaige weitere (Alt-)Owner bleiben unangetastet.
   */
  @Transactional
  public void transferOwnership(long callerUserId, long projectId, long newOwnerUserId) {
    permissions.require(callerUserId, projectId, Permission.PROJECT_OWNER_TRANSFER);
    ProjectMembership target =
        memberships
            .findByProjectIdAndUserId(projectId, newOwnerUserId)
            .orElseThrow(MemberNotFoundException::new);
    if (target.role() == ProjectRole.OWNER) {
      return;
    }
    memberships.save(target.withRole(ProjectRole.OWNER));
    // Den bisherigen Owner (Aufrufer) herabstufen — nur wenn er ein echtes OWNER-Mitglied ist
    // (ein Plattform-Admin ohne Mitgliedschaft hat keine Rolle im Projekt).
    memberships
        .findByProjectIdAndUserId(projectId, callerUserId)
        .filter(m -> m.role() == ProjectRole.OWNER)
        .ifPresent(m -> memberships.save(m.withRole(ProjectRole.ADMIN)));
  }

  @Transactional
  public void removeMember(long actorUserId, long projectId, long targetUserId) {
    permissions.require(actorUserId, projectId, Permission.MEMBER_REMOVE);
    ProjectMembership target =
        memberships
            .findByProjectIdAndUserId(projectId, targetUserId)
            .orElseThrow(MemberNotFoundException::new);

    if (target.role() == ProjectRole.OWNER && isLastOwner(projectId, targetUserId)) {
      throw new LastOwnerException();
    }
    memberships.deleteById(target.requireId());
  }

  private boolean isLastOwner(long projectId, long targetUserId) {
    List<ProjectMembership> owners =
        memberships.findByProjectId(projectId).stream()
            .filter(m -> m.role() == ProjectRole.OWNER)
            .toList();
    return owners.size() == 1 && owners.get(0).userId() == targetUserId;
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
