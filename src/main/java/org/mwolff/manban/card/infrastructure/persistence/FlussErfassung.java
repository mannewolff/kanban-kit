package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.mwolff.manban.card.application.CardFlowStayRepository;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;
import org.mwolff.manban.card.domain.Flusslage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Erfasst die Zustandswechsel der Arbeitspakete in {@code card_flow_stay} (Issue #1768, Plan #1765
 * E2, E3, E4, E5, E12) — an der einen Stelle, die jeder Schreibweg auf Karten passiert: Der {@link
 * CardRepositoryAdapter} meldet jede Karte, bevor er sie schreibt.
 *
 * <p>Gesammelt wird je Transaktion. Beim ersten Kontakt merkt sich die Erfassung die Flusslage
 * vorher; vor dem Commit liest sie den Stand nachher und schreibt höchstens einen Wechsel je Karte.
 * So entsteht kein Nullaufenthalt, wenn ein Weg wie {@code transfer} Board und Zustand in zwei
 * Zugriffen setzt. Ein Wechsel entsteht nur, wenn sich Zustand, Board oder Fluss-Zugehörigkeit
 * ändern (E4); gestempelt wird mit der injizierten {@link Clock}, ein neu angelegtes Paket mit
 * seinem {@code created_at}.
 *
 * <p>Gelesen wird per JDBC, nicht über JPA: {@code move}, {@code transfer}, {@code softDelete} und
 * {@code restoreFromTrash} schreiben am Persistenzkontext vorbei. Den JPA-Merge aus {@code save}
 * schiebt {@code beforeCommit} darum zuerst mit {@code entityManager.flush()} in die Datenbank —
 * sonst flösse er erst im {@code doCommit}, nach dem Lesen.
 */
@Component
class FlussErfassung {

  private final JdbcTemplate jdbc;
  private final CardFlowStayRepository aufenthalte;
  private final EntityManager entityManager;
  private final Clock clock;

  FlussErfassung(
      JdbcTemplate jdbc,
      CardFlowStayRepository aufenthalte,
      EntityManager entityManager,
      Clock clock) {
    this.jdbc = jdbc;
    this.aufenthalte = aufenthalte;
    this.entityManager = entityManager;
    this.clock = clock;
  }

  /**
   * Meldet eine bestehende Karte vor dem Schreiben. Nur der erste Kontakt in der Transaktion liest
   * die Flusslage vorher; spätere Kontakte ändern sie nicht.
   *
   * @throws IllegalStateException ohne laufende Transaktion — der Adapter schreibt nur in einer
   */
  void beruehrt(long cardId) {
    sammlung().vorher.computeIfAbsent(cardId, this::lage);
  }

  /**
   * Meldet eine eben angelegte Karte mit ihrer vergebenen ID: vorher nicht im Fluss, Eintritt zu
   * ihrem {@code created_at}.
   *
   * @throws IllegalStateException ohne laufende Transaktion — der Adapter schreibt nur in einer
   */
  void neu(long cardId) {
    // Eine eben vergebene ID kann in dieser Transaktion noch nicht gemeldet sein.
    Sammlung sammlung = sammlung();
    sammlung.vorher.put(cardId, Optional.empty());
    sammlung.neu.add(cardId);
  }

  private Sammlung sammlung() {
    Sammlung sammlung = (Sammlung) TransactionSynchronizationManager.getResource(this);
    if (sammlung == null) {
      sammlung = new Sammlung();
      TransactionSynchronizationManager.registerSynchronization(sammlung);
      TransactionSynchronizationManager.bindResource(this, sammlung);
    }
    return sammlung;
  }

  /** Die Flusslage der Karte nach der Regel {@link Flusslage}, leer, wenn es sie nicht gibt. */
  private Optional<Flusslage> lage(long cardId) {
    return zeile(cardId).flatMap(Zeile::lage);
  }

  private Optional<Zeile> zeile(long cardId) {
    return jdbc
        .query(
            "SELECT board_id, status, archived, deleted_at IS NOT NULL AS im_papierkorb,"
                + " created_at FROM card WHERE id = ?",
            (rs, rowNum) -> {
              String status = rs.getString("status");
              return new Zeile(
                  Flusslage.von(
                      rs.getLong("board_id"),
                      status == null ? null : CardStatus.valueOf(status),
                      rs.getBoolean("archived"),
                      rs.getBoolean("im_papierkorb")),
                  rs.getObject("created_at", OffsetDateTime.class).toInstant());
            },
            cardId)
        .stream()
        .findFirst();
  }

  /** Was die Erfassung von einer Kartenzeile braucht. */
  private record Zeile(Optional<Flusslage> lage, Instant createdAt) {}

  /**
   * Schreibt den Wechsel einer Karte. Eine endgültig gelöschte Karte hat keine Zeile mehr — ihre
   * Aufenthalte nimmt die Cascade, hier bleibt nichts zu tun.
   */
  private void schreibe(long cardId, Optional<Flusslage> vorher, boolean neu, Instant jetzt) {
    Optional<Zeile> zeile = zeile(cardId);
    Optional<Flusslage> nachher = zeile.flatMap(Zeile::lage);
    if (vorher.equals(nachher)) {
      return;
    }
    vorher.ifPresent(v -> aufenthalte.close(cardId, jetzt, grund(v, nachher)));
    if (nachher.isPresent()) {
      Instant eintritt = neu ? zeile.get().createdAt() : jetzt;
      aufenthalte.open(cardId, nachher.get(), eintritt);
    }
  }

  /** Warum der Aufenthalt {@code vorher} endet (E4). */
  private static EndedBy grund(Flusslage vorher, Optional<Flusslage> nachher) {
    if (nachher.isEmpty()) {
      return EndedBy.EXIT;
    }
    return vorher.boardId() == nachher.get().boardId() ? EndedBy.STATUS : EndedBy.BOARD;
  }

  /** Die gemeldeten Karten einer Transaktion, in der Reihenfolge des ersten Kontakts. */
  private final class Sammlung implements TransactionSynchronization {

    private final Map<Long, Optional<Flusslage>> vorher = new LinkedHashMap<>();
    private final Set<Long> neu = new HashSet<>();

    @Override
    public void beforeCommit(boolean readOnly) {
      entityManager.flush();
      Instant jetzt = clock.instant();
      for (Map.Entry<Long, Optional<Flusslage>> karte : new ArrayList<>(vorher.entrySet())) {
        schreibe(karte.getKey(), karte.getValue(), neu.contains(karte.getKey()), jetzt);
      }
    }

    @Override
    public void afterCompletion(int status) {
      TransactionSynchronizationManager.unbindResourceIfPossible(FlussErfassung.this);
    }
  }
}
