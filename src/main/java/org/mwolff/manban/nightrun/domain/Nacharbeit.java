package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Nacharbeit der Runs an einem Board (Issue #1756, Plan #1752): wie oft der Runner Pakete aus
 * In Progress zurückzog und Dokumente anhielt, gemessen an den Karten, die die Runs bearbeitet
 * haben. Gebildet von {@link NacharbeitErmittlung}.
 *
 * @param runsImZeitraum ob mindestens ein aufbewahrter Run in einem der zwölf Wochenfenster
 *     startete
 * @param pakete Zeile „Pakete zurückgezogen“
 * @param dokumente Zeile „Dokumente angehalten“
 * @param wochen die zwölf Wochenfenster, das älteste zuerst
 */
public record Nacharbeit(
    boolean runsImZeitraum, Zeile pakete, Zeile dokumente, List<Woche> wochen) {

  public Nacharbeit {
    wochen = List.copyOf(wochen);
  }

  /**
   * Eine Zeile der Platte über alle Wochen mit Daten (AK 4, 9, 14).
   *
   * @param datenbeginn ab wann die Gruppe Daten hat (E7); {@code null} ohne aufbewahrten Run
   * @param betroffen Zahl der Karten mit Signal, jede Karte höchstens einmal
   * @param bearbeitet Zahl der bearbeiteten Karten, jede Karte höchstens einmal
   * @param karten die Karten mit Signal, nach Anzahl absteigend, dann nach Nummer (E9)
   */
  public record Zeile(
      @Nullable Instant datenbeginn, int betroffen, int bearbeitet, List<Karteneintrag> karten) {

    public Zeile {
      karten = List.copyOf(karten);
    }
  }

  /**
   * Eine Karte der Kartenliste.
   *
   * @param nummer projektweite Nummer der Karte
   * @param anzahl wie oft es geschah: je Rückzug bzw. je Run mit Halt-Label (E9)
   */
  public record Karteneintrag(int nummer, String titel, int anzahl) {}

  /**
   * Ein Wochenfenster des Verlaufs (E6).
   *
   * @param beginn Beginn des Fensters
   */
  public record Woche(Instant beginn, Wochenwert pakete, Wochenwert dokumente) {}

  /** Wert einer Gruppe in einer Woche (AK 5, 14). */
  public record Wochenwert(Zustand zustand, int betroffen, int bearbeitet) {}

  /** Die drei sichtbar verschiedenen Zustände einer Woche (AK 14). */
  public enum Zustand {
    /** Das Fenster endet vor dem Datenbeginn der Gruppe. */
    KEINE_DATEN,
    /** Daten vorhanden, aber keine bearbeitete Karte der Gruppe. */
    KEINE_KARTE,
    /** Eine Quote aus betroffenen und bearbeiteten Karten. */
    QUOTE
  }
}
