package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Card;

/**
 * Schmaler Port neben {@link CardRepository} für Auskünfte über den Kartenbestand, die der Record
 * {@link org.mwolff.manban.card.domain.Card} nicht trägt (Plan #1574, E17). Eigener Port, weil
 * {@link CardRepository} an der PMD-Grenze {@code TooManyMethods} steht (A6).
 *
 * <p>Die Spalte {@code card.derived_from_lost_number} (V51) bewahrt die Nummer eines endgültig
 * gelöschten Vorfahren (E12): Sie wird nur unmittelbar vor einem Purge geschrieben und nur gelesen,
 * wenn {@code derived_from_card_id} {@code NULL} ist. Das Zurücksetzen beim Neusetzen der Herkunft
 * übernimmt {@link CardRepository#save}.
 */
public interface CardBestandRepository {

  /**
   * Verlorene Herkunftsnummern der Karten eines Boards: Schlüssel ist die Karten-ID, Wert die
   * Nummer des endgültig gelöschten Vorfahren. Enthält nur Karten ohne gesetzte Herkunft — eine
   * Karte mit {@code derived_from_card_id} erscheint nie.
   */
  Map<Long, Integer> verloreneHerkunftsnummern(long boardId);

  /**
   * Bewahrt an allen Kindern der genannten Karten die Nummer des Vorfahren (E18). Muss unmittelbar
   * vor dem endgültigen Löschen laufen, weil die FK {@code fk_card_derived_from} die Herkunft dann
   * auf {@code NULL} setzt. Eine leere Sammlung ist ein No-op.
   */
  void bewahreHerkunftsnummern(Collection<Long> ids);

  /**
   * Fundstelle eines Suchbegriffs je Karte des Boards ohne Papierkorb, archivierte eingeschlossen
   * (Plan #1574, E3, E4, E17): {@code TITLE}, wenn er im Titel steht, sonst {@code BODY}, wenn er
   * im Text steht; Karten ohne Fundstelle fehlen. Abgeglichen wird in der Datenbank mit {@code
   * strpos(lower(feld), lower(q)) > 0} — {@code %} und {@code _} gelten wörtlich.
   *
   * @param q Suchbegriff, getrimmt und nicht leer
   */
  Map<Long, String> textFundstellenAufBoard(long boardId, String q);

  /**
   * Karten zu IDs samt den Auskünften, die der Record nicht trägt (Issue #1930): ob sie im
   * Papierkorb liegen und die verlorene Herkunftsnummer. Liefert <strong>alle</strong> Treffer,
   * archivierte und Papierkorb-Karten eingeschlossen, über Board- und Projektgrenzen hinweg; eine
   * unbekannte ID fehlt. Eine leere Sammlung ist ein No-op.
   */
  List<KarteImBestand> findImBestand(Collection<Long> ids);

  /**
   * Eine Karte mit ihren Bestandsauskünften.
   *
   * @param karte die Karte
   * @param imPapierkorb ob {@code deleted_at} gesetzt ist
   * @param verloreneHerkunftsnummer Nummer des endgültig gelöschten Vorfahren; nur gesetzt, wenn
   *     {@code derivedFromCardId} {@code null} ist
   */
  record KarteImBestand(
      Card karte, boolean imPapierkorb, @Nullable Integer verloreneHerkunftsnummer) {}
}
