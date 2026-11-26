package org.mwolff.manban.codeanalyse.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.codeanalyse.domain.Messung;
import org.mwolff.manban.codeanalyse.domain.Zustand;

/**
 * Eine Messung, wie das Workflow-Kit sie meldet (Issue #2275, Plan #2272 Vertrag V1, E8). Anders
 * als {@link Messung} darf sie bei {@code zaehlt} einen Grund tragen; den nimmt {@link
 * #alsMessung()} weg, weil eine zählende Messung keinen Grund speichert.
 *
 * @param publishedCommit Kennung des veröffentlichten Stands
 * @param publishedAt Zeitpunkt der Veröffentlichung
 * @param baseCommit Stand, gegen den gemessen wurde
 * @param kitVersion Fassung des Workflow-Kits
 * @param zaehlt ob das Kit die Messung als zählend meldet
 * @param grund gemeldeter Grund; bei einer zählenden Messung ohne Wirkung
 * @param karten Zahl der Karten der Veröffentlichung
 * @param vorher Zustand vor der Codeanalyse
 * @param endzustand Zustand des veröffentlichten Stands
 */
public record GemeldeteMessung(
    String publishedCommit,
    Instant publishedAt,
    String baseCommit,
    String kitVersion,
    boolean zaehlt,
    @Nullable String grund,
    int karten,
    @Nullable Zustand vorher,
    @Nullable Zustand endzustand) {

  /**
   * Die zu speichernde Messung: bei {@code zaehlt} ohne Grund (E8), sonst mit dem Grund, wie er
   * gemeldet wurde, auch einem unbekannten (AK 3).
   *
   * @throws IllegalArgumentException wenn die Meldung die Regeln von {@link Messung} verletzt
   */
  Messung alsMessung() {
    return new Messung(
        publishedCommit,
        publishedAt,
        baseCommit,
        kitVersion,
        zaehlt,
        zaehlt ? null : grund,
        karten,
        vorher,
        endzustand);
  }
}
