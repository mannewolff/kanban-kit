package org.mwolff.manban.ratelimit.application;

/**
 * Die Vorgänge, die die Zählbremse begrenzt — Anmeldung, Registrierung und Reset-Anforderung (Plan
 * #892, fachlich #840).
 *
 * <p>Der Pfad steht am Vorgang und nicht am Filter: Die Registrierung des Filters und die Zuordnung
 * eines Requests zum Vorgang griffen sonst auf zwei getrennt gepflegte Listen zu, und eine davon
 * wäre irgendwann falsch.
 *
 * <p>Ob nur Fehlschläge zählen, unterscheidet die Anmeldung von den beiden anderen. Bei der
 * Anmeldung ist der Fehlversuch das Angriffsmuster, und ein Formfehler ist kein Passwortversuch
 * (E4). Bei Registrierung und Reset-Anforderung entsteht der Schaden gerade durch die
 * <em>erfolgreichen</em> Aufrufe — angelegte Konten, versandte Mails —, und aus Absendersicht
 * antworten beide ohnehin neutral (E5).
 */
public enum RateLimitedOperation {
  LOGIN("/api/auth/login", true),
  REGISTER("/api/auth/register", false),
  FORGOT("/api/auth/forgot", false);

  private final String requestPath;
  private final boolean failuresOnly;

  RateLimitedOperation(String requestPath, boolean failuresOnly) {
    this.requestPath = requestPath;
    this.failuresOnly = failuresOnly;
  }

  /** Der Pfad, unter dem der Vorgang angesprochen wird. */
  public String path() {
    return requestPath;
  }

  /** Ob nur abgewiesene Aufrufe zählen ({@code true}) oder jeder Aufruf ({@code false}). */
  public boolean countsOnlyFailures() {
    return failuresOnly;
  }
}
