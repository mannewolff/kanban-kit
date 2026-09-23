package org.mwolff.manban.nightrun.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunMode;

/**
 * Ausgehender Port des Plattform-Leitstands: offene Störungen über alle teilnehmenden Projekte und
 * die Quittung je Lauf (Issue #1080, Plan #1072 E1).
 *
 * <p><b>Eine Störung ist kein gespeicherter Datensatz.</b> Gespeichert wird allein die Quittung;
 * die Störung selbst entsteht bei jeder Abfrage neu aus den aufbewahrten Läufen. Das ist keine
 * Sparsamkeit, sondern die einzige Bauform, in der AK 12, 18 und 19 keine Regeln brauchen: Eine
 * Störung verschwindet mit ihrem Lauf, weil sie ohne ihn nicht mehr entsteht; sie erscheint beim
 * Anhaken auch für ältere Läufe, weil die Abfrage sie dann mitnimmt; und sie kommt beim
 * Wiederanhaken zurück, weil nichts sie gelöscht hat. Eine materialisierte Tabelle bräuchte dafür
 * einen Backfill und drei Aufräumwege.
 *
 * <p>Die Rechteprüfung liegt <b>beim Aufrufer</b>: {@link DisruptionService} verlangt die
 * Plattform-Rolle ADMIN, bevor er hier etwas holt.
 */
public interface DisruptionRepository {

  /**
   * Die Kandidaten für die Störungsliste, jüngster Lauf zuoberst (AK 13).
   *
   * <p>Gefiltert wird auf das, was die Datenbank entscheiden kann: Gattung {@code NIGHT}
   * (interaktive Sitzungen sind keine Nachtläufe), abgeschlossener Lauf, teilnehmendes Projekt und
   * fehlende Quittung. <b>Ob ein Lauf tatsächlich gestört ist</b>, entscheidet danach {@link
   * org.mwolff.manban.nightrun.domain.NightRunOutcome} — dieser Maßstab gehört in die Domäne und
   * nicht in SQL, sonst gäbe es ihn zweimal.
   */
  List<DisruptionCandidate> openCandidates();

  /**
   * Die Kandidaten der beiden Lauf-Bereiche des Leitstands, jüngster zuoberst (Issue #1094, Plan
   * #1088 E4; um den zweiten Zweig erweitert in Issue #1109).
   *
   * <p><b>Zwei Zweige, eine Abfrage.</b> Aufgenommen wird ein Lauf, wenn <em>eine</em> der beiden
   * Bedingungen zutrifft:
   *
   * <ol>
   *   <li><b>Er gehört zur Nacht:</b> Sein <em>Startzeitpunkt</em> liegt zwischen {@code from}
   *       einschließlich und {@code to} ausschließlich.
   *   <li><b>Er arbeitet noch:</b> Er hat sich nicht als abgeschlossen gemeldet, und sein letztes
   *       Lebenszeichen ist nicht älter als {@code jetzt} minus {@code stilleFrist} —
   *       <em>unabhängig vom Start</em>.
   * </ol>
   *
   * <p><b>Warum der zweite Zweig</b> (Issue #1109): Ein Lauf, der um 10:27 begann und über Mittag
   * arbeitet, gehört zur alten Nacht und fiele allein nach dem Start heraus. AK 1 der fachlichen
   * Quelle #1086 kennt für den Bereich der laufenden Läufe aber keine Nachtgrenze — sie zieht AK 9
   * ausdrücklich nur für die beendeten. Dass der zweite Zweig auch Läufe früherer Nächte liefert,
   * ist gewollt; die Nachtgrenze für die beendeten zieht der {@link DisruptionService}.
   *
   * <p><b>Die Frist filtert nur vor.</b> Ob ein Lauf läuft, entscheidet weiter allein {@link
   * org.mwolff.manban.nightrun.domain.NightRunOutcome} — derselbe Maßstab, derselbe Rand (genau
   * <em>auf</em> der Frist lebt der Lauf noch). Wichen beide voneinander ab, zeigte der Leitstand
   * einen Lauf, den die Auswertung des Projekts anders sieht (#1086 AK 8). Vorgefiltert wird
   * trotzdem, weil sonst jeder je unvollendet gebliebene Lauf aller Nächte anfiele.
   *
   * <p><b>Eine Abfrage für beide Bereiche</b>, laufende wie beendete, nicht zwei nach {@code
   * complete}: Ein Lauf, der nach der Stillefrist verstummt ist, trägt {@code complete = false} und
   * gehört trotzdem zu den beendeten. Zwei Abfragen zwängen den Dienst, die Frist ein zweites Mal
   * zu rechnen, um ihn umzusortieren; mit einer entscheidet allein der Befund. Aus demselben Grund
   * ist der zweite Zweig ein {@code OR} in derselben Abfrage und keine zweite daneben — ein Lauf,
   * der beide Bedingungen erfüllt, erscheint so genau einmal.
   *
   * <p>Auch <b>ohne</b> den Ausschluss quittierter Läufe: Das Quittieren sagt „gesehen" und ändert
   * den Ausgang eines Laufs nicht — die Nacht zeigt ihn weiter.
   *
   * @param from Beginn der Nacht, einschließlich
   * @param to Ende der Nacht, ausschließlich
   * @param jetzt Bezugszeitpunkt, gegen den das Lebenszeichen gemessen wird
   * @param stilleFrist die Stille, die ein unfertiger Lauf sich erlauben darf
   */
  List<DisruptionCandidate> candidatesOfNight(
      Instant from, Instant to, Instant jetzt, Duration stilleFrist);

  /**
   * Das Ziel einer Quittung: der Lauf, sofern er existiert <b>und</b> sein Projekt teilnimmt.
   *
   * <p>Beides zusammen, weil der Aufrufer beides gleich beantwortet — mit 404. Ein verdrängter Lauf
   * und ein abgehaktes Projekt sind für den Plattform-Leitstand dasselbe: nichts, das er quittieren
   * dürfte.
   */
  Optional<AckTarget> ackTarget(long nightRunId);

  /**
   * Schreibt die Quittung. Liegt sie schon vor, bleibt sie unverändert stehen — der erste
   * Quittierende bleibt vermerkt, und der zweite bekommt keinen Fehler (AK 8).
   */
  void acknowledge(long nightRunId, long userId, Instant at);

  /**
   * Ein Lauf mit allem, was der Maßstab braucht — als Störungs-Kandidat wie als Lauf einer Nacht.
   *
   * @param nightRunId Lauf-Id, zugleich die anklickbare Kennung der Störzeile
   * @param projectId Projekt des Laufs
   * @param projectName Projektname zum Zeitpunkt der Abfrage
   * @param mode Laufart des Laufs; der Maßstab braucht sie, weil bei einer Kette unter
   *     gleichrangigen Paketen das letzte maßgeblich ist (Issue #1123)
   * @param startedAt Startzeitpunkt des Laufs
   * @param updatedAt letztes Lebenszeichen des Laufs; {@code null}, wenn er nie fortgeschrieben
   *     wurde — der Upload-Weg lässt es bewusst leer, dort ist der Start das einzige Lebenszeichen
   * @param complete ob der Lauf sich als abgeschlossen gemeldet hat; über {@link #openCandidates()}
   *     stets {@code true}, weil jene Abfrage darauf filtert
   * @param noWorkReason Grund, warum der Lauf nichts abgearbeitet hat; {@code null}, wenn er
   *     gearbeitet hat
   * @param abortReason Grund, warum der Lauf hart abgebrochen ist (Issue #1143); {@code null}, wenn
   *     er nicht abbrach. Der Maßstab braucht ihn, weil ein abgebrochener Lauf nie gelingt — auch
   *     nicht mit lauter grünen Paketen
   */
  record DisruptionCandidate(
      long nightRunId,
      long projectId,
      String projectName,
      NightRunMode mode,
      Instant startedAt,
      @Nullable Instant updatedAt,
      boolean complete,
      @Nullable String noWorkReason,
      @Nullable String abortReason) {}

  /** Der Lauf, auf den sich eine Quittung bezieht. */
  record AckTarget(long nightRunId, long projectId) {}
}
