package org.mwolff.manban.benachrichtigung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
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
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.FaelligeZustellung;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LabelAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LaufAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.NeueZustellung;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungenRepository.EingeschaltetePerson;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.mwolff.manban.benachrichtigung.domain.Ruhezeit;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe.BenachrichtigbarerLauf;
import org.mwolff.manban.nightrun.application.NightRunProperties;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunAbortKind;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Die Anlässe aus Läufen und ihre Zustellungen (Issue #2077, Plan #2070 E2, E4, E5, E6, E7).
 *
 * <p>Der Port liegt hier als Attrappe mit der Eindeutigkeit der Datenbank vor: {@code (lauf_id,
 * art)} und {@code (anlass_id, user_id)}. Dass die Datenbank sie wirklich hält, belegt {@code
 * AnlassRepositoryIT}.
 */
// PMD.ExcessiveImports: Der Test verdrahtet alle Ports der Erkennung und baut ihre Datentypen.
@SuppressWarnings("PMD.ExcessiveImports")
class BenachrichtigungErkennungTest {

  private static final Instant JETZT = Instant.parse("2026-11-09T12:00:00Z");
  private static final Instant START = JETZT.minus(Duration.ofHours(1));
  private static final Instant MELDUNG = JETZT.minus(Duration.ofMinutes(10));
  private static final Instant VOR_DER_MELDUNG = MELDUNG.minusSeconds(1);
  private static final Duration STILLE = Duration.ofMinutes(90);
  private static final long LAUF_ID = 5L;
  private static final long PROJEKT = 7L;
  private static final long PERSON = 11L;
  private static final long KARTEN_ID = 300L;
  private static final NightRun LAUFEND = lauf(false, START, MELDUNG, null, null, null, null);
  private static final NightRun BEENDET = lauf(true, START, MELDUNG, null, null, null, null);
  private static final NightRun VERSTUMMT =
      lauf(
          false,
          START.minus(Duration.ofHours(3)),
          JETZT.minus(STILLE).minusSeconds(1),
          null,
          null,
          null,
          null);

  private final List<BenachrichtigbarerLauf> laeufe = new ArrayList<>();
  private final AnlaesseAttrappe anlaesse = new AnlaesseAttrappe();
  private BenachrichtigungEinstellungenRepository einstellungen;
  private PermissionChecker permissions;
  private CardRunQueryService karten;
  private BenachrichtigungErkennung erkennung;

  @BeforeEach
  void setUp() {
    BenachrichtigbareLaeufe sicht = mock(BenachrichtigbareLaeufe.class);
    when(sicht.laeufe()).thenAnswer(inv -> List.copyOf(laeufe));
    einstellungen = mock(BenachrichtigungEinstellungenRepository.class);
    permissions = mock(PermissionChecker.class);
    karten = mock(CardRunQueryService.class);
    when(karten.kartenId(anyLong(), anyInt())).thenReturn(Optional.empty());
    when(einstellungen.ruhezeit(anyLong())).thenReturn(Optional.empty());
    when(einstellungen.projektEingeschaltetSeit(PROJEKT)).thenReturn(Optional.of(VOR_DER_MELDUNG));
    when(einstellungen.eingeschaltetePersonen(PROJEKT))
        .thenReturn(List.of(new EingeschaltetePerson(PERSON, VOR_DER_MELDUNG)));
    when(permissions.hatNachtlaufZugriff(PERSON, PROJEKT)).thenReturn(true);
    erkennung =
        new BenachrichtigungErkennung(
            sicht,
            anlaesse,
            einstellungen,
            permissions,
            karten,
            new NightRunProperties(null, null, null, null, STILLE),
            new BenachrichtigungProperties(null, null, 0),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static NightRun lauf(
      boolean complete,
      Instant start,
      @Nullable Instant updatedAt,
      @Nullable Instant closedAt,
      @Nullable String noWorkReason,
      @Nullable String abortReason,
      @Nullable NightRunAbortKind abortKind) {
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
        complete,
        updatedAt,
        null,
        noWorkReason,
        null,
        abortReason,
        abortKind,
        null,
        List.of(),
        closedAt);
  }

  private static NightRunItem paket(
      int karte, NightRunState state, @Nullable NightRunErrorClass klasse) {
    return new NightRunItem(
        (long) karte,
        LAUF_ID,
        PROJEKT,
        START,
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

  private void gegeben(NightRun run, NightRunItem... pakete) {
    laeufe.clear();
    laeufe.add(new BenachrichtigbarerLauf(run, List.of(pakete), "Alpha", "Produkt"));
  }

  private List<AnlassArt> arten() {
    return anlaesse.angelegt.stream().map(LaufAnlass::art).toList();
  }

  private LaufAnlass anlass(AnlassArt art) {
    return anlaesse.angelegt.stream().filter(a -> a.art() == art).findFirst().orElseThrow();
  }

  @Test
  void hoechstensEinAnlassJeLaufUndArtUeberMehrereTakte() {
    gegeben(BEENDET, paket(1, NightRunState.RED, NightRunErrorClass.AWAITING_DECISION));

    assertThat(erkennung.erkenneLaeufe()).isEqualTo(2);
    assertThat(erkennung.erkenneLaeufe()).isZero();

    assertThat(arten()).containsExactlyInAnyOrder(AnlassArt.LAUF_WARTET, AnlassArt.UEBERSICHT);
    assertThat(anlaesse.zustellungen).hasSize(2);
  }

  @Test
  void rotMitAwaitingDecisionWartetUndIstKeineStoerung() {
    gegeben(LAUFEND, paket(1, NightRunState.RED, NightRunErrorClass.AWAITING_DECISION));

    erkennung.erkenneLaeufe();

    assertThat(arten()).containsExactly(AnlassArt.LAUF_WARTET);
  }

  @Test
  void einePaketAbhaengigkeitOhneMenschErgibtKeinenAnlass() {
    gegeben(LAUFEND, paket(1, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET));

    assertThat(erkennung.erkenneLaeufe()).isZero();

    assertThat(arten()).isEmpty();
  }

  @Test
  void einLaufMitHaltArbeitetWeiterUndDerAnlassEntstehtTrotzdem() {
    gegeben(
        LAUFEND,
        paket(1, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION),
        paket(2, NightRunState.GREEN, null));

    erkennung.erkenneLaeufe();

    assertThat(arten()).containsExactly(AnlassArt.LAUF_WARTET);
  }

  @Test
  void einePaketStoerungKommtWaehrendDesLaufsOhneUebersicht() {
    when(karten.kartenId(PROJEKT, 1)).thenReturn(Optional.of(KARTEN_ID));
    gegeben(LAUFEND, paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED));

    assertThat(erkennung.erkenneLaeufe()).isEqualTo(1);

    assertThat(arten()).containsExactly(AnlassArt.STOERUNG);
    assertThat(anlass(AnlassArt.STOERUNG).cardId()).isEqualTo(KARTEN_ID);
  }

  @Test
  void einLaufOhneGrundBekommtWaehrendDesLaufsNichts() {
    gegeben(LAUFEND, paket(1, NightRunState.GREEN, null));

    assertThat(erkennung.erkenneLaeufe()).isZero();
  }

  @Test
  void einBeendeterLaufBekommtDieUebersichtOhneKarte() {
    gegeben(BEENDET, paket(1, NightRunState.GREEN, null));

    erkennung.erkenneLaeufe();

    assertThat(arten()).containsExactly(AnlassArt.UEBERSICHT);
    assertThat(anlass(AnlassArt.UEBERSICHT).cardId()).isNull();
    assertThat(anlass(AnlassArt.UEBERSICHT).projectId()).isEqualTo(PROJEKT);
    assertThat(anlass(AnlassArt.UEBERSICHT).laufId()).isEqualTo(LAUF_ID);
  }

  @Test
  void einBeendeterLaufMitRotemPaketBekommtStoerungUndUebersicht() {
    gegeben(BEENDET, paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED));

    erkennung.erkenneLaeufe();

    assertThat(arten()).containsExactlyInAnyOrder(AnlassArt.STOERUNG, AnlassArt.UEBERSICHT);
  }

  @Test
  void einNichtAngelaufenerLaufBekommtDieUebersicht() {
    gegeben(lauf(true, START, MELDUNG, null, null, "Vorflug", NightRunAbortKind.REPORTED));

    erkennung.erkenneLaeufe();

    assertThat(arten()).containsExactly(AnlassArt.UEBERSICHT);
  }

  @Test
  void einVerstummterLaufErgibtNurDieUebersicht() {
    gegeben(VERSTUMMT);

    erkennung.erkenneLaeufe();

    assertThat(arten()).containsExactly(AnlassArt.UEBERSICHT);
  }

  @Test
  void einSpaeterAbgeschlossenerVerstummterLaufErgibtKeineZweiteUebersicht() {
    gegeben(VERSTUMMT);
    erkennung.erkenneLaeufe();

    gegeben(BEENDET);

    assertThat(erkennung.erkenneLaeufe()).isZero();
    assertThat(arten()).containsExactly(AnlassArt.UEBERSICHT);
  }

  @Test
  void einVonHandBeendeterLaufErgibtDieUebersichtOhneStoerung() {
    gegeben(
        lauf(false, START, MELDUNG, JETZT.minusSeconds(5), null, null, null),
        paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED),
        paket(2, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION));

    erkennung.erkenneLaeufe();

    assertThat(arten()).containsExactly(AnlassArt.UEBERSICHT);
  }

  @Test
  void einLaufOhneArbeitErgibtNichts() {
    gegeben(lauf(true, START, MELDUNG, null, "Nichts in Ready", null, null));

    assertThat(erkennung.erkenneLaeufe()).isZero();

    assertThat(arten()).isEmpty();
  }

  @Test
  void einWartenderLaufTraegtDieKarteDesErstenWartendenPakets() {
    when(karten.kartenId(PROJEKT, 2)).thenReturn(Optional.of(KARTEN_ID));
    gegeben(
        LAUFEND,
        paket(1, NightRunState.GREEN, null),
        paket(2, NightRunState.RED, NightRunErrorClass.AWAITING_DECISION),
        paket(3, NightRunState.RED, NightRunErrorClass.AWAITING_DECISION));

    erkennung.erkenneLaeufe();

    assertThat(arten()).containsExactly(AnlassArt.LAUF_WARTET);
    assertThat(anlass(AnlassArt.LAUF_WARTET).cardId()).isEqualTo(KARTEN_ID);
  }

  @Test
  void ohneZugeordneteKarteBleibtDieKarteLeer() {
    gegeben(LAUFEND, paket(9, NightRunState.RED, NightRunErrorClass.AWAITING_DECISION));

    erkennung.erkenneLaeufe();

    assertThat(anlass(AnlassArt.LAUF_WARTET).cardId()).isNull();
  }

  @Test
  void derEintrittKommtAusDerLetztenMeldungUndNichtAusDemTakt() {
    gegeben(BEENDET);

    erkennung.erkenneLaeufe();

    assertThat(anlass(AnlassArt.UEBERSICHT).eingetretenAt()).isEqualTo(MELDUNG);
    assertThat(anlass(AnlassArt.UEBERSICHT).angelegtAt()).isEqualTo(JETZT);
  }

  @Test
  void ohneFortschreibungIstDerStartDerEintritt() {
    when(einstellungen.projektEingeschaltetSeit(PROJEKT))
        .thenReturn(Optional.of(START.minusSeconds(1)));
    when(einstellungen.eingeschaltetePersonen(PROJEKT))
        .thenReturn(List.of(new EingeschaltetePerson(PERSON, START.minusSeconds(1))));
    gegeben(lauf(true, START, null, null, null, null, null));

    erkennung.erkenneLaeufe();

    assertThat(anlass(AnlassArt.UEBERSICHT).eingetretenAt()).isEqualTo(START);
    assertThat(anlaesse.zustellungen).hasSize(1);
  }

  @Test
  void einZugelasseneZustellungIstSofortFaelligUndNichtVerschoben() {
    gegeben(BEENDET);

    erkennung.erkenneLaeufe();

    assertThat(anlaesse.zustellungen)
        .containsExactly(new NeueZustellung(anlaesse.idVon(0), PERSON, JETZT, false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-11-09T11:50:00Z", "2026-11-09T12:00:00Z"})
  void einProjektschalterAbDemEintrittErgibtKeineZustellung(Instant seit) {
    when(einstellungen.projektEingeschaltetSeit(PROJEKT)).thenReturn(Optional.of(seit));
    gegeben(BEENDET);

    erkennung.erkenneLaeufe();

    assertThat(arten()).containsExactly(AnlassArt.UEBERSICHT);
    assertThat(anlaesse.zustellungen).isEmpty();
  }

  @Test
  void einAusgeschaltetesProjektErgibtKeineZustellung() {
    when(einstellungen.projektEingeschaltetSeit(PROJEKT)).thenReturn(Optional.empty());
    gegeben(BEENDET);

    erkennung.erkenneLaeufe();

    assertThat(anlaesse.zustellungen).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-11-09T11:50:00Z", "2026-11-09T12:00:00Z"})
  void einPersonenschalterAbDemEintrittErgibtKeineZustellung(Instant seit) {
    when(einstellungen.eingeschaltetePersonen(PROJEKT))
        .thenReturn(List.of(new EingeschaltetePerson(PERSON, seit)));
    gegeben(BEENDET);

    erkennung.erkenneLaeufe();

    assertThat(anlaesse.zustellungen).isEmpty();
  }

  @Test
  void eineZustellungVerlangtDasRechtLaeufeZuSehen() {
    when(permissions.hatNachtlaufZugriff(PERSON, PROJEKT)).thenReturn(false);
    gegeben(BEENDET);

    erkennung.erkenneLaeufe();

    assertThat(anlaesse.zustellungen).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "11:00, 13:00, 2026-11-09T13:00:00Z, true",
    "22:00, 06:00, 2026-11-09T12:00:00Z, false"
  })
  void dieRuhezeitBestimmtFaelligkeitUndVerschiebung(
      LocalTime beginn, LocalTime ende, Instant faelligAb, boolean verschoben) {
    when(einstellungen.ruhezeit(PERSON))
        .thenReturn(Optional.of(new Ruhezeit(beginn, ende, ZoneId.of("UTC"))));
    gegeben(BEENDET);

    erkennung.erkenneLaeufe();

    assertThat(anlaesse.zustellungen)
        .containsExactly(new NeueZustellung(anlaesse.idVon(0), PERSON, faelligAb, verschoben));
  }

  /** Hält die beiden Eindeutigkeiten der Datenbank nach. */
  private static final class AnlaesseAttrappe implements AnlassRepository {

    private final List<LaufAnlass> angelegt = new ArrayList<>();
    private final List<NeueZustellung> zustellungen = new ArrayList<>();

    long idVon(int position) {
      return 1000L + position;
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
      boolean vorhanden =
          angelegt.stream().anyMatch(a -> a.laufId() == anlass.laufId() && a.art() == anlass.art());
      if (vorhanden) {
        return Optional.empty();
      }
      angelegt.add(anlass);
      return Optional.of(idVon(angelegt.size() - 1));
    }

    @Override
    public Optional<Long> legeAnlassZumLabelAn(LabelAnlass anlass) {
      throw new UnsupportedOperationException("Läufe legen keinen Label-Anlass an");
    }

    @Override
    public boolean gibtAnlassZurKarte(long cardId, AnlassArt art) {
      throw new UnsupportedOperationException("Läufe prüfen keine Karte");
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
