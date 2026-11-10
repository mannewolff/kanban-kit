package org.mwolff.manban.accesstoken.application;

import org.mwolff.manban.auth.application.KontoGesperrtEvent;
import org.mwolff.manban.project.application.MitgliedRolleVerlorenEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Widerruft die Zugänge der App, sobald der Inhaber Rolle oder Konto verliert (Issue #2104, Plan
 * #2100 E4).
 *
 * <p>Bewusst ein synchroner {@link EventListener}: Er läuft in der Transaktion der Änderung, der
 * Widerruf committet also mit ihr oder gar nicht. Erst damit ist der Verlust endgültig. Die Prüfung
 * beim Aufruf allein ließe den Zugang wieder wirksam werden, wenn zwischen Verlust und Rückgabe der
 * Rolle kein Aufruf kam.
 */
@Component
class AppZugangWiderrufListener {

  private final AccessTokenRepository tokens;

  AppZugangWiderrufListener(AccessTokenRepository tokens) {
    this.tokens = tokens;
  }

  @EventListener
  void onRolleVerloren(MitgliedRolleVerlorenEvent event) {
    tokens.markAppZugaengeRevoked(event.userId(), event.projectId());
  }

  @EventListener
  void onKontoGesperrt(KontoGesperrtEvent event) {
    tokens.markAppZugaengeRevoked(event.userId());
  }
}
