package org.mwolff.manban.benachrichtigung.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.mwolff.manban.benachrichtigung.domain.Ruhezeit;

/**
 * Port für Schalter und Ruhezeit (Issue #2076, Plan #2070): Je eingeschaltetem Schalter eine Zeile
 * mit dem Einschaltzeitpunkt, keine Zeile heißt ausgeschaltet.
 *
 * <p>Einschalten lässt eine vorhandene Zeile stehen, auch bei gleichzeitigen Aufrufen; Ausschalten
 * einer fehlenden Zeile ist kein Fehler. Der Port prüft keine Rechte, das tut {@link
 * BenachrichtigungEinstellungen}.
 */
public interface BenachrichtigungEinstellungenRepository {

  Optional<Instant> projektEingeschaltetSeit(long projectId);

  void schalteProjektEin(long projectId, Instant seit);

  void schalteProjektAus(long projectId);

  /** Die eingeschalteten unter den genannten Projekten. */
  Set<Long> eingeschalteteProjekte(Collection<Long> projectIds);

  Optional<Instant> personEingeschaltetSeit(long userId, long projectId);

  void schaltePersonEin(long userId, long projectId, Instant seit);

  void schaltePersonAus(long userId, long projectId);

  /** Alle Projekte, für die die Person ihren Schalter eingeschaltet hat. */
  Set<Long> eingeschalteteProjekteDerPerson(long userId);

  Optional<Ruhezeit> ruhezeit(long userId);

  /** Legt die Ruhezeit der Person an oder ersetzt sie. */
  void speichereRuhezeit(long userId, Ruhezeit ruhezeit);

  void loescheRuhezeit(long userId);
}
