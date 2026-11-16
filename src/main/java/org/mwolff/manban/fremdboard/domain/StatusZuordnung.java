package org.mwolff.manban.fremdboard.domain;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Art;

/**
 * Status einer Karte des fremden Boards (Issue #2158, Plan #2152 A3), wie der GitLab-Adapter des
 * Kits ihn liest: Das erste Statuslabel in der Reihenfolge Backlog, Ready, In progress, In review
 * gilt, ohne Statuslabel Backlog. Die Labelnamen kommen aus der Einrichtung und werden exakt
 * verglichen.
 *
 * <p>Abweichend vom Kit-Adapter gilt eine geschlossene Karte immer als Done, auch wenn sie noch ein
 * Statuslabel trägt: Wer in GitLab von Hand schließt, nimmt das Label meist nicht ab (#2150, „Eine
 * in GitLab geschlossene Karte gilt als Done“).
 *
 * <p>Einen Status tragen nur Arbeitspakete, wie in {@code card} (Plan #1294): Die Art liest {@link
 * KitNamen#artAusTitel}, die Dokumentarten nennt {@link Art#istDokument} — keine eigene
 * Präfixliste.
 */
public final class StatusZuordnung {

  private final StatusLabels labels;

  /**
   * Zuordnung für die Labelnamen einer Verbindung.
   *
   * @param labels die Labelnamen je Status aus der Einrichtung
   */
  public StatusZuordnung(StatusLabels labels) {
    this.labels = labels;
  }

  /** Ob eine Karte dieses Titels einen Status trägt: alles außer Idee, Anforderung und Plan. */
  public static boolean statusfaehig(String titel) {
    return KitNamen.artAusTitel(titel).filter(Art::istDokument).isEmpty();
  }

  /** Der Status der Karte; leer bei einem Dokument. */
  public Optional<FremdStatus> status(FremdKarte karte) {
    if (!statusfaehig(karte.titel())) {
      return Optional.empty();
    }
    if (karte.geschlossen()) {
      return Optional.of(FremdStatus.DONE);
    }
    List<String> an = karte.labels();
    if (an.contains(labels.backlog())) {
      return Optional.of(FremdStatus.BACKLOG);
    }
    if (an.contains(labels.ready())) {
      return Optional.of(FremdStatus.READY);
    }
    if (an.contains(labels.inProgress())) {
      return Optional.of(FremdStatus.IN_PROGRESS);
    }
    if (an.contains(labels.inReview())) {
      return Optional.of(FremdStatus.IN_REVIEW);
    }
    return Optional.of(FremdStatus.BACKLOG);
  }
}
