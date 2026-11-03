package org.mwolff.manban.nightrun.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.FestgehalteneVoraussage;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.Stand;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.VorhabenKosten;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Art;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die festgehaltenen Kosten je Vorhaben gegen echtes Postgres (Issue #2004, {@code V68}): beide
 * eingebetteten Voraussagen, die Array-Spalte der Kostenkarten, das Ersetzen einer Zeile, die
 * Einführung je Projekt und das Entfallen mit der Vorhaben-Karte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VorhabenKostenRepositoryAdapterIT extends AbstractIntegrationTest {

  private static final Instant FESTGEHALTEN = Instant.parse("2026-10-20T08:00:00Z");
  private static final Instant ABSCHLUSS = Instant.parse("2026-10-30T08:00:00Z");

  @Autowired private VorhabenKostenRepository repository;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long boardId;
  private long spalte;

  @BeforeEach
  void bestand() {
    long userId =
        id("INSERT INTO app_user (email, display_name) VALUES ('a@example.com', 'A') RETURNING id");
    projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    boardId =
        id("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    spalte =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Ready', 0) RETURNING id");
  }

  private long vorhaben(int nummer) {
    return id(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column, type) VALUES ("
            + boardId
            + ", "
            + spalte
            + ", "
            + nummer
            + ", 'Export', "
            + nummer
            + ", 'EPIC') RETURNING id");
  }

  private long id(String sql) {
    Long erzeugt = jdbc.queryForObject(sql, Long.class);
    return erzeugt == null ? 0L : erzeugt;
  }

  @Test
  void eineVollstaendigeZeileKommtUnveraendertZurueck() {
    long epicId = vorhaben(1);
    VorhabenKosten zeile =
        VorhabenKosten.leer(epicId, projectId)
            .mitAnforderung(
                new FestgehalteneVoraussage(
                    Stand.BEZIFFERT,
                    Art.ANFORDERUNG,
                    new BigDecimal("16.000000"),
                    new BigDecimal("12.500000"),
                    new BigDecimal("21.250000"),
                    FESTGEHALTEN))
            .mitPaketen(FestgehalteneVoraussage.keine(FESTGEHALTEN))
            .mitAbschluss(ABSCHLUSS, new BigDecimal("18.123456"), true, List.of(3, 1, 2));

    repository.speichere(zeile);

    assertThat(repository.findByProjectId(projectId)).containsExactly(zeile);
  }

  @Test
  void eineLeereZeileHatKeineVoraussageUndKeineKostenkarten() {
    long epicId = vorhaben(1);
    VorhabenKosten leer = VorhabenKosten.leer(epicId, projectId);

    repository.speichere(leer);

    VorhabenKosten gelesen = repository.findByProjectId(projectId).getFirst();
    assertThat(gelesen.anforderung()).isNull();
    assertThat(gelesen.pakete()).isNull();
    assertThat(gelesen.kostenKarten()).isEmpty();
    assertThat(gelesen).isEqualTo(leer);
  }

  @Test
  void speichernErsetztDieZeileVollstaendig() {
    long epicId = vorhaben(1);
    VorhabenKosten abgeschlossen =
        VorhabenKosten.leer(epicId, projectId)
            .mitAnforderung(FestgehalteneVoraussage.keine(FESTGEHALTEN))
            .mitAbschluss(ABSCHLUSS, BigDecimal.TEN, false, List.of(1, 2));
    repository.speichere(abgeschlossen);

    repository.speichere(abgeschlossen.wiederOffen());

    VorhabenKosten gelesen = repository.findByProjectId(projectId).getFirst();
    assertThat(gelesen.abgeschlossenAt()).isNull();
    assertThat(gelesen.tatsaechlichUsd()).isNull();
    assertThat(gelesen.kostenKarten()).isEmpty();
    assertThat(gelesen.anforderung()).isEqualTo(FestgehalteneVoraussage.keine(FESTGEHALTEN));
  }

  @Test
  void dieZeilenEinesProjektsKommenNachVorhabenId() {
    long zweites = vorhaben(2);
    long erstes = vorhaben(1);
    repository.speichere(VorhabenKosten.leer(zweites, projectId));
    repository.speichere(VorhabenKosten.leer(erstes, projectId));

    assertThat(repository.findByProjectId(projectId))
        .extracting(VorhabenKosten::epicId)
        .containsExactly(Math.min(erstes, zweites), Math.max(erstes, zweites));
    assertThat(repository.findByProjectId(projectId + 1)).isEmpty();
  }

  @Test
  void mitDerVorhabenKarteEntfaelltDieZeile() {
    long epicId = vorhaben(1);
    repository.speichere(VorhabenKosten.leer(epicId, projectId));

    jdbc.update("DELETE FROM card WHERE id = ?", epicId);

    assertThat(repository.findByProjectId(projectId)).isEmpty();
  }

  @Test
  void dieEinfuehrungWirdEinmalVermerkt() {
    assertThat(repository.eingefuehrtAm(projectId)).isEmpty();

    repository.einfuehren(projectId, FESTGEHALTEN);
    repository.einfuehren(projectId, ABSCHLUSS);

    assertThat(repository.eingefuehrtAm(projectId)).hasValue(FESTGEHALTEN);
  }
}
