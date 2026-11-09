package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import java.util.Optional;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository;
import org.springframework.stereotype.Component;

/** Adapter des {@link AnlassRepository}-Ports auf die Tabellen für Anlass und Zustellung. */
@Component
class AnlassRepositoryAdapter implements AnlassRepository {

  private final BenachrichtigungAnlassJpaRepository anlaesse;
  private final BenachrichtigungZustellungJpaRepository zustellungen;

  AnlassRepositoryAdapter(
      BenachrichtigungAnlassJpaRepository anlaesse,
      BenachrichtigungZustellungJpaRepository zustellungen) {
    this.anlaesse = anlaesse;
    this.zustellungen = zustellungen;
  }

  @Override
  public Optional<Long> legeAnlassZumLaufAn(LaufAnlass anlass) {
    int neu =
        anlaesse.legeZumLaufAn(
            anlass.art().name(),
            anlass.projectId(),
            anlass.laufId(),
            anlass.cardId(),
            anlass.eingetretenAt(),
            anlass.angelegtAt());
    if (neu == 0) {
      return Optional.empty();
    }
    return Optional.of(anlaesse.findIdByLaufIdAndArt(anlass.laufId(), anlass.art()));
  }

  @Override
  public void legeZustellungAn(NeueZustellung zustellung) {
    zustellungen.legeAn(
        zustellung.anlassId(),
        zustellung.userId(),
        zustellung.faelligAb(),
        zustellung.verschoben());
  }
}
