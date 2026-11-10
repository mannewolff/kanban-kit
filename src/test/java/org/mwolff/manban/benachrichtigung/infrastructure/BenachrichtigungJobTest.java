package org.mwolff.manban.benachrichtigung.infrastructure;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungErkennung;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungVersand;

/**
 * Der Takt der Benachrichtigungen (Issue #2081, Plan #2070 E2, E10): Erkennen, dann einplanen, und
 * bei abgeschaltetem Mailversand gar nichts.
 */
class BenachrichtigungJobTest {

  private final BenachrichtigungErkennung erkennung = mock(BenachrichtigungErkennung.class);
  private final BenachrichtigungVersand versand = mock(BenachrichtigungVersand.class);

  private BenachrichtigungJob job(boolean mailEnabled) {
    return new BenachrichtigungJob(erkennung, versand, mailEnabled);
  }

  @Test
  void beiAbgeschaltetemMailversandRuhtDerJobUndLegtKeineAnlaesseAn() {
    job(false).run();

    verifyNoInteractions(erkennung, versand);
  }

  @Test
  void einTaktErkenntLaeufeUndKartenUndPlantDanachEin() {
    job(true).run();

    InOrder reihenfolge = inOrder(erkennung, versand);
    reihenfolge.verify(erkennung).erkenneLaeufe();
    reihenfolge.verify(erkennung).erkenneKarten();
    reihenfolge.verify(versand).planeEin();
  }

  @Test
  void einFehlerBeiDenLaeufenHaeltKartenUndVersandNichtAuf() {
    when(erkennung.erkenneLaeufe()).thenThrow(new IllegalStateException("Datenbank weg"));

    job(true).run();

    verify(erkennung).erkenneKarten();
    verify(versand).planeEin();
  }

  @Test
  void einFehlerBeiDenKartenHaeltDenVersandNichtAuf() {
    when(erkennung.erkenneKarten()).thenThrow(new IllegalStateException("Datenbank weg"));

    job(true).run();

    verify(versand).planeEin();
  }

  @Test
  void einFehlerBeimVersandBeendetDenTaktOhneAusnahme() {
    when(erkennung.erkenneLaeufe()).thenReturn(1);
    when(erkennung.erkenneKarten()).thenReturn(1);
    when(versand.planeEin()).thenThrow(new IllegalStateException("Datenbank weg"));

    job(true).run();

    verify(versand).planeEin();
  }
}
