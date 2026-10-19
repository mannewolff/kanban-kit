package org.mwolff.manban.deployment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.deployment.application.NeueMeldung.Commit;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.mwolff.manban.deployment.domain.Auslieferungsversuch;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.domain.Permission;

/**
 * Verhaltenstests des Melde-Dienstes (Issue #1833, Plan #1831 E2, E3, E4, E7) gegen einen
 * Doppelgänger des Ports. Dass der Port selbst hält, was er zusagt, belegt {@code
 * JdbcDeploymentStoreIT}.
 */
class DeploymentServiceTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;
  private static final String TOKEN = "deploy-token";
  private static final Instant JETZT = Instant.parse("2026-10-17T12:00:00Z");
  private static final Instant FERTIG = Instant.parse("2026-10-17T11:00:00Z");

  private static final Commit EINS = new Commit("aaa111", Instant.parse("2026-10-16T08:00:00Z"));
  private static final Commit ZWEI = new Commit("bbb222", Instant.parse("2026-10-16T09:00:00Z"));

  private FakeStore store;
  private PermissionChecker permissions;
  private DeploymentService service;

  @BeforeEach
  void setUp() {
    store = new FakeStore();
    permissions = mock(PermissionChecker.class);
    service = new DeploymentService(store, permissions, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static NeueMeldung meldung(String attemptId, Ausgang ausgang, Instant fertig) {
    return new NeueMeldung(attemptId, ausgang, fertig, "rev-1", List.of(EINS, ZWEI));
  }

  private static NeueMeldung meldung() {
    return meldung("run-1", Ausgang.SUCCESS, FERTIG);
  }

  @Test
  void neueKennungWirdGespeichertUndIstCreated() {
    Meldeergebnis ergebnis = service.melden(USER, PROJECT, TOKEN, meldung());

    assertThat(ergebnis).isEqualTo(Meldeergebnis.CREATED);
    assertThat(store.eingefuegt).containsExactly(new Eingefuegt(PROJECT, TOKEN, meldung(), JETZT));
    verify(permissions).require(USER, PROJECT, Permission.TICKET_CREATE);
  }

  @Test
  void viewerBekommt403UndNichtsWirdGespeichert() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(USER, PROJECT, Permission.TICKET_CREATE);

    assertThatThrownBy(() -> service.melden(USER, PROJECT, TOKEN, meldung()))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThat(store.eingefuegt).isEmpty();
  }

  @Test
  void nichtmitgliedBekommt404UndNichtsWirdGespeichert() {
    doThrow(new ProjectNotFoundException())
        .when(permissions)
        .require(USER, PROJECT, Permission.TICKET_CREATE);

    assertThatThrownBy(() -> service.melden(USER, PROJECT, TOKEN, meldung()))
        .isInstanceOf(ProjectNotFoundException.class);
    assertThat(store.eingefuegt).isEmpty();
  }

  @Test
  void sechsMinutenInDerZukunftWirdAbgewiesen() {
    NeueMeldung zukunft = meldung("run-1", Ausgang.SUCCESS, JETZT.plusSeconds(6 * 60));

    assertThatThrownBy(() -> service.melden(USER, PROJECT, TOKEN, zukunft))
        .isInstanceOf(DeploymentInFutureException.class);
    assertThat(store.eingefuegt).isEmpty();
  }

  @Test
  void vierMinutenInDerZukunftWirdAngenommen() {
    NeueMeldung bald = meldung("run-1", Ausgang.SUCCESS, JETZT.plusSeconds(4 * 60));

    assertThat(service.melden(USER, PROJECT, TOKEN, bald)).isEqualTo(Meldeergebnis.CREATED);
    assertThat(store.eingefuegt).hasSize(1);
  }

  @Test
  void genauFuenfMinutenInDerZukunftWirdAngenommen() {
    NeueMeldung grenze = meldung("run-1", Ausgang.SUCCESS, JETZT.plusSeconds(5 * 60));

    assertThat(service.melden(USER, PROJECT, TOKEN, grenze)).isEqualTo(Meldeergebnis.CREATED);
  }

  @Test
  void eineSekundeUeberFuenfMinutenWirdAbgewiesen() {
    NeueMeldung knapp = meldung("run-1", Ausgang.SUCCESS, JETZT.plusSeconds(5 * 60 + 1));

    assertThatThrownBy(() -> service.melden(USER, PROJECT, TOKEN, knapp))
        .isInstanceOf(DeploymentInFutureException.class);
  }

  @Test
  void doppelmeldungMitGleichemInhaltInAndererReihenfolgeIstUnchanged() {
    service.melden(USER, PROJECT, TOKEN, meldung());
    NeueMeldung vertauscht =
        new NeueMeldung("run-1", Ausgang.SUCCESS, FERTIG, "rev-1", List.of(ZWEI, EINS));

    Meldeergebnis ergebnis = service.melden(USER, PROJECT, "anderes-token", vertauscht);

    assertThat(ergebnis).isEqualTo(Meldeergebnis.UNCHANGED);
    assertThat(store.eingefuegt).hasSize(1);
  }

  @Test
  void gleicheKennungMitAnderemAusgangIstKonflikt() {
    pruefeKonflikt(meldung("run-1", Ausgang.FAILURE, FERTIG));
  }

  @Test
  void gleicheKennungMitAnderemZeitpunktIstKonflikt() {
    pruefeKonflikt(meldung("run-1", Ausgang.SUCCESS, FERTIG.plusSeconds(1)));
  }

  @Test
  void gleicheKennungMitAndererRevisionIstKonflikt() {
    pruefeKonflikt(new NeueMeldung("run-1", Ausgang.SUCCESS, FERTIG, "rev-2", List.of(EINS, ZWEI)));
  }

  @Test
  void gleicheKennungMitWenigerCommitsIstKonflikt() {
    pruefeKonflikt(new NeueMeldung("run-1", Ausgang.SUCCESS, FERTIG, "rev-1", List.of(EINS)));
  }

  @Test
  void gleicheKennungMitAnderemCommitZeitpunktIstKonflikt() {
    Commit verschoben = new Commit(ZWEI.sha(), ZWEI.committedAt().plusSeconds(1));
    pruefeKonflikt(
        new NeueMeldung("run-1", Ausgang.SUCCESS, FERTIG, "rev-1", List.of(EINS, verschoben)));
  }

  private void pruefeKonflikt(NeueMeldung andere) {
    service.melden(USER, PROJECT, TOKEN, meldung());

    assertThatThrownBy(() -> service.melden(USER, PROJECT, TOKEN, andere))
        .isInstanceOf(DeploymentAttemptConflictException.class)
        .extracting(e -> ((DeploymentAttemptConflictException) e).schluessel())
        .isEqualTo("deployment.attempt.conflict");
    assertThat(store.eingefuegt).hasSize(1);
  }

  @Test
  void dieselbeKennungInEinemAnderenProjektIstNeu() {
    service.melden(USER, PROJECT, TOKEN, meldung());

    Meldeergebnis ergebnis =
        service.melden(USER, PROJECT + 1, TOKEN, meldung("run-1", Ausgang.FAILURE, FERTIG));

    assertThat(ergebnis).isEqualTo(Meldeergebnis.CREATED);
    assertThat(store.eingefuegt).hasSize(2);
  }

  @Test
  void letzterStandLiefertDenSpaetestenVersuchAuchWennErGescheitertIst() {
    service.melden(USER, PROJECT, TOKEN, meldung("run-1", Ausgang.SUCCESS, FERTIG));
    service.melden(USER, PROJECT, TOKEN, meldung("run-2", Ausgang.FAILURE, FERTIG.plusSeconds(60)));
    service.melden(
        USER, PROJECT, TOKEN, meldung("run-0", Ausgang.SUCCESS, FERTIG.minusSeconds(60)));

    Optional<Auslieferungsversuch> letzter = service.letzterStand(USER, PROJECT);

    assertThat(letzter).map(Auslieferungsversuch::attemptId).contains("run-2");
    assertThat(letzter).map(Auslieferungsversuch::ausgang).contains(Ausgang.FAILURE);
    verify(permissions, times(4)).require(USER, PROJECT, Permission.TICKET_CREATE);
  }

  @Test
  void letzterStandOhneVersuchIstLeer() {
    assertThat(service.letzterStand(USER, PROJECT)).isEmpty();
    verify(permissions).require(USER, PROJECT, Permission.TICKET_CREATE);
  }

  @Test
  void letzterStandPrueftDieRechte() {
    service.melden(USER, PROJECT, TOKEN, meldung());
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(USER, PROJECT, Permission.TICKET_CREATE);

    assertThatThrownBy(() -> service.letzterStand(USER, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  private record Eingefuegt(
      long projectId, String tokenName, NeueMeldung meldung, Instant eingang) {}

  /** Doppelgänger des Ports: hält die Einfügungen in Eingangsreihenfolge. */
  private static final class FakeStore implements DeploymentStore {

    private final List<Eingefuegt> eingefuegt = new ArrayList<>();

    @Override
    public void fuegeEin(long projectId, String tokenName, NeueMeldung meldung, Instant eingang) {
      eingefuegt.add(new Eingefuegt(projectId, tokenName, meldung, eingang));
    }

    @Override
    public Optional<NeueMeldung> findeVersuch(long projectId, String attemptId) {
      return eingefuegt.stream()
          .filter(e -> e.projectId() == projectId && e.meldung().attemptId().equals(attemptId))
          .map(Eingefuegt::meldung)
          .findFirst();
    }

    @Override
    public Auslieferungsdaten ladeAuswertung(long projectId, Instant fensterBeginn) {
      throw new UnsupportedOperationException("nicht Teil des Dienstes");
    }

    @Override
    public Optional<Auslieferungsversuch> letzterVersuch(long projectId) {
      List<Auslieferungsversuch> versuche = new ArrayList<>();
      for (int i = 0; i < eingefuegt.size(); i++) {
        Eingefuegt e = eingefuegt.get(i);
        if (e.projectId() == projectId) {
          NeueMeldung m = e.meldung();
          versuche.add(
              new Auslieferungsversuch(
                  i + 1L, m.attemptId(), m.outcome(), m.finishedAt(), m.revision()));
        }
      }
      return versuche.stream()
          .max(
              Comparator.comparing(Auslieferungsversuch::finishedAt)
                  .thenComparingLong(Auslieferungsversuch::id));
    }
  }
}
