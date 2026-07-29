package org.mwolff.manban.outbox.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository für {@link OutboxEntryEntity}. */
interface OutboxJpaRepository extends JpaRepository<OutboxEntryEntity, Long> {

  /**
   * Legt einen offenen Eintrag an, sofern der Idempotenzschlüssel frei ist (Issue #501).
   *
   * <p>Bewusst nativ mit {@code ON CONFLICT DO NOTHING} statt „prüfen, dann speichern": Die Prüfung
   * wäre ein eigener Lesevorgang und damit unter READ COMMITTED nicht mit dem Einfügen verschränkt
   * — zwei gleichzeitige Aufrufer sähen beide „frei". Postgres entscheidet das hier in einer
   * Anweisung, und der Verlierer bekommt {@code 0} zurück statt einer Constraint-Verletzung, die
   * seine <em>fachliche</em> Transaktion mit zurückrollte.
   *
   * @return 1, wenn eine Zeile entstand; 0, wenn der Schlüssel schon vergeben war
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "insert into outbox_entry"
              + " (event_type, idempotency_key, payload, status, attempts,"
              + " created_at, next_attempt_at)"
              + " values (:eventType, :idempotencyKey, :payload, :status, :attempts,"
              + " :createdAt, :nextAttemptAt)"
              + " on conflict (idempotency_key) do nothing",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("eventType") String eventType,
      @Param("idempotencyKey") String idempotencyKey,
      @Param("payload") String payload,
      @Param("status") String status,
      @Param("attempts") int attempts,
      @Param("createdAt") Instant createdAt,
      @Param("nextAttemptAt") Instant nextAttemptAt);

  /** IDs der offenen, spätestens jetzt fälligen Einträge — älteste Fälligkeit zuerst. */
  @Query(
      value =
          "select id from outbox_entry where status = 'PENDING' and next_attempt_at <= :now"
              + " order by next_attempt_at, id limit :limit",
      nativeQuery = true)
  List<Long> findDueIds(@Param("now") Instant now, @Param("limit") int limit);

  /**
   * Sperrt die Zeile bis zum Transaktionsende, sofern sie offen und fällig ist, und liefert ihre
   * ID.
   *
   * <p>{@code SKIP LOCKED} statt Warten: Ein zweiter Worker soll den Eintrag überspringen und
   * weiterarbeiten, nicht an ihm hängen bleiben. Die Bedingungen stehen bewusst hier und nicht nur
   * im vorherigen {@link #findDueIds} — zwischen Auflisten und Sperren kann der Eintrag längst
   * erledigt sein.
   *
   * <p>Bewusst eine Skalar-Projektion statt einer Entity-Abfrage (wie in #498/#499): Gäbe die
   * Abfrage {@link OutboxEntryEntity} zurück, lieferte Hibernate für eine bereits im
   * Persistenzkontext liegende Zeile die zwischengespeicherte Instanz — und damit womöglich einen
   * veralteten Zustand, obwohl gerade darum gesperrt wird.
   */
  @Query(
      value =
          "select id from outbox_entry where id = :id and status = 'PENDING'"
              + " and next_attempt_at <= :now for update skip locked",
      nativeQuery = true)
  List<Long> lockDueId(@Param("id") long id, @Param("now") Instant now);

  /** Löscht erledigte Einträge, die vor {@code threshold} abgeschlossen wurden. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value = "delete from outbox_entry where status = 'DONE' and completed_at < :threshold",
      nativeQuery = true)
  int deleteCompletedBefore(@Param("threshold") Instant threshold);
}
