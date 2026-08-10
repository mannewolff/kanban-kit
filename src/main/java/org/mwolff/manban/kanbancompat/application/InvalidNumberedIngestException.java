package org.mwolff.manban.kanbancompat.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Ingest mit vorgegebener Kartennummer (#565) erfüllt die Begleitpflichten nicht: Es fehlt
 * {@code direct=true} oder der {@code externalKey}. Ergibt HTTP 400 — Requestfehler, kein
 * Zustandskonflikt: Derselbe Aufruf ist zu keinem Zeitpunkt und gegen kein Projekt gültig.
 *
 * <p>Bewusst hier statt im card-Modul: {@code direct} und {@code externalKey} sind Begriffe des
 * Ingest-Protokolls, die das card-Modul nicht kennt. Die Fassaden-Regel (card.application ist von
 * außen nur über CardService/LabelService erreichbar) hätte einen Import von dort ohnehin
 * verhindert — und sie hat damit recht: Wer die Protokollregel prüft, gehört zum Protokoll.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidNumberedIngestException extends RuntimeException {

  public InvalidNumberedIngestException(String message) {
    super(message);
  }
}
