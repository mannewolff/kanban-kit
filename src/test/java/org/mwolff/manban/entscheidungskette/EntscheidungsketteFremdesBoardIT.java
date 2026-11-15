package org.mwolff.manban.entscheidungskette;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.FremdKartenSchreiber;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremdeKarte;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremderAkteur;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteService;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.AbschnittView;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.AkteurView;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.GliedView;
import org.mwolff.manban.entscheidungskette.domain.Akteurtyp;
import org.mwolff.manban.entscheidungskette.domain.Dokumentart;
import org.mwolff.manban.entscheidungskette.domain.Freigabefall;
import org.mwolff.manban.entscheidungskette.domain.Gliedart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Entscheidungskette eines Projekts, dessen Board in GitLab liegt (Issue #2165, Plan #2152
 * A11): Der Spiegel schreibt Karten, Label-Ereignisse und Züge mit dem Benutzer in GitLab, der
 * Dienst rechnet die Kette über den echten Lesepfad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EntscheidungsketteFremdesBoardIT extends AbstractIntegrationTest {

  private static final Instant ANGEBUNDEN = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant GESETZT = Instant.parse("2026-11-02T09:00:00Z");
  private static final Instant GEZOGEN = Instant.parse("2026-11-02T10:00:00Z");
  private static final String MODELL = "claude-opus-5-5";
  private static final FremderAkteur KIT = FremderAkteur.von("kit-bot", "kit-bot");
  private static final FremderAkteur ERIKA = FremderAkteur.von("erika", "kit-bot");

  @Autowired private FremdKartenSchreiber schreiber;
  @Autowired private EntscheidungsketteService service;
  @Autowired private JdbcTemplate jdbc;

  private long userId;
  private long boardId;

  @BeforeEach
  void seed() {
    userId =
        insert(
            "INSERT INTO app_user (email, display_name) VALUES ('a@example.com', 'A')"
                + " RETURNING id");
    long projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
        projectId,
        userId);
    boardId =
        insert(
            "INSERT INTO board (project_id, name) VALUES ("
                + projectId
                + ", 'GitLab') RETURNING id");
    String[] spalten = {"Backlog", "Ready", "In progress", "In review", "Done"};
    for (int i = 0; i < spalten.length; i++) {
      jdbc.update(
          "INSERT INTO board_column (board_id, name, position) VALUES (?, ?, ?)",
          boardId,
          spalten[i],
          i);
    }
    jdbc.update(
        "INSERT INTO fremdboard_verbindung (project_id, art, basis_url, projekt_pfad,"
            + " zugang_verschluesselt, kit_konto, statuslabels, board_id, angebunden_am)"
            + " VALUES (?, 'GITLAB', 'https://gitlab.example.org', 'gruppe/projekt', 'geheim',"
            + " 'kit-bot', '{\"ready\":\"Ready\"}'::jsonb, ?, ?)",
        projectId,
        boardId,
        Timestamp.from(ANGEBUNDEN));
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }

  private long anlegen(int nummer, String titel, String text) {
    return schreiber.anlegen(
        boardId, new FremdeKarte(nummer, titel, text, "BACKLOG", null), ANGEBUNDEN, KIT);
  }

  @Test
  @DisplayName(
      "Freigabe in einer Nacht-Kette bei fremdem Board nennt den GitLab-Benutzer des Ziel-Labels")
  void freigabeInEinerNachtKetteBeiFremdemBoardNenntDenGitLabBenutzerDesZiellabels() {
    // Given: Fachplan, Plan und Paket aus GitLab; Erika setzt dort das Ziel, das Kit-Konto zieht.
    anlegen(10, "[Requirement] Export", "Autor-Modell: " + MODELL);
    anlegen(20, "[Plan] Export", "Plan-Modell: " + MODELL + "\nFachliche Quelle: Issue #10");
    long paket = anlegen(30, "Exportpaket", "Autor-Modell: " + MODELL + "\nPlan: Issue #20");
    schreiber.labelSetzen(boardId, 10, "goal:implementation", GESETZT, ERIKA);
    schreiber.wechseln(boardId, 30, "READY", GEZOGEN, KIT);

    // When
    EntscheidungsketteView kette = service.kette(userId, paket);

    // Then: Erika hat freigegeben, das Kit-Konto hat ausgeführt — ohne Laufkennung aus GitLab.
    GliedView freigabe =
        kette.abschnitte().stream()
            .filter(a -> a.art() == Dokumentart.PAKET)
            .map(AbschnittView::glieder)
            .flatMap(java.util.List::stream)
            .filter(g -> g.art() == Gliedart.FREIGABE)
            .findFirst()
            .orElseThrow();
    assertThat(freigabe.freigabe()).isNotNull();
    assertThat(freigabe.freigabe().fall()).isEqualTo(Freigabefall.KENNZEICHEN);
    assertThat(freigabe.freigabe().zugNachReady()).isEqualTo(GEZOGEN);
    assertThat(freigabe.freigegebenVon())
        .isEqualTo(new AkteurView(Akteurtyp.MENSCH, "erika", null, true));
    assertThat(freigabe.ausgefuehrtVon())
        .isEqualTo(new AkteurView(Akteurtyp.WERKZEUG, "kit-bot", null, false));
    assertThat(freigabe.zeitpunkt()).isEqualTo(GESETZT);
    assertThat(freigabe.quelle().nummer()).isEqualTo(10);
  }
}
