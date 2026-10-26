package org.mwolff.manban.card.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;

/**
 * Vormerkung der Nachricht an neu Zuständige in der Outbox (Issue #1916): je Empfänger ein Eintrag,
 * der Schlüssel aus Karte, Empfänger und Zeitpunkt, die Payload ohne Kartentext.
 */
class OutboxZuweisungsMailerTest {

  private static final long KARTE = 77L;
  private static final long PROJEKT = 9L;
  private static final long BOARD = 4L;
  private static final long MANNE = 1L;
  private static final long BO = 2L;
  private static final long CARLA = 3L;
  private static final Instant JETZT = Instant.parse("2026-10-26T19:00:00Z");
  private static final String LINK = "https://app.example.org/projects/9/karten/1916";

  private final OutboxWriter outbox = mock(OutboxWriter.class);
  private final CardRepository cards = mock(CardRepository.class);
  private final BoardService boards = mock(BoardService.class);
  private final ProjectService projects = mock(ProjectService.class);
  private final UserLookup users = mock(UserLookup.class);
  private Clock clock = Clock.fixed(JETZT, ZoneOffset.UTC);

  private OutboxZuweisungsMailer mailer() {
    return new OutboxZuweisungsMailer(
        outbox,
        cards,
        boards,
        projects,
        users,
        new AuthProperties("https://app.example.org", "secret", null, null, null),
        clock);
  }

  private static Card gespeicherteKarte() {
    return new Card(
        KARTE,
        BOARD,
        20L,
        1916,
        "Mail bei Zuweisung",
        "Geheimer Kartentext",
        0,
        false,
        null,
        MANNE,
        JETZT,
        JETZT,
        CardType.CARD,
        null,
        null,
        null,
        PROJEKT,
        null,
        null,
        null,
        null);
  }

  private static UserSummary user(long id, String email, String name) {
    return new UserSummary(id, email, name, true);
  }

  @BeforeEach
  void setUp() {
    when(cards.findById(KARTE)).thenReturn(Optional.of(gespeicherteKarte()));
    when(boards.requireBoardSummary(BOARD)).thenReturn(new BoardSummary(BOARD, "Board B", false));
    when(users.findById(MANNE)).thenReturn(Optional.of(user(MANNE, "m@example.org", "Manne")));
    when(users.findById(BO)).thenReturn(Optional.of(user(BO, "bo@example.org", "Bo")));
    when(users.findById(CARLA)).thenReturn(Optional.of(user(CARLA, "c@example.org", "Carla")));
    when(projects.listAccessible(any(Long.class)))
        .thenReturn(
            List.of(new AccessibleProject(8L, "Fremd"), new AccessibleProject(PROJEKT, "P")));
  }

  private List<OutboxMessage> vorgemerkt(int anzahl) {
    ArgumentCaptor<OutboxMessage> captured = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(anzahl)).schedule(captured.capture());
    return captured.getAllValues();
  }

  @Test
  void jeEmpfaengerEinEintragMitSchluesselUndFeldernOhneKartentext() {
    mailer().zugewiesen(KARTE, List.of(BO, CARLA), MANNE);

    List<OutboxMessage> messages = vorgemerkt(2);
    OutboxMessage an = messages.get(0);
    assertThat(an.eventType()).isEqualTo("mail.card-assigned");
    assertThat(an.idempotencyKey())
        .isEqualTo("mail.card-assigned:" + SecureTokens.sha256Hex("77\n2\n" + JETZT));
    assertThat(PayloadFields.split(an.payload(), 7))
        .containsExactly(
            "bo@example.org", "1916", "Mail bei Zuweisung", "P", "Board B", "Manne", LINK);
    assertThat(an.payload()).doesNotContain("Geheimer");
    assertThat(PayloadFields.split(messages.get(1).payload(), 7).get(0)).isEqualTo("c@example.org");
    assertThat(messages.get(1).idempotencyKey()).isNotEqualTo(an.idempotencyKey());
  }

  @Test
  void dieselbeZuweisungErgibtDenselbenSchluesselEineSpaetereEinenNeuen() {
    mailer().zugewiesen(KARTE, List.of(BO), MANNE);
    mailer().zugewiesen(KARTE, List.of(BO), MANNE);
    clock = Clock.fixed(JETZT.plusSeconds(60), ZoneOffset.UTC);
    mailer().zugewiesen(KARTE, List.of(BO), MANNE);

    List<OutboxMessage> messages = vorgemerkt(3);
    assertThat(messages.get(1).idempotencyKey()).isEqualTo(messages.get(0).idempotencyKey());
    assertThat(messages.get(2).idempotencyKey()).isNotEqualTo(messages.get(0).idempotencyKey());
  }

  @Test
  void unbekannterEmpfaengerWirdUebersprungen() {
    when(users.findById(BO)).thenReturn(Optional.empty());

    mailer().zugewiesen(KARTE, List.of(BO, CARLA), MANNE);

    List<OutboxMessage> messages = vorgemerkt(1);
    assertThat(PayloadFields.split(messages.get(0).payload(), 7).get(0)).isEqualTo("c@example.org");
  }

  @Test
  void ohneNamenStehenDieKennungenDerHandelndenPersonUndDesProjekts() {
    when(users.findById(MANNE)).thenReturn(Optional.empty());
    when(projects.listAccessible(BO)).thenReturn(List.of());

    mailer().zugewiesen(KARTE, List.of(BO), MANNE);

    List<String> felder = PayloadFields.split(vorgemerkt(1).get(0).payload(), 7);
    assertThat(felder.get(3)).isEqualTo("#9");
    assertThat(felder.get(5)).isEqualTo("#1");
  }

  @Test
  void unbekannteKarteWirdAbgewiesen() {
    when(cards.findById(KARTE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mailer().zugewiesen(KARTE, List.of(BO), MANNE))
        .isInstanceOf(CardNotFoundException.class);
    verify(outbox, never()).schedule(any());
  }
}
