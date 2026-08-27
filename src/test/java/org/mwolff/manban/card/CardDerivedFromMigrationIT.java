package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifiziert die Migration {@code V26__card_derived_from.sql}: die Spalte {@code
 * derived_from_card_id} existiert nullable, trägt den benannten Fremdschlüssel {@code
 * fk_card_derived_from} mit {@code ON DELETE SET NULL} und den benannten Index {@code
 * idx_card_derived_from}.
 *
 * <p>Geprüft wird gegen das <strong>voll migrierte</strong> Schema. Der Vorher/Nachher-Aufbau der
 * Backfill-Tests (erst bis V(n-1) migrieren, Daten säen, dann V(n) anwenden) ist hier unnötig: Die
 * Migration ist rein additiv und fasst keine Daten an.
 *
 * <p>Die Namen sind Teil der Zusicherung. Ohne sie vergäbe Postgres Auto-Namen, und der Test müsste
 * unscharf nach <em>irgendeinem</em> Index auf der Spalte suchen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CardDerivedFromMigrationIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void columnIsNullable() {
    String nullable =
        jdbc.queryForObject(
            "SELECT is_nullable FROM information_schema.columns"
                + " WHERE table_schema = current_schema()"
                + " AND table_name = 'card' AND column_name = 'derived_from_card_id'",
            String.class);

    assertThat(nullable).isEqualTo("YES");
  }

  @Test
  void foreignKeyDeletesToNull() {
    String deleteRule =
        jdbc.queryForObject(
            "SELECT delete_rule FROM information_schema.referential_constraints"
                + " WHERE constraint_schema = current_schema()"
                + " AND constraint_name = 'fk_card_derived_from'",
            String.class);

    assertThat(deleteRule).isEqualTo("SET NULL");
  }

  @Test
  void indexExists() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes"
                + " WHERE schemaname = current_schema()"
                + " AND tablename = 'card' AND indexname = 'idx_card_derived_from'",
            Integer.class);

    assertThat(count).isEqualTo(1);
  }
}
