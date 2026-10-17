package org.mwolff.manban.ratelimit.application;

/**
 * Die Vorgänge, die die Zählbremse begrenzt — Registrierung (Plan #892, fachlich #840) sowie
 * Anforderung, Eingabe und Link der Anmeldung per Einmalcode (Plan #1801 A7, fachlich #1781).
 *
 * <p>Der Pfad steht am Vorgang und nicht am Filter: Die Registrierung des Filters und die Zuordnung
 * eines Requests zum Vorgang griffen sonst auf zwei getrennt gepflegte Listen zu, und eine davon
 * wäre irgendwann falsch.
 *
 * <p>Ob nur Fehlschläge zählen, entscheidet die Art des Schadens. Bei Registrierung und
 * Code-Anforderung entsteht er gerade durch die <em>erfolgreichen</em> Aufrufe — angelegte Konten,
 * versandte Mails —, und aus Absendersicht antworten beide ohnehin neutral (E5). Code-Eingabe und
 * Link sind Rateversuche und zählen nur den abgewiesenen; ein Formfehler ist kein Rateversuch (E4).
 * Der Link hat keine eigenen Grenzwerte, er nutzt die der Code-Eingabe ({@link
 * RateLimitProperties#limitsFor}), führt aber einen eigenen Zähler. Passwort-Anmeldung ({@code
 * LOGIN}) und Reset-Anforderung ({@code FORGOT}) sind mit ihren Pfaden entfallen (Plan #1801, A7).
 */
public enum RateLimitedOperation {
  REGISTER("/api/auth/register", false),
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
