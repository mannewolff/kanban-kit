package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardLabelRepository;
import org.mwolff.manban.card.application.LabelRepository;
import org.mwolff.manban.card.domain.Label;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adapter-Test für Labels (LabelRepository) und die Zuordnung (card_label). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LabelPersistenceIT extends AbstractIntegrationTest {

  @Autowired private LabelRepository labels;
  @Autowired private CardLabelRepository cardLabels;
  @Autowired private JdbcTemplate jdbc;

  private long boardId;
  private long cardId;

  @BeforeEach
  void seed() {
    long userId =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('a@example.com', 'x', 'A') RETURNING id");
    long projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    boardId =
        insert("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    long columnId =
        insert(
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Ready', 0) RETURNING id");
    cardId =
        insert(
            "INSERT INTO card (board_id, column_id, number, title, position_in_column) "
                + "VALUES ("
                + boardId
                + ", "
                + columnId
                + ", 1, 'A', 0) RETURNING id");
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }

  @Test
  void saveFindAndDeleteLabels() {
    Label saved = labels.save(new Label(null, boardId, "Bug", "#f00"));
    assertThat(saved.id()).isNotNull();
    assertThat(labels.findById(saved.requireId())).isPresent();
    assertThat(labels.existsByBoardIdAndName(boardId, "Bug")).isTrue();
    assertThat(labels.existsByBoardIdAndName(boardId, "Ux")).isFalse();
    assertThat(labels.findByBoardId(boardId)).extracting(Label::name).containsExactly("Bug");

    labels.deleteById(saved.requireId());
    assertThat(labels.findByBoardId(boardId)).isEmpty();
  }

  @Test
  void replaceAndFindCardLabels() {
    long bug = labels.save(new Label(null, boardId, "Bug", "#f00")).requireId();
    long ux = labels.save(new Label(null, boardId, "Ux", "#0f0")).requireId();

    cardLabels.replaceLabels(cardId, List.of(ux, bug));
    assertThat(cardLabels.findByCardId(cardId)).containsExactly(bug, ux); // sortiert nach id

    cardLabels.replaceLabels(cardId, List.of(bug));
    assertThat(cardLabels.findByCardId(cardId)).containsExactly(bug);
  }

  @Test
  void deletingLabelCascadesToCardLabel() {
    long bug = labels.save(new Label(null, boardId, "Bug", "#f00")).requireId();
    cardLabels.replaceLabels(cardId, List.of(bug));

    labels.deleteById(bug);

    assertThat(cardLabels.findByCardId(cardId)).isEmpty();
  }
}
