package org.mwolff.manban.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/**
 * Gemeinsame Spring-Data-Basis der Auth-Token-Entities. Beide Tokenarten teilen dieselbe
 * Spaltenstruktur ({@link AbstractAuthTokenEntity}) und damit auch dieselben Abfragen; über {@code
 * #{#entityName}} bindet Spring Data die Query je erbendem Repository an dessen Zieltabelle.
 *
 * @param <E> konkrete Token-Entity
 */
@NoRepositoryBean
interface AuthTokenJpaRepository<E extends AbstractAuthTokenEntity> extends JpaRepository<E, Long> {

  Optional<E> findByTokenHash(String tokenHash);

  /**
   * Setzt die Verbrauchsmarke, aber nur wenn das Token noch unbenutzt und nicht abgelaufen ist —
   * Prüfung und Schreiben in einer einzigen Anweisung.
   *
   * <p>Die Bedingung {@code used_at IS NULL} macht das Update zum Wettlauf-Gewinner-Test:
   * PostgreSQL sperrt die Zeile, und eine gleichzeitig laufende zweite Transaktion wertet die
   * Bedingung nach dem Commit der ersten auf der <em>neuen</em> Zeilenversion aus. Sie trifft dann
   * keine Zeile mehr und erfährt so, dass sie verloren hat. Ein Lesen mit anschließendem Schreiben
   * könnte das nicht leisten (Issue #497).
   *
   * <p>{@code expiresAt >= :now} entspricht der bisherigen Ablaufregel: gültig bis einschließlich
   * des Ablaufzeitpunkts.
   *
   * @return Zahl der geänderten Zeilen; {@code 1} genau für den Aufruf, der das Token verbraucht
   *     hat
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update #{#entityName} t set t.usedAt = :now"
          + " where t.tokenHash = :tokenHash and t.usedAt is null and t.expiresAt >= :now")
  int markUsedIfUnused(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
