package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository für {@link AppUserEntity}. */
interface AppUserJpaRepository extends JpaRepository<AppUserEntity, Long> {

  Optional<AppUserEntity> findByEmail(String email);

  boolean existsByEmail(String email);

  List<AppUserEntity> findByPlatformRole(PlatformRole platformRole);

  /**
   * Sperrt die Zeilen aller Benutzer mit der angegebenen Plattform-Rolle bis zum Transaktionsende
   * und liefert deren IDs (Issue #498, Begründung am Port {@code
   * AppUserRepository.lockPlatformAdminIds}).
   *
   * <p>Bewusst als native Abfrage mit <strong>Skalar-Projektion</strong> statt als
   * {@code @Lock}-Abfrage auf Entities: Gäbe die Abfrage {@link AppUserEntity} zurück, lieferte
   * Hibernate für bereits im Persistenzkontext liegende Benutzer (der Aufrufer schlägt sich selbst
   * über {@code PlatformAdminChecker} nach) die <em>zwischengespeicherte</em> Instanz samt alter
   * Rolle — genau die veraltete Sicht, die das Sperren verhindern soll. IDs umgehen den
   * Persistenzkontext.
   *
   * <p>{@code ORDER BY id} legt die Sperrreihenfolge fest: Zwei Aufrufer greifen dieselbe Menge in
   * derselben Folge, deshalb kann kein Deadlock entstehen.
   */
  @Query(
      value = "select id from app_user where platform_role = :role order by id for update",
      nativeQuery = true)
  List<Long> lockIdsByPlatformRole(@Param("role") String role);
}
