package org.mwolff.manban.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.auth.application.AdminVorschauRepository;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.VorschauEndgrund;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter des {@link AdminVorschauRepository}-Ports auf Spring Data JPA (Issue #1917). Die
 * schreibenden Methoden laufen in einer Transaktion, die eine schon offene des Aufrufers mitnimmt.
 */
@Component
class AdminVorschauRepositoryAdapter implements AdminVorschauRepository {

  private final AdminVorschauJpaRepository jpa;

  AdminVorschauRepositoryAdapter(AdminVorschauJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public AdminVorschau save(AdminVorschau vorschau) {
    return jpa.save(new AdminVorschauEntity(vorschau)).toDomain();
  }

  @Override
  public Optional<AdminVorschau> findByKennung(String kennung) {
    return jpa.findByKennung(kennung).map(AdminVorschauEntity::toDomain);
  }

  @Override
  @Transactional
  public boolean beende(long id, Instant beendetAm, VorschauEndgrund endgrund) {
    return jpa.beende(id, beendetAm, endgrund) == 1;
  }

  @Override
  @Transactional
  public int beendeAbgelaufene(long zielId, Instant now) {
    return jpa.beendeAbgelaufene(zielId, now, VorschauEndgrund.ABLAUF);
  }

  @Override
  public List<AdminVorschau> findByZiel(long zielId) {
    return jpa.findByZielIdOrderByGestartetAmDescIdDesc(zielId).stream()
        .map(AdminVorschauEntity::toDomain)
        .toList();
  }
}
