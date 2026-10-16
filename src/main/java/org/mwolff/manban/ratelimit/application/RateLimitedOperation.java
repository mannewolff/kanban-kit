package org.mwolff.manban.ratelimit.application;

/**
 * Die Vorgänge, die die Zählbremse begrenzt — Anmeldung, Registrierung und Reset-Anforderung (Plan
 * #892, fachlich #840), dazu Anforderung, Eingabe und Link der Anmeldung per Einmalcode (Plan #1801
 * A7, fachlich #1781).
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
 *
 * <p>Die Code-Anmeldung folgt demselben Schnitt: Die Anforderung verschickt Mails und zählt jeden
 * Aufruf, Eingabe und Link sind Rateversuche und zählen nur den abgewiesenen. Der Link hat keine
 * eigenen Grenzwerte, er nutzt die der Code-Eingabe ({@link RateLimitProperties#limitsFor}), führt
 * aber einen eigenen Zähler. {@code LOGIN} und {@code FORGOT} bleiben, bis Passwort-Anmeldung und
 * Reset entfallen — ihre Pfade blieben sonst ungebremst.
 */
public enum RateLimitedOperation {
  LOGIN("/api/auth/login", true),
  REGISTER("/api/auth/register", false),
  FORGOT("/api/auth/forgot", false),
  CODE_REQUEST("/api/auth/code/request", false),
  CODE_VERIFY("/api/auth/code/verify", true),
  CODE_LINK("/api/auth/code/link", true);

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
