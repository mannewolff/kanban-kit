package org.mwolff.manban.project.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository für {@link ProjectMembershipEntity}. */
interface ProjectMembershipJpaRepository extends JpaRepository<ProjectMembershipEntity, Long> {

  List<ProjectMembershipEntity> findByUserId(Long userId);

  List<ProjectMembershipEntity> findByProjectId(Long projectId);

  Optional<ProjectMembershipEntity> findByProjectIdAndUserId(Long projectId, Long userId);

  /**
   * Sperrt alle Mitgliedschaftszeilen des Projekts bis zum Transaktionsende (Issue #498, Begründung
   * am Port {@code ProjectMembershipRepository.lockOwnerUserIds}).
   *
   * <p>{@code ORDER BY id} legt die Sperrreihenfolge fest: Zwei Aufrufer greifen dieselbe Menge in
   * derselben Folge, deshalb kann kein Deadlock entstehen. Die zurückgegebenen IDs werden nicht
   * gebraucht — die Wirkung ist die Sperre.
   */
  @Query(
      value =
          "select id from project_membership where project_id = :projectId order by id"
              + " for update",
      nativeQuery = true)
  List<Long> lockIdsByProjectId(@Param("projectId") long projectId);

  /**
   * Benutzer-IDs der Mitglieder mit der angegebenen Rolle.
   *
   * <p>Bewusst als native Abfrage mit <strong>Skalar-Projektion</strong> statt über {@link
   * #findByProjectId}: Gäbe die Abfrage Entities zurück, lieferte Hibernate für bereits im
   * Persistenzkontext liegende Mitgliedschaften (die Rechteprüfung schlägt die des Aufrufers nach)
   * die <em>zwischengespeicherte</em> Instanz samt alter Rolle — genau die veraltete Sicht, die das
   * Sperren verhindern soll. IDs umgehen den Persistenzkontext.
   */
  @Query(
      value =
          "select user_id from project_membership where project_id = :projectId"
              + " and role = :role order by user_id",
      nativeQuery = true)
  List<Long> findUserIdsByProjectIdAndRole(
      @Param("projectId") long projectId, @Param("role") String role);
}
