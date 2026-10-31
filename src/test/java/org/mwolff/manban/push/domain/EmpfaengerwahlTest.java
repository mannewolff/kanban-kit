package org.mwolff.manban.push.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.push.domain.Empfaengerwahl.Konten;
import org.mwolff.manban.push.domain.Empfaengerwahl.Lauf;

/**
 * Welches Abo zu einem Lauf benachrichtigt wird (Issue #1998, Plan #1989 E12, E13; fachliche Quelle
 * #1961 AK 11, 12).
 */
class EmpfaengerwahlTest {

  private static final Instant GEMELDET = Instant.parse("2026-10-29T03:00:00Z");
  private static final long KONTO = 5L;
  private static final long PROJEKT = 7L;
  private static final long PRODUKT = 40L;
  private static final long SPIELWIESE = 41L;

  private final Set<Long> inaktiveKonten = new HashSet<>();
  private final Set<Long> ohneRecht = new HashSet<>();

  private final Konten konten =
      new Konten() {
        @Override
        public boolean istAktiv(long userId) {
          return !inaktiveKonten.contains(userId);
        }

        @Override
        public boolean darfSehen(long userId, long projectId) {
          return projectId == PROJEKT && !ohneRecht.contains(userId);
        }
      };

  private static PushAbo abo(Instant angelegt, Instant giltBis, Long... boardsAus) {
    return new PushAbo(
        1L, KONTO, "https://push.example/a", "p", "a", angelegt, giltBis, Set.of(boardsAus));
  }

  private static PushAbo abo(Long... boardsAus) {
    return abo(GEMELDET.minus(Duration.ofDays(1)), GEMELDET.plus(Duration.ofDays(29)), boardsAus);
  }

  private static Lauf lauf(@Nullable Long boardId, Long... aktiveBoards) {
    return new Lauf(PROJEKT, boardId, Set.of(aktiveBoards), GEMELDET);
  }

  @Test
  void einEingeschaltetesAboZuEinemSichtbarenLaufWirdBenachrichtigt() {
    assertThat(Empfaengerwahl.benachrichtigt(abo(), lauf(PRODUKT), konten)).isTrue();
  }

  @Test
  @DisplayName("Szenario: Benachrichtigungen für ein Board abgeschaltet")
  void szenarioBenachrichtigungenFuerEinBoardAbgeschaltet() {
    // Angenommen ein Mitglied hat Benachrichtigungen für das Board „Spielwiese" ausgeschaltet
    PushAbo abo = abo(SPIELWIESE);

    // Wenn ein Lauf auf „Spielwiese" hängt — dann bekommt es keine Nachricht
    assertThat(Empfaengerwahl.benachrichtigt(abo, lauf(SPIELWIESE), konten)).isFalse();
    // Ein Lauf auf einem anderen Board meldet weiter.
    assertThat(Empfaengerwahl.benachrichtigt(abo, lauf(PRODUKT), konten)).isTrue();
  }

  @Test
  void einAboNachDemErstenAuftretenBekommtNichtsNachgemeldet() {
    PushAbo spaeter = abo(GEMELDET.plusSeconds(1), GEMELDET.plus(Duration.ofDays(30)));
    PushAbo gleichzeitig = abo(GEMELDET, GEMELDET.plus(Duration.ofDays(30)));

    assertThat(Empfaengerwahl.benachrichtigt(spaeter, lauf(PRODUKT), konten)).isFalse();
    assertThat(Empfaengerwahl.benachrichtigt(gleichzeitig, lauf(PRODUKT), konten)).isFalse();
  }

  @Test
  void einLaufOhneBoardMeldetSolangeEinAktivesBoardDesProjektsAnIst() {
    Lauf ohneBoard = lauf(null, PRODUKT, SPIELWIESE);

    assertThat(Empfaengerwahl.benachrichtigt(abo(SPIELWIESE), ohneBoard, konten)).isTrue();
    assertThat(Empfaengerwahl.benachrichtigt(abo(PRODUKT, SPIELWIESE), ohneBoard, konten))
        .isFalse();
  }

  @Test
  void einLaufOhneBoardInEinemProjektOhneNichtArchiviertesBoardMeldet() {
    assertThat(Empfaengerwahl.benachrichtigt(abo(PRODUKT), lauf(null), konten)).isTrue();
  }

  @Test
  void einAbgelaufenesAboWirdNichtBenachrichtigt() {
    PushAbo abgelaufen = abo(GEMELDET.minus(Duration.ofDays(31)), GEMELDET);

    assertThat(Empfaengerwahl.benachrichtigt(abgelaufen, lauf(PRODUKT), konten)).isFalse();
  }

  @Test
  void einInaktivesKontoWirdNichtBenachrichtigt() {
    inaktiveKonten.add(KONTO);

    assertThat(Empfaengerwahl.benachrichtigt(abo(), lauf(PRODUKT), konten)).isFalse();
  }

  @Test
  void ohneRechtAufDieLaeufeDesProjektsKommtNichts() {
    ohneRecht.add(KONTO);

    assertThat(Empfaengerwahl.benachrichtigt(abo(), lauf(PRODUKT), konten)).isFalse();
  }

  @Test
  void empfaengerFiltertDieAbosDerWahlNach() {
    PushAbo an = abo();
    PushAbo aus = abo(PRODUKT);

    assertThat(Empfaengerwahl.empfaenger(List.of(an, aus), lauf(PRODUKT), konten))
        .containsExactly(an);
  }
}
