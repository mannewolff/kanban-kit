package org.mwolff.manban.push.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.nightrun.application.MeldbareLaeufe.MeldbarerLauf;
import org.mwolff.manban.nightrun.domain.Meldegrund;
import org.mwolff.manban.nightrun.domain.Meldegrund.Grund;
import org.mwolff.manban.nightrun.domain.Meldegrund.Zustand;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.push.domain.PushAbo;

/**
 * Der Meldeservice (Issue #1998, Plan #1989 E12; fachliche Quelle #1961 AK 11): Beim ersten
 * Auftreten eines Meldegrunds plant er je passendem Abo genau eine Outbox-Nachricht; jeder weitere
 * Takt und jedes weitere Paket desselben Laufs planen nichts mehr.
 *
 * <p>Der Meldegrund entsteht hier mit der echten Regel {@link Meldegrund#von} aus einem Lauf und
 * seinen Paketen — die Szenarien sollen am Zustand des Laufs hängen, nicht an einem gestellten
 * Grund.
 */
// PMD.CouplingBetweenObjects: Der Test baut Lauf und Pakete mit den echten
// Typen von nightrun, damit der Meldegrund aus der echten Regel kommt.
@SuppressWarnings("PMD.CouplingBetweenObjects")
class LaufMeldeServiceTest {

  private static final Instant JETZT = Instant.parse("2026-10-29T03:00:00Z");
  private static final Duration STILLEFRIST = Duration.ofMinutes(90);
  private static final long KONTO = 5L;
  private static final long ANDERES_KONTO = 6L;
  private static final long PROJEKT = 7L;
  private static final long PRODUKT = 40L;
  private static final long LAUF_ID = 300L;

  private final Set<Long> gemeldet = new HashSet<>();
  private final Map<Long, String> gruende = new HashMap<>();
  private final List<PushAbo> abos = new ArrayList<>();
  private final List<Long> geloescht = new ArrayList<>();
  private final List<OutboxMessage> outbox = new ArrayList<>();
  private PermissionChecker permissions;
  private BoardService boards;
  private LaufMeldeService service;

  @BeforeEach
  void setUp() {
    PushLaufMeldungRepository meldungen =
        (laufId, gemeldetAt, grund) -> {
          if (!gemeldet.add(laufId)) {
            return false;
          }
          gruende.put(laufId, grund);
          return true;
        };
    PushAboRepository aboRepository = mock(PushAboRepository.class);
    when(aboRepository.findAll()).thenAnswer(inv -> List.copyOf(abos));
    doAnswer(
            inv -> {
              long id = inv.getArgument(0);
              geloescht.add(id);
              abos.removeIf(abo -> abo.requireId() == id);
              return null;
            })
        .when(aboRepository)
        .deleteById(anyLong());
    UserLookup users = mock(UserLookup.class);
    when(users.istAktiv(anyLong())).thenReturn(true);
    permissions = mock(PermissionChecker.class);
    when(permissions.hatNachtlaufZugriff(anyLong(), anyLong())).thenReturn(true);
    boards = mock(BoardService.class);
    OutboxWriter writer = mock(OutboxWriter.class);
    when(writer.schedule(any()))
        .thenAnswer(
            inv -> {
              OutboxMessage message = inv.getArgument(0);
              if (outbox.stream()
                  .anyMatch(m -> m.idempotencyKey().equals(message.idempotencyKey()))) {
                return false;
              }
              outbox.add(message);
              return true;
            });
    service =
        new LaufMeldeService(
            meldungen,
            aboRepository,
            users,
            permissions,
            boards,
            writer,
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  // --- Vorrichtungen -------------------------------------------------------------------------

  private PushAbo abo(long id, long userId, Long... boardsAus) {
    PushAbo abo =
        new PushAbo(
            id,
            userId,
            "https://push.example/" + id,
            "p256dh",
            "auth",
            JETZT.minus(Duration.ofDays(1)),
            JETZT.plus(Duration.ofDays(29)),
            Set.of(boardsAus));
    abos.add(abo);
    return abo;
  }

  private static NightRun lauf(Instant updatedAt) {
    Instant start = JETZT.minus(Duration.ofHours(3));
    return new NightRun(
        LAUF_ID,
        PROJEKT,
        start,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        0,
        0,
        0,
        0,
        null,
        start,
        NightRunOrigin.TOKEN,
        "runner",
        false,
        updatedAt,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        PRODUKT);
  }

  private static NightRunItem paket(
      int karte, NightRunState state, @Nullable NightRunErrorClass klasse) {
    return new NightRunItem(
        LAUF_ID * 100 + karte,
        LAUF_ID,
        PROJEKT,
        JETZT,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        karte,
        "Paket " + karte,
        state,
        klasse,
        1_000L,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  private static MeldbarerLauf meldbar(
      @Nullable Long boardId, @Nullable String boardName, NightRunItem... pakete) {
    Meldegrund grund =
        Meldegrund.von(lauf(JETZT), List.of(pakete), JETZT, STILLEFRIST).orElseThrow();
    return new MeldbarerLauf(LAUF_ID, PROJEKT, "Alpha", boardId, boardName, grund);
  }

  private static MeldbarerLauf gescheitertAufProdukt() {
    return meldbar(
        PRODUKT, "Produkt", paket(1975, NightRunState.RED, NightRunErrorClass.HARD_ABORT));
  }

  private List<LaufNachricht> nachrichten() {
    assertThat(outbox).allSatisfy(m -> assertThat(m.eventType()).isEqualTo(LaufNachricht.TYP));
    return outbox.stream().map(m -> LaufNachricht.aus(m.payload())).toList();
  }

  // --- Szenarien -----------------------------------------------------------------------------

  @Test
  @DisplayName("Szenario: Ein Lauf scheitert unterwegs")
  void szenarioEinLaufScheitertUnterwegs() {
    // Angenommen ein Mitglied hat Benachrichtigungen für das Board „Produkt" eingeschaltet
    abo(11L, KONTO);

    // Wenn ein Lauf auf „Produkt" scheitert
    int geplant = service.melde(gescheitertAufProdukt());

    // Dann bekommt es eine Nachricht mit Projekt, Board und dem Grund …
    assertThat(geplant).isEqualTo(1);
    assertThat(outbox)
        .singleElement()
        .satisfies(m -> assertThat(m.idempotencyKey()).isEqualTo("push-lauf:300:11"));
    LaufNachricht nachricht = nachrichten().getFirst();
    assertThat(nachricht.titel()).isEqualTo("Alpha · Produkt");
    assertThat(nachricht.grund()).isEqualTo(Grund.GESCHEITERT);
    assertThat(nachricht.karte()).isEqualTo(1975);
    assertThat(nachricht.userId()).isEqualTo(KONTO);
    assertThat(nachricht.projectId()).isEqualTo(PROJEKT);
    assertThat(nachricht.gemeldetAt()).isEqualTo(JETZT);
    // … und ein Tipp darauf zeigt den Lauf.
    assertThat(nachricht.adresse()).isEqualTo("/mobil/laeufe/300");
    assertThat(gruende).containsEntry(LAUF_ID, "GESCHEITERT");
  }

  @Test
  @DisplayName("Szenario: Lauf verstummt")
  void szenarioLaufVerstummt() {
    // Angenommen ein Mitglied hat Benachrichtigungen für das Board „Produkt" eingeschaltet
    abo(11L, KONTO);
    // Wenn ein Lauf auf „Produkt" seit mehr als 90 Minuten nichts mehr meldet
    Meldegrund grund =
        Meldegrund.von(lauf(JETZT.minus(Duration.ofMinutes(91))), List.of(), JETZT, STILLEFRIST)
            .orElseThrow();

    service.melde(new MeldbarerLauf(LAUF_ID, PROJEKT, "Alpha", PRODUKT, "Produkt", grund));

    // Dann bekommt es eine Nachricht, dass der Lauf gescheitert ist
    LaufNachricht nachricht = nachrichten().getFirst();
    assertThat(nachrichten()).hasSize(1);
    assertThat(nachricht.grund()).isEqualTo(Grund.VERSTUMMT);
    assertThat(nachricht.grund().zustand()).isEqualTo(Zustand.GESCHEITERT);
    assertThat(nachricht.karte()).isNull();
    assertThat(nachricht.titel()).isEqualTo("Alpha · Produkt");
  }

  // --- Höchstens eine Nachricht je Lauf ------------------------------------------------------

  @Test
  void jedesPassendeAboBekommtGenauEineNachricht() {
    abo(11L, KONTO);
    abo(12L, ANDERES_KONTO);

    assertThat(service.melde(gescheitertAufProdukt())).isEqualTo(2);

    assertThat(outbox)
        .extracting(OutboxMessage::idempotencyKey)
        .containsExactly("push-lauf:300:11", "push-lauf:300:12");
  }

  @Test
  void einZweiterTaktPlantKeineNeueNachricht() {
    abo(11L, KONTO);
    service.melde(gescheitertAufProdukt());

    assertThat(service.melde(gescheitertAufProdukt())).isZero();

    assertThat(outbox).hasSize(1);
  }

  @Test
  void einZweitesPaketDesselbenLaufsPlantKeineNeueNachricht() {
    abo(11L, KONTO);
    service.melde(
        meldbar(PRODUKT, "Produkt", paket(1, NightRunState.GREY, NightRunErrorClass.STUCK)));

    int geplant =
        service.melde(
            meldbar(
                PRODUKT,
                "Produkt",
                paket(1, NightRunState.GREY, NightRunErrorClass.STUCK),
                paket(2, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(geplant).isZero();
    assertThat(nachrichten())
        .singleElement()
        .satisfies(n -> assertThat(n.grund()).isEqualTo(Grund.FESTGEFAHREN));
  }

  @Test
  void einAboMitAbgeschaltetemBoardBekommtNichtsUndDerLaufGiltTrotzdemAlsGemeldet() {
    abo(11L, KONTO, PRODUKT);

    assertThat(service.melde(gescheitertAufProdukt())).isZero();

    assertThat(outbox).isEmpty();
    assertThat(gemeldet).containsExactly(LAUF_ID);
  }

  @Test
  void einAboOhneRechtAufDieLaeufeBekommtNichts() {
    abo(11L, KONTO);
    when(permissions.hatNachtlaufZugriff(KONTO, PROJEKT)).thenReturn(false);

    assertThat(service.melde(gescheitertAufProdukt())).isZero();
  }

  @Test
  void abgelaufeneAbosWerdenUebersprungenUndGeloescht() {
    abo(11L, KONTO);
    abos.add(
        new PushAbo(
            12L,
            ANDERES_KONTO,
            "https://push.example/alt",
            "p256dh",
            "auth",
            JETZT.minus(Duration.ofDays(40)),
            JETZT.minus(Duration.ofDays(10)),
            Set.of()));

    assertThat(service.melde(gescheitertAufProdukt())).isEqualTo(1);

    assertThat(geloescht).containsExactly(12L);
    assertThat(nachrichten()).extracting(LaufNachricht::aboId).containsExactly(11L);
  }

  @Test
  void einLaufOhneBoardFragtDieAktivenBoardsDesProjektsAb() {
    abo(11L, KONTO, PRODUKT);
    when(boards.listActiveBoardIds(PROJEKT)).thenReturn(List.of(PRODUKT, 41L));

    service.melde(meldbar(null, null, paket(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(nachrichten())
        .singleElement()
        .satisfies(n -> assertThat(n.titel()).isEqualTo("Alpha"));
  }

  @Test
  void einLaufMitBoardFragtKeineBoardsAb() {
    abo(11L, KONTO);

    service.melde(gescheitertAufProdukt());

    verify(boards, never()).listActiveBoardIds(anyLong());
  }

  @Test
  void ohneAbosWirdDerLaufDennochAlsGemeldetFestgehalten() {
    assertThat(service.melde(gescheitertAufProdukt())).isZero();

    assertThat(gemeldet).containsExactly(LAUF_ID);
  }
}
