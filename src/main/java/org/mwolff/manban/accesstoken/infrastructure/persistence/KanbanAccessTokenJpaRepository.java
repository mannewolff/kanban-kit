package org.mwolff.manban.accesstoken.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Repository für {@link KanbanAccessTokenEntity}.
 *
 * <p>Nutzung und Widerruf schreiben <strong>spaltenscharf</strong> und damit disjunkt (Issue #878):
 * Ein vollständiges {@code save} aus einem zuvor gelesenen Zustand würde einen dazwischen
 * eingetroffenen Widerruf wieder überschreiben — das Token lebte weiter, obwohl die Liste es als
 * widerrufen zeigt. Ein Widerruf ist ein Endzustand.
 */
interface KanbanAccessTokenJpaRepository extends JpaRepository<KanbanAccessTokenEntity, Long> {

  List<KanbanAccessTokenEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

  Optional<KanbanAccessTokenEntity> findByTokenHash(String tokenHash);

  /**
   * Stempelt die Nutzung — aber nur, solange das Token nicht widerrufen ist. Die Bedingung wird von
   * PostgreSQL nach dem Warten auf die Zeilensperre neu ausgewertet: Ein parallel committeter
   * Widerruf lässt das Update auf null Zeilen laufen, statt ihn zurückzunehmen.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update #{#entityName} t set t.lastUsedAt = :when where t.id = :id and t.revoked = false")
  void touchLastUsedAt(@Param("id") long id, @Param("when") Instant when);

  /**
   * Widerruft das Token; idempotent, weil der Zielzustand fest steht und nicht vom Lesen abhängt.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update #{#entityName} t set t.revoked = true where t.id = :id")
  void markRevoked(@Param("id") long id);
}
