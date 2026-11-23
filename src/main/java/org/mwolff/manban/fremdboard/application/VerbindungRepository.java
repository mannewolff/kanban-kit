package org.mwolff.manban.fremdboard.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.fremdboard.domain.Verbindung;

/**
 * Port der gespeicherten Verbindung je Projekt (Issue #2156, Plan #2152). Prüft keine Rechte; das
 * tut der Anwendungsfall, der ihn ruft.
 */
public interface VerbindungRepository {

  /** Legt die Verbindung des Projekts an oder ersetzt sie. */
  void speichere(Verbindung verbindung);

  /** Die Verbindung des Projekts; leer, wenn das Projekt kein fremdes Board hat. */
  Optional<Verbindung> finde(long projectId);

  /** Ob das Projekt eine Verbindung hat. */
  boolean existiert(long projectId);

  /**
   * Die Projekte, deren letzter Abgleich nicht nach {@code grenze} liegt oder die noch keinen
   * hatten, der am längsten zurückliegende zuerst (Issue #2168, Plan #2152 A7).
   */
  List<Long> faellige(Instant grenze);

  /**
   * Beansprucht den Abgleich des Projekts für diesen Knoten: setzt {@code letzter_abgleich_am} auf
   * {@code jetzt}, sofern dort noch {@code bisher} steht (A7). Läuft in einer eigenen kurzen
   * Transaktion unter einer Sperre je Projekt; von zwei Knoten mit demselben Stand gewinnt einer.
   *
   * @param bisher der beim Lesen gesehene Zeitpunkt des letzten Abgleichs; {@code null} vor dem
   *     ersten
   * @return {@code true}, wenn dieser Knoten den Abgleich übernimmt
   */
  boolean beanspruchen(long projectId, @Nullable Instant bisher, Instant jetzt);

  /**
   * Vermerkt einen gelungenen Abgleich: Der Fehler verschwindet, {@code letzter_abgleich_am} bleibt
   * beim Anspruch, und {@code stand_am} — die Quelle des Board-Hinweises (Issue #2222, Plan #2221
   * E4) — rückt auf {@code beansprucht} vor, fällt aber bei einem älteren Anspruch nicht zurück.
   *
   * @param beansprucht der Zeitpunkt, den dieser Knoten beim Anspruch gesetzt hat
   * @param vollabgleichAm Zeitpunkt des Anspruchs, wenn es ein Vollabgleich war; sonst {@code null}
   */
  void abgleichGelungen(long projectId, Instant beansprucht, @Nullable Instant vollabgleichAm);

  /**
   * Vermerkt einen gescheiterten Abgleich (A9): Der Fehler steht an der Verbindung, und {@code
   * letzter_abgleich_am} kehrt auf {@code bisher} zurück. {@code stand_am}, die Quelle des
   * Board-Hinweises, bleibt unverändert — der Stand ist so alt wie der letzte gelungene Abgleich.
   * Hat inzwischen ein anderer Knoten neu beansprucht, bleibt dessen Anspruch.
   *
   * @param beansprucht der Zeitpunkt, den dieser Knoten beim Anspruch gesetzt hat
   */
  void abgleichGescheitert(
      long projectId, Instant beansprucht, @Nullable Instant bisher, String fehler);
}
