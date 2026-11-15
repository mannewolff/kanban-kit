package org.mwolff.manban.fremdboard.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.fremdboard.application.VerbindungRepository;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.mwolff.manban.project.application.BoardQuelle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die gespeicherte Verbindung zum fremden Board und der Port {@link BoardQuelle} gegen echtes
 * PostgreSQL (Issue #2156, Plan #2152 A10).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VerbindungRepositoryAdapterIT extends AbstractIntegrationTest {

  private static final Instant ANGEBUNDEN = Instant.parse("2026-11-15T09:00:00Z");
  private static final Instant ABGLEICH = Instant.parse("2026-11-15T09:05:00Z");
  private static final Instant VOLLABGLEICH = Instant.parse("2026-11-15T10:00:00Z");
  private static final StatusLabels LABELS =
      new StatusLabels("Backlog", "Ready", "In progress", "In review");

  @Autowired private VerbindungRepository verbindungen;
  @Autowired private BoardQuelle boardQuelle;
  @Autowired private DataSource dataSource;

  @Test
  void eineGespeicherteVerbindungKommtVollstaendigZurueck() {
    Projekt projekt = projektMitBoard("Gespiegelt");
    Verbindung verbindung =
        new Verbindung(
            projekt.id(),
            FremdBoardArt.GITLAB,
            "https://gitlab.example.org",
            "gruppe/projekt",
            "v1:verschluesselt",
            "kit-bot",
            LABELS,
            projekt.board(),
            ANGEBUNDEN,
            ABGLEICH,
            VOLLABGLEICH,
            "zuletzt nicht erreichbar");

    verbindungen.speichere(verbindung);

    assertThat(verbindungen.finde(projekt.id())).contains(verbindung);
  }

  @Test
  void eineVerbindungOhneOptionaleAngabenKommtMitLeerenFeldernZurueck() {
    Projekt projekt = projektMitBoard("Neu");
    Verbindung verbindung = neueVerbindung(projekt);

    verbindungen.speichere(verbindung);

    assertThat(verbindungen.finde(projekt.id())).contains(verbindung);
  }

  @Test
  void erneutesSpeichernErsetztDieVerbindungDesProjekts() {
    Projekt projekt = projektMitBoard("Ersetzt");
    verbindungen.speichere(neueVerbindung(projekt));
    Verbindung nachAbgleich =
        new Verbindung(
            projekt.id(),
            FremdBoardArt.GITLAB,
            "https://gitlab.com",
            "gruppe/anderes",
            "v1:neu",
            "kit-konto",
            new StatusLabels("Offen", "Bereit", "Dran", "Pruefen"),
            projekt.board(),
            ANGEBUNDEN,
            ABGLEICH,
            null,
            null);

    verbindungen.speichere(nachAbgleich);

    assertThat(verbindungen.finde(projekt.id())).contains(nachAbgleich);
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject("SELECT count(*) FROM fremdboard_verbindung", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void ohneVerbindungIstNichtsZuFinden() {
    Projekt projekt = projektMitBoard("Eigen");

    assertThat(verbindungen.finde(projekt.id())).isEmpty();
  }

  @Test
  void istFremdNurFuerEinProjektMitVerbindung() {
    Projekt gespiegelt = projektMitBoard("Gespiegelt");
    Projekt eigen = projektMitBoard("Eigen");
    verbindungen.speichere(neueVerbindung(gespiegelt));

    assertThat(boardQuelle.istFremd(gespiegelt.id())).isTrue();
    assertThat(boardQuelle.istFremd(eigen.id())).isFalse();
    assertThat(boardQuelle.istFremd(Long.MAX_VALUE)).isFalse();
  }

  private static Verbindung neueVerbindung(Projekt projekt) {
    return new Verbindung(
        projekt.id(),
        FremdBoardArt.GITLAB,
        "https://gitlab.com",
        "gruppe/projekt",
        "v1:verschluesselt",
        null,
        LABELS,
        projekt.board(),
        ANGEBUNDEN,
        null,
        null,
        null);
  }

  /** Legt Besitzer, Projekt und Board an; liefert Projekt- und Board-Id. */
  private Projekt projektMitBoard(String name) {
    long besitzer =
        id(
            "INSERT INTO app_user (email, display_name) VALUES (?, 'N') RETURNING id",
            name + "@example.com");
    long projekt =
        id("INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id", name, besitzer);
    long board = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projekt);
    return new Projekt(projekt, board);
  }

  /** Projekt- und Board-Id einer Testeinrichtung. */
  private record Projekt(long id, long board) {}

  private long id(String sql, Object... argumente) {
    Long erzeugt = new JdbcTemplate(dataSource).queryForObject(sql, Long.class, argumente);
    return erzeugt == null ? 0L : erzeugt;
  }
}
