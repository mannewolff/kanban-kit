package org.mwolff.manban.card.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardFlowStayRepository.ErfassungsMarke;
import org.mwolff.manban.card.application.FlussNachtragQuelle.Bestand;
import org.mwolff.manban.card.application.FlussNachtragQuelle.Kartenverlauf;
import org.mwolff.manban.card.domain.FlussRekonstruktion;
import org.mwolff.manban.card.domain.FlussStay;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der einmalige Nachtrag der Zustandsaufenthalte aus dem Spaltenverlauf (Issue #1770, Plan #1765
 * E6, E8, E20): ersetzt die Saat aus {@code V56} durch den rekonstruierten Verlauf.
 *
 * <p>Die Erfassung läuft ab {@code since} schon; der Nachtrag schreibt darum nur, was davor liegt.
 * Je Karte ist der Schnitt der Beginn ihrer Saat — {@code since}, bei einer fertigen Karte mit
 * {@code moved_to_done_at} dieser Zeitpunkt —, ohne Saat {@code since}. Aufenthalte, die bis zum
 * Schnitt enden, kommen hinzu. Der rekonstruierte Aufenthalt über den Schnitt hinweg gibt der Saat
 * ihren Eintritt, wenn er in Zustand und Board mit ihr übereinstimmt — auch einer Saat, die die
 * Erfassung inzwischen geschlossen hat. Stimmt er nicht überein, ist ungewiss, wann die Karte in
 * den Stand der Migration kam: Die Zeit bis zum Schnitt wird eine unbekannte Spanne (E7).
 */
@Service
public class FlussNachtrag {

  private final FlussNachtragQuelle quelle;
  private final CardFlowStayRepository aufenthalte;
  private final Clock clock;

  public FlussNachtrag(
      FlussNachtragQuelle quelle, CardFlowStayRepository aufenthalte, Clock clock) {
    this.quelle = quelle;
    this.aufenthalte = aufenthalte;
    this.clock = clock;
  }

  /**
   * Trägt nach, wenn es noch nicht geschah. Die Zeilensperre auf der Marke hält einen zweiten
   * Nachtrag an, bis der erste festgeschrieben ist; danach sieht er die gesetzte Marke (E20).
   *
   * @return {@code false}, wenn der Nachtrag schon gelaufen war
   */
  @Transactional
  public boolean nachtragen() {
    ErfassungsMarke marke = aufenthalte.markeSperren();
    if (marke.reconstructedAt() != null) {
      return false;
    }
    Bestand bestand = quelle.lesen();
    Map<Long, FlussStay> saat = new HashMap<>();
    aufenthalte.findEnteredUpTo(marke.since()).forEach(s -> saat.putIfAbsent(s.cardId(), s));
    List<FlussStay> neu = new ArrayList<>();
    for (Kartenverlauf karte : bestand.karten()) {
      List<FlussStay> verlauf =
          FlussRekonstruktion.rekonstruiere(
              karte.stand(), karte.zeilen(), karte.ereignisse(), bestand.folgen());
      einpassen(verlauf, saat.get(karte.stand().cardId()), marke.since(), neu);
    }
    aufenthalte.nachtragen(neu);
    aufenthalte.markeSetzen(clock.instant());
    return true;
  }

  private void einpassen(
      List<FlussStay> verlauf, @Nullable FlussStay anker, Instant since, List<FlussStay> neu) {
    Instant schnitt = anker == null ? since : anker.enteredAt();
    for (FlussStay stay : verlauf) {
      Instant leftAt = stay.leftAt();
      if (leftAt != null && !leftAt.isAfter(schnitt)) {
        neu.add(stay);
      } else if (stay.enteredAt().isBefore(schnitt)) {
        if (anker == null || stay.status() != anker.status() || stay.boardId() != anker.boardId()) {
          neu.add(
              new FlussStay(
                  null,
                  stay.cardId(),
                  stay.boardId(),
                  null,
                  stay.enteredAt(),
                  schnitt,
                  false,
                  grund(stay, anker)));
        } else if (!anker.enteredKnown()) {
          aufenthalte.eintrittKorrigieren(
              Objects.requireNonNull(anker.id()), stay.enteredAt(), stay.enteredKnown());
        }
      }
    }
  }

  /** Warum die unbekannte Spanne vor dem Anker endet (E4). */
  private static EndedBy grund(FlussStay stay, @Nullable FlussStay anker) {
    if (anker == null) {
      return EndedBy.EXIT;
    }
    return anker.boardId() == stay.boardId() ? EndedBy.STATUS : EndedBy.BOARD;
  }
}
