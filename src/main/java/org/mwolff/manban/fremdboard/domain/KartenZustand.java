package org.mwolff.manban.fremdboard.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Labels und Zustand einer Karte des fremden Boards zu einem Zeitpunkt (Issue #2167, Plan #2152
 * A4). Das fremde System nennt nur den heutigen Stand und die Wechsel dahin; den Stand vor einer
 * Reihe von Wechseln rechnet {@link #vor} rückwärts aus, den nach einem Wechsel {@link #nach}
 * vorwärts. Daraus liest der Abgleich den Status zwischen zwei Wechseln, damit jeder Wechsel mit
 * dem Zeitpunkt aus dem fremden System gebucht wird.
 *
 * <p>Unveränderlich: Jeder Schritt liefert einen neuen Zustand.
 *
 * @param labels Labelnamen in der Reihenfolge, in der sie hinzukamen
 * @param geschlossen ob die Karte geschlossen ist
 */
public record KartenZustand(List<String> labels, boolean geschlossen) {

  /** Kopiert die Labels, damit der Zustand unverändert bleibt. */
  public KartenZustand {
    labels = List.copyOf(labels);
  }

  /** Der heutige Stand der Karte. */
  public static KartenZustand von(FremdKarte karte) {
    return new KartenZustand(karte.labels(), karte.geschlossen());
  }

  /** Der Stand nach dem Wechsel. */
  public KartenZustand nach(FremdEreignis ereignis) {
    return switch (ereignis.art()) {
      case LABEL_GESETZT -> mitLabel(ereignis.label(), true);
      case LABEL_ENTFERNT -> mitLabel(ereignis.label(), false);
      case GESCHLOSSEN -> new KartenZustand(labels, true);
      case WIEDER_GEOEFFNET -> new KartenZustand(labels, false);
    };
  }

  /**
   * Der Stand vor den Wechseln: Sie werden vom jüngsten zum ältesten rückgängig gemacht.
   *
   * @param ereignisse die Wechsel, ältester zuerst
   */
  public KartenZustand vor(List<FremdEreignis> ereignisse) {
    KartenZustand zustand = this;
    for (FremdEreignis ereignis : ereignisse.reversed()) {
      zustand = zustand.ohne(ereignis);
    }
    return zustand;
  }

  /** Der Status in diesem Stand, gelesen mit Titel und Text der Karte; leer bei einem Dokument. */
  public Optional<FremdStatus> status(FremdKarte karte, StatusZuordnung zuordnung) {
    return zuordnung.status(
        new FremdKarte(
            karte.nummer(),
            karte.titel(),
            karte.beschreibung(),
            labels,
            geschlossen,
            karte.angelegtAm(),
            karte.geaendertAm()));
  }

  private KartenZustand ohne(FremdEreignis ereignis) {
    return switch (ereignis.art()) {
      case LABEL_GESETZT -> mitLabel(ereignis.label(), false);
      case LABEL_ENTFERNT -> mitLabel(ereignis.label(), true);
      case GESCHLOSSEN -> new KartenZustand(labels, false);
      case WIEDER_GEOEFFNET -> new KartenZustand(labels, true);
    };
  }

  private KartenZustand mitLabel(@Nullable String label, boolean gesetzt) {
    if (label == null) {
      return this;
    }
    Set<String> neu = new LinkedHashSet<>(labels);
    if (gesetzt) {
      neu.add(label);
    } else {
      neu.remove(label);
    }
    return new KartenZustand(new ArrayList<>(neu), geschlossen);
  }
}
