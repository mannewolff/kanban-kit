package org.mwolff.manban.card.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.manban.card.application.CardDependencyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verwaltet die Tabelle {@code card_dependency} (zusammengesetzter Schlüssel ohne eigene ID) direkt
 * per SQL.
 */
@Component
class JdbcCardDependencyRepository implements CardDependencyRepository {

  private final JdbcTemplate jdbc;

  JdbcCardDependencyRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Ersetzt die Verweise einer Karte vollständig.
   *
   * <p>Die Kartenzeile wird zuerst gesperrt. Ohne die Sperre ist die Ersetzen-Semantik bei
   * gleichzeitigen Aufrufen nicht gewährleistet: Starten beide mit leerer Ausgangsmenge, sperrt das
   * {@code DELETE} nichts (es gibt keine Zeilen zu sperren), und beide fügen anschließend ein — das
   * Ergebnis ist die Vereinigung beider Listen statt einer der beiden. Bei überlappenden Listen
   * kollidiert stattdessen der Primärschlüssel.
   *
   * <p>Gesperrt wird die <em>Karte</em> und nicht die Verweiszeilen, weil genau die fehlen können.
   * Die Sperre gilt für beide Schreibwege (UI und Ingest), die diese Methode teilen.
   *
   * <p>Die {@code INSERT}s laufen als Batch — eine Migration setzt viele Verweise auf einmal, und
   * jeder Einzelaufruf wäre ein eigener Roundtrip innerhalb derselben Transaktion.
   */
  @Override
  public void replaceDependencies(long cardId, List<Integer> dependsOnNumbers) {
    jdbc.queryForList("SELECT id FROM card WHERE id = ? FOR UPDATE", Long.class, cardId);
    jdbc.update("DELETE FROM card_dependency WHERE card_id = ?", cardId);
    if (dependsOnNumbers.isEmpty()) {
      return;
    }
    jdbc.batchUpdate(
        "INSERT INTO card_dependency (card_id, depends_on_card_number) VALUES (?, ?)",
        dependsOnNumbers.stream().map(n -> new Object[] {cardId, n}).toList());
  }

  @Override
  public List<Integer> findByCardId(long cardId) {
    return jdbc.queryForList(
        "SELECT depends_on_card_number FROM card_dependency WHERE card_id = ? "
            + "ORDER BY depends_on_card_number",
        Integer.class,
        cardId);
  }

  /**
   * Sammelzugriff für den Herkunftsbaum (Issue #609): eine Abfrage statt einer je Karte.
   *
   * <p>Die leere Eingabe wird ohne Abfrage beantwortet — {@code IN ()} ist kein gültiges SQL, und
   * ein Roundtrip für eine Antwort, die feststeht, wäre ohnehin verschenkt.
   */
  @Override
  public Map<Long, List<Integer>> findByCardIds(Collection<Long> cardIds) {
    if (cardIds.isEmpty()) {
      return Map.of();
    }
    String platzhalter = String.join(",", Collections.nCopies(cardIds.size(), "?"));
    Map<Long, List<Integer>> ergebnis = new HashMap<>();
    jdbc.query(
        "SELECT card_id, depends_on_card_number FROM card_dependency WHERE card_id IN ("
            + platzhalter
            + ") ORDER BY card_id, depends_on_card_number",
        rs -> {
          ergebnis
              .computeIfAbsent(rs.getLong("card_id"), k -> new ArrayList<>())
              .add(rs.getInt("depends_on_card_number"));
        },
        cardIds.toArray());
    return ergebnis;
  }

  @Override
  public void deleteByCardId(long cardId) {
    jdbc.update("DELETE FROM card_dependency WHERE card_id = ?", cardId);
  }
}
