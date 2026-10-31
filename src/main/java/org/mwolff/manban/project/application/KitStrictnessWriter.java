package org.mwolff.manban.project.application;

/**
 * Schreibender Port für die zuletzt vom Kit gemeldete Strenge-Stufe {@code project.kit_strictness}
 * (Issue #2014). Das Kit schickt sie an jeder Anfrage ans Board im Header {@code X-Kit-Strictness};
 * ein Filter im {@code accesstoken}-Modul legt sie damit am Projekt ab, an das das Token gebunden
 * ist. Gebaut nach dem Vorbild von {@link InteractiveUsageSinceWriter}.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte. Der
 * Aufrufer <em>muss</em> das Projekt aus einem gültigen, an genau dieses Projekt gebundenen
 * Zugriffs-Token haben — verbindlich ist {@code KanbanPrincipal.projectId} der laufenden
 * Authentifizierung, wie im {@code KitStrictnessFilter}.
 *
 * <p>Der Aufruferkreis ist deshalb maschinell begrenzt: {@code
 * PortWhitelistArchitectureTest.KIT_STRICTNESS_PORTS_HABEN_AUFRUFER_WHITELIST} lässt ausschließlich
 * {@code project.application}, {@code project.infrastructure} und {@code accesstoken.web.security}
 * zu.
 */
@FunctionalInterface
public interface KitStrictnessWriter {

  /**
   * Legt die gemeldete Stufe am Projekt ab, wenn sie sich vom bisherigen Wert unterscheidet; ein
   * unbekanntes Projekt ist ein No-Op.
   *
   * @param value {@code light}, {@code normal} oder {@code strict} — der Aufrufer hat den Wert
   *     bereits geprüft und normalisiert
   */
  void reportStrictness(long projectId, String value);
}
