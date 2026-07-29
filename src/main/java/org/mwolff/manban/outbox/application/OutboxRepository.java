package org.mwolff.manban.outbox.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.outbox.domain.OutboxEntry;

/** Persistenz-Port der Outbox (Issue #501). */
public interface OutboxRepository {

  /**
   * Legt den Eintrag an, sofern sein Idempotenzschlüssel noch nicht vergeben ist.
   *
   * <p>Die Prüfung gehört in dieselbe Anweisung wie der Einfügevorgang, nicht davor: „schau nach,
   * dann schreibe" ist unter READ COMMITTED nicht atomar, und zwei gleichzeitige Aufrufer derselben
   * fachlichen Operation würden beide zwei Einträge sehen wollen. Der Adapter setzt das deshalb
   * über den eindeutigen Index um — der zweite Aufrufer bekommt ein ruhiges {@code false} statt
   * eines Constraint-Fehlers, der seine Fachtransaktion zurückrollte.
   *
   * @return {@code true}, wenn ein Eintrag entstand; {@code false}, wenn der Schlüssel schon
   *     vergeben war
   */
  boolean saveIfAbsent(OutboxEntry entry);

  /** IDs der offenen, spätestens jetzt fälligen Einträge — älteste Fälligkeit zuerst. */
  List<Long> findDueIds(Instant now, int limit);

  /**
   * Sperrt den Eintrag bis zum Transaktionsende und liefert ihn, sofern er noch offen und fällig
   * ist.
   *
   * <p>Ein bereits von einem anderen Worker gesperrter Eintrag wird übersprungen statt abgewartet
   * ({@code SKIP LOCKED}) — deshalb kommt ein zweiter Worker weder durcheinander noch zum
   * Stillstand, er greift einfach den nächsten. Die erneute Zustandsprüfung unter der Sperre ist
   * der eigentliche Schutz gegen doppelte Wirkung: Zwischen dem Auflisten der fälligen IDs und dem
   * Sperren kann derselbe Eintrag längst erledigt worden sein.
   *
   * @return der gesperrte Eintrag, oder leer, wenn er gesperrt, erledigt oder nicht mehr fällig ist
   */
  Optional<OutboxEntry> claimDue(long id, Instant now);

  /** Schreibt den fortgeschriebenen Zustand eines bestehenden Eintrags zurück. */
  void update(OutboxEntry entry);

  /**
   * Löscht erledigte Einträge, die vor {@code threshold} abgeschlossen wurden. Endgültig
   * gescheiterte bleiben bewusst stehen — sie sind der sichtbare Rückstand.
   *
   * @return Anzahl der gelöschten Einträge
   */
  int deleteCompletedBefore(Instant threshold);
}
