package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungenRepository;
import org.mwolff.manban.benachrichtigung.domain.Ruhezeit;
import org.springframework.stereotype.Component;

/** Adapter des {@link BenachrichtigungEinstellungenRepository}-Ports auf die drei Tabellen. */
@Component
class BenachrichtigungEinstellungenRepositoryAdapter
    implements BenachrichtigungEinstellungenRepository {

  private final BenachrichtigungProjektJpaRepository projekte;
  private final BenachrichtigungPersonJpaRepository personen;
  private final BenachrichtigungRuhezeitJpaRepository ruhezeiten;

  BenachrichtigungEinstellungenRepositoryAdapter(
      BenachrichtigungProjektJpaRepository projekte,
      BenachrichtigungPersonJpaRepository personen,
      BenachrichtigungRuhezeitJpaRepository ruhezeiten) {
    this.projekte = projekte;
    this.personen = personen;
    this.ruhezeiten = ruhezeiten;
  }

  @Override
  public Optional<Instant> projektEingeschaltetSeit(long projectId) {
    return projekte.findById(projectId).map(BenachrichtigungProjektEntity::getEingeschaltetSeit);
  }

  @Override
  public void schalteProjektEin(long projectId, Instant seit) {
    projekte.schalteEin(projectId, seit);
  }

  @Override
  public void schalteProjektAus(long projectId) {
    projekte.schalteAus(projectId);
  }

  @Override
  public Set<Long> eingeschalteteProjekte(Collection<Long> projectIds) {
    return projekte.findAllById(projectIds).stream()
        .map(BenachrichtigungProjektEntity::getProjectId)
        .collect(Collectors.toSet());
  }

  @Override
  public Optional<Instant> personEingeschaltetSeit(long userId, long projectId) {
    return personen
        .findById(new BenachrichtigungPersonEntity.Schluessel(userId, projectId))
        .map(BenachrichtigungPersonEntity::getEingeschaltetSeit);
  }

  @Override
  public void schaltePersonEin(long userId, long projectId, Instant seit) {
    personen.schalteEin(userId, projectId, seit);
  }

  @Override
  public void schaltePersonAus(long userId, long projectId) {
    personen.schalteAus(userId, projectId);
  }

  @Override
  public Set<Long> eingeschalteteProjekteDerPerson(long userId) {
    return personen.findProjectIdsByUserId(userId);
  }

  @Override
  public List<EingeschaltetePerson> eingeschaltetePersonen(long projectId) {
    return personen.findByProjectId(projectId).stream()
        .map(p -> new EingeschaltetePerson(p.getUserId(), p.getEingeschaltetSeit()))
        .toList();
  }

  @Override
  public Optional<Ruhezeit> ruhezeit(long userId) {
    return ruhezeiten
        .findById(userId)
        .map(z -> new Ruhezeit(z.getBeginn(), z.getEnde(), ZoneId.of(z.getZeitzone())));
  }

  @Override
  public void speichereRuhezeit(long userId, Ruhezeit ruhezeit) {
    ruhezeiten.speichere(userId, ruhezeit.beginn(), ruhezeit.ende(), ruhezeit.zone().getId());
  }

  @Override
  public void loescheRuhezeit(long userId) {
    ruhezeiten.loesche(userId);
  }
}
