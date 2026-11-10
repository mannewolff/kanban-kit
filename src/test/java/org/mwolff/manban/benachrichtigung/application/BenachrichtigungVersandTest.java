package org.mwolff.manban.benachrichtigung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.FaelligeZustellung;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LabelAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LaufAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.NeueZustellung;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.KartenStandView;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe.LaufMitPaketen;
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
import org.mwolff.manban.project.domain.Permission;

/**
 * Das Prüfen und Einplanen der Zustellungen (Issue #2079, Plan #2070 E6 bis E9).
 *
 * <p>Der Port liegt als Attrappe mit der Fälligkeitsregel vor. Dass die Datenbank sie wirklich
 * hält, belegt {@code AnlassRepositoryIT}.
 */
// PMD.ExcessiveImports: Der Test verdrahtet alle Ports des Versands und baut ihre Datentypen.
// PMD.TooManyMethods: Je Regel aus E8 ein Test.
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.TooManyMethods"})
class BenachrichtigungVersandTest {

  private static final Instant JETZT = Instant.parse("2026-11-09T12:00:00Z");
  private static final Instant START = JETZT.minus(Duration.ofHours(3));
  private static final long PROJEKT = 7L;
  private static final long PERSON = 11L;
  private static final long ANDERE = 12L;
  private static final long LAUF_ID = 500L;
  private static final long KARTEN_ID = 300L;
  private static final int NUMMER = 42;
  private static final int NUMMER_OHNE_KARTE = 99;
  private static final String FRAGE = "kit:klaeren";
  private static final String FRAGE_ENGLISCH = "kit:clarify";

  private final AnlaesseAttrappe anlaesse = new AnlaesseAttrappe();
  private final List<KartenStandView> staende = new ArrayList<>();
  private BenachrichtigbareLaeufe laeufe;
  private PermissionChecker permissions;
  private CardRunQueryService karten;
  private OutboxWriter outbox;
  private BenachrichtigungVersand versand;

  @BeforeEach
  void setUp() {
    laeufe = mock(BenachrichtigbareLaeufe.class);
    permissions = mock(PermissionChecker.class);
    karten = mock(CardRunQueryService.class);
    outbox = mock(OutboxWriter.class);
    when(permissions.hatNachtlaufZugriff(anyLong(), eq(PROJEKT))).thenReturn(true);
    when(permissions.hasPermission(anyLong(), eq(PROJEKT), eq(Permission.TICKET_READ)))
        .thenReturn(true);
    when(karten.kartenStand(any()))
        .thenAnswer(
            inv -> {
              Collection<Long> ids = inv.getArgument(0);
              return staende.stream().filter(k -> ids.contains(k.id())).toList();
            });
    when(karten.kartenId(anyLong(), anyInt())).thenReturn(Optional.empty());
    when(karten.kartenId(PROJEKT, NUMMER)).thenReturn(Optional.of(KARTEN_ID));
    when(laeufe.laufMitPaketen(anyLong(), anyLong())).thenReturn(Optional.empty());
    versand =
        new BenachrichtigungVersand(
            anlaesse, laeufe, karten, permissions, outbox, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void karte(boolean archiviert, boolean imPapierkorb, String... labels) {
    staende.clear();
    staende.add(
        new KartenStandView(
            KARTEN_ID, PROJEKT, NUMMER, "Titel", List.of(labels), archiviert, imPapierkorb));
  }

  private void lauf(boolean complete, @Nullable Instant closedAt, NightRunItem... pakete) {
    NightRun run =
        new NightRun(
            LAUF_ID,
            PROJEKT,
            START,
            NightRunMode.IMPLEMENTATION,
            NightRunKind.NIGHT,
            0,
            0,
            0,
            0,
            null,
            START,
            NightRunOrigin.TOKEN,
            "runner",
            complete,
            START,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            closedAt);
    when(laeufe.laufMitPaketen(PROJEKT, LAUF_ID))
        .thenReturn(Optional.of(new LaufMitPaketen(run, List.of(pakete))));
  }

  private static NightRunItem paket(int nummer, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        1L,
        LAUF_ID,
        PROJEKT,
        START,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        nummer,
        "Paket",
        NightRunState.RED,
        errorClass,
        1_000L,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  private static NightRunItem wartend() {
    return paket(NUMMER, NightRunErrorClass.AWAITING_DECISION);
  }

  /** Ein Anlass aus einem Lauf. */
  private void ausLauf(long id, AnlassArt art, @Nullable Long cardId) {
    anlaesse.lege(new FaelligeZustellung(id, PERSON, false, art, PROJEKT, LAUF_ID, cardId), JETZT);
  }

  /** Ein Anlass aus einem Label-Ereignis. */
  private void ausLabel(long id, long userId, boolean verschoben, AnlassArt art) {
    anlaesse.lege(
        new FaelligeZustellung(id, userId, verschoben, art, PROJEKT, null, KARTEN_ID), JETZT);
  }

  private void uebersicht(long id, long userId, boolean verschoben) {
    anlaesse.lege(
        new FaelligeZustellung(
            id, userId, verschoben, AnlassArt.UEBERSICHT, PROJEKT, LAUF_ID, null),
        JETZT);
  }

  private List<OutboxMessage> nachrichten() {
    ArgumentCaptor<OutboxMessage> nachricht = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, atLeast(0)).schedule(nachricht.capture());
    return nachricht.getAllValues();
  }

  private void lieferbar() {
    lauf(true, null);
  }

  @Test
  void eineZustellungInDerZukunftWirdNichtEingeplant() {
    lieferbar();
    anlaesse.lege(
        new FaelligeZustellung(1L, PERSON, true, AnlassArt.UEBERSICHT, PROJEKT, LAUF_ID, null),
        JETZT.plusSeconds(1));

    assertThat(versand.planeEin()).isZero();

    assertThat(nachrichten()).isEmpty();
    assertThat(anlaesse.zustand(1L)).isEqualTo("OFFEN");
  }

  @Test
  void eineGeradeFaelligeZustellungWirdEingeplant() {
    lieferbar();
    uebersicht(1L, PERSON, true);

    assertThat(versand.planeEin()).isEqualTo(1);

    assertThat(anlaesse.zustand(1L)).isEqualTo("EINGEPLANT");
  }

  @Test
  void verschobeneZustellungenEinerPersonErgebenGenauEineNachrichtMitAllenIds() {
    lieferbar();
    uebersicht(3L, PERSON, true);
    uebersicht(5L, PERSON, true);
    uebersicht(9L, PERSON, true);

    assertThat(versand.planeEin()).isEqualTo(1);

    assertThat(nachrichten())
        .containsExactly(
            new OutboxMessage("mail.benachrichtigung", "benachrichtigung:11:3", "3,5,9"));
    assertThat(anlaesse.eingeplant).containsExactly(3L, 5L, 9L);
  }

  @Test
  void verschobeneZweierPersonenErgebenJeEineNachricht() {
    lieferbar();
    uebersicht(3L, PERSON, true);
    uebersicht(4L, ANDERE, true);
    uebersicht(5L, PERSON, true);

    assertThat(versand.planeEin()).isEqualTo(2);

    assertThat(nachrichten())
        .containsExactlyInAnyOrder(
            new OutboxMessage("mail.benachrichtigung", "benachrichtigung:11:3", "3,5"),
            new OutboxMessage("mail.benachrichtigung", "benachrichtigung:12:4", "4"));
  }

  @Test
  void nichtVerschobeneZustellungenGehenEinzeln() {
    lieferbar();
    uebersicht(3L, PERSON, false);
    uebersicht(5L, PERSON, false);

    assertThat(versand.planeEin()).isEqualTo(2);

    assertThat(nachrichten())
        .containsExactly(
            new OutboxMessage("mail.benachrichtigung", "benachrichtigung:11:3", "3"),
            new OutboxMessage("mail.benachrichtigung", "benachrichtigung:11:5", "5"));
  }

  @Test
  void verschobeneUndNichtVerschobeneEinerPersonBleibenGetrennt() {
    lieferbar();
    uebersicht(3L, PERSON, false);
    uebersicht(5L, PERSON, true);
    uebersicht(7L, PERSON, true);

    assertThat(versand.planeEin()).isEqualTo(2);

    assertThat(nachrichten())
        .containsExactly(
            new OutboxMessage("mail.benachrichtigung", "benachrichtigung:11:3", "3"),
            new OutboxMessage("mail.benachrichtigung", "benachrichtigung:11:5", "5,7"));
  }

  @Test
  void dieEntfalleneZaehltNichtFuerSchluesselUndPayload() {
    lieferbar();
    karte(false, false);
    ausLabel(3L, PERSON, true, AnlassArt.KARTE_FRAGE);
    uebersicht(5L, PERSON, true);

    versand.planeEin();

    assertThat(nachrichten())
        .containsExactly(new OutboxMessage("mail.benachrichtigung", "benachrichtigung:11:5", "5"));
    assertThat(anlaesse.zustand(3L)).isEqualTo("ENTFALLEN");
  }

  @ParameterizedTest
  @CsvSource({
    "KARTE_FRAGE, kit:klaeren",
    "KARTE_FRAGE, kit:clarify",
    "KARTE_GESCHUETZT, kit:geschuetzt",
    "KARTE_GESCHUETZT, kit:protected",
    "LAUF_WARTET, lauf:wartet",
    "LAUF_WARTET, run:waiting"
  })
  void einLabelAnlassBleibtSolangeDieKarteEinenNamenDesPaarsTraegt(AnlassArt art, String label) {
    karte(false, false, "anderes", label);
    ausLabel(1L, PERSON, false, art);

    assertThat(versand.planeEin()).isEqualTo(1);

    assertThat(anlaesse.zustand(1L)).isEqualTo("EINGEPLANT");
  }

  @ParameterizedTest
  @CsvSource({
    "KARTE_FRAGE, kit:geschuetzt",
    "KARTE_GESCHUETZT, kit:klaeren",
    "LAUF_WARTET, kit:klaeren"
  })
  void einLabelAnlassEntfaelltWennNurLabelEinesAnderenPaarsBleiben(AnlassArt art, String label) {
    karte(false, false, label);
    ausLabel(1L, PERSON, false, art);

    assertThat(versand.planeEin()).isZero();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void eineArchivierteKarteErledigtDenLabelAnlass() {
    karte(true, false, FRAGE);
    ausLabel(1L, PERSON, false, AnlassArt.KARTE_FRAGE);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void eineKarteImPapierkorbErledigtDenLabelAnlass() {
    karte(false, true, FRAGE_ENGLISCH);
    ausLabel(1L, PERSON, false, AnlassArt.KARTE_FRAGE);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void eineVerschwundeneKarteErledigtDenLabelAnlass() {
    ausLabel(1L, PERSON, false, AnlassArt.KARTE_FRAGE);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void einHaltBleibtNachDemLaufendeOffenUndWirdEingeplant() {
    karte(false, false);
    lauf(true, null, wartend());
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    assertThat(versand.planeEin()).isEqualTo(1);

    assertThat(anlaesse.zustand(1L)).isEqualTo("EINGEPLANT");
  }

  @Test
  void einPaketOhneAwaitingDecisionErledigtDenHalt() {
    karte(false, false);
    lauf(true, null, paket(NUMMER, NightRunErrorClass.STUCK));
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void einWartendesPaketEinerAnderenKarteHaeltDenHaltNichtOffen() {
    karte(false, false);
    lauf(true, null, paket(NUMMER_OHNE_KARTE, NightRunErrorClass.AWAITING_DECISION));
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void beiBeidenLeerenKartenBleibtDerHaltOffen() {
    lauf(true, null, paket(NUMMER_OHNE_KARTE, NightRunErrorClass.AWAITING_DECISION));
    ausLauf(1L, AnlassArt.LAUF_WARTET, null);

    assertThat(versand.planeEin()).isEqualTo(1);

    assertThat(anlaesse.zustand(1L)).isEqualTo("EINGEPLANT");
  }

  @Test
  void einAnlassOhneKarteZaehltKeinPaketMitKarte() {
    lauf(true, null, wartend());
    ausLauf(1L, AnlassArt.LAUF_WARTET, null);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void einVonHandBeendeterLaufErledigtDenHalt() {
    karte(false, false);
    lauf(false, START, wartend());
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void einAbgeschlossenerLaufMitStempelGiltNichtAlsVonHandBeendet() {
    karte(false, false);
    lauf(true, START, wartend());
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    assertThat(versand.planeEin()).isEqualTo(1);
  }

  @Test
  void einNochArbeitenderLaufHaeltDenHaltOffen() {
    karte(false, false);
    lauf(false, null, wartend());
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    assertThat(versand.planeEin()).isEqualTo(1);
  }

  @Test
  void eineArchivierteKarteErledigtDenHaltDesPakets() {
    karte(true, false);
    lauf(true, null, wartend());
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void eineKarteImPapierkorbErledigtDenHaltDesPakets() {
    karte(false, true);
    lauf(true, null, wartend());
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void einVerschwundenerLaufErledigtDenHalt() {
    karte(false, false);
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    versand.planeEin();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @ParameterizedTest
  @EnumSource(
      value = AnlassArt.class,
      names = {"STOERUNG", "UEBERSICHT"})
  void stoerungUndUebersichtWerdenNieErledigt(AnlassArt art) {
    karte(true, true);
    lauf(false, START, paket(NUMMER, NightRunErrorClass.STUCK));
    ausLauf(1L, art, KARTEN_ID);

    assertThat(versand.planeEin()).isEqualTo(1);

    assertThat(anlaesse.zustand(1L)).isEqualTo("EINGEPLANT");
  }

  @Test
  void ohneLaufzugriffEntfaelltEinLaufAnlass() {
    lieferbar();
    when(permissions.hatNachtlaufZugriff(PERSON, PROJEKT)).thenReturn(false);
    uebersicht(1L, PERSON, false);

    assertThat(versand.planeEin()).isZero();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
    assertThat(nachrichten()).isEmpty();
  }

  @Test
  void ohneLeserechtEntfaelltEinKartenAnlass() {
    karte(false, false, FRAGE);
    when(permissions.hasPermission(PERSON, PROJEKT, Permission.TICKET_READ)).thenReturn(false);
    ausLabel(1L, PERSON, false, AnlassArt.KARTE_FRAGE);

    assertThat(versand.planeEin()).isZero();

    assertThat(anlaesse.zustand(1L)).isEqualTo("ENTFALLEN");
  }

  @Test
  void einWartenderLabelAnlassVerlangtDasLeserechtDerKarteNichtDenLaufzugriff() {
    karte(false, false, "run:waiting");
    when(permissions.hatNachtlaufZugriff(PERSON, PROJEKT)).thenReturn(false);
    ausLabel(1L, PERSON, false, AnlassArt.LAUF_WARTET);

    assertThat(versand.planeEin()).isEqualTo(1);
  }

  @Test
  void einLaufAnlassMitKarteVerlangtDenLaufzugriffNichtDasLeserecht() {
    karte(false, false);
    lauf(true, null, wartend());
    when(permissions.hasPermission(PERSON, PROJEKT, Permission.TICKET_READ)).thenReturn(false);
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    assertThat(versand.planeEin()).isEqualTo(1);
  }

  @Test
  void bleibtNichtsUebrigEntstehtKeineNachricht() {
    karte(false, false);
    ausLabel(1L, PERSON, true, AnlassArt.KARTE_FRAGE);
    ausLabel(2L, PERSON, true, AnlassArt.KARTE_GESCHUETZT);

    assertThat(versand.planeEin()).isZero();

    verify(outbox, never()).schedule(any());
    assertThat(anlaesse.entfallen).containsExactly(1L, 2L);
    assertThat(anlaesse.eingeplant).isEmpty();
  }

  @Test
  void ohneFaelligeZustellungGeschiehtNichts() {
    assertThat(versand.planeEin()).isZero();

    verify(outbox, never()).schedule(any());
  }

  @Test
  void diePayloadTraegtNurIdsUndDerSchluesselDieKleinste() {
    lieferbar();
    uebersicht(1234L, PERSON, true);
    uebersicht(5678L, PERSON, true);

    versand.planeEin();

    verify(outbox, times(1)).schedule(any());
    OutboxMessage nachricht = nachrichten().getFirst();
    assertThat(nachricht.payload()).matches("\\d+(,\\d+)*").isEqualTo("1234,5678");
    assertThat(nachricht.idempotencyKey()).isEqualTo("benachrichtigung:11:1234");
    assertThat(nachricht.eventType()).isEqualTo(BenachrichtigungVersand.TYP);
  }

  /** Der Port mit der Fälligkeitsregel; die Zustände ändern nur die beiden Setzer. */
  private static final class AnlaesseAttrappe implements AnlassRepository {

    private final Map<FaelligeZustellung, Instant> zeilen = new LinkedHashMap<>();
    private final Map<Long, String> zustaende = new LinkedHashMap<>();
    private final List<Long> eingeplant = new ArrayList<>();
    private final List<Long> entfallen = new ArrayList<>();

    void lege(FaelligeZustellung zustellung, Instant faelligAb) {
      zeilen.put(zustellung, faelligAb);
      zustaende.put(zustellung.id(), "OFFEN");
    }

    String zustand(long id) {
      return zustaende.get(id);
    }

    @Override
    public List<FaelligeZustellung> eingeplanteZustellungen(Collection<Long> ids) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setzeZugestellt(Collection<Long> ids) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setzeEingeplanteEntfallen(Collection<Long> ids) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int loescheAelterAls(Instant grenze) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<FaelligeZustellung> faelligeZustellungen(Instant jetzt) {
      return zeilen.entrySet().stream()
          .filter(zeile -> !zeile.getValue().isAfter(jetzt))
          .map(Map.Entry::getKey)
          .toList();
    }

    @Override
    public void setzeEingeplant(Collection<Long> ids) {
      eingeplant.addAll(ids);
      ids.forEach(id -> zustaende.put(id, "EINGEPLANT"));
    }

    @Override
    public void setzeEntfallen(Collection<Long> ids) {
      entfallen.addAll(ids);
      ids.forEach(id -> zustaende.put(id, "ENTFALLEN"));
    }

    @Override
    public Optional<Long> legeAnlassZumLaufAn(LaufAnlass anlass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Long> legeAnlassZumLabelAn(LabelAnlass anlass) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean gibtAnlassZurKarte(long cardId, AnlassArt art) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void legeZustellungAn(NeueZustellung zustellung) {
      throw new UnsupportedOperationException();
    }
  }
}
