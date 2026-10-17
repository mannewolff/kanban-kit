package org.mwolff.manban.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Repository für {@link LoginCodeEntity}.
 *
 * <p>Verbrauch und Fehlversuch sind je <strong>eine</strong> Anweisung (Plan #1801, A2): Die
 * Bedingung „offen" steht im {@code WHERE} desselben {@code UPDATE}. PostgreSQL sperrt die Zeile;
 * eine gleichzeitige zweite Anweisung wertet die Bedingung nach dem Commit der ersten auf der neuen
 * Zeilenversion aus, trifft keine Zeile mehr und hat damit verloren (Muster aus Issue #497). Der
 * Verbrauch liefert das Konto per {@code RETURNING} — ein vorgelagertes Lesen gibt es nicht.
 */
interface LoginCodeJpaRepository extends JpaRepository<LoginCodeEntity, Long> {

  @Query(
      "select count(c) from LoginCodeEntity c"
          + " where c.emailHash = :emailHash and c.createdAt >= :since")
  long countSince(@Param("emailHash") String emailHash, @Param("since") Instant since);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update LoginCodeEntity c set c.supersededAt = :now"
          + " where c.emailHash = :emailHash and c.consumedAt is null and c.supersededAt is null")
  int supersedeOpen(@Param("emailHash") String emailHash, @Param("now") Instant now);

  @Query(
      value =
          "UPDATE login_code SET consumed_at = :now"
              + " WHERE email_hash = :emailHash AND code_hash = :codeHash"
              + " AND consumed_at IS NULL AND superseded_at IS NULL"
              + " AND expires_at > :now AND failed_attempts < :maxAttempts"
              + " RETURNING user_id",
      nativeQuery = true)
  Optional<Long> consumeByCode(
      @Param("emailHash") String emailHash,
      @Param("codeHash") String codeHash,
      @Param("now") Instant now,
      @Param("maxAttempts") int maxAttempts);

  @Query(
      value =
          "UPDATE login_code SET consumed_at = :now"
              + " WHERE link_hash = :linkHash"
              + " AND consumed_at IS NULL AND superseded_at IS NULL"
              + " AND expires_at > :now AND failed_attempts < :maxAttempts"
              + " RETURNING user_id",
      nativeQuery = true)
  Optional<Long> consumeByLink(
      @Param("linkHash") String linkHash,
      @Param("now") Instant now,
      @Param("maxAttempts") int maxAttempts);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update LoginCodeEntity c set c.failedAttempts = c.failedAttempts + 1"
          + " where c.emailHash = :emailHash and c.consumedAt is null and c.supersededAt is null"
          + " and c.expiresAt > :now and c.failedAttempts < :maxAttempts")
  int recordFailure(
      @Param("emailHash") String emailHash,
      @Param("now") Instant now,
      @Param("maxAttempts") int maxAttempts);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from LoginCodeEntity c where c.createdAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
