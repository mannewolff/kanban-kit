package org.mwolff.manban.kanbancompat.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Ingest mit vorgegebener Kartennummer (#565) erfüllt die Begleitpflichten nicht: Es fehlt
 * {@code direct=true} oder der {@code externalKey}. Ergibt HTTP 400 — Requestfehler, kein
 * Zustandskonflikt: Derselbe Aufruf ist zu keinem Zeitpunkt und gegen kein Projekt gültig.
 *
 * <p>Bewusst hier statt im card-Modul: {@code direct} und {@code externalKey} sind Begriffe des
 * Ingest-Protokolls, die das card-Modul nicht kennt. Die Fassaden-Regel (card.application ist von
 * außen nur über ihre Zugänge wie CardService erreichbar) hätte einen Import von dort ohnehin
 * verhindert — und sie hat damit recht: Wer die Protokollregel prüft, gehört zum Protokoll.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidNumberedIngestException extends RuntimeException
    implements UebersetzbareMeldung {

  public InvalidNumberedIngestException() {
    super(
        "Eine vorgegebene Nummer verlangt einen externalKey — sonst blockiert der Import sich"
            + " nach dem ersten Aufruf selbst.");
  }

  @Override
  public String schluessel() {
    return "kanbancompat.nummer-ohne-externalkey";
  }
}
