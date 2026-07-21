package org.mwolff.manban.card.infrastructure.persistence;

import java.util.Optional;
import org.mwolff.manban.card.application.AppSettingRepository;
import org.springframework.stereotype.Component;

/** Adapter des {@link AppSettingRepository}-Ports auf Spring Data JPA. */
@Component
class AppSettingRepositoryAdapter implements AppSettingRepository {

  private final AppSettingJpaRepository jpa;

  AppSettingRepositoryAdapter(AppSettingJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<String> find(String key) {
    return jpa.findById(key).map(AppSettingEntity::getValue);
  }

  @Override
  public void save(String key, String value) {
    // Der Schlüssel ist der Primärschlüssel -> save() legt an oder überschreibt (Upsert).
    jpa.save(new AppSettingEntity(key, value));
  }
}
