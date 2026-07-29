package org.mwolff.manban.outbox.application;

/**
 * Ein einzuplanender Seiteneffekt, so wie ihn ein fachlicher Use-Case beschreibt (Issue #501).
 *
 * <p><strong>Die Payload trägt Referenzen, keine Geheimnisse.</strong> Verifikations- und
 * Reset-Links enthalten Klartext-Tokens; die gehören nicht in die Tabelle. Ein Handler baut den
 * Link stattdessen aus den referenzierten Daten zusammen. {@link
 * org.mwolff.manban.outbox.domain.OutboxEntry} beschreibt, wie das zusätzlich abgesichert ist.
 *
 * <p>Das Format der Payload legt der jeweilige {@link OutboxHandler} fest — die Outbox behandelt
 * sie als undurchsichtige Zeichenkette.
 *
 * @param eventType fachlicher Ereignistyp; wählt den zuständigen {@link OutboxHandler}
 * @param idempotencyKey stabiler Schlüssel des fachlichen Ereignisses. Zweimaliges Einplanen
 *     desselben Schlüssels ergibt genau einen Eintrag — daran hängt, dass ein wiederholter
 *     Use-Case-Aufruf nicht zu zwei Mails führt.
 * @param payload Referenzen für den Handler; darf leer sein
 */
public record OutboxMessage(String eventType, String idempotencyKey, String payload) {

  public OutboxMessage {
    if (eventType.isBlank()) {
      throw new IllegalArgumentException("Ereignistyp darf nicht leer sein");
    }
    if (idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Idempotenzschlüssel darf nicht leer sein");
    }
  }
}
