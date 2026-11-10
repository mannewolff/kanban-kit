package org.mwolff.manban.project.application;

/**
 * Schreibender Port für die zuletzt vom Kit gemeldete Einstellung zur Prüfung vor Ready (Issue
 * #2112, Plan #2108 E18). Das Kit schickt sie im Header {@code X-Kit-Review-Before-Ready}; der
 * {@code KitStrictnessFilter} legt sie am Projekt ab, an das das Token gebunden ist.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte. Der
 * Aufrufer <em>muss</em> das Projekt aus einem gültigen, an genau dieses Projekt gebundenen
 * Zugriffs-Token haben. Der Aufruferkreis ist deshalb in {@code
 * PortWhitelistArchitectureTest.KIT_REVIEW_VOR_READY_WRITER_NUR_IM_FILTER} maschinell begrenzt.
 */
@FunctionalInterface
public interface KitReviewVorReadyWriter {

  /**
   * Legt den gemeldeten Wert am Projekt ab, wenn er sich vom bisherigen unterscheidet; ein
   * unbekanntes Projekt ist ein No-Op.
   */
  void reportReviewBeforeReady(long projectId, boolean value);
}
