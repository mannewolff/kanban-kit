package org.mwolff.manban.nightrun.application;

import java.time.Instant;
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

  /**
   * Schreibt einen gemeldeten Lauf und <b>ersetzt</b> einen vorhandenen vollständig (Issue #945).
   *
   * <p>Die übergebenen Werte sind der <b>vollständige Stand</b> des Laufs: Was diese Meldung nicht
   * mehr führt, ist danach fort. Eine meldende Kette schickt denselben Lauf mehrfach, jedes Mal mit
   * allem, was sie bis dahin weiß — die spätere Meldung gilt.
   *
   * <p><b>Abgrenzung zu {@link #insertIfAbsent}:</b> Dort gewinnt der erste Stand und bleibt
   * unangetastet. Das ist für den Upload-Weg richtig, denn ein hochgeladenes Protokoll ist ärmer
   * als ein maschineller Stand und darf ihn nicht plätten. Für die Meldung wäre es falsch: Ein
   * unvollständiger Zwischenstand blockierte den späteren vollständigen dauerhaft.
   *
   * <p>{@code created_at} bleibt beim Ersetzen unangetastet — es trägt, seit wann der Lauf am Board
   * steht, nicht wann er zuletzt gemeldet wurde.
   */
  UpsertResult upsert(NightRun run, List<NightRunItem> items);

  /**
   * Ergebnis eines {@link #upsert}.
   *
   * @param id die Kennung des Laufs — beim Ersetzen dieselbe wie bei der ersten Meldung
   * @param created {@code true}, wenn der Lauf angelegt wurde; {@code false}, wenn er ersetzt wurde
   */
  record UpsertResult(long id, boolean created) {}

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
   * Löscht die <b>verwaisten</b> Arbeitspakete eines Laufs — die mit diesem Projekt und diesem
   * Startzeitpunkt, deren Lauf verdrängt wurde (Issue #965).
   *
   * <p>Pakete eines noch vorhandenen Laufs berührt der Aufruf nie; die ersetzt {@link #upsert}
   * selbst. Gerufen wird er, bevor ein Lauf angelegt wird: Ein verdrängter Lauf, der wiederkommt,
   * bringt seinen vollständigen Stand mit, und {@code ON CONFLICT} kennt nur den Lauf-Kopf — ohne
   * diesen Schritt stünden seine Pakete danach doppelt da.
   *
   * @return Zahl der gelöschten Arbeitspakete
   */
  int deleteOrphanItemsOfRun(long projectId, Instant startedAt);

  /**
   * Kappt die <b>verwaisten</b> Arbeitspakete des Projekts: behält die {@code keep} jüngsten nach
   * {@code started_at} und löscht die älteren (Issue #966).
   *
   * <p>Pakete mit gesetzter {@code night_run_id} sind für diesen Aufruf unsichtbar — sie zählen
   * nicht mit und werden nicht gekappt. Eine Grenze über alle Pakete risse Löcher in Läufe, die der
   * Leitstand noch anzeigt.
   *
   * @return Zahl der gelöschten Arbeitspakete
   */
  int deleteOrphanItemsOlderThanNewest(long projectId, int keep);

  /**
   * Die Anläufe einer Karte über Läufe hinweg, jüngster Startzeitpunkt zuerst — einschließlich der
   * verwaisten Pakete verdrängter Läufe (Issue #967). Bei gleichem Startzeitpunkt entscheidet die
   * ID.
   */
  List<NightRunItem> findByCard(long projectId, int cardNumber);

  /**
   * Zählt je Fehlerklasse die aufbewahrten Läufe des Projekts, in denen sie mindestens einmal
   * vorkam. Ein Lauf zählt je Klasse höchstens einmal; verdrängte Läufe zählen nicht mehr.
   */
  Map<NightRunErrorClass, Long> countRunsByErrorClass(long projectId);
}
