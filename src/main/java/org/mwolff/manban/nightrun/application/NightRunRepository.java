package org.mwolff.manban.nightrun.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;

/** Ausgehender Port für die Persistenz der Nachtlauf-Auswertungen (Issue #721). */
public interface NightRunRepository {

  /**
   * Legt den Lauf samt seiner Arbeitspakete an, sofern {@code (projectId, startedAt)} noch frei
   * ist.
   *
   * <p>Lauf und Arbeitspakete sind ein Vorgang und keine zwei: Die frisch vergebene ID ist der
   * Fremdschlüssel der Pakete und verließe den Port sonst nur, um sofort wieder hineingereicht zu
   * werden. Umgesetzt als {@code INSERT … ON CONFLICT (project_id, started_at) DO NOTHING RETURNING
   * id} (Plan #718, A11) — bewusst ohne vorgelagertes {@code SELECT} und ohne gefangene
   * Constraint-Verletzung: Die Prüfung hätte ein Rennen, und die Verletzung risse die fachliche
   * Transaktion des Aufrufers mit zurück.
   *
   * @return die vergebene ID; leer, wenn der Lauf schon vorlag — dann bleibt der vorhandene Lauf
   *     unverändert und es werden keine Arbeitspakete geschrieben
   */
  Optional<Long> insertIfAbsent(NightRun run, List<NightRunItem> items);

  /** Läufe des Projekts, jüngster Startzeitpunkt zuerst; bei Gleichstand entscheidet die ID. */
  List<NightRun> findByProjectOrderByStartedAtDesc(long projectId);

  /** Arbeitspakete der genannten Läufe, nach Lauf und Einfügereihenfolge sortiert. */
  List<NightRunItem> findItemsByRunIds(Collection<Long> runIds);

  /**
   * Verdrängt die Läufe des Projekts jenseits der {@code keep} jüngsten (Ringpuffer, Plan #718
   * A14).
   *
   * @return Zahl der gelöschten Läufe
   */
  int deleteOlderThanNewest(long projectId, int keep);

  /**
   * Zählt je Fehlerklasse die aufbewahrten Läufe des Projekts, in denen sie mindestens einmal
   * vorkam. Ein Lauf zählt je Klasse höchstens einmal; verdrängte Läufe zählen nicht mehr.
   */
  Map<NightRunErrorClass, Long> countRunsByErrorClass(long projectId);
}
