package org.mwolff.manban.ratelimit.domain;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Unveränderlicher Zustand einer Herkunft für einen einzelnen Vorgang (Anmeldung, Registrierung,
 * Reset-Anforderung) samt der Zeitregeln darauf — Issue #896, Plan #892 (E10).
 *
 * <p><strong>Warum die Sperruhr erst beim abgewiesenen Versuch startet:</strong> Das Erreichen von
 * {@link Limits#maxAttempts()} macht die Grenze aus<em>geschöpft</em>, aber noch nicht wirksam —
 * der Versuch, der die Grenze erreicht, wird selbst noch regulär beantwortet. Erst der erste
 * tatsächlich abgewiesene Versuch setzt {@link #blockedUntil()} auf {@code jetzt + blockDuration},
 * und zwar genau einmal (AK 2 aus #840). Startete die Uhr schon beim Erreichen der Grenze, wäre
 * eine Sperre bei langsamer Versuchsfolge bereits halb abgelaufen, bevor der Absender überhaupt
 * eine Abweisung gesehen hat. Weitere Versuche während der Sperre verschieben sie <em>nicht</em> —
 * ein gleitendes Fenster wäre genau die Waffe, vor der das Ziel warnt: Ein Angreifer könnte die
 * Sperre eines Opfers beliebig lange offen halten.
 *
 * <p>Die Klasse ist framework-frei und zieht die Zeit nicht selbst: Jede Methode bekommt den
 * Zeitpunkt herein, die Grenzwerte ebenso. Erst die Fassade darüber kennt {@code Clock} und
 * Konfiguration.
 *
 * @param attempts Zahl der im laufenden Zählfenster gezählten Versuche
 * @param windowStartedAt Beginn des laufenden Zählfensters
 * @param blockedUntil Ende der Sperre; {@code null}, solange keine Sperre läuft oder lief
 */
public record OriginAttempts(
    int attempts, Instant windowStartedAt, @Nullable Instant blockedUntil) {

  /**
   * Grenzwerte eines Vorgangs. Sie kommen von außen herein — dieses Paket kennt keine
   * Konfiguration.
   *
   * @param maxAttempts Zahl der Versuche, ab der die Grenze ausgeschöpft ist
   * @param window Dauer des Zählfensters
   * @param blockDuration Dauer der Sperre ab dem ersten abgewiesenen Versuch
   */
  public record Limits(int maxAttempts, Duration window, Duration blockDuration) {}

  /** Unbelasteter Zustand: kein Versuch gezählt, keine Sperre. */
  public static OriginAttempts none(Instant now) {
    return new OriginAttempts(0, now, null);
  }

  /** Ob zu {@code now} eine Sperre läuft. Der Ablaufzeitpunkt selbst ist nicht mehr gesperrt. */
  public boolean isBlocked(Instant now) {
    return blockedUntil != null && now.isBefore(blockedUntil);
  }

  /** Restdauer der laufenden Sperre; {@link Duration#ZERO}, wenn keine läuft. */
  public Duration retryAfter(Instant now) {
    if (!isBlocked(now)) {
      return Duration.ZERO;
    }
    return Duration.between(now, blockedUntil);
  }

  /**
   * Zählt einen Versuch. Während einer laufenden Sperre bleibt der Zustand unberührt — abgewiesene
   * Versuche zählen nicht mit, sonst verlängerte jeder Anklopfer seine eigene Sperre.
   */
  public OriginAttempts recordAttempt(Instant now, Limits limits) {
    if (isBlocked(now)) {
      return this;
    }
    OriginAttempts current = currentWindow(now, limits);
    return new OriginAttempts(current.attempts + 1, current.windowStartedAt, null);
  }

  /**
   * Verhängt die Sperre, sofern die Grenze im laufenden Fenster ausgeschöpft ist — der Aufrufer
   * meldet damit einen abgewiesenen Versuch, ohne die Zeitregel selbst zu kennen. Läuft bereits
   * eine Sperre, bleibt ihr Ende unverändert; ist die Grenze nicht ausgeschöpft, entsteht keine.
   */
  public OriginAttempts block(Instant now, Limits limits) {
    if (isBlocked(now)) {
      return this;
    }
    OriginAttempts current = currentWindow(now, limits);
    if (current.attempts < limits.maxAttempts()) {
      return current;
    }
    return new OriginAttempts(
        current.attempts, current.windowStartedAt, now.plus(limits.blockDuration()));
  }

  /**
   * Der Zustand, auf dem ein Versuch zu {@code now} zählt.
   *
   * <p>Setzt voraus, dass zu {@code now} keine Sperre läuft (beide Aufrufer prüfen das vorher). Ein
   * gesetztes {@code blockedUntil} bedeutet hier also: Die Sperre ist abgelaufen — und dann wird
   * der Zustand <em>vollständig</em> zurückgesetzt, sonst löste der erste Versuch nach dem Ablauf
   * sofort die nächste Sperre aus. Ohne Sperre entscheidet allein das Zählfenster.
   */
  private OriginAttempts currentWindow(Instant now, Limits limits) {
    if (blockedUntil != null || !now.isBefore(windowStartedAt.plus(limits.window()))) {
      return none(now);
    }
    return this;
  }
}
