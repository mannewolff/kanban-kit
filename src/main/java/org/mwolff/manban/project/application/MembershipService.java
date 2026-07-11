package org.mwolff.manban.project.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mitgliederverwaltung geteilter Projekte: Einladen (Token per E-Mail), Annehmen,
 * Rolle ändern und Entfernen. Rechteprüfung über den {@link PermissionChecker}
 * (MEMBER_INVITE bzw. MEMBER_REMOVE). Der letzte OWNER ist geschützt.
 */
@Service
public class MembershipService {

    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final ProjectInvitationRepository invitations;
    private final PermissionChecker permissions;
    private final InvitationMailer mailer;
    private final AppUserRepository users;
    private final AuthProperties authProperties;
    private final ProjectProperties projectProperties;
    private final Clock clock;

    public MembershipService(ProjectRepository projects, ProjectMembershipRepository memberships,
                             ProjectInvitationRepository invitations, PermissionChecker permissions,
                             InvitationMailer mailer, AppUserRepository users,
                             AuthProperties authProperties, ProjectProperties projectProperties, Clock clock) {
        this.projects = projects;
        this.memberships = memberships;
        this.invitations = invitations;
        this.permissions = permissions;
        this.mailer = mailer;
        this.users = users;
        this.authProperties = authProperties;
        this.projectProperties = projectProperties;
        this.clock = clock;
    }

    @Transactional
    public void invite(long inviterUserId, long projectId, String email, ProjectRole role) {
        permissions.require(inviterUserId, projectId, Permission.MEMBER_INVITE);
        Project project = projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);

        String plaintext = SecureTokens.newToken();
        invitations.save(new ProjectInvitation(
                null, projectId, email.trim().toLowerCase(), role, SecureTokens.sha256Hex(plaintext),
                clock.instant().plus(projectProperties.invitationTtl()), null, inviterUserId));

        String url = authProperties.baseUrl() + "/invitations/accept?token=" + plaintext;
        mailer.sendInvitationEmail(email.trim().toLowerCase(), project.name(), url);
    }

    @Transactional
    public MemberView accept(long acceptingUserId, String plaintextToken) {
        Instant now = clock.instant();
        ProjectInvitation invitation = invitations.findByTokenHash(SecureTokens.sha256Hex(plaintextToken))
                .orElseThrow(InvalidInvitationException::new);
        if (invitation.isAccepted() || invitation.isExpired(now)) {
            throw new InvalidInvitationException();
        }

        AppUser user = users.findById(acceptingUserId).orElseThrow(InvalidInvitationException::new);
        if (!user.email().equalsIgnoreCase(invitation.email())) {
            throw new InvitationEmailMismatchException();
        }

        ProjectMembership membership = memberships
                .findByProjectIdAndUserId(invitation.projectId(), acceptingUserId)
                .orElseGet(() -> memberships.save(new ProjectMembership(
                        null, invitation.projectId(), acceptingUserId, invitation.role(), now)));

        invitations.save(invitation.markAccepted(now));
        return toView(membership);
    }

    @Transactional(readOnly = true)
    public List<MemberView> listMembers(long userId, long projectId) {
        // Jedes Mitglied darf die Mitgliederliste sehen.
        memberships.findByProjectIdAndUserId(projectId, userId).orElseThrow(ProjectNotFoundException::new);
        return memberships.findByProjectId(projectId).stream().map(this::toView).toList();
    }

    @Transactional
    public MemberView changeRole(long actorUserId, long projectId, long targetUserId, ProjectRole newRole) {
        permissions.require(actorUserId, projectId, Permission.MEMBER_REMOVE);
        ProjectMembership target = memberships.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(MemberNotFoundException::new);

        if (target.role() == ProjectRole.OWNER && newRole != ProjectRole.OWNER && isLastOwner(projectId, targetUserId)) {
            throw new LastOwnerException();
        }
        return toView(memberships.save(target.withRole(newRole)));
    }

    @Transactional
    public void removeMember(long actorUserId, long projectId, long targetUserId) {
        permissions.require(actorUserId, projectId, Permission.MEMBER_REMOVE);
        ProjectMembership target = memberships.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(MemberNotFoundException::new);

        if (target.role() == ProjectRole.OWNER && isLastOwner(projectId, targetUserId)) {
            throw new LastOwnerException();
        }
        memberships.deleteById(target.id());
    }

    private boolean isLastOwner(long projectId, long targetUserId) {
        List<ProjectMembership> owners = memberships.findByProjectId(projectId).stream()
                .filter(m -> m.role() == ProjectRole.OWNER)
                .toList();
        return owners.size() == 1 && owners.get(0).userId() == targetUserId;
    }

    private MemberView toView(ProjectMembership m) {
        return users.findById(m.userId())
                .map(u -> new MemberView(u.id(), u.email(), u.displayName(), m.role()))
                .orElseGet(() -> new MemberView(m.userId(), null, null, m.role()));
    }

    /** Mitgliederdarstellung. */
    public record MemberView(Long userId, String email, String displayName, ProjectRole role) {
    }
}
