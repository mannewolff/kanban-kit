package org.mwolff.manban.push.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * JPA-Abbildung der Tabelle {@code push_abo}; die abgeschalteten Boards liegen in {@code
 * push_abo_board_aus} und hängen als Sammlung am Abo.
 */
@Entity
@Table(name = "push_abo")
class PushAboEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "endpoint", nullable = false)
  private String endpoint;

  @Column(name = "p256dh", nullable = false)
  private String p256dh;

  @Column(name = "auth", nullable = false)
  private String auth;

  @Column(name = "angelegt_at", nullable = false)
  private Instant angelegtAt;

  @Column(name = "gilt_bis", nullable = false)
  private Instant giltBis;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "push_abo_board_aus", joinColumns = @JoinColumn(name = "abo_id"))
  @Column(name = "board_id", nullable = false)
  private Set<Long> boardsAus = new HashSet<>();

  protected PushAboEntity() {
    // für JPA
  }

  PushAboEntity(
      @Nullable Long id,
      Long userId,
      String endpoint,
      String p256dh,
      String auth,
      Instant angelegtAt,
      Instant giltBis,
      Set<Long> boardsAus) {
    this.id = id;
    this.userId = userId;
    this.endpoint = endpoint;
    this.p256dh = p256dh;
    this.auth = auth;
    this.angelegtAt = angelegtAt;
    this.giltBis = giltBis;
    this.boardsAus = new HashSet<>(boardsAus);
  }

  @Nullable Long getId() {
    return id;
  }

  Long getUserId() {
    return userId;
  }

  String getEndpoint() {
    return endpoint;
  }

  String getP256dh() {
    return p256dh;
  }

  String getAuth() {
    return auth;
  }

  Instant getAngelegtAt() {
    return angelegtAt;
  }

  Instant getGiltBis() {
    return giltBis;
  }

  Set<Long> getBoardsAus() {
    return boardsAus;
  }
}
