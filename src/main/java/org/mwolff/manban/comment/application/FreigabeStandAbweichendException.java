package org.mwolff.manban.comment.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Stand, den der Mensch freigibt, ist nicht der, den die Rückfrage jetzt zur Freigabe stellt
 * (Issue #2253): Der Kommentar wurde nach dem Anzeigen ausgetauscht, oder der Klick nennt keinen
 * Stand. Die Freigabe gäbe nicht frei, was gezeigt wurde. Ergibt HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class FreigabeStandAbweichendException extends RuntimeException
    implements UebersetzbareMeldung {

  public FreigabeStandAbweichendException() {
    super("Der angezeigte Stand ist nicht mehr der zur Freigabe gestellte");
  }

  @Override
  public String schluessel() {
    return "comment.rueckfrage.stand-abweichend";
  }
}
