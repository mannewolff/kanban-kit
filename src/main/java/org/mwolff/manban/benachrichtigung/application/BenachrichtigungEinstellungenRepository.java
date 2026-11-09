package org.mwolff.manban.benachrichtigung.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

  /**
   * Eine Person mit eingeschaltetem Schalter für ein Projekt.
   *
   * @param userId die Person
   * @param seit wann sie ihn eingeschaltet hat
   */
  record EingeschaltetePerson(long userId, Instant seit) {}

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

  /** Alle Personen, die ihren Schalter für das Projekt eingeschaltet haben. */
  List<EingeschaltetePerson> eingeschaltetePersonen(long projectId);

  Optional<Ruhezeit> ruhezeit(long userId);

  /** Legt die Ruhezeit der Person an oder ersetzt sie. */
  void speichereRuhezeit(long userId, Ruhezeit ruhezeit);

  void loescheRuhezeit(long userId);
}
