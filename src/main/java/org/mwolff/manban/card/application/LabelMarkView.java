package org.mwolff.manban.card.application;

/**
 * Ein Label als Marke im Herkunftsbaum: Name als Text, Farbe als Chip-Fläche.
 *
 * <p>Beides und nicht nur der Name (Entscheidung Manne, 2026-08-31): Nur Namen zu übertragen wäre
 * schmaler, ließe aber Baum und Vorhaben-Kachel unterschiedlich aussehen.
 */
public record LabelMarkView(String name, String color) {}
