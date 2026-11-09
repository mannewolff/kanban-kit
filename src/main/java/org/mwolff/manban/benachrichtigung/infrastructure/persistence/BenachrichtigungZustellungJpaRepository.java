package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository der Zustellungen. */
interface BenachrichtigungZustellungJpaRepository
    extends JpaRepository<BenachrichtigungZustellungEntity, Long> {

  /** {@code ON CONFLICT DO NOTHING}: Je Anlass und Person bleibt es bei einer Zeile. */
  @Modifying
  @Query(
      value =
          "insert into benachrichtigung_zustellung"
              + " (anlass_id, user_id, faellig_ab, verschoben, zustand)"
              + " values (:anlassId, :userId, :faelligAb, :verschoben, 'OFFEN')"
              + " on conflict (anlass_id, user_id) do nothing",
      nativeQuery = true)
  void legeAn(
      @Param("anlassId") long anlassId,
      @Param("userId") long userId,
      @Param("faelligAb") Instant faelligAb,
      @Param("verschoben") boolean verschoben);

  /**
   * Die offenen, fälligen Zustellungen samt Anlass. {@code SKIP LOCKED}: Ein paralleler Takt soll
   * die Zeilen überspringen, statt mit einem anderen Ausschnitt denselben Idempotenzschlüssel zu
   * treffen; dann ginge eine Zustellung als eingeplant durch, die in keiner Nachricht steht.
   */
  @Query(
      value =
          "select z.id as id, z.user_id as userId, z.verschoben as verschoben, a.art as art,"
              + " a.project_id as projectId, a.lauf_id as laufId, a.card_id as cardId"
              + " from benachrichtigung_zustellung z"
              + " join benachrichtigung_anlass a on a.id = z.anlass_id"
              + " where z.zustand = 'OFFEN' and z.faellig_ab <= :jetzt"
              + " order by z.id for update of z skip locked",
      nativeQuery = true)
  List<FaelligeZeile> findeFaellige(@Param("jetzt") Instant jetzt);

  /** Ändert nur offene Zeilen: Was schon eingeplant oder entfallen ist, bleibt, wie es ist. */
  @Modifying
  @Query(
      value =
          "update benachrichtigung_zustellung set zustand = :zustand"
              + " where id in (:ids) and zustand = 'OFFEN'",
      nativeQuery = true)
  void setzeZustand(@Param("ids") Collection<Long> ids, @Param("zustand") String zustand);

  /** Die eingeplanten Zeilen unter den Kennungen samt Anlass (Issue #2080). */
  @Query(
      value =
          "select z.id as id, z.user_id as userId, z.verschoben as verschoben, a.art as art,"
              + " a.project_id as projectId, a.lauf_id as laufId, a.card_id as cardId"
              + " from benachrichtigung_zustellung z"
              + " join benachrichtigung_anlass a on a.id = z.anlass_id"
              + " where z.id in (:ids) and z.zustand = 'EINGEPLANT'"
              + " order by z.id",
      nativeQuery = true)
  List<FaelligeZeile> findeEingeplante(@Param("ids") Collection<Long> ids);

  /** Ändert nur eingeplante Zeilen: Was schon erledigt ist, bleibt, wie es ist. */
  @Modifying
  @Query(
      value =
          "update benachrichtigung_zustellung set zustand = :zustand"
              + " where id in (:ids) and zustand = 'EINGEPLANT'",
      nativeQuery = true)
  void setzeZustandEingeplanter(
      @Param("ids") Collection<Long> ids, @Param("zustand") String zustand);

  /** Eine Zeile von {@link #findeFaellige} und {@link #findeEingeplante}. */
  interface FaelligeZeile {
    long getId();

    long getUserId();

    boolean getVerschoben();

    String getArt();

    long getProjectId();

    @Nullable Long getLaufId();

    @Nullable Long getCardId();
  }
}
