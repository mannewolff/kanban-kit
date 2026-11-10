package org.mwolff.manban.benachrichtigung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.FaelligeZustellung;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LabelAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LaufAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.NeueZustellung;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungenRepository.EingeschaltetePerson;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.mwolff.manban.benachrichtigung.domain.Ruhezeit;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.KartenStandView;
import org.mwolff.manban.card.application.CardRunQueryService.LabelAnlassView;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe;
import org.mwolff.manban.nightrun.application.NightRunProperties;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;

/**
 * Die Anlässe aus Kartenlabels und ihre Zustellungen (Issue #2078, Plan #2070 E3, E4, E6).
 *
 * <p>Der Port liegt als Attrappe mit der Eindeutigkeit je {@code label_event_id} vor. Dass die
 * Datenbank sie wirklich hält, belegt {@code AnlassRepositoryIT}.
 */
// PMD.ExcessiveImports: Der Test verdrahtet alle Ports der Erkennung und baut ihre Datentypen.
@SuppressWarnings("PMD.ExcessiveImports")
class BenachrichtigungErkennungKartenTest {

  private static final Instant JETZT = Instant.parse("2026-11-09T12:00:00Z");
  private static final Duration FRIST = Duration.ofMinutes(10);
  private static final Instant ZEITPUNKT = JETZT.minus(Duration.ofMinutes(2));
  private static final Instant LAUF_START = JETZT.minus(Duration.ofHours(1));
  private static final Instant VOR_DEM_EREIGNIS = LAUF_START.minusSeconds(1);
  private static final long PROJEKT = 7L;
  private static final long PERSON = 11L;
  private static final long KARTE = 300L;
  private static final int NUMMER = 42;

  private final List<LabelAnlassView> ereignisse = new ArrayList<>();
  private final List<KartenStandView> staende = new ArrayList<>();
  private final AnlaesseAttrappe anlaesse = new AnlaesseAttrappe();
  private BenachrichtigbareLaeufe sicht;
  private BenachrichtigungEinstellungenRepository einstellungen;
  private PermissionChecker permissions;
  private CardRunQueryService karten;
  private BenachrichtigungErkennung erkennung;

  @BeforeEach
  void setUp() {
    sicht = mock(BenachrichtigbareLaeufe.class);
    einstellungen = mock(BenachrichtigungEinstellungenRepository.class);
    permissions = mock(PermissionChecker.class);
    karten = mock(CardRunQueryService.class);
    when(karten.labelAnlaesse(any(), any())).thenAnswer(inv -> List.copyOf(ereignisse));
    when(karten.kartenStand(any()))
        .thenAnswer(
            inv -> {
              Collection<Long> ids = inv.getArgument(0);
              return staende.stream().filter(k -> ids.contains(k.id())).toList();
            });
    when(sicht.paketeDerKarte(PROJEKT, NUMMER)).thenReturn(List.of());
    when(einstellungen.ruhezeit(anyLong())).thenReturn(Optional.empty());
    when(einstellungen.projektEingeschaltetSeit(PROJEKT)).thenReturn(Optional.of(VOR_DEM_EREIGNIS));
    when(einstellungen.eingeschaltetePersonen(PROJEKT))
        .thenReturn(List.of(new EingeschaltetePerson(PERSON, VOR_DEM_EREIGNIS)));
    when(permissions.hasPermission(PERSON, PROJEKT, Permission.TICKET_READ)).thenReturn(true);
    erkennung =
        new BenachrichtigungErkennung(
            sicht,
            anlaesse,
            einstellungen,
            permissions,
            karten,
            new NightRunProperties(null, null, null, null, Duration.ofMinutes(90)),
            new BenachrichtigungProperties(FRIST, null, 0),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private void ereignis(long id, String label, Instant createdAt, @Nullable Instant laufStart) {
    ereignisse.add(new LabelAnlassView(id, KARTE, label, createdAt, laufStart));
  }

  private void karteTraegt(String... labels) {
    staende.clear();
    staende.add(
        new KartenStandView(KARTE, PROJEKT, NUMMER, "Titel", List.of(labels), false, false));
  }

  private void pakete(NightRunItem... pakete) {
    when(sicht.paketeDerKarte(PROJEKT, NUMMER)).thenReturn(List.of(pakete));
  }

  private static NightRunItem paket(
      Instant start, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        1L,
        5L,
        PROJEKT,
        start,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        NUMMER,
        "Paket",
        state,
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

  private static NightRunItem paketMitGrund(Instant start) {
    return paket(start, NightRunState.RED, NightRunErrorClass.AWAITING_DECISION);
  }

  @ParameterizedTest
  @CsvSource({
    "kit:klaeren, KARTE_FRAGE",
    "kit:clarify, KARTE_FRAGE",
    "lauf:wartet, LAUF_WARTET",
    "run:waiting, LAUF_WARTET",
    "kit:geschuetzt, KARTE_GESCHUETZT",
    "kit:protected, KARTE_GESCHUETZT"
  })
  void einEreignisOhneLaufErgibtImNaechstenTaktEinenAnlass(String label, AnlassArt art) {
    karteTraegt(label);
    ereignis(1L, label, ZEITPUNKT, null);

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);

    assertThat(anlaesse.angelegt)
        .containsExactly(new LabelAnlass(art, PROJEKT, 1L, KARTE, ZEITPUNKT, JETZT));
    verify(sicht, never()).paketeDerKarte(anyLong(), anyInt());
  }

  @Test
  void gelesenWerdenDieNamenDerDreiPaareUeberAchtundvierzigStunden() {
    erkennung.erkenneKarten();

    // unchecked: ArgumentCaptor.forClass kennt zur Laufzeit keine Typparameter.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<String>> namen = ArgumentCaptor.forClass(Collection.class);
    verify(karten).labelAnlaesse(namen.capture(), eq(JETZT.minus(Duration.ofDays(2))));
    assertThat(namen.getValue())
        .containsExactlyInAnyOrder(
            "kit:klaeren",
            "kit:clarify",
            "lauf:wartet",
            "run:waiting",
            "kit:geschuetzt",
            "kit:protected");
  }

  @Test
  void ohneEreignisseEntstehtNichts() {
    assertThat(erkennung.erkenneKarten()).isZero();

    assertThat(anlaesse.angelegt).isEmpty();
  }

  @Test
  void eineKarteAusserhalbDesBestandsErgibtNichts() {
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);

    assertThat(erkennung.erkenneKarten()).isZero();

    assertThat(anlaesse.angelegt).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "kit:klaeren, kit:clarify",
    "kit:clarify, kit:klaeren",
    "lauf:wartet, run:waiting",
    "run:waiting, lauf:wartet",
    "kit:geschuetzt, kit:protected",
    "kit:protected, kit:geschuetzt"
  })
  void beideLabelNamenEinesPaarsErgebenEinenAnlass(String erstes, String zweites) {
    karteTraegt(erstes, zweites);
    ereignis(1L, erstes, ZEITPUNKT, null);
    ereignis(2L, zweites, ZEITPUNKT.plusSeconds(1), null);

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);

    assertThat(anlaesse.angelegt).extracting(LabelAnlass::labelEventId).containsExactly(1L);
  }

  @Test
  void einAnlassDerselbenArtZaehltNurBeiDerselbenKarte() {
    anlaesse.vorhanden.add(new VorhandenerAnlass(KARTE + 1, AnlassArt.KARTE_FRAGE));
    karteTraegt("kit:klaeren", "kit:clarify");
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);
  }

  @Test
  void einLaufAnlassDerselbenArtDerKarteZaehltFuerDasPaar() {
    anlaesse.vorhanden.add(new VorhandenerAnlass(KARTE, AnlassArt.LAUF_WARTET));
    karteTraegt("lauf:wartet", "run:waiting");
    ereignis(1L, "run:waiting", ZEITPUNKT, null);

    assertThat(erkennung.erkenneKarten()).isZero();
  }

  @Test
  void einAndererAnlassAnDerKarteVerhindertKeinenNeuen() {
    karteTraegt("kit:klaeren", "kit:protected");
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);
    ereignis(2L, "kit:protected", ZEITPUNKT.plusSeconds(1), null);

    assertThat(erkennung.erkenneKarten()).isEqualTo(2);
  }

  @Test
  void dasAndereLabelDesPaarsNichtMehrDranErgibtEinenNeuenAnlass() {
    karteTraegt("kit:clarify");
    ereignis(1L, "kit:klaeren", ZEITPUNKT.minusSeconds(60), null);
    erkennung.erkenneKarten();
    ereignis(2L, "kit:clarify", ZEITPUNKT, null);

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);

    assertThat(anlaesse.angelegt).extracting(LabelAnlass::labelEventId).containsExactly(1L, 2L);
  }

  @Test
  void dasselbeLabelErneutGesetztErgibtEinenNeuenAnlass() {
    karteTraegt("kit:klaeren");
    ereignis(1L, "kit:klaeren", ZEITPUNKT.minusSeconds(60), null);
    ereignis(2L, "kit:klaeren", ZEITPUNKT, null);

    assertThat(erkennung.erkenneKarten()).isEqualTo(2);

    assertThat(anlaesse.zustellungen).hasSize(2);
  }

  @Test
  void eineKarteDieWeiterWartetErgibtKeinenWeiteren() {
    karteTraegt("kit:klaeren");
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);
    assertThat(erkennung.erkenneKarten()).isZero();

    assertThat(anlaesse.angelegt).hasSize(1);
    assertThat(anlaesse.zustellungen).hasSize(1);
  }

  @Test
  void einEreignisMitLaufErgibtVorAblaufDerFristNichts() {
    karteTraegt("lauf:wartet");
    ereignis(1L, "lauf:wartet", JETZT.minus(FRIST).plusNanos(1_000), LAUF_START);

    assertThat(erkennung.erkenneKarten()).isZero();

    assertThat(anlaesse.angelegt).isEmpty();
    verify(sicht, never()).paketeDerKarte(anyLong(), anyInt());
  }

  @Test
  void mitAblaufDerFristOhnePaketEntstehtDerAnlass() {
    karteTraegt("lauf:wartet");
    ereignis(1L, "lauf:wartet", JETZT.minus(FRIST), LAUF_START);

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);

    verify(sicht).paketeDerKarte(PROJEKT, NUMMER);
  }

  @Test
  void nachAblaufMitPaketMitGrundEntstehtKeinAnlass() {
    karteTraegt("lauf:wartet");
    ereignis(1L, "lauf:wartet", JETZT.minus(FRIST), LAUF_START);
    pakete(paket(LAUF_START.minusSeconds(1), NightRunState.GREEN, null), paketMitGrund(LAUF_START));

    assertThat(erkennung.erkenneKarten()).isZero();

    assertThat(anlaesse.angelegt).isEmpty();
  }

  @Test
  void einPaketEinesAnderenLaufsZaehltNicht() {
    karteTraegt("lauf:wartet");
    ereignis(1L, "lauf:wartet", JETZT.minus(FRIST), LAUF_START);
    pakete(paketMitGrund(LAUF_START.minusSeconds(1)));

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);
  }

  @Test
  void einPaketDesLaufsOhneGrundZaehltNicht() {
    karteTraegt("lauf:wartet");
    ereignis(1L, "lauf:wartet", JETZT.minus(FRIST), LAUF_START);
    pakete(paket(LAUF_START, NightRunState.GREEN, null));

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);
  }

  @Test
  void eineZugelasseneZustellungIstSofortFaelligUndNichtVerschoben() {
    karteTraegt("kit:klaeren");
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);

    erkennung.erkenneKarten();

    assertThat(anlaesse.zustellungen)
        .containsExactly(new NeueZustellung(anlaesse.idVon(0), PERSON, JETZT, false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-11-09T11:58:00Z", "2026-11-09T11:59:00Z"})
  void einPersonenschalterAbDemEreignisErgibtKeineZustellung(Instant seit) {
    when(einstellungen.eingeschaltetePersonen(PROJEKT))
        .thenReturn(List.of(new EingeschaltetePerson(PERSON, seit)));
    karteTraegt("kit:klaeren");
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);

    assertThat(anlaesse.zustellungen).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-11-09T11:58:00Z", "2026-11-09T11:59:00Z"})
  void einProjektschalterAbDemEreignisErgibtKeineZustellung(Instant seit) {
    when(einstellungen.projektEingeschaltetSeit(PROJEKT)).thenReturn(Optional.of(seit));
    karteTraegt("kit:klaeren");
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);

    assertThat(erkennung.erkenneKarten()).isEqualTo(1);

    assertThat(anlaesse.zustellungen).isEmpty();
  }

  @Test
  void einePersonOhneTicketReadBekommtKeineZustellung() {
    when(permissions.hasPermission(PERSON, PROJEKT, Permission.TICKET_READ)).thenReturn(false);
    when(permissions.hatNachtlaufZugriff(PERSON, PROJEKT)).thenReturn(true);
    karteTraegt("kit:klaeren");
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);

    erkennung.erkenneKarten();

    assertThat(anlaesse.zustellungen).isEmpty();
  }

  @Test
  void einePersonMitTicketReadBrauchtKeinenNachtlaufZugriff() {
    when(permissions.hatNachtlaufZugriff(PERSON, PROJEKT)).thenReturn(false);
    karteTraegt("kit:klaeren");
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);

    erkennung.erkenneKarten();

    assertThat(anlaesse.zustellungen).hasSize(1);
  }

  @Test
  void dieRuhezeitVerschiebtDieZustellung() {
    when(einstellungen.ruhezeit(PERSON))
        .thenReturn(
            Optional.of(new Ruhezeit(LocalTime.of(11, 0), LocalTime.of(13, 0), ZoneId.of("UTC"))));
    karteTraegt("kit:klaeren");
    ereignis(1L, "kit:klaeren", ZEITPUNKT, null);

    erkennung.erkenneKarten();

    assertThat(anlaesse.zustellungen)
        .containsExactly(
            new NeueZustellung(
                anlaesse.idVon(0), PERSON, Instant.parse("2026-11-09T13:00:00Z"), true));
  }

  private record VorhandenerAnlass(long cardId, AnlassArt art) {}

  /** Hält die Eindeutigkeit je {@code label_event_id} nach. */
  private static final class AnlaesseAttrappe implements AnlassRepository {

    private final List<LabelAnlass> angelegt = new ArrayList<>();
    private final List<VorhandenerAnlass> vorhanden = new ArrayList<>();
    private final List<NeueZustellung> zustellungen = new ArrayList<>();

    long idVon(int position) {
      return 2000L + position;
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
    public Optional<Long> legeAnlassZumLaufAn(LaufAnlass anlass) {
      throw new UnsupportedOperationException("Karten legen keinen Lauf-Anlass an");
    }

    @Override
    public Optional<Long> legeAnlassZumLabelAn(LabelAnlass anlass) {
      if (angelegt.stream().anyMatch(a -> a.labelEventId() == anlass.labelEventId())) {
        return Optional.empty();
      }
      angelegt.add(anlass);
      vorhanden.add(new VorhandenerAnlass(anlass.cardId(), anlass.art()));
      return Optional.of(idVon(angelegt.size() - 1));
    }

    @Override
    public boolean gibtAnlassZurKarte(long cardId, AnlassArt art) {
      return vorhanden.contains(new VorhandenerAnlass(cardId, art));
    }

    @Override
    public void legeZustellungAn(NeueZustellung zustellung) {
      zustellungen.add(zustellung);
    }

    @Override
    public List<FaelligeZustellung> faelligeZustellungen(Instant jetzt) {
      throw new UnsupportedOperationException("Die Erkennung liest keine Zustellungen");
    }

    @Override
    public void setzeEingeplant(Collection<Long> ids) {
      throw new UnsupportedOperationException("Die Erkennung plant nichts ein");
    }

    @Override
    public void setzeEntfallen(Collection<Long> ids) {
      throw new UnsupportedOperationException("Die Erkennung lässt nichts entfallen");
    }
  }
}
