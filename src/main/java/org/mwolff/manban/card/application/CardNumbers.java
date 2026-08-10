package org.mwolff.manban.card.application;

/**
 * Fachliche Grenzen projektweiter Kartennummern.
 *
 * <p>Die Obergrenze schützt die Nummernvergabe vor einem Überlauf: {@code nextCardNumber} rechnet
 * {@code MAX(number) + 1} auf einer {@code integer}-Spalte. Ein einziger Import mit {@link
 * Integer#MAX_VALUE} — oder eine so gesetzte Projekt-Startnummer — ließe jede folgende automatische
 * Anlage in diesem Projekt mit einem Datenbankfehler enden. Der Wert wäre danach nur noch per SQL
 * zu korrigieren.
 *
 * <p>Eine Million liegt weit über allem, was ein Board fachlich erreicht (das grösste bekannte
 * Projekt liegt im niedrigen vierstelligen Bereich), und lässt gleichzeitig drei Zehnerpotenzen
 * Luft bis zur technischen Grenze — auch eine Migration mit grossen Fremdnummern passt hinein.
 *
 * <p>Die Grenze gilt bewusst für <strong>beide</strong> Wege, auf denen eine Nummer von aussen
 * gesetzt werden kann: den Import mit vorgegebener Nummer und die Projekt-Startnummer. Sie nur an
 * einer Stelle zu ziehen, machte das Verhalten uneinheitlich und liesse die Lücke offen.
 */
public final class CardNumbers {

  /** Grösste zulässige projektweite Kartennummer. */
  public static final int MAX = 1_000_000;

  private CardNumbers() {}
}
