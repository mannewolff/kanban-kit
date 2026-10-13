package org.mwolff.manban.card.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Abhängigkeit verweist auf eine unbekannte Kartennummer oder auf die Karte selbst.
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidDependencyException extends RuntimeException implements UebersetzbareMeldung {

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  /** Für die Unterklassen mit eigenem Feld; nach außen entstehen Instanzen nur über Fabriken. */
  protected InvalidDependencyException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente.clone();
  }

  /** Das Vorhaben liegt nicht auf diesem Board oder ist keines. */
  public static InvalidDependencyException keinEpicDesBoards(Long vorhaben) {
    return new InvalidDependencyException(
        "card.abhaengigkeit.kein-epic-des-boards",
        "Kein Epic dieses Boards: " + vorhaben,
        vorhaben);
  }

  /** Das Vorhaben ist archiviert. */
  public static InvalidDependencyException vorhabenArchiviert(Long vorhaben) {
    return new InvalidDependencyException(
        "card.abhaengigkeit.vorhaben-archiviert",
        "Das Vorhaben ist archiviert: " + vorhaben,
        vorhaben);
  }

  /** Nur Karten lassen sich einem Vorhaben zuordnen. */
  public static InvalidDependencyException nurKartenImEpic() {
    return new InvalidDependencyException(
        "card.abhaengigkeit.nur-karten-im-epic", "Nur Karten können einem Epic zugeordnet werden");
  }

  /** Ein Vorhaben eröffnet keinen Vorgang aus sich selbst. */
  public static InvalidDependencyException vorhabenAusSichSelbst(Integer nummer) {
    return new InvalidDependencyException(
        "card.abhaengigkeit.vorhaben-aus-sich-selbst",
        "Ein Vorhaben eroeffnet keinen Vorgang aus sich selbst: " + nummer,
        nummer);
  }

  /** Eine ruhende Karte eröffnet keinen Vorgang. */
  public static InvalidDependencyException ruhendeKarte(Integer nummer) {
    return new InvalidDependencyException(
        "card.abhaengigkeit.ruhende-karte",
        "Eine ruhende Karte eroeffnet keinen Vorgang: " + nummer,
        nummer);
  }

  /** Die Karte gehört schon zu einem Vorhaben. */
  public static InvalidDependencyException bereitsZugeordnet(Integer nummer) {
    return new InvalidDependencyException(
        "card.abhaengigkeit.bereits-zugeordnet",
        "Die Karte ist bereits einem Vorhaben zugeordnet: " + nummer,
        nummer);
  }

  /** Nur Karten haben Zuständige. */
  public static InvalidDependencyException nurKartenMitZustaendigen() {
    return new InvalidDependencyException(
        "card.abhaengigkeit.nur-karten-mit-zustaendigen", "Nur Karten haben Zuständige");
  }

  /** Nur Karten haben Labels. */
  public static InvalidDependencyException nurKartenMitLabels() {
    return new InvalidDependencyException(
        "card.abhaengigkeit.nur-karten-mit-labels", "Nur Karten haben Labels");
  }

  /** Eine Karte hängt von sich selbst ab. */
  public static InvalidDependencyException vonSichSelbst() {
    return new InvalidDependencyException(
        "card.abhaengigkeit.von-sich-selbst", "Karte kann nicht von sich selbst abhängen");
  }

  /** Die Kartennummer gibt es im Projekt nicht. */
  public static InvalidDependencyException unbekannteNummer(Integer nummer) {
    return new InvalidDependencyException(
        "card.abhaengigkeit.unbekannte-nummer", "Unbekannte Kartennummer: " + nummer, nummer);
  }

  /** Vorhaben haben keine Position auf dem Board. */
  public static InvalidDependencyException epicsNichtPositioniert() {
    return new InvalidDependencyException(
        "card.abhaengigkeit.epics-nicht-positioniert",
        "Epics werden nicht auf dem Board positioniert");
  }

  /** Vorhaben lassen sich nicht verschieben. */
  public static InvalidDependencyException epicsNichtVerschiebbar() {
    return new InvalidDependencyException(
        "card.abhaengigkeit.epics-nicht-verschiebbar", "Epics können nicht verschoben werden");
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
