package org.mwolff.manban.accesstoken.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.accesstoken.domain.AccessTokenArt;
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

  Optional<KanbanAccessTokenEntity> findByBoardIdAndArtAndRevokedFalse(
      Long boardId, AccessTokenArt art);

  List<KanbanAccessTokenEntity> findByBoardIdInAndArtAndRevokedFalse(
      Collection<Long> boardIds, AccessTokenArt art);

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

  /**
   * Widerruft das Token nur, solange es wirksam ist, und liefert die Zahl der geänderten Zeilen.
   * Die Bedingung wertet PostgreSQL nach dem Warten auf die Zeilensperre neu aus.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update #{#entityName} t set t.revoked = true where t.id = :id and t.revoked = false")
  int markRevokedIfActive(@Param("id") long id);

  /**
   * Nimmt die Ausgabesperre der Zugänge der App dieses Inhabers bis zum Ende der Transaktion (Issue
   * #2122). Eine transaktionsgebundene Advisory-Sperre statt einer Zeilensperre: Die neue Zeile, um
   * die es geht, gibt es beim Sperren noch nicht. Der Schlüssel ist ein Hash über eine feste
   * Vorsilbe und die Nutzer-ID; eine Kollision kostet nur Wartezeit, nie Korrektheit.
   */
  @Query(
      value =
          "select 1 from (select pg_advisory_xact_lock("
              + "hashtextextended('app-zugang-ausgabe:' || :userId, 0))) gesperrt",
      nativeQuery = true)
  int sperreAppZugangAusgabe(@Param("userId") long userId);

  /** Widerruft alle wirksamen Token dieser Art und dieses Inhabers. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update #{#entityName} t set t.revoked = true"
          + " where t.userId = :userId and t.art = :art and t.revoked = false")
  void markRevokedByUser(@Param("userId") long userId, @Param("art") AccessTokenArt art);

  /** Widerruft die wirksamen Token dieser Art, dieses Inhabers und dieses Projekts. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update #{#entityName} t set t.revoked = true where t.userId = :userId"
          + " and t.projectId = :projectId and t.art = :art and t.revoked = false")
  void markRevokedByUserAndProject(
      @Param("userId") long userId,
      @Param("projectId") long projectId,
      @Param("art") AccessTokenArt art);
}
