package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA-Abbildung der Key-Value-Tabelle {@code app_setting}. */
@Entity
@Table(name = "app_setting")
class AppSettingEntity {

  @Id
  @Column(name = "setting_key", nullable = false)
  private String key;

  @Column(name = "setting_value", nullable = false)
  private String value;

  protected AppSettingEntity() {
    // für JPA
  }

  AppSettingEntity(String key, String value) {
    this.key = key;
    this.value = value;
  }

  String getKey() {
    return key;
  }

  String getValue() {
    return value;
  }
}
