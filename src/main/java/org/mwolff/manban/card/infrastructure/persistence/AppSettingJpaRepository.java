package org.mwolff.manban.card.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link AppSettingEntity} (Schlüssel ist der Primärschlüssel). */
interface AppSettingJpaRepository extends JpaRepository<AppSettingEntity, String> {}
