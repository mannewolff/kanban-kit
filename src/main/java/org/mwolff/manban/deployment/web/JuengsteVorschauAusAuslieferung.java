package org.mwolff.manban.deployment.web;

import java.util.Optional;
import org.mwolff.manban.comment.application.JuengsteVorschau;
import org.mwolff.manban.comment.domain.Vorschau;
import org.mwolff.manban.deployment.application.DeploymentStore;
import org.mwolff.manban.deployment.domain.Auslieferungsart;
import org.springframework.stereotype.Component;

/**
 * Umsetzung des Ports {@link JuengsteVorschau} aus den gemeldeten Auslieferungen (Issue #2215, Plan
 * #2206 E10, E11): die jüngste Vorschau des Projekts mit ihrem Stand.
 *
 * <p>Prüft keine Rechte — der Aufrufer im Rückfragen-Dienst hat die Karte und damit das Projekt
 * schon geprüft. Als Zeitpunkt der Meldung gilt ihr Eingang beim Leitstand: Er wird mit der Anlage
 * der Rückfrage verglichen, und beide stammen so von derselben Uhr.
 */
@Component
class JuengsteVorschauAusAuslieferung implements JuengsteVorschau {

  private final DeploymentStore store;

  JuengsteVorschauAusAuslieferung(DeploymentStore store) {
    this.store = store;
  }

  @Override
  public Optional<Vorschau> juengste(long projectId) {
    return store
        .juengster(projectId, Auslieferungsart.VORSCHAU)
        .map(stand -> new Vorschau(stand.eingang(), stand.revision()));
  }
}
