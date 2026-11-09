package org.mwolff.manban.benachrichtigung.application;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;

/**
 * Port für Anlässe und Zustellungen (Issue #2077, Plan #2070 E4).
 *
 * <p>Die Eindeutigkeit hält die Datenbank, nicht der Aufrufer: Wer eine vorhandene Zeile anlegen
 * will, bekommt keinen Fehler, auch bei parallelen Takten. Der Port prüft keine Rechte.
 */
public interface AnlassRepository {

  /**
   * Ein Anlass aus einem Lauf.
   *
   * @param art worüber die Mail informiert
   * @param projectId das Projekt des Laufs
   * @param laufId der Lauf
   * @param cardId die zugeordnete Karte; {@code null} ohne Karte
   * @param eingetretenAt der Zeitpunkt aus den Daten des Laufs
   * @param angelegtAt der Zeitpunkt des Takts
   */
  record LaufAnlass(
      AnlassArt art,
      long projectId,
      long laufId,
      @Nullable Long cardId,
      Instant eingetretenAt,
      Instant angelegtAt) {}

  /**
   * Ein Anlass aus einem Label-Ereignis.
   *
   * @param art worüber die Mail informiert
   * @param projectId das Projekt der Karte
   * @param labelEventId die Zeile aus {@code card_label_event}
   * @param cardId die Karte
   * @param eingetretenAt der Zeitpunkt des Ereignisses
   * @param angelegtAt der Zeitpunkt des Takts
   */
  record LabelAnlass(
      AnlassArt art,
      long projectId,
      long labelEventId,
      long cardId,
      Instant eingetretenAt,
      Instant angelegtAt) {}

  /**
   * Eine Zustellung an eine Person; sie beginnt im Zustand {@code OFFEN}.
   *
   * @param anlassId der Anlass
   * @param userId die Person
   * @param faelligAb ab wann die Mail gehen darf
   * @param verschoben wahr, wenn die Ruhezeit den Zeitpunkt verschoben hat
   */
  record NeueZustellung(long anlassId, long userId, Instant faelligAb, boolean verschoben) {}

  /**
   * Legt den Anlass an, wenn es zu Lauf und Art noch keinen gibt.
   *
   * @return die Kennung des neuen Anlasses, leer, wenn es ihn schon gab
   */
  Optional<Long> legeAnlassZumLaufAn(LaufAnlass anlass);

  /**
   * Legt den Anlass an, wenn es zum Label-Ereignis noch keinen gibt.
   *
   * @return die Kennung des neuen Anlasses, leer, wenn es ihn schon gab
   */
  Optional<Long> legeAnlassZumLabelAn(LabelAnlass anlass);

  /** Ob es zur Karte schon einen Anlass dieser Art gibt, aus einem Lauf oder einem Label. */
  boolean gibtAnlassZurKarte(long cardId, AnlassArt art);

  /** Legt die Zustellung an; gibt es zu Anlass und Person schon eine, geschieht nichts. */
  void legeZustellungAn(NeueZustellung zustellung);
}
