package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.FlussStay;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;
import org.mwolff.manban.card.domain.Flusslage;

/**
 * Ausgehender Port für den Verlauf der Zustandsaufenthalte der Arbeitspakete (Plan #1765, E1) und
 * die Erfassungsmarke (E20). Ein Wechsel besteht aus {@link #close} (verlassener Aufenthalt)
 * gefolgt von {@link #open} (neuer Aufenthalt).
 */
public interface CardFlowStayRepository {

  /**
   * Seit wann erfasst wird und ob der einmalige Nachtrag gelaufen ist.
   *
   * @param since Zeitpunkt der Migration V56 — ab hier schreibt die Erfassung
   * @param reconstructedAt wann der Nachtrag lief; {@code null}, solange er aussteht
   */
  record ErfassungsMarke(Instant since, @Nullable Instant reconstructedAt) {}

  /** Alle Aufenthalte auf dem Board, chronologisch nach Eintritt, bei Gleichstand nach ID. */
  List<FlussStay> findByBoardId(long boardId);

  /** Der offene Aufenthalt der Karte, leer, wenn sie nicht im Fluss ist. */
  Optional<FlussStay> openFor(long cardId);

  /** Öffnet einen Aufenthalt in der Flusslage, mit bekanntem Eintritt zum Zeitpunkt. */
  void open(long cardId, Flusslage lage, Instant enteredAt);

  /** Schließt den offenen Aufenthalt der Karte. No-op, wenn keiner offen ist. */
  void close(long cardId, Instant leftAt, EndedBy endedBy);

  /** Schreibt die Aufenthalte des Nachtrags so, wie sie sind (E6). */
  void nachtragen(List<FlussStay> aufenthalte);

  /**
   * Die Aufenthalte, die spätestens zum Zeitpunkt begannen — offen oder schon geschlossen. Mit dem
   * Beginn der Erfassung ({@code since}) sind das die Saat aus {@code V56} (E20).
   */
  List<FlussStay> findEnteredUpTo(Instant zeitpunkt);

  /** Setzt den Beginn eines Aufenthalts auf den rekonstruierten Eintritt (E8, E20). */
  void eintrittKorrigieren(long stayId, Instant enteredAt, boolean enteredKnown);

  /** Liest die Marke. */
  ErfassungsMarke marke();

  /**
   * Liest die Marke und sperrt ihre Zeile bis zum Ende der laufenden Transaktion ({@code SELECT …
   * FOR UPDATE}) — ein zweiter Nachtrag wartet, bis der erste festgeschrieben ist (E20).
   */
  ErfassungsMarke markeSperren();

  /** Vermerkt den Nachtrag als gelaufen. */
  void markeSetzen(Instant reconstructedAt);
}
