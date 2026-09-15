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
   * Sperrt die Zeilen aller <strong>nicht gesperrten</strong> Benutzer mit der angegebenen
   * Plattform-Rolle bis zum Transaktionsende und liefert deren IDs (Issue #498, Begründung am Port
   * {@code AppUserRepository.lockActivePlatformAdminIds}).
   *
   * <p>{@code disabled_at is null} gehört in die Bedingung, weil ein gesperrter Träger der Rolle
   * die Instanz nicht handlungsfähig hält: Er kann sich nicht anmelden und damit niemanden
   * befördern oder entsperren. Zählte er mit, genügte neben einem aktiven Administrator ein
   * gesperrter, damit der aktive herabgestuft werden kann — und die Instanz stünde führungslos da
   * (Issue #880).
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
      value =
          "select id from app_user where platform_role = :role and disabled_at is null"
              + " order by id for update",
      nativeQuery = true)
  List<Long> lockActiveIdsByPlatformRole(@Param("role") String role);
}
