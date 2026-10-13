package org.mwolff.manban.card.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Access-Token wollte ein Freigabe-Label des Kits gegen seine Richtung ändern (403, Issue
 * #1421). Die Meldung nennt Label und Richtung; Einzelheiten in {@link FreigabeLabels}.
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public final class FreigabeLabelException extends RuntimeException implements UebersetzbareMeldung {

  /** Begründung eines unbekannten Namens im Namensraum, zwischen Name und Folge (E7). */
  private static final String NAMENSRAUM_UNBEKANNT =
      " liegt im Namensraum des Kits und ist dort unbekannt; ";

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private FreigabeLabelException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Das Label setzt nur ein Mensch im Board. */
  public static FreigabeLabelException nurMenschSetzt(String label) {
    return new FreigabeLabelException(
        "card.freigabe-label.nur-mensch-setzt",
        "Label " + label + " setzt nur ein Mensch im Board",
        label);
  }

  /** Das Label nimmt nur ein Mensch im Board ab. */
  public static FreigabeLabelException nurMenschNimmtAb(String label) {
    return new FreigabeLabelException(
        "card.freigabe-label.nur-mensch-nimmt-ab",
        "Label " + label + " nimmt nur ein Mensch im Board ab",
        label);
  }

  /** Das Label ist im Namensraum des Kits unbekannt; setzen darf es nur ein Mensch. */
  public static FreigabeLabelException unbekanntSetzen(String label) {
    return new FreigabeLabelException(
        "card.freigabe-label.unbekannt-setzen",
        "Label " + label + NAMENSRAUM_UNBEKANNT + "es setzt nur ein Mensch im Board",
        label);
  }

  /** Auf einen im Namensraum des Kits unbekannten Namen benennt nur ein Mensch um. */
  public static FreigabeLabelException unbekanntUmbenennen(String label) {
    return new FreigabeLabelException(
        "card.freigabe-label.unbekannt-umbenennen",
        "Label "
            + label
            + NAMENSRAUM_UNBEKANNT
            + "auf diesen Namen benennt nur ein Mensch im Board um",
        label);
  }

  /** Die Definition eines Freigabe-Labels ändert nur ein Mensch. */
  public static FreigabeLabelException definitionGesperrt(String label) {
    return new FreigabeLabelException(
        "card.freigabe-label.definition-gesperrt",
        "Label "
            + label
            + " ist ein Freigabe-Label; seine Definition ändert nur ein Mensch im Board",
        label);
  }

  @Override
  public String schluessel() {
    return meldungsschluessel;
  }

  @Override
  public Object[] argumente() {
    return meldungsargumente.clone();
  }
}
