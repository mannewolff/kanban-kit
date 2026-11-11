package org.mwolff.manban.accesstoken.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.auth.application.KontoGesperrtEvent;
import org.mwolff.manban.project.application.MitgliedRolleVerlorenEvent;

/** Der Widerruf nach Verlust von Rolle oder Konto (Issue #2104, Plan #2100 E4). */
class AppZugangWiderrufListenerTest {

  private final AccessTokenRepository tokens = mock(AccessTokenRepository.class);
  private final AppZugangWiderrufListener listener = new AppZugangWiderrufListener(tokens);

  @Test
  void rollenverlust_widerruftNurDieZugaengeInDiesemProjekt() {
    // When
    listener.onRolleVerloren(new MitgliedRolleVerlorenEvent(9L, 2L));

    // Then: erst die Ausgabesperre des Inhabers, dann der Sammelwiderruf (Issue #2122).
    InOrder reihenfolge = inOrder(tokens);
    reihenfolge.verify(tokens).sperreAppZugangAusgabe(2L);
    reihenfolge.verify(tokens).markAppZugaengeRevoked(2L, 9L);
    verifyNoMoreInteractions(tokens);
  }

  @Test
  void kontosperre_widerruftAlleZugaengeDesInhabers() {
    // When
    listener.onKontoGesperrt(new KontoGesperrtEvent(2L));

    // Then
    InOrder reihenfolge = inOrder(tokens);
    reihenfolge.verify(tokens).sperreAppZugangAusgabe(2L);
    reihenfolge.verify(tokens).markAppZugaengeRevoked(2L);
    verifyNoMoreInteractions(tokens);
  }
}
