package org.mwolff.manban.codeanalyse.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die Messung der Codeanalyse zu genau einer Veröffentlichung eines Projekts (Issue #2273, Plan
 * #2272 E7, E9; Fachplan #2271 AK 16). Eine zählende Messung trägt beide Zustände und keinen Grund,
 * einer nicht zählenden dürfen Zustände fehlen, ihr Grund ist Pflicht.
 *
 * @param publishedCommit Kennung des veröffentlichten Stands
 * @param publishedAt Zeitpunkt der Veröffentlichung; er bestimmt die Woche, nicht der Eingang
 * @param baseCommit Stand, gegen den die Änderung gemessen wurde
 * @param kitVersion Fassung des Workflow-Kits, das gemessen hat
 * @param zaehlt ob das Kit die Messung als zählend meldet
 * @param grund warum sie nicht zählt; {@code null} genau dann, wenn sie zählt
 * @param karten Zahl der Karten der Veröffentlichung
 * @param vorher Zustand vor der Codeanalyse; Pflicht, wenn sie zählt
 * @param endzustand Zustand des veröffentlichten Stands; Pflicht, wenn sie zählt
 */
public record Messung(
    String publishedCommit,
    Instant publishedAt,
    String baseCommit,
    String kitVersion,
    boolean zaehlt,
    @Nullable String grund,
    int karten,
    @Nullable Zustand vorher,
    @Nullable Zustand endzustand) {

  public Messung {
    if (karten < 0) {
      throw new IllegalArgumentException("karten darf nicht negativ sein");
    }
    if (zaehlt) {
      if (grund != null) {
        throw new IllegalArgumentException("Eine zählende Messung trägt keinen Grund");
      }
      if (vorher == null) {
        throw new IllegalArgumentException("Eine zählende Messung braucht den Zustand vorher");
      }
      if (endzustand == null) {
        throw new IllegalArgumentException("Eine zählende Messung braucht den Endzustand");
      }
    } else if (grund == null || grund.isBlank()) {
      throw new IllegalArgumentException("Eine nicht zählende Messung braucht einen Grund");
    }
  }
}
