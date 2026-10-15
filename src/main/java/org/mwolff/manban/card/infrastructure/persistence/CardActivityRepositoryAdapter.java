package org.mwolff.manban.card.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardActivityRepository;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.LaufBewegung;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link CardActivityRepository}-Ports auf Spring Data JPA; die Lauf-Bewegungen per
 * SQL, weil die Fensterfunktion {@code LAG} in JPQL fehlt.
 */
@Component
class CardActivityRepositoryAdapter implements CardActivityRepository {

  /**
   * Plan #1752 E16: {@code LAG} läuft über alle Aktivitäten der Karten mit gesetztem {@code
   * status_after}, ohne Zeitfilter; erst die äußere Abfrage schränkt auf Laufkennung und {@code
   * von} ein. Andersherum hätte die erste Bewegung im Fenster keinen Vorgänger. Zugriff je Karte
   * über {@code idx_card_activity_card}.
   */
  private static final String LAUF_BEWEGUNGEN =
      "SELECT card_id, type, created_at, run_started_at, status_after, status_vorher FROM ("
          + " SELECT a.id, a.card_id, a.type, a.created_at, a.run_started_at, a.status_after,"
          + " LAG(a.status_after) OVER (PARTITION BY a.card_id ORDER BY a.created_at, a.id)"
          + " AS status_vorher"
          + " FROM card_activity a"
          + " WHERE a.card_id IN (:cardIds) AND a.status_after IS NOT NULL) b"
          + " WHERE b.run_started_at IS NOT NULL AND b.created_at >= :von"
          + " ORDER BY b.created_at, b.id";

  private final CardActivityJpaRepository jpa;
  private final NamedParameterJdbcTemplate jdbc;

  CardActivityRepositoryAdapter(CardActivityJpaRepository jpa, NamedParameterJdbcTemplate jdbc) {
    this.jpa = jpa;
    this.jdbc = jdbc;
  }

  @Override
  public void add(
      long cardId,
      long actorUserId,
      CardActivityType type,
      String detail,
      Instant createdAt,
      ActorStamp stamp,
      @Nullable CardStatus statusAfter) {
    jpa.save(
        new CardActivityEntity(
            cardId, actorUserId, type.name(), detail, createdAt, stamp, statusAfter));
  }

  @Override
  public List<CardActivity> findByCardId(long cardId) {
    return jpa.findByCardIdOrderByCreatedAt(cardId).stream()
        .map(CardActivityRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<CardActivity> findTokenActivitiesInWindow(
      long projectId, String tokenName, Instant von, Instant bis) {
    return jpa.findTokenActivitiesInWindow(projectId, tokenName, von, bis).stream()
        .map(CardActivityRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<LaufBewegung> findLaufBewegungen(Collection<Long> cardIds, Instant von) {
    if (cardIds.isEmpty()) {
      return List.of();
    }
    return jdbc.query(
        LAUF_BEWEGUNGEN,
        Map.of("cardIds", cardIds, "von", OffsetDateTime.ofInstant(von, ZoneOffset.UTC)),
        (rs, n) -> laufBewegung(rs));
  }

  private static LaufBewegung laufBewegung(ResultSet rs) throws SQLException {
    String vorher = rs.getString("status_vorher");
    return new LaufBewegung(
        rs.getLong("card_id"),
        CardActivityType.valueOf(rs.getString("type")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("run_started_at", OffsetDateTime.class).toInstant(),
        CardStatus.valueOf(rs.getString("status_after")),
        vorher == null ? null : CardStatus.valueOf(vorher));
  }

  private static CardActivity toDomain(CardActivityEntity e) {
    String origin = e.getOrigin();
    String statusAfter = e.getStatusAfter();
    return new CardActivity(
        e.getId(),
        e.getCardId(),
        e.getActorUserId(),
        CardActivityType.valueOf(e.getType()),
        e.getDetail(),
        e.getCreatedAt(),
        origin == null ? null : CardActivityOrigin.valueOf(origin),
        e.getTokenName(),
        e.getAgent(),
        e.getRunStartedAt(),
        statusAfter == null ? null : CardStatus.valueOf(statusAfter));
  }
}
