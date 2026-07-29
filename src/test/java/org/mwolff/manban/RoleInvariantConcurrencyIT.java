package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminService;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.LastAdminException;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.LastOwnerException;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Weist gegen echtes PostgreSQL nach, dass die beiden Aussperr-Invarianten auch bei echter
 * Nebenläufigkeit halten (Issue #498):
 *
 * <ul>
 *   <li>Die Plattform behält mindestens einen Admin.
 *   <li>Jedes Projekt behält mindestens einen OWNER — über alle rollenändernden Pfade hinweg.
 * </ul>
 *
 * <p>Beides sind Bedingungen über eine <em>Zeilenmenge</em>, geprüft vor dem Schreiben. Ohne
 * Serialisierung sähen zwei gleichzeitige Degradierungen der letzten beiden Rolleninhaber beide
 * noch zwei — und danach gäbe es keinen mehr. {@link TransactionRace} erzwingt genau diese Lage
 * deterministisch: Der zweite Aufruf muss nachweislich auf einer Sperre warten, sonst schlägt der
 * Lauf fehl. Damit ist jeder Test hier zugleich die Gegenprobe — ohne die Sperre in {@code
 * lockPlatformAdminIds} bzw. {@code lockOwnerUserIds} liefe der zweite Aufruf ungehindert durch und
 * der Test bräche mit „wartet auf keiner Sperre" ab.
 *
 * <p>Die Kontext-Konfiguration ist bewusst identisch mit den übrigen {@code
 * WebEnvironment.NONE}-ITs (kein {@code @TestConfiguration}, kein Mock-Bean): Ein eigener
 * Spring-Kontext brächte einen weiteren Verbindungspool mit und sprengte die {@code
 * max_connections} des geteilten Postgres-Containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RoleInvariantConcurrencyIT extends AbstractIntegrationTest {

  @Autowired private AdminService adminService;
  @Autowired private MembershipService membershipService;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectRepository projects;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @Test
  void lastTwoPlatformAdmins_cannotDemoteEachOtherIntoNothingness() throws Exception {
    // Given: genau zwei Plattform-Admins (und ein einfacher Nutzer, damit die Zählung nachweislich
    // nur Admins erfasst).
    long adminA = saveUser("race-admin-a@example.com", PlatformRole.ADMIN);
    long adminB = saveUser("race-admin-b@example.com", PlatformRole.ADMIN);
    saveUser("race-plain@example.com", PlatformRole.USER);

    // When: beide degradieren einander gleichzeitig.
    TransactionRace.Result race =
        race(
            () -> adminService.changePlatformRole(adminA, adminB, PlatformRole.USER),
            () -> adminService.changePlatformRole(adminB, adminA, PlatformRole.USER));

    // Then: der zweite Aufruf sieht die bereits erfolgte Degradierung und lehnt ab.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(LastAdminException.class);
    assertThat(users.findByPlatformRole(PlatformRole.ADMIN))
        .singleElement()
        .extracting(AppUser::requireId)
        .isEqualTo(adminA);
  }

  @Test
  void lastTwoProjectOwners_cannotDemoteEachOtherIntoNothingness() throws Exception {
    // Given: ein Projekt mit genau zwei OWNERn und einem einfachen Mitglied.
    long ownerA = saveUser("race-owner-a@example.com", PlatformRole.USER);
    long ownerB = saveUser("race-owner-b@example.com", PlatformRole.USER);
    long projectId = saveProject(ownerA);
    saveMembership(projectId, ownerA, ProjectRole.OWNER);
    saveMembership(projectId, ownerB, ProjectRole.OWNER);
    saveMembership(
        projectId,
        saveUser("race-plain-member@example.com", PlatformRole.USER),
        ProjectRole.MEMBER);

    // When: beide degradieren einander gleichzeitig.
    TransactionRace.Result race =
        race(
            () -> membershipService.changeRole(ownerA, projectId, ownerB, ProjectRole.MEMBER),
            () -> membershipService.changeRole(ownerB, projectId, ownerA, ProjectRole.MEMBER));

    // Then
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(LastOwnerException.class);
    assertThat(ownerUserIds(projectId)).containsExactly(ownerA);
  }

  @Test
  void removingOneOwner_whileDemotingTheOther_leavesAnOwner() throws Exception {
    // Given: zwei OWNER — der eine wird entfernt, der andere gleichzeitig degradiert. Deckt das
    // Zusammenspiel zweier verschiedener Pfade ab (removeMember gegen changeRole).
    long ownerA = saveUser("race-remove-a@example.com", PlatformRole.USER);
    long ownerB = saveUser("race-remove-b@example.com", PlatformRole.USER);
    long projectId = saveProject(ownerA);
    saveMembership(projectId, ownerA, ProjectRole.OWNER);
    saveMembership(projectId, ownerB, ProjectRole.OWNER);

    // When
    TransactionRace.Result race =
        race(
            () -> membershipService.removeMember(ownerA, projectId, ownerB),
            () -> membershipService.changeRole(ownerB, projectId, ownerA, ProjectRole.MEMBER));

    // Then
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(LastOwnerException.class);
    assertThat(ownerUserIds(projectId)).containsExactly(ownerA);
  }

  @Test
  void ownershipTransfer_isNotUndoneByConcurrentRemovalOfTheNewOwner() throws Exception {
    // Given: ein Projekt mit einem einzigen OWNER und einem einfachen Mitglied. Der Transfer
    // befördert das Mitglied und degradiert den Alt-Owner — genau die Lücke, die ohne Sperre
    // aufginge: Ein gleichzeitiges removeMember auf das Mitglied sähe es noch als Nicht-Owner,
    // löschte es, und das Projekt bliebe ohne Owner zurück.
    long owner = saveUser("race-transfer-owner@example.com", PlatformRole.USER);
    long successor = saveUser("race-transfer-successor@example.com", PlatformRole.USER);
    long platformAdmin = saveUser("race-transfer-admin@example.com", PlatformRole.ADMIN);
    long projectId = saveProject(owner);
    saveMembership(projectId, owner, ProjectRole.OWNER);
    saveMembership(projectId, successor, ProjectRole.MEMBER);

    // When
    TransactionRace.Result race =
        race(
            () -> membershipService.transferOwnership(owner, projectId, successor),
            () -> membershipService.removeMember(platformAdmin, projectId, successor));

    // Then: der Transfer geht durch, das Entfernen erkennt den neuen (einzigen) Owner.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(LastOwnerException.class);
    assertThat(ownerUserIds(projectId)).containsExactly(successor);
    assertThat(memberships.findByProjectIdAndUserId(projectId, owner).orElseThrow().role())
        .isEqualTo(ProjectRole.ADMIN);
  }

  /** Kurzform für das gemeinsame Renn-Harness. */
  private TransactionRace.Result race(Runnable first, Runnable second) throws InterruptedException {
    return new TransactionRace(transactionManager, dataSource).run(first, second);
  }

  private long saveUser(String email, PlatformRole role) {
    return users.save(new AppUser(null, email, "hash", "U", true, role)).requireId();
  }

  private long saveProject(long ownerUserId) {
    return projects.save(new Project(null, "Rennen", ownerUserId, Instant.now())).requireId();
  }

  private void saveMembership(long projectId, long userId, ProjectRole role) {
    memberships.save(new ProjectMembership(null, projectId, userId, role, Instant.now()));
  }

  /** Owner des Projekts — als reine Leseabfrage, damit die Erwartung selbst keine Sperre nimmt. */
  private List<Long> ownerUserIds(long projectId) {
    return memberships.findByProjectId(projectId).stream()
        .filter(m -> m.role() == ProjectRole.OWNER)
        .map(ProjectMembership::userId)
        .toList();
  }
}
