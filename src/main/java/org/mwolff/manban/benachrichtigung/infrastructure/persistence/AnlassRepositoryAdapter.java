package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.mwolff.manban.benachrichtigung.infrastructure.persistence.BenachrichtigungZustellungJpaRepository.FaelligeZeile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
  public Optional<Long> legeAnlassZumLabelAn(LabelAnlass anlass) {
    int neu =
        anlaesse.legeZumLabelAn(
            anlass.art().name(),
            anlass.projectId(),
            anlass.labelEventId(),
            anlass.cardId(),
            anlass.eingetretenAt(),
            anlass.angelegtAt());
    if (neu == 0) {
      return Optional.empty();
    }
    return Optional.of(anlaesse.findIdByLabelEventId(anlass.labelEventId()));
  }

  @Override
  public boolean gibtAnlassZurKarte(long cardId, AnlassArt art) {
    return anlaesse.existsByCardIdAndArt(cardId, art);
  }

  @Override
  public void legeZustellungAn(NeueZustellung zustellung) {
    zustellungen.legeAn(
        zustellung.anlassId(),
        zustellung.userId(),
        zustellung.faelligAb(),
        zustellung.verschoben());
  }

  @Override
  public List<FaelligeZustellung> faelligeZustellungen(Instant jetzt) {
    return zustellungen.findeFaellige(jetzt).stream().map(this::zustellung).toList();
  }

  @Override
  public List<FaelligeZustellung> eingeplanteZustellungen(Collection<Long> ids) {
    return zustellungen.findeEingeplante(ids).stream().map(this::zustellung).toList();
  }

  private FaelligeZustellung zustellung(FaelligeZeile zeile) {
    return new FaelligeZustellung(
        zeile.getId(),
        zeile.getUserId(),
        zeile.getVerschoben(),
        AnlassArt.valueOf(zeile.getArt()),
        zeile.getProjectId(),
        zeile.getLaufId(),
        zeile.getCardId());
  }

  @Override
  public void setzeEingeplant(Collection<Long> ids) {
    setzeZustand(ids, "EINGEPLANT");
  }

  @Override
  public void setzeEntfallen(Collection<Long> ids) {
    setzeZustand(ids, "ENTFALLEN");
  }

  @Override
  public void setzeZugestellt(Collection<Long> ids) {
    setzeZustandEingeplanter(ids, "ZUGESTELLT");
  }

  @Override
  public void setzeEingeplanteEntfallen(Collection<Long> ids) {
    setzeZustandEingeplanter(ids, "ENTFALLEN");
  }

  /** Eigene Transaktion: Der Aufräum-Job ruft ohne eine; die Datenbank löscht die Zustellungen. */
  @Override
  @Transactional
  public int loescheAelterAls(Instant grenze) {
    return anlaesse.loescheAelterAls(grenze);
  }

  private void setzeZustandEingeplanter(Collection<Long> ids, String zustand) {
    if (!ids.isEmpty()) {
      zustellungen.setzeZustandEingeplanter(ids, zustand);
    }
  }

  private void setzeZustand(Collection<Long> ids, String zustand) {
    if (!ids.isEmpty()) {
      zustellungen.setzeZustand(ids, zustand);
    }
  }
}
