package org.mwolff.manban.accesstoken.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.KontoGesperrtEvent;
import org.mwolff.manban.auth.application.UserDisplayNameWriter;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.project.application.InvitationMailer;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.MitgliedRolleVerlorenEvent;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectInvitationRepository;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.application.ProjectProperties;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/** Der Widerruf nach Verlust von Rolle oder Konto (Issue #2104, Plan #2100 E4; Plan #2206 E3). */
class AppZugangWiderrufListenerTest {

  private static final long PROJEKT = 9L;
  private static final long MITGLIED = 2L;
  private static final long OWNER = 1L;

  private final AccessTokenRepository tokens = mock(AccessTokenRepository.class);
  private final AppZugangWiderrufListener listener = new AppZugangWiderrufListener(tokens);

  @Test
  void rollenverlust_widerruftNurDieZugaengeInDiesemProjekt() {
    // When
    listener.onRolleVerloren(new MitgliedRolleVerlorenEvent(PROJEKT, MITGLIED));

    // Then: erst die Ausgabesperre des Inhabers, dann der Sammelwiderruf (Issue #2122).
    InOrder reihenfolge = inOrder(tokens);
    reihenfolge.verify(tokens).sperreAppZugangAusgabe(MITGLIED);
    reihenfolge.verify(tokens).markAppZugaengeRevoked(MITGLIED, PROJEKT);
    verifyNoMoreInteractions(tokens);
  }

  @Test
  void kontosperre_widerruftAlleZugaengeDesInhabers() {
    // When
    listener.onKontoGesperrt(new KontoGesperrtEvent(MITGLIED));

    // Then
    InOrder reihenfolge = inOrder(tokens);
    reihenfolge.verify(tokens).sperreAppZugangAusgabe(MITGLIED);
    reihenfolge.verify(tokens).markAppZugaengeRevoked(MITGLIED);
    verifyNoMoreInteractions(tokens);
  }

  @Test
  void kundeWirdMitglied_widerruftDenZugang() {
    // Given
    MembershipService mitglieder = mitgliederdienstMitListener(ProjectRole.CUSTOMER);

    // When
    mitglieder.changeRole(OWNER, PROJEKT, MITGLIED, ProjectRole.MEMBER);

    // Then
    InOrder reihenfolge = inOrder(tokens);
    reihenfolge.verify(tokens).sperreAppZugangAusgabe(MITGLIED);
    reihenfolge.verify(tokens).markAppZugaengeRevoked(MITGLIED, PROJEKT);
    verifyNoMoreInteractions(tokens);
  }

  @Test
  void adminWirdKunde_behaeltDenZugang() {
    // Given
    MembershipService mitglieder = mitgliederdienstMitListener(ProjectRole.ADMIN);

    // When
    mitglieder.changeRole(OWNER, PROJEKT, MITGLIED, ProjectRole.CUSTOMER);

    // Then
    verifyNoInteractions(tokens);
  }

  /**
   * Ein echter {@link MembershipService}, dessen Ereignisse direkt beim Listener ankommen — so
   * prüft der Test die Regel, wann ein Rollenwechsel den Zugang kostet, über beide Module hinweg.
   */
  private MembershipService mitgliederdienstMitListener(ProjectRole bisherigeRolle) {
    ProjectMembershipRepository memberships = mock(ProjectMembershipRepository.class);
    Instant jetzt = Instant.parse("2026-11-22T10:00:00Z");
    when(memberships.lockOwnerUserIds(PROJEKT)).thenReturn(List.of(OWNER));
    when(memberships.findByProjectIdAndUserId(PROJEKT, MITGLIED))
        .thenReturn(
            Optional.of(new ProjectMembership(3L, PROJEKT, MITGLIED, bisherigeRolle, jetzt)));
    when(memberships.save(any(ProjectMembership.class))).thenAnswer(inv -> inv.getArgument(0));
    return new MembershipService(
        mock(ProjectRepository.class),
        memberships,
        mock(ProjectInvitationRepository.class),
        mock(PermissionChecker.class),
        mock(InvitationMailer.class),
        mock(UserLookup.class),
        mock(UserDisplayNameWriter.class),
        new AuthProperties("https://app.example", null, null, null, null),
        new ProjectProperties(Duration.ofDays(7)),
        Clock.fixed(jetzt, ZoneOffset.UTC),
        ereignis -> {
          if (ereignis instanceof MitgliedRolleVerlorenEvent verloren) {
            listener.onRolleVerloren(verloren);
          }
        });
  }
}
