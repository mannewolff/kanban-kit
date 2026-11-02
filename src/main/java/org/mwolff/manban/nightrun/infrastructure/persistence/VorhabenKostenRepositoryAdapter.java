package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository;
import org.springframework.stereotype.Component;

/**
 * Adapter der festgehaltenen Kosten je Vorhaben (Issue #2004) über {@link
 * VorhabenKostenJpaRepository} und {@link VorhabenKostenEinfuehrungJpaRepository}. Die Transaktion
 * stellt der Dienst.
 */
@Component
class VorhabenKostenRepositoryAdapter implements VorhabenKostenRepository {

  private final VorhabenKostenJpaRepository kosten;
  private final VorhabenKostenEinfuehrungJpaRepository einfuehrung;

  VorhabenKostenRepositoryAdapter(
      VorhabenKostenJpaRepository kosten, VorhabenKostenEinfuehrungJpaRepository einfuehrung) {
    this.kosten = kosten;
    this.einfuehrung = einfuehrung;
  }

  @Override
  public List<VorhabenKosten> findByProjectId(long projectId) {
    return kosten.findByProjectIdOrderByEpicIdAsc(projectId).stream()
        .map(VorhabenKostenEntity::toDomain)
        .toList();
  }

  @Override
  public void speichere(VorhabenKosten zeile) {
    kosten.save(new VorhabenKostenEntity(zeile));
  }

  @Override
  public Optional<Instant> eingefuehrtAm(long projectId) {
    return einfuehrung.findById(projectId).map(VorhabenKostenEinfuehrungEntity::getEingefuehrtAt);
  }

  @Override
  public void einfuehren(long projectId, Instant am) {
    if (!einfuehrung.existsById(projectId)) {
      einfuehrung.save(new VorhabenKostenEinfuehrungEntity(projectId, am));
    }
  }
}
