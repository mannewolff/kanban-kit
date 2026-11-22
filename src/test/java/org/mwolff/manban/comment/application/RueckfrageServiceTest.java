package org.mwolff.manban.comment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.comment.application.CommentService.CommentView;
import org.mwolff.manban.comment.application.RueckfrageService.AntwortAuftrag;
import org.mwolff.manban.comment.application.RueckfrageService.EintragView;
import org.mwolff.manban.comment.application.RueckfrageService.EinzelAuftrag;
import org.mwolff.manban.comment.application.RueckfrageService.OptionView;
import org.mwolff.manban.comment.application.RueckfrageService.RueckfrageView;
import org.mwolff.manban.comment.domain.Antwort;
import org.mwolff.manban.comment.domain.Rueckfrage.Art;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.FrageZustand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.RueckfrageZustand;
import org.mwolff.manban.comment.domain.Vorschau;
import org.mwolff.manban.common.TextLimits;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;

/** Rückfragen lesen und beantworten (Issue #2210, Plan #2206 E8, E9, E11). */
class RueckfrageServiceTest {

  private static final long USER = 3L;
  private static final long CARD = 5L;
  private static final long PROJECT = 1L;
  private static final long RUECKFRAGE_ID = 40L;
  private static final Instant FRAGE_AM = Instant.parse("2026-11-22T10:00:00Z");
  private static final Instant SPAETER = Instant.parse("2026-11-22T11:00:00Z");

  private static final String DREI_FRAGEN =
      """
      ```json kit-vorlage
      [
        {"nr": 1, "art": "frage", "frage": "Welche Farbe?", "empfehlung": "Blau",
         "grund": "passt", "einarbeitung": "Farbe Blau",
         "optionen": [{"text": "Rot"}, {"text": "Blau", "hinweis": "ruhig", "empfohlen": true}]},
        {"nr": 2, "frage": "Wie gross?", "empfehlung": "mittel", "grund": "lesbar",
         "einarbeitung": "mittel"},
        {"nr": 3, "frage": "Wohin?", "empfehlung": "oben", "grund": "sichtbar",
         "einarbeitung": "oben"}
      ]
      ```
      """;

  private CommentService comments;
  private PermissionChecker permissions;
  private JuengsteVorschau vorschauen;
  private RueckfrageService service;
  private List<CommentView> ankarte;
  private long naechsteId;

  @BeforeEach
  void setUp() {
    comments = mock(CommentService.class);
    CardService cards = mock(CardService.class);
    permissions = mock(PermissionChecker.class);
    vorschauen = mock(JuengsteVorschau.class);
    service = new RueckfrageService(comments, cards, permissions, vorschauen);
    when(cards.requireProjectId(CARD)).thenReturn(PROJECT);
    when(vorschauen.juengste(PROJECT)).thenReturn(Optional.empty());
    ankarte = new ArrayList<>();
    naechsteId = 100L;
    when(comments.list(USER, CARD)).thenAnswer(inv -> List.copyOf(ankarte));
    when(comments.create(eq(USER), eq(CARD), anyString()))
        .thenAnswer(
            inv -> {
              CommentView neu = kommentar(naechsteId, inv.getArgument(2), SPAETER, null, false);
              naechsteId++;
              ankarte.add(neu);
              return neu;
            });
  }

  private static CommentView kommentar(
      long id, String body, Instant am, String zugangArt, boolean vonKi) {
    return new CommentView(
        id, CARD, USER, "Ada", body, am, am, "SESSION", null, null, zugangArt, vonKi);
  }

  private void rueckfrageAnDerKarte(String kopf) {
    ankarte.add(kommentar(RUECKFRAGE_ID, kopf + DREI_FRAGEN, FRAGE_AM, "STANDARD", true));
  }

  private static AntwortAuftrag allesUebernehmen() {
    return new AntwortAuftrag(true, List.of(), null, false);
  }

  @Test
  @DisplayName("Szenario: Fragen der KI per Klick beantworten")
  void szenarioFragenDerKiPerKlickBeantworten() {
    // Angenommen an einer Idee steht eine Rückfrage der KI mit drei Fragen
    rueckfrageAnDerKarte("## Rückfrage\n");

    // Wenn der Kunde „Alles übernehmen“ drückt
    RueckfrageView nachher = service.antworten(USER, CARD, RUECKFRAGE_ID, allesUebernehmen());

    // Dann steht an der Karte ein Kommentar „Antwort“ …
    CommentView antwort = ankarte.getLast();
    assertThat(antwort.body()).startsWith("## Antwort\n").contains("```json kit-antwort\n");
    assertThat(Antwort.lies(antwort.body()).orElseThrow().vorlage()).isEqualTo(RUECKFRAGE_ID);
    assertThat(nachher.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
    // … und die Karte zeigt zu jeder der drei Fragen die Empfehlung als gewählt
    List<RueckfrageView> rueckfragen = service.liste(USER, CARD);
    assertThat(rueckfragen).singleElement().isEqualTo(nachher);
    assertThat(rueckfragen.getFirst().eintraege())
        .allSatisfy(
            eintrag -> {
              assertThat(eintrag.zustand()).isEqualTo(FrageZustand.BEANTWORTET);
              assertThat(eintrag.empfehlungUebernommen()).isTrue();
            });
    assertThat(rueckfragen.getFirst().eintraege()).hasSize(3);
    verify(permissions).require(USER, PROJECT, Permission.COMMENT_CREATE);
  }

  @Test
  void listeZeigtEintraegeMitMoeglichkeitenUndStand() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\nVorschau: https://p\nCommit: abc\n");
    ankarte.add(kommentar(41L, "Nur ein Kommentar", SPAETER, null, false));

    // When
    List<RueckfrageView> rueckfragen = service.liste(USER, CARD);

    // Then
    assertThat(rueckfragen).hasSize(1);
    RueckfrageView view = rueckfragen.getFirst();
    assertThat(view.kommentarId()).isEqualTo(RUECKFRAGE_ID);
    assertThat(view.angelegtAm()).isEqualTo(FRAGE_AM);
    assertThat(view.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
    assertThat(view.vorschau()).isEqualTo("https://p");
    assertThat(view.commit()).isEqualTo("abc");
    assertThat(view.eintraege().getFirst())
        .isEqualTo(
            new EintragView(
                1,
                Art.FRAGE,
                "Welche Farbe?",
                "Blau",
                "passt",
                "Farbe Blau",
                List.of(new OptionView("Rot", null, false), new OptionView("Blau", "ruhig", true)),
                FrageZustand.OFFEN,
                null,
                null,
                false));
  }

  @Test
  void antwortDesStandardZugangsZaehltInDerListeNicht() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\n");
    String antwort =
        new Antwort(RUECKFRAGE_ID, true, List.of(), null, null)
            .alsKommentar(org.mwolff.manban.comment.domain.Kopfsprache.DEUTSCH);
    ankarte.add(kommentar(41L, antwort, SPAETER, "STANDARD", false));

    // When / Then
    assertThat(service.liste(USER, CARD).getFirst().zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void englischeRueckfrageBekommtEnglischeAntwort() {
    // Given
    rueckfrageAnDerKarte("## Question\n");

    // When
    service.antworten(USER, CARD, RUECKFRAGE_ID, allesUebernehmen());

    // Then
    assertThat(ankarte.getLast().body()).startsWith("## Answer\n");
  }

  @Test
  void einzelantwortenWerdenGeschrieben() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\n");
    AntwortAuftrag auftrag =
        new AntwortAuftrag(
            false,
            List.of(
                new EinzelAuftrag(1, 1, null, null),
                new EinzelAuftrag(2, null, "eigene", false),
                new EinzelAuftrag(3, null, null, true)),
            null,
            false);

    // When
    RueckfrageView view = service.antworten(USER, CARD, RUECKFRAGE_ID, auftrag);

    // Then
    assertThat(view.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
    assertThat(view.eintraege())
        .extracting(EintragView::zustand)
        .containsExactly(
            FrageZustand.BEANTWORTET, FrageZustand.BEANTWORTET, FrageZustand.ZURUECKGESTELLT);
    assertThat(view.eintraege().get(0).wahl()).isEqualTo(1);
    assertThat(view.eintraege().get(1).text()).isEqualTo("eigene");
  }

  @Test
  void freigabeSchreibtDenCommitDerRueckfrage() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\nVorschau: https://p\nCommit: abc\n");
    when(vorschauen.juengste(PROJECT)).thenReturn(Optional.of(new Vorschau(SPAETER, "abc")));

    // When
    RueckfrageView view =
        service.antworten(
            USER, CARD, RUECKFRAGE_ID, new AntwortAuftrag(true, List.of(), null, true));

    // Then
    assertThat(Antwort.lies(ankarte.getLast().body()).orElseThrow().commit()).isEqualTo("abc");
    assertThat(view.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
  }

  @Test
  void freigebenAnUeberholterRueckfrageEndetMit409() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\nVorschau: https://p\nCommit: abc\n");
    when(vorschauen.juengste(PROJECT)).thenReturn(Optional.of(new Vorschau(SPAETER, "neu")));

    // When / Then
    assertThatThrownBy(
            () ->
                service.antworten(
                    USER, CARD, RUECKFRAGE_ID, new AntwortAuftrag(true, List.of(), null, true)))
        .isInstanceOf(RueckfrageUeberholtException.class);
    verify(comments, never()).create(anyLong(), anyLong(), anyString());
    assertThat(service.liste(USER, CARD).getFirst().zustand())
        .isEqualTo(RueckfrageZustand.UEBERHOLT);
  }

  @Test
  void antwortOhneFreigabeAnUeberholterRueckfrageIstErlaubt() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\nVorschau: https://p\nCommit: abc\n");
    when(vorschauen.juengste(PROJECT)).thenReturn(Optional.of(new Vorschau(SPAETER, "neu")));

    // When
    service.antworten(USER, CARD, RUECKFRAGE_ID, allesUebernehmen());

    // Then
    assertThat(Antwort.lies(ankarte.getLast().body()).orElseThrow().commit()).isNull();
  }

  @Test
  void freigebenOhneFreigabeRueckfrageIstUngueltig() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\n");

    // When / Then
    assertThatThrownBy(
            () ->
                service.antworten(
                    USER, CARD, RUECKFRAGE_ID, new AntwortAuftrag(true, List.of(), null, true)))
        .isInstanceOf(UngueltigeAntwortException.class)
        .hasMessage("Diese Rückfrage fragt nach keiner Freigabe");
  }

  @Test
  void unbekannteRueckfrageEndetMit404() {
    // Given
    ankarte.add(kommentar(41L, "kein Kopf", SPAETER, null, false));

    // When / Then
    assertThatThrownBy(() -> service.antworten(USER, CARD, 41L, allesUebernehmen()))
        .isInstanceOf(CommentNotFoundException.class);
    assertThatThrownBy(() -> service.antworten(USER, CARD, 99L, allesUebernehmen()))
        .isInstanceOf(CommentNotFoundException.class);
  }

  @Test
  void ohneSchreibrechtWirdNichtsGelesenUndNichtsGeschrieben() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\n");
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(USER, PROJECT, Permission.COMMENT_CREATE);

    // When / Then
    assertThatThrownBy(() -> service.antworten(USER, CARD, RUECKFRAGE_ID, allesUebernehmen()))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(comments, never()).list(anyLong(), anyLong());
    verify(comments, never()).create(anyLong(), anyLong(), anyString());
  }

  @Test
  void einzelantwortenWerdenGeprueft() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\n");

    // Then
    assertUngueltig(
        new EinzelAuftrag(7, 1, null, null), "Die Antwort auf Frage 7 passt nicht zur Rückfrage");
    assertUngueltig(
        new EinzelAuftrag(1, 3, null, null), "Die Antwort auf Frage 1 passt nicht zur Rückfrage");
    assertUngueltig(
        new EinzelAuftrag(1, 0, null, null), "Die Antwort auf Frage 1 passt nicht zur Rückfrage");
    assertUngueltig(
        new EinzelAuftrag(2, 1, null, null), "Die Antwort auf Frage 2 passt nicht zur Rückfrage");
    assertUngueltig(
        new EinzelAuftrag(2, null, "  ", null),
        "Die Antwort auf Frage 2 passt nicht zur Rückfrage");
    assertUngueltig(
        new EinzelAuftrag(2, null, null, false),
        "Die Antwort auf Frage 2 passt nicht zur Rückfrage");
    assertUngueltig(
        new EinzelAuftrag(1, 1, "auch", null), "Die Antwort auf Frage 1 passt nicht zur Rückfrage");
    assertUngueltig(
        new EinzelAuftrag(1, 1, null, true), "Die Antwort auf Frage 1 passt nicht zur Rückfrage");
    assertUngueltig(
        new EinzelAuftrag(2, null, "x", true), "Die Antwort auf Frage 2 passt nicht zur Rückfrage");
    verify(comments, never()).create(anyLong(), anyLong(), anyString());
  }

  private void assertUngueltig(EinzelAuftrag einzel, String meldung) {
    AntwortAuftrag auftrag = new AntwortAuftrag(false, List.of(einzel), null, false);
    assertThatThrownBy(() -> service.antworten(USER, CARD, RUECKFRAGE_ID, auftrag))
        .isInstanceOf(UngueltigeAntwortException.class)
        .hasMessage(meldung);
  }

  @Test
  void wegWirdGeprueft() {
    // Given — eine Rückfrage ohne Eintrag der Art weg
    rueckfrageAnDerKarte("## Rückfrage\n");

    // When / Then
    assertThatThrownBy(
            () ->
                service.antworten(
                    USER, CARD, RUECKFRAGE_ID, new AntwortAuftrag(false, List.of(), 1, false)))
        .isInstanceOf(UngueltigeAntwortException.class)
        .hasMessage("Die Rückfrage bietet diesen Weg nicht an");
  }

  @Test
  void wegWirdGeschrieben() {
    // Given
    ankarte.add(
        kommentar(
            RUECKFRAGE_ID,
            """
            ## Rückfrage
            ```json kit-vorlage
            [{"nr": 1, "art": "weg", "frage": "Weg?", "optionen": [{"text": "A"}, {"text": "B"}]}]
            ```
            """,
            FRAGE_AM,
            "STANDARD",
            true));

    // When
    RueckfrageView view =
        service.antworten(
            USER, CARD, RUECKFRAGE_ID, new AntwortAuftrag(false, List.of(), 2, false));

    // Then
    assertThat(view.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
    assertThat(view.eintraege().getFirst().wahl()).isEqualTo(2);
    assertThatThrownBy(
            () ->
                service.antworten(
                    USER, CARD, RUECKFRAGE_ID, new AntwortAuftrag(false, List.of(), 3, false)))
        .isInstanceOf(UngueltigeAntwortException.class);
    assertThatThrownBy(
            () ->
                service.antworten(
                    USER, CARD, RUECKFRAGE_ID, new AntwortAuftrag(false, List.of(), 0, false)))
        .isInstanceOf(UngueltigeAntwortException.class);
  }

  @Test
  void zuLangeAntwortIstUngueltig() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\n");
    String lang = "x".repeat(TextLimits.MAX_TEXT);

    // When / Then
    assertThatThrownBy(
            () ->
                service.antworten(
                    USER,
                    CARD,
                    RUECKFRAGE_ID,
                    new AntwortAuftrag(
                        false, List.of(new EinzelAuftrag(2, null, lang, null)), null, false)))
        .isInstanceOf(UngueltigeAntwortException.class)
        .hasMessage("Die Antwort ist zu lang");
  }

  @Test
  void fehlendeAntwortlisteGiltAlsLeer() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\n");

    // When
    RueckfrageView view =
        service.antworten(USER, CARD, RUECKFRAGE_ID, new AntwortAuftrag(true, null, null, false));

    // Then
    assertThat(view.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
    assertThat(Antwort.lies(ankarte.getLast().body()).orElseThrow().antworten()).isEmpty();
  }

  @Test
  void antwortOhneAngabeIstUngueltig() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\n");

    // When / Then
    assertThatThrownBy(
            () ->
                service.antworten(
                    USER, CARD, RUECKFRAGE_ID, new AntwortAuftrag(false, null, null, false)))
        .isInstanceOf(UngueltigeAntwortException.class)
        .hasMessage("Die Antwort enthält keine Angabe");
  }

  @Test
  void staendeJeKarteWertetAlleKartenMitEinemZugriffAus() {
    // Given (Issue #2216): an CARD eine beantwortete und eine offene Rückfrage, an Karte 6 keine
    rueckfrageAnDerKarte("## Rückfrage\n");
    service.antworten(USER, CARD, RUECKFRAGE_ID, allesUebernehmen());
    ankarte.add(kommentar(41L, "## Question\n" + DREI_FRAGEN, SPAETER, "STANDARD", true));
    when(comments.listJeKarte(USER, PROJECT, List.of(CARD, 6L)))
        .thenReturn(
            Map.of(
                CARD,
                List.copyOf(ankarte),
                6L,
                List.of(kommentar(50L, "Nur ein Kommentar", SPAETER, null, false))));

    // When
    Map<Long, List<RueckfrageView>> staende =
        service.staendeJeKarte(USER, PROJECT, List.of(CARD, 6L));

    // Then
    assertThat(staende).containsOnlyKeys(CARD);
    assertThat(staende.get(CARD))
        .extracting(RueckfrageView::kommentarId, RueckfrageView::zustand)
        .containsExactly(
            tuple(RUECKFRAGE_ID, RueckfrageZustand.BEANTWORTET),
            tuple(41L, RueckfrageZustand.OFFEN));
  }

  @Test
  void staendeJeKarteErkenntUeberholteFreigaben() {
    // Given
    rueckfrageAnDerKarte("## Rückfrage\nVorschau: https://p\nCommit: abc\n");
    when(vorschauen.juengste(PROJECT)).thenReturn(Optional.of(new Vorschau(SPAETER, "neu")));
    when(comments.listJeKarte(USER, PROJECT, List.of(CARD)))
        .thenReturn(Map.of(CARD, List.copyOf(ankarte)));

    // When / Then
    assertThat(service.staendeJeKarte(USER, PROJECT, List.of(CARD)).get(CARD))
        .extracting(RueckfrageView::zustand)
        .containsExactly(RueckfrageZustand.UEBERHOLT);
  }

  @Test
  void staendeJeKarteOhneKartenFragtNichts() {
    // When / Then
    assertThat(service.staendeJeKarte(USER, PROJECT, List.of())).isEmpty();
    verify(comments, never()).listJeKarte(anyLong(), anyLong(), anyCollection());
  }
}
