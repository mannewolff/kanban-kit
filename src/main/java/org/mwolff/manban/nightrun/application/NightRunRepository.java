package org.mwolff.manban.nightrun.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;

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

  /**
   * Läufe <b>dieser Gattung</b> im Projekt, jüngster Startzeitpunkt zuerst; bei Gleichstand
   * entscheidet die ID.
   *
   * <p>Die Gattung ist seit Issue #1012 Teil der Frage und nicht optional: Seit sich Nachtläufe und
   * interaktive Sitzungen dieselbe Tabelle teilen, gibt es keine sinnvolle Liste über beide. Die
   * Nachtlauf-Seite fragt nach {@code NIGHT} und bekommt dieselben Ergebnisse wie vorher, auch wenn
   * im selben Projekt Sitzungen liegen — sonst stünde nach der ersten Sitzung eine Sitzung als
   * „letzter Lauf" da.
   */
  List<NightRun> findByProjectAndKindOrderByStartedAtDesc(long projectId, NightRunKind kind);

  /** Arbeitspakete der genannten Läufe, nach Lauf und Einfügereihenfolge sortiert. */
  List<NightRunItem> findItemsByRunIds(Collection<Long> runIds);

  /**
   * Verdrängt die Läufe <b>dieser Gattung</b> im Projekt jenseits der {@code keep} jüngsten
   * (Ringpuffer, Plan #718 A14; je Gattung getrennt seit Issue #1011, Plan #1007 E14).
   *
   * <p>Verdrängt wird innerhalb einer Gattung, und eine Gattung berührt die andere nie: Läufe der
   * anderen Gattung zählen weder mit noch fallen sie. Interaktive Sitzungen sind deutlich häufiger
   * als Nachtläufe — unter einer gemeinsamen Grenze räumten sie die Nachtlauf-Auswertung binnen
   * Tagen aus.
   *
   * @return Zahl der gelöschten Läufe
   */
  int deleteOlderThanNewest(long projectId, NightRunKind kind, int keep);

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
   * Kappt die <b>verwaisten</b> Arbeitspakete <b>dieser Gattung</b> im Projekt: behält die {@code
   * keep} jüngsten nach {@code started_at} und löscht die älteren (Issue #966; je Gattung getrennt
   * seit Issue #1011).
   *
   * <p>Pakete mit gesetzter {@code night_run_id} sind für diesen Aufruf unsichtbar — sie zählen
   * nicht mit und werden nicht gekappt. Eine Grenze über alle Pakete risse Löcher in Läufe, die der
   * Leitstand noch anzeigt.
   *
   * <p>Wie bei {@link #deleteOlderThanNewest} gilt die Grenze innerhalb einer Gattung, und eine
   * Gattung berührt die andere nie.
   *
   * @return Zahl der gelöschten Arbeitspakete
   */
  int deleteOrphanItemsOlderThanNewest(long projectId, NightRunKind kind, int keep);

  /**
   * Die Anläufe einer Karte über Läufe hinweg, jüngster Startzeitpunkt zuerst — einschließlich der
   * verwaisten Pakete verdrängter Läufe (Issue #967). Bei gleichem Startzeitpunkt entscheidet die
   * ID.
   *
   * <p><b>Beide Gattungen</b>, jede mit ihrer eigenen am Paket (Issue #1015): Anders als {@link
   * #findByProjectAndKindOrderByStartedAtDesc} filtert dieser Abruf nicht nach der Gattung, sondern
   * liefert sie mit. Auf dem Kartenblatt ist sie eine Eigenschaft des Anlaufs und keine Frage —
   * eine Karte wird nachts und am Tag angefasst. Ein verwaistes Paket trägt seine Gattung selbst,
   * seit {@code V34} die Spalte auch auf {@code night_run_item} führt.
   */
  List<NightRunItem> findByCard(long projectId, int cardNumber);

  /**
   * Zählt je Fehlerklasse die aufbewahrten Läufe <b>dieser Gattung</b> im Projekt, in denen sie
   * mindestens einmal vorkam. Ein Lauf zählt je Klasse höchstens einmal; verdrängte Läufe zählen
   * nicht mehr.
   *
   * <p>Die Gattung filtert aus demselben Grund wie bei {@link
   * #findByProjectAndKindOrderByStartedAtDesc} (Issue #1012): Die Platte „Abbruchgründe" der
   * Nachtlauf-Seite fragt nach {@code NIGHT}, und eine rote Sitzung trüge dieselbe Fehlerklasse —
   * sie würde die Zahl still erhöhen.
   */
  Map<NightRunErrorClass, Long> countRunsByErrorClass(long projectId, NightRunKind kind);
}
