package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Ausgehender Port für die Label-Zuordnung einer Karte. */
public interface CardLabelRepository {

  /** Ersetzt die Labels der Karte vollständig durch die übergebenen Label-IDs. */
  void replaceLabels(long cardId, List<Long> labelIds);

  /**
   * Fügt genau eine Zuordnung hinzu und lässt alle übrigen unangetastet (#574). Idempotent: eine
   * bereits vorhandene Zuordnung ist kein Fehler.
   *
   * <p>Die Idempotenz gehört in die Datenbank, nicht in einen vorgelagerten Existenztest — zwischen
   * Prüfung und Einfügen läge sonst ein offenes Rennen. Der Primärschlüssel {@code (card_id,
   * label_id)} trägt das bereits.
   *
   * @return {@code true}, wenn eine Zeile entstanden ist; {@code false}, wenn sie schon existierte
   */
  boolean addLabel(long cardId, long labelId);

  /**
   * Entfernt genau eine Zuordnung und lässt alle übrigen unangetastet (#574). Idempotent: eine
   * nicht vorhandene Zuordnung ist kein Fehler.
   *
   * @return {@code true}, wenn eine Zeile verschwunden ist; {@code false}, wenn keine existierte
   */
  boolean removeLabel(long cardId, long labelId);

  /** Label-IDs der Karte, aufsteigend. */
  List<Long> findByCardId(long cardId);

  /**
   * Label-IDs mehrerer Karten in einer einzigen Batch-Abfrage ({@code IN}-Clause), je Karte
   * aufsteigend. Karten ohne Labels fehlen im Ergebnis; leere Eingabe liefert eine leere Map.
   */
  Map<Long, List<Long>> findByCardIds(Collection<Long> cardIds);
}
