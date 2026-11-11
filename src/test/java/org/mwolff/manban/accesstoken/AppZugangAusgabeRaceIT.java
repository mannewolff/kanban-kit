package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TransactionRace;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.AccessTokenService.CreatedAccessToken;
import org.mwolff.manban.auth.application.AdminService;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Weist gegen echtes PostgreSQL nach, dass die Ausgabe eines Zugangs der App den endgültigen
 * Widerruf bei Rollenverlust oder Kontosperre nicht umgehen kann (Issue #2122).
 *
 * <p>Der Angriff: Ein noch angemeldeter Projekt-Admin legt einen Zugang an oder erneuert ihn,
 * während er herabgestuft oder gesperrt wird. Ohne gemeinsame Sperre prüft die Ausgabe die alte
 * Rolle, der Sammelwiderruf der Herabstufung sieht die noch nicht committete neue Zeile nicht, und
 * das Token überlebt — nach Rückgabe der Rolle oder Entsperrung wirkt es wieder.
 *
 * <p>{@link TransactionRace#run} erzwingt beide Reihenfolgen deterministisch: Der zweite Aufruf
 * muss nachweislich auf einer Sperre des ersten warten. Ohne die gemeinsame Sperre läuft er in der
 * Richtung „Entzug zuerst“ ungehindert durch und der Lauf endet mit „wartet auf keiner Sperre“; in
 * der Richtung „Ausgabe zuerst“ überlebt das neue Token und die Erwartung schlägt fehl.
 *
 * <p>Kontext wie die übrigen {@code WebEnvironment.NONE}-ITs, ohne eigene Testkonfiguration — ein
 * weiterer Kontext brächte einen weiteren Verbindungspool mit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AppZugangAusgabeRaceIT extends AbstractIntegrationTest {

  @Autowired private AccessTokenService accessTokens;
  @Autowired private MembershipService membershipService;
  @Autowired private AdminService adminService;
  @Autowired private ProjectService projectService;
  @Autowired private BoardService boardService;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  /** Ein Projekt mit Board, seiner Owner-Person, einem Admin und einem Plattform-Admin. */
  private record Lage(long projekt, long board, long owner, long admin, long plattformAdmin) {}

  // --- Rollenverlust ----------------------------------------------------------

  @Test
  void dieAnlageWartetAufDieHerabstufungUndWirdAbgewiesen() throws Exception {
    // Given
    Lage lage = lage();

    // When: die Herabstufung hält ihre Transaktion offen, die Anlage läuft hinein.
    TransactionRace.Result race =
        race(
            () -> herabstufen(lage),
            () -> accessTokens.appZugangAnlegen(lage.admin(), lage.projekt(), lage.board()));

    // Then: die Anlage sieht die neue Rolle, und nach der Rückgabe bleibt das Board ohne Zugang.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(ProjectAccessDeniedException.class);
    rolleZurueckgeben(lage);
    assertThat(wirksameZugaenge(lage.board())).isZero();
  }

  @Test
  void dieHerabstufungWartetAufDieAnlageUndWiderruftDenNeuenZugang() throws Exception {
    // Given
    Lage lage = lage();
    AtomicReference<CreatedAccessToken> neu = new AtomicReference<>();

    // When: die Anlage hat die Rolle geprüft und hält ihr neues Token uncommittet.
    TransactionRace.Result race =
        race(
            () ->
                neu.set(accessTokens.appZugangAnlegen(lage.admin(), lage.projekt(), lage.board())),
            () -> herabstufen(lage));

    // Then: der Sammelwiderruf erfasst auch das neue Token, über die Rückgabe der Rolle hinweg.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    rolleZurueckgeben(lage);
    assertThat(wirksameZugaenge(lage.board())).isZero();
    assertThat(accessTokens.resolveBinding(neu.get().plaintext())).isEmpty();
  }

  @Test
  void dieHerabstufungWartetAufDasErneuernUndWiderruftDenNeuenZugang() throws Exception {
    // Given
    Lage lage = lage();
    CreatedAccessToken alt =
        accessTokens.appZugangAnlegen(lage.admin(), lage.projekt(), lage.board());
    AtomicReference<CreatedAccessToken> neu = new AtomicReference<>();

    // When: das Erneuern hat den alten Zugang widerrufen und hält den neuen uncommittet.
    TransactionRace.Result race =
        race(
            () -> neu.set(accessTokens.appZugangErneuern(lage.admin(), alt.id())),
            () -> herabstufen(lage));

    // Then
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    rolleZurueckgeben(lage);
    assertThat(wirksameZugaenge(lage.board())).isZero();
    assertThat(accessTokens.resolveBinding(neu.get().plaintext())).isEmpty();
    assertThat(accessTokens.resolveBinding(alt.plaintext())).isEmpty();
  }

  // --- Kontosperre --------------------------------------------------------------

  @Test
  void dieAnlageWartetAufDieKontosperreUndWirdAbgewiesen() throws Exception {
    // Given
    Lage lage = lage();

    // When
    TransactionRace.Result race =
        race(
            () -> adminService.disable(lage.plattformAdmin(), lage.admin()),
            () -> accessTokens.appZugangAnlegen(lage.admin(), lage.projekt(), lage.board()));

    // Then: kein Zugang, auch nicht nach der Entsperrung.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(ProjectAccessDeniedException.class);
    adminService.enable(lage.plattformAdmin(), lage.admin());
    assertThat(wirksameZugaenge(lage.board())).isZero();
  }

  @Test
  void dieKontosperreWartetAufDasErneuernUndWiderruftDenNeuenZugang() throws Exception {
    // Given
    Lage lage = lage();
    CreatedAccessToken alt =
        accessTokens.appZugangAnlegen(lage.admin(), lage.projekt(), lage.board());
    AtomicReference<CreatedAccessToken> neu = new AtomicReference<>();

    // When
    TransactionRace.Result race =
        race(
            () -> neu.set(accessTokens.appZugangErneuern(lage.admin(), alt.id())),
            () -> adminService.disable(lage.plattformAdmin(), lage.admin()));

    // Then
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    adminService.enable(lage.plattformAdmin(), lage.admin());
    assertThat(wirksameZugaenge(lage.board())).isZero();
    assertThat(accessTokens.resolveBinding(neu.get().plaintext())).isEmpty();
  }

  // --- Fixtures -------------------------------------------------------------------

  private Lage lage() {
    long plattformAdmin = konto("plattform-admin@example.com", PlatformRole.ADMIN);
    long owner = konto("owner@example.com", PlatformRole.USER);
    long admin = konto("admin@example.com", PlatformRole.USER);
    long projekt = projectService.create(plattformAdmin, "Alpha", "owner@example.com").id();
    memberships.save(new ProjectMembership(null, projekt, admin, ProjectRole.ADMIN, Instant.now()));
    long board = boardService.createBoard(owner, projekt, "Entwicklung").id();
    return new Lage(projekt, board, owner, admin, plattformAdmin);
  }

  private long konto(String email, PlatformRole rolle) {
    return users.save(new AppUser(null, email, email, true, rolle)).requireId();
  }

  private void herabstufen(Lage lage) {
    membershipService.changeRole(lage.owner(), lage.projekt(), lage.admin(), ProjectRole.MEMBER);
  }

  private void rolleZurueckgeben(Lage lage) {
    membershipService.changeRole(lage.owner(), lage.projekt(), lage.admin(), ProjectRole.ADMIN);
  }

  private TransactionRace.Result race(Runnable first, Runnable second) throws InterruptedException {
    return new TransactionRace(transactionManager, dataSource).run(first, second);
  }

  /** Bewusst per SQL: geprüft wird der Stand der Tabelle, nicht die Sicht der Anwendung. */
  private int wirksameZugaenge(long boardId) {
    return Objects.requireNonNull(
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM kanban_access_token"
                    + " WHERE board_id = ? AND art = 'APP' AND revoked = false",
                Integer.class,
                boardId));
  }
}
