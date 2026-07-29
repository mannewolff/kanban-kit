package org.mwolff.manban.outbox.application;

/**
 * Führt den Seiteneffekt eines Ereignistyps aus (Issue #501). Fachmodule implementieren dieses
 * Interface als Spring-Bean; der Worker findet sie über den {@link #eventType()}.
 *
 * <p><strong>Vertrag:</strong>
 *
 * <ul>
 *   <li>Ein Handler spricht <em>äußere</em> Systeme an (SMTP, Objektspeicher). Er schreibt nicht
 *       auf die Fachdatenbank: Er läuft innerhalb der Transaktion, die den Outbox-Eintrag sperrt,
 *       und ein dort ausgelöster Datenbankfehler machte diese Transaktion rollback-only — dann
 *       ließe sich nicht einmal mehr der Fehlversuch vermerken.
 *   <li>Er ist <em>idempotent</em>: Die Zustellung erfolgt mindestens einmal. Ein Absturz zwischen
 *       Seiteneffekt und Commit führt zu einem erneuten Aufruf mit derselben Payload.
 *   <li>Er wirft bei Misserfolg. Eine geworfene {@link RuntimeException} bedeutet „später erneut
 *       versuchen"; ihre Meldung landet im Eintrag. Stilles Schlucken sähe wie Erfolg aus.
 * </ul>
 */
public interface OutboxHandler {

  /** Ereignistyp, für den dieser Handler zuständig ist; systemweit eindeutig. */
  String eventType();

  /**
   * Führt den Seiteneffekt aus.
   *
   * @param payload die beim Einplanen übergebene Payload
   * @throws RuntimeException wenn der Seiteneffekt fehlschlug und wiederholt werden soll
   */
  void handle(String payload);
}
