package org.mwolff.manban.benachrichtigung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.FaelligeZustellung;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungVersand;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.KartenStandView;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe.LaufMitPaketen;
import org.mwolff.manban.nightrun.application.NightRunProperties;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunAbortKind;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Das Zustellen der eingeplanten Benachrichtigungen (Issue #2080, Plan #2070 E6, E9, E11, E13,
 * E16): Rechte und Konto zum Versandzeitpunkt, Zustände und was in der Mail steht.
 */
// PMD.ExcessiveImports: Der Test verdrahtet alle Ports des Handlers und baut ihre Datentypen.
// PMD.TooManyMethods, PMD.GodClass, PMD.CouplingBetweenObjects: Je Regel aus dem
// Akzeptanzkriterium ein Test gegen dieselben Attrappen.
@SuppressWarnings({
  "PMD.ExcessiveImports",
  "PMD.TooManyMethods",
  "PMD.GodClass",
  "PMD.CouplingBetweenObjects"
})
class BenachrichtigungMailHandlerTest {

  private static final Instant JETZT = Instant.parse("2026-11-09T12:00:00Z");
  private static final Instant START = JETZT.minus(Duration.ofHours(3));
  private static final Duration STILLE_FRIST = Duration.ofMinutes(90);
  private static final long PROJEKT = 7L;
  private static final long PERSON = 11L;
  private static final long LAUF_ID = 500L;
  private static final long KARTEN_ID = 300L;
  private static final int NUMMER = 42;
  private static final String TITEL = "Geheimer Titel";
  private static final String ADRESSE = "person@example.org";
  private static final String BASIS = "https://app.example.org";
  private static final String KARTEN_LINK = BASIS + "/projects/7/karten/42";
  private static final String LAUF_LINK = BASIS + "/projects/7/nachtlauf";
  private static final String KEIN_BEDARF = "Kein Handlungsbedarf";

  private final List<FaelligeZustellung> eingeplant = new ArrayList<>();
  private AnlassRepository anlaesse;
  private UserLookup users;
  private PermissionChecker permissions;
  private ProjectService projects;
  private CardRunQueryService karten;
  private BenachrichtigbareLaeufe laeufe;
  private JavaMailSender mailSender;

  @BeforeEach
  void setUp() {
    anlaesse = mock(AnlassRepository.class);
    users = mock(UserLookup.class);
    permissions = mock(PermissionChecker.class);
    projects = mock(ProjectService.class);
    karten = mock(CardRunQueryService.class);
    laeufe = mock(BenachrichtigbareLaeufe.class);
    mailSender = mock(JavaMailSender.class);
    when(anlaesse.eingeplanteZustellungen(any())).thenReturn(eingeplant);
    when(users.findById(PERSON))
        .thenReturn(Optional.of(new UserSummary(PERSON, ADRESSE, "Person", true)));
    when(users.istAktiv(PERSON)).thenReturn(true);
    when(permissions.hatNachtlaufZugriff(anyLong(), eq(PROJEKT))).thenReturn(true);
    when(permissions.hasPermission(anyLong(), eq(PROJEKT), eq(Permission.TICKET_READ)))
        .thenReturn(true);
    when(projects.findName(PROJEKT)).thenReturn(Optional.of("Alpha"));
    when(karten.kartenStand(any())).thenReturn(List.of(karte()));
    when(laeufe.laufMitPaketen(anyLong(), anyLong())).thenReturn(Optional.empty());
  }

  private BenachrichtigungMailHandler handler(Sprache sprache, boolean mailEnabled) {
    NightRunProperties nightRun = mock(NightRunProperties.class);
    when(nightRun.stilleFrist()).thenReturn(STILLE_FRIST);
    return new BenachrichtigungMailHandler(
        anlaesse,
        users,
        permissions,
        projects,
        karten,
        laeufe,
        nightRun,
        new AuthProperties(BASIS, "secret", null, null, null),
        SprachFixture.mit(sprache),
        mailSender,
        Clock.fixed(JETZT, ZoneOffset.UTC),
        mailEnabled,
        "no-reply@example.org",
        "kanban-kit");
  }

  private BenachrichtigungMailHandler handler() {
    return handler(Sprache.DE, true);
  }

  private static KartenStandView karte() {
    return new KartenStandView(KARTEN_ID, PROJEKT, NUMMER, TITEL, List.of(), false, false);
  }

  /** Ein Anlass aus einem Lauf, sofort zuzustellen. */
  private void ausLauf(long id, AnlassArt art, @Nullable Long cardId) {
    eingeplant.add(new FaelligeZustellung(id, PERSON, false, art, PROJEKT, LAUF_ID, cardId));
  }

  /** Ein Anlass aus einem Label-Ereignis, sofort zuzustellen. */
  private void ausLabel(long id, AnlassArt art) {
    eingeplant.add(new FaelligeZustellung(id, PERSON, false, art, PROJEKT, null, KARTEN_ID));
  }

  private void verschoben(long id, AnlassArt art) {
    eingeplant.add(new FaelligeZustellung(id, PERSON, true, art, PROJEKT, LAUF_ID, null));
  }

  private static NightRun run(
      boolean complete,
      @Nullable NightRunUsage usage,
      @Nullable String abortReason,
      @Nullable NightRunAbortKind abortKind,
      @Nullable Instant closedAt) {
    return new NightRun(
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
        usage,
        null,
        null,
        abortReason,
        abortKind,
        null,
        List.of(),
        closedAt,
        null);
  }

  private static NightRunUsage kosten(@Nullable BigDecimal costUsd) {
    return new NightRunUsage(costUsd, null, null, null, null, null);
  }

  private static NightRunItem paket(
      int nummer, String titel, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        1L,
        LAUF_ID,
        PROJEKT,
        START,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        nummer,
        titel,
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

  private void lauf(NightRun run, NightRunItem... pakete) {
    when(laeufe.laufMitPaketen(PROJEKT, LAUF_ID))
        .thenReturn(Optional.of(new LaufMitPaketen(run, List.of(pakete))));
  }

  private void gelungenerLauf(NightRunItem... pakete) {
    lauf(run(true, kosten(BigDecimal.ONE), null, null, null), pakete);
  }

  private SimpleMailMessage versandt() {
    ArgumentCaptor<SimpleMailMessage> nachricht = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(nachricht.capture());
    return nachricht.getValue();
  }

  private String textDerUebersicht() {
    ausLauf(1L, AnlassArt.UEBERSICHT, null);
    handler().handle("1");
    return versandt().getText();
  }

  private void keineMail() {
    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  private static Collection<Long> ids(Long... ids) {
    return List.of(ids);
  }

  @Test
  void derTypPasstZurVormerkung() {
    assertThat(handler().eventType()).isEqualTo(BenachrichtigungVersand.TYP);
  }

  @Test
  void dieMailGehtAlsTextAnDiePersonUndDieZustellungWirdZugestellt() {
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    handler().handle("1");

    SimpleMailMessage mail = versandt();
    assertThat(mail.getFrom()).isEqualTo("no-reply@example.org");
    assertThat(mail.getTo()).containsExactly(ADRESSE);
    assertThat(mail.getText())
        .contains("Projekt: Alpha")
        .contains("Karte: #42 " + TITEL)
        .contains("Nächster Schritt: ");
    verify(anlaesse).setzeZugestellt(ids(1L));
    verify(anlaesse).setzeEingeplanteEntfallen(List.of());
  }

  @Test
  void dieIdsKommenAusDerPayload() {
    verschoben(3L, AnlassArt.STOERUNG);

    handler().handle("3,5,9");

    verify(anlaesse).eingeplanteZustellungen(List.of(3L, 5L, 9L));
  }

  @Test
  void ohneEingeplanteZustellungGeschiehtNichts() {
    handler().handle("1");

    keineMail();
    verify(anlaesse, never()).setzeZugestellt(any());
    verify(anlaesse, never()).setzeEingeplanteEntfallen(any());
  }

  @Test
  void einEntzogenerLaufzugriffFuehrtZuKeinerMailUndEntfaellt() {
    when(permissions.hatNachtlaufZugriff(PERSON, PROJEKT)).thenReturn(false);
    ausLauf(1L, AnlassArt.STOERUNG, null);

    handler().handle("1");

    keineMail();
    verify(anlaesse).setzeEingeplanteEntfallen(List.of(1L));
    verify(anlaesse, never()).setzeZugestellt(any());
  }

  @Test
  void einEntzogenesLeserechtFuehrtZuKeinerMailUndEntfaellt() {
    when(permissions.hasPermission(PERSON, PROJEKT, Permission.TICKET_READ)).thenReturn(false);
    ausLabel(1L, AnlassArt.KARTE_FRAGE);

    handler().handle("1");

    keineMail();
    verify(anlaesse).setzeEingeplanteEntfallen(List.of(1L));
  }

  @Test
  void einLaufAnlassVerlangtNurDenLaufzugriff() {
    when(permissions.hasPermission(PERSON, PROJEKT, Permission.TICKET_READ)).thenReturn(false);
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    handler().handle("1");

    versandt();
  }

  @Test
  void einKartenAnlassVerlangtNurDasLeserecht() {
    when(permissions.hatNachtlaufZugriff(PERSON, PROJEKT)).thenReturn(false);
    ausLabel(1L, AnlassArt.KARTE_FRAGE);

    handler().handle("1");

    versandt();
  }

  @Test
  void einDeaktiviertesKontoBekommtKeineMailUndAllesEntfaellt() {
    when(users.istAktiv(PERSON)).thenReturn(false);
    verschoben(1L, AnlassArt.STOERUNG);
    verschoben(2L, AnlassArt.UEBERSICHT);

    handler().handle("1,2");

    keineMail();
    verify(anlaesse).setzeEingeplanteEntfallen(List.of(1L, 2L));
  }

  @Test
  void einVerschwundenesKontoBekommtKeineMailUndEntfaellt() {
    when(users.findById(PERSON)).thenReturn(Optional.empty());
    ausLauf(1L, AnlassArt.STOERUNG, null);

    handler().handle("1");

    keineMail();
    verify(anlaesse).setzeEingeplanteEntfallen(List.of(1L));
  }

  @Test
  void einVerschwundenesProjektLaesstDieZustellungEntfallen() {
    when(projects.findName(PROJEKT)).thenReturn(Optional.empty());
    ausLauf(1L, AnlassArt.STOERUNG, null);

    handler().handle("1");

    keineMail();
    verify(anlaesse).setzeEingeplanteEntfallen(List.of(1L));
  }

  @Test
  void einVerschwundenerLaufLaesstDieUebersichtEntfallen() {
    ausLauf(1L, AnlassArt.UEBERSICHT, null);

    handler().handle("1");

    keineMail();
    verify(anlaesse).setzeEingeplanteEntfallen(List.of(1L));
  }

  @Test
  void eineVerschwundeneKarteLaesstDenKartenAnlassEntfallen() {
    when(karten.kartenStand(any())).thenReturn(List.of());
    ausLabel(1L, AnlassArt.KARTE_FRAGE);

    handler().handle("1");

    keineMail();
    verify(anlaesse).setzeEingeplanteEntfallen(List.of(1L));
  }

  @Test
  void beiEinemLaufAnlassFehltDieVerschwundeneKarteNurAlsAngabe() {
    when(karten.kartenStand(any())).thenReturn(List.of());
    ausLauf(1L, AnlassArt.LAUF_WARTET, KARTEN_ID);

    handler().handle("1");

    assertThat(versandt().getText()).doesNotContain("Karte:");
    verify(anlaesse).setzeZugestellt(ids(1L));
  }

  @Test
  void ohneKarteFehltDieAngabe() {
    ausLauf(1L, AnlassArt.STOERUNG, null);

    handler().handle("1");

    assertThat(versandt().getText()).doesNotContain("Karte:").doesNotContain(TITEL);
    verify(karten).kartenStand(List.of());
  }

  @Test
  void entfalleneFehlenInDerMailDerRestWirdZugestellt() {
    when(karten.kartenStand(any())).thenReturn(List.of());
    gelungenerLauf();
    eingeplant.add(
        new FaelligeZustellung(1L, PERSON, true, AnlassArt.KARTE_FRAGE, PROJEKT, null, KARTEN_ID));
    verschoben(2L, AnlassArt.UEBERSICHT);

    handler().handle("1,2");

    assertThat(versandt().getText()).doesNotContain("Frage an dich").contains("Ausgang:");
    verify(anlaesse).setzeEingeplanteEntfallen(List.of(1L));
    verify(anlaesse).setzeZugestellt(ids(2L));
  }

  @Test
  void entfalleneWerdenVorDemVersandVermerktZugestellteDanach() {
    when(karten.kartenStand(any())).thenReturn(List.of());
    gelungenerLauf();
    eingeplant.add(
        new FaelligeZustellung(1L, PERSON, true, AnlassArt.KARTE_FRAGE, PROJEKT, null, KARTEN_ID));
    verschoben(2L, AnlassArt.UEBERSICHT);

    handler().handle("1,2");

    InOrder reihenfolge = inOrder(anlaesse, mailSender);
    reihenfolge.verify(anlaesse).setzeEingeplanteEntfallen(List.of(1L));
    reihenfolge.verify(mailSender).send(any(SimpleMailMessage.class));
    reihenfolge.verify(anlaesse).setzeZugestellt(ids(2L));
  }

  @Test
  void einVersandfehlerWirftUndNichtsGiltAlsZugestellt() {
    ausLauf(1L, AnlassArt.STOERUNG, null);
    doThrow(new MailSendException("weg")).when(mailSender).send(any(SimpleMailMessage.class));

    BenachrichtigungMailHandler handler = handler();

    assertThatThrownBy(() -> handler.handle("1")).isInstanceOf(MailSendException.class);
    verify(anlaesse, never()).setzeZugestellt(any());
  }

  @Test
  void ohneMailversandGehtNichtsHinausUndKeinZustandAendertSich() {
    ausLauf(1L, AnlassArt.STOERUNG, null);

    handler(Sprache.DE, false).handle("1");

    verifyNoInteractions(mailSender);
    verify(anlaesse, never()).setzeZugestellt(any());
    verify(anlaesse, never()).setzeEingeplanteEntfallen(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = AnlassArt.class,
      names = {"KARTE_FRAGE", "KARTE_GESCHUETZT", "LAUF_WARTET"})
  void einLabelAnlassVerlinktDieKarte(AnlassArt art) {
    ausLabel(1L, art);

    handler().handle("1");

    assertThat(versandt().getText()).contains(KARTEN_LINK).doesNotContain(LAUF_LINK);
  }

  @ParameterizedTest
  @EnumSource(
      value = AnlassArt.class,
      names = {"LAUF_WARTET", "STOERUNG"})
  void einLaufAnlassVerlinktDenLauf(AnlassArt art) {
    ausLauf(1L, art, KARTEN_ID);

    handler().handle("1");

    assertThat(versandt().getText()).contains(LAUF_LINK).doesNotContain(KARTEN_LINK);
  }

  @Test
  void dieUebersichtVerlinktDenLauf() {
    gelungenerLauf();

    assertThat(textDerUebersicht()).contains(LAUF_LINK);
  }

  @Test
  void einProjektnameMitZeilenumbruchErgibtEinenBetreffOhneZeilenumbruch() {
    when(projects.findName(PROJEKT)).thenReturn(Optional.of("Alpha\r\nBcc: boese@example.org"));
    ausLauf(1L, AnlassArt.STOERUNG, null);

    handler().handle("1");

    assertThat(versandt().getSubject())
        .doesNotContain("\n")
        .doesNotContain("\r")
        .isEqualTo("kanban-kit: Alpha Bcc: boese@example.org – Störung im Lauf");
  }

  @Test
  void derBetreffEnthaeltDenKartentitelNicht() {
    ausLabel(1L, AnlassArt.KARTE_FRAGE);

    handler().handle("1");

    SimpleMailMessage mail = versandt();
    assertThat(mail.getSubject()).doesNotContain(TITEL).contains("#42");
    assertThat(mail.getText()).contains(TITEL);
  }

  @Test
  void eineFehlendeKostenangabeErscheintAlsNichtGemeldetUndNichtAlsNull() {
    lauf(run(true, kosten(null), null, null, null));

    assertThat(textDerUebersicht()).contains("nicht gemeldet").doesNotContain("0,00");
  }

  @Test
  void einLaufOhneVerbrauchsmeldungErscheintAlsNichtGemeldet() {
    lauf(run(true, null, null, null, null));

    assertThat(textDerUebersicht()).contains("nicht gemeldet");
  }

  @Test
  void gemeldeteKostenStehenInDerUebersicht() {
    lauf(run(true, kosten(new BigDecimal("2.5")), null, null, null));

    assertThat(textDerUebersicht()).contains("Kosten: 2,50 USD");
  }

  @Test
  void ohneWartendeUndOhneStoerungStehtKeinHandlungsbedarf() {
    gelungenerLauf(paket(1, "Fertig", NightRunState.GREEN, null));

    assertThat(textDerUebersicht()).contains(KEIN_BEDARF).contains("Ausgang: Gelungen");
  }

  @Test
  void wartendeMenschenschritteSindDiePaketeMitAwaitingDecision() {
    gelungenerLauf(
        paket(42, "Wartet", NightRunState.RED, NightRunErrorClass.AWAITING_DECISION),
        paket(43, "Fertig", NightRunState.GREEN, null));

    assertThat(textDerUebersicht())
        .contains("- #42 Wartet")
        .doesNotContain("#43")
        .doesNotContain(KEIN_BEDARF);
  }

  @Test
  void einGescheitertesPaketIstEineStoerung() {
    gelungenerLauf(paket(42, "Rot", NightRunState.RED, null));

    assertThat(textDerUebersicht()).doesNotContain(KEIN_BEDARF).contains("Ausgang: Gescheitert");
  }

  @Test
  void einAbgebrochenerLaufOhnePaketVerlangtHandeln() {
    lauf(
        run(true, kosten(BigDecimal.ONE), "Abbruch", NightRunAbortKind.REPORTED, null),
        paket(1, "Fertig", NightRunState.GREEN, null));

    assertThat(textDerUebersicht()).doesNotContain(KEIN_BEDARF).contains("Ausgang: Gescheitert");
  }

  @Test
  void einStillerLaufHeisstVerstummtUndVerlangtHandeln() {
    NightRun still =
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
            false,
            JETZT.minus(STILLE_FRIST).minusSeconds(1),
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null);
    lauf(still);

    assertThat(textDerUebersicht()).contains("Ausgang: Verstummt").doesNotContain(KEIN_BEDARF);
  }

  @Test
  void einVomWaechterAbgeschlossenerLaufHeisstVerstummt() {
    lauf(run(true, null, "Stille", NightRunAbortKind.SILENCED, null));

    assertThat(textDerUebersicht()).contains("Ausgang: Verstummt").doesNotContain(KEIN_BEDARF);
  }

  @Test
  void einAndererAbbruchIstNichtVerstummt() {
    lauf(
        run(true, null, "Abbruch", NightRunAbortKind.REPORTED, null),
        paket(1, "Rot", NightRunState.RED, null));

    assertThat(textDerUebersicht()).contains("Ausgang: Gescheitert");
  }

  @Test
  void einVonHandBeendeterLaufIstKeineStoerung() {
    lauf(run(false, null, null, null, START));

    assertThat(textDerUebersicht()).contains("Ausgang: Von Hand beendet").contains(KEIN_BEDARF);
  }

  @Test
  void einGrundDesAbbruchsErscheintNichtInDerMail() {
    lauf(
        run(true, null, "/geheim/pfad.log kaputt", NightRunAbortKind.REPORTED, null),
        paket(1, "Rot", NightRunState.RED, null));

    assertThat(textDerUebersicht()).doesNotContain("/geheim/pfad.log");
  }

  @Test
  void eineVerschobeneZustellungKommtAlsSammelmailAuchAllein() {
    verschoben(1L, AnlassArt.STOERUNG);

    handler().handle("1");

    SimpleMailMessage mail = versandt();
    assertThat(mail.getSubject()).contains("Ruhezeit");
    assertThat(mail.getText()).contains("Während deiner Ruhezeit trat");
  }

  @Test
  void jeZustellungEinAbschnittInDerSammelmailUndAlleWerdenZugestellt() {
    gelungenerLauf();
    verschoben(1L, AnlassArt.STOERUNG);
    verschoben(2L, AnlassArt.UEBERSICHT);

    handler().handle("1,2");

    assertThat(versandt().getText())
        .contains("trat in einem Lauf eine Störung auf")
        .contains("endete ein Lauf");
    verify(anlaesse).setzeZugestellt(ids(1L, 2L));
  }

  @Test
  void dieEingestellteSpracheBestimmtDieTexte() {
    ausLauf(1L, AnlassArt.STOERUNG, null);

    handler(Sprache.EN, true).handle("1");

    SimpleMailMessage mail = versandt();
    assertThat(mail.getSubject()).isEqualTo("kanban-kit: Alpha – Disruption in a run");
    assertThat(mail.getText()).contains("Project: Alpha");
  }

  @ParameterizedTest
  @CsvSource({"DE", "EN"})
  void dieMailEnthaeltKeinenLabelNamen(Sprache sprache) {
    gelungenerLauf(paket(42, "Wartet", NightRunState.RED, NightRunErrorClass.AWAITING_DECISION));
    ausLabel(1L, AnlassArt.KARTE_FRAGE);

    handler(sprache, true).handle("1");
    SimpleMailMessage mail = versandt();

    Arrays.stream(Sache.values())
        .flatMap(sache -> KitNamen.STANDARD.namen(sache).stream())
        .forEach(
            name -> {
              assertThat(mail.getSubject()).doesNotContain(name);
              assertThat(mail.getText()).doesNotContain(name);
            });
  }
}
