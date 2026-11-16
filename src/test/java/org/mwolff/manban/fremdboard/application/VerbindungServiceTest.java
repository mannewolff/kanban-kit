package org.mwolff.manban.fremdboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.card.application.FremdKartenBestand;
import org.mwolff.manban.card.application.FremdKartenBestand.KartenBestand;
import org.mwolff.manban.fremdboard.domain.FremdAbrufException;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.FremdBoardQuelle;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/**
 * Einrichten, Prüfen und Ändern der Verbindung zu einem fremden Board (Issue #2166, Plan #2152 A2,
 * A5, A13, A14, A18).
 */
class VerbindungServiceTest {

  private static final long OWNER = 3L;
  private static final long PROJEKT = 7L;
  private static final long BOARD_ID = 11L;
  private static final Instant JETZT = Instant.parse("2026-11-15T10:00:00Z");
  private static final String ADRESSE = "https://gitlab.example.org";
  private static final String PFAD = "gruppe/projekt";
  private static final String ZUGANG = "glpat-geheim";
  private static final String VERSCHLUESSELT = "v1:verschluesselt";
  private static final KartenBestand LEER = new KartenBestand(0, 0, 0, 0);

  private final PermissionChecker permissions = mock(PermissionChecker.class);
  private final VerbindungRepository verbindungen = mock(VerbindungRepository.class);
  private final FremdKartenBestand karten = mock(FremdKartenBestand.class);
  private final BoardService boards = mock(BoardService.class);
  private final ZugangsSchutz schutz = mock(ZugangsSchutz.class);
  private final AdressPruefung adressen = mock(AdressPruefung.class);
  private final FremdBoardQuelle quelle = mock(FremdBoardQuelle.class);

  private VerbindungService service;

  @BeforeEach
  void aufbau() {
    when(quelle.art()).thenReturn(FremdBoardArt.GITLAB);
    when(schutz.verfuegbar()).thenReturn(true);
    when(schutz.verschluesseln(ZUGANG)).thenReturn(VERSCHLUESSELT);
    when(schutz.lesbar(anyString())).thenReturn(true);
    when(adressen.ablehnung(anyString())).thenReturn(Optional.empty());
    when(verbindungen.finde(PROJEKT)).thenReturn(Optional.empty());
    when(karten.bestand(PROJEKT)).thenReturn(LEER);
    when(karten.fehlendeStatus(anyLong())).thenReturn(List.of());
    when(boards.listBoards(OWNER, PROJEKT)).thenReturn(List.of(board(BOARD_ID, "default")));
    when(boards.listArchivedBoards(OWNER, PROJEKT)).thenReturn(List.of());
    service = neuerService(List.of(quelle));
  }

  private VerbindungService neuerService(List<FremdBoardQuelle> quellen) {
    return new VerbindungService(
        permissions,
        verbindungen,
        karten,
        boards,
        schutz,
        adressen,
        quellen,
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static BoardView board(long id, String name) {
    return new BoardView(id, PROJEKT, name, JETZT, List.of());
  }

  private static VerbindungEingabe eingabe(String zugang) {
    return new VerbindungEingabe(FremdBoardArt.GITLAB, ADRESSE, PFAD, zugang, "kit-bot", null);
  }

  private static Verbindung gespeichert() {
    return new Verbindung(
        PROJEKT,
        FremdBoardArt.GITLAB,
        ADRESSE,
        PFAD,
        "v0:alt",
        "kit-alt",
        StatusLabels.STANDARD,
        BOARD_ID,
        Instant.parse("2026-11-01T08:00:00Z"),
        Instant.parse("2026-11-15T09:58:00Z"),
        null,
        "GitLab ist nicht erreichbar");
  }

  private Verbindung gespeicherteVerbindung() {
    ArgumentCaptor<Verbindung> captor = ArgumentCaptor.forClass(Verbindung.class);
    verify(verbindungen).speichere(captor.capture());
    return captor.getValue();
  }

  static Stream<Arguments> kartenOrte() {
    return Stream.of(
        Arguments.of("Karte auf dem Board", new KartenBestand(1, 0, 0, 0), "1 auf Boards"),
        Arguments.of("archivierte Karte", new KartenBestand(0, 1, 0, 0), "1 im Archiv"),
        Arguments.of("Karte im Papierkorb", new KartenBestand(0, 0, 1, 0), "1 im Papierkorb"),
        Arguments.of("Vorhaben", new KartenBestand(0, 0, 0, 1), "1 Vorhaben"),
        // Ideen des Pools sind seit V44 gewöhnliche Karten mit dem Präfix [Idea] auf dem Board.
        Arguments.of("Pool-Idee", new KartenBestand(1, 0, 0, 0), "1 auf Boards"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("kartenOrte")
  @DisplayName("Szenario: Projekt mit vorhandenen Karten")
  void szenarioProjektMitVorhandenenKarten(String fall, KartenBestand bestand, String ort) {
    // Angenommen ein Projekt hat schon Karten in kanban-kit
    when(karten.bestand(PROJEKT)).thenReturn(bestand);

    // Wenn der Owner GitLab als Ort des Boards wählt
    // Dann lehnt kanban-kit das ab und nennt als Grund, dass das Projekt schon Karten hat
    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .as(fall)
        .isInstanceOf(AnbindenAbgelehntException.class)
        .hasMessageContaining("schon Karten")
        .hasMessageContaining(ort);
    verify(verbindungen, never()).speichere(any());
    verify(boards, never()).createBoard(anyLong(), anyLong(), anyString());
  }

  @Test
  void ownerBindetAnUndUebernimmtDasEinzigeLeereBoard() {
    VerbindungAnsicht ansicht = service.speichern(OWNER, PROJEKT, eingabe(ZUGANG));

    Verbindung neu = gespeicherteVerbindung();
    assertThat(neu.boardId()).isEqualTo(BOARD_ID);
    assertThat(neu.zugangVerschluesselt()).isEqualTo(VERSCHLUESSELT);
    assertThat(neu.angebundenAm()).isEqualTo(JETZT);
    assertThat(neu.kitKonto()).isEqualTo("kit-bot");
    assertThat(neu.statuslabels()).isEqualTo(StatusLabels.STANDARD);
    assertThat(neu.letzterAbgleichAm()).isNull();
    assertThat(ansicht.angebunden()).isTrue();
    assertThat(ansicht.zugangLesbar()).isTrue();
    verify(permissions).requireOwner(OWNER, PROJEKT);
    verify(boards, never()).createBoard(anyLong(), anyLong(), anyString());
    // Probeabruf mit dem frisch verschlüsselten Zugang, bevor gespeichert wird.
    ArgumentCaptor<Verbindung> probe = ArgumentCaptor.forClass(Verbindung.class);
    verify(quelle).geaenderteKarten(probe.capture(), any());
    assertThat(probe.getValue().zugangVerschluesselt()).isEqualTo(VERSCHLUESSELT);
    assertThat(probe.getValue().basisUrl()).isEqualTo(ADRESSE);
  }

  @Test
  void ohneBoardEntstehtEinSpiegelBoardMitDenStandardspalten() {
    when(boards.listBoards(OWNER, PROJEKT)).thenReturn(List.of());
    when(boards.createBoard(OWNER, PROJEKT, VerbindungService.SPIEGEL_BOARD))
        .thenReturn(board(23L, VerbindungService.SPIEGEL_BOARD));

    service.speichern(OWNER, PROJEKT, eingabe(ZUGANG));

    assertThat(gespeicherteVerbindung().boardId()).isEqualTo(23L);
  }

  @Test
  void weitereBoardsWerdenAbgelehntUndGenannt() {
    when(boards.listBoards(OWNER, PROJEKT))
        .thenReturn(List.of(board(BOARD_ID, "default"), board(12L, "Zweites")));
    when(boards.listArchivedBoards(OWNER, PROJEKT)).thenReturn(List.of(board(13L, "Alt")));

    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(AnbindenAbgelehntException.class)
        .hasMessageContaining("default, Zweites, Alt");
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void zweiAktiveBoardsOhneArchivWerdenAbgelehnt() {
    when(boards.listBoards(OWNER, PROJEKT))
        .thenReturn(List.of(board(BOARD_ID, "default"), board(12L, "Zweites")));

    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(AnbindenAbgelehntException.class)
        .hasMessageContaining("default, Zweites");
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void einArchiviertesBoardNebenDemAktivenWirdAbgelehnt() {
    when(boards.listArchivedBoards(OWNER, PROJEKT)).thenReturn(List.of(board(13L, "Alt")));

    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(AnbindenAbgelehntException.class)
        .hasMessageContaining("default, Alt");
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void einArchiviertesBoardStehtDemSpiegelBoardImWeg() {
    when(boards.listBoards(OWNER, PROJEKT)).thenReturn(List.of());
    when(boards.listArchivedBoards(OWNER, PROJEKT)).thenReturn(List.of(board(13L, "Alt")));

    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(AnbindenAbgelehntException.class)
        .hasMessageContaining("Alt");
    verify(boards, never()).createBoard(anyLong(), anyLong(), anyString());
  }

  @Test
  void einBoardOhneStatusspaltenWirdNichtUebernommen() {
    when(karten.fehlendeStatus(BOARD_ID)).thenReturn(List.of("In review", "Done"));

    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(AnbindenAbgelehntException.class)
        .hasMessageContaining("default")
        .hasMessageContaining("In review, Done");
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void einFremderHostWirdAbgelehnt() {
    when(adressen.ablehnung(ADRESSE))
        .thenReturn(Optional.of("Host gitlab.example.org steht nicht in der Erlaubnisliste"));

    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(VerbindungUngueltigException.class)
        .hasMessageContaining("Erlaubnisliste");
    verify(quelle, never()).geaenderteKarten(any(), any());
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void ohneSchluesselIstDasAnbindenGesperrt() {
    when(schutz.verfuegbar()).thenReturn(false);

    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(AnbindenAbgelehntException.class)
        .hasMessageContaining("MANBAN_SECRET_KEY");
    verify(schutz, never()).verschluesseln(anyString());
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void ohneZugangLaesstSichNichtAnbinden() {
    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe("  ")))
        .isInstanceOf(VerbindungUngueltigException.class)
        .hasMessageContaining("Zugang");
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void scheitertDerProbeabrufWirdNichtsGespeichert() {
    when(quelle.geaenderteKarten(any(), any()))
        .thenThrow(new FremdAbrufException("GitLab hat den Zugang abgelehnt (401)"));

    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(VerbindungUngueltigException.class)
        .hasMessageContaining("abgelehnt (401)")
        .hasCauseInstanceOf(FremdAbrufException.class);
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void einNichtOwnerKommtNichtBisZumDienst() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(OWNER, PROJEKT);

    assertThatThrownBy(() -> service.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThatThrownBy(() -> service.lesen(OWNER, PROJEKT))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(verbindungen, never()).finde(anyLong());
  }

  @Test
  void ohneQuelleFuerDieArtGibtEsKeineVerbindung() {
    VerbindungService ohneQuelle = neuerService(List.of());

    assertThatThrownBy(() -> ohneQuelle.speichern(OWNER, PROJEKT, eingabe(ZUGANG)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GITLAB");
  }

  @Test
  void derProbeabrufNimmtDieQuelleDerGewaehltenArt() {
    // Eine Quelle ohne Art steht vorn: Sie darf den Probeabruf nicht bekommen.
    FremdBoardQuelle andere = mock(FremdBoardQuelle.class);
    VerbindungService mitZweiQuellen = neuerService(List.of(andere, quelle));

    mitZweiQuellen.speichern(OWNER, PROJEKT, eingabe(ZUGANG));

    verify(quelle).geaenderteKarten(any(), any());
    verify(andere, never()).geaenderteKarten(any(), any());
  }

  @Test
  void eineAndereArtGiltAlsUmhaengenUndIstGesperrt() {
    when(verbindungen.finde(PROJEKT)).thenReturn(Optional.of(gespeichert()));

    // Heute gibt es nur GitLab; eine fehlende Art steht für jede andere.
    assertThatThrownBy(
            () ->
                service.speichern(
                    OWNER, PROJEKT, new VerbindungEingabe(null, ADRESSE, PFAD, ZUGANG, null, null)))
        .isInstanceOf(AnbindenAbgelehntException.class)
        .hasMessageContaining("lösen");
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void ohneVerbindungSagtDieAnsichtObEinSchluesselGesetztIst() {
    when(schutz.verfuegbar()).thenReturn(false);

    VerbindungAnsicht ansicht = service.lesen(OWNER, PROJEKT);

    assertThat(ansicht.angebunden()).isFalse();
    assertThat(ansicht.schluesselGesetzt()).isFalse();
    assertThat(ansicht.art()).isNull();
    assertThat(ansicht.zugangLesbar()).isFalse();
    verify(permissions).requireOwner(OWNER, PROJEKT);
  }

  @Test
  void pruefenNenntStandUndObDerZugangLesbarIst() {
    when(verbindungen.finde(PROJEKT)).thenReturn(Optional.of(gespeichert()));
    when(schutz.lesbar("v0:alt")).thenReturn(false);

    VerbindungAnsicht ansicht = service.lesen(OWNER, PROJEKT);

    assertThat(ansicht.angebunden()).isTrue();
    assertThat(ansicht.schluesselGesetzt()).isTrue();
    assertThat(ansicht.art()).isEqualTo(FremdBoardArt.GITLAB);
    assertThat(ansicht.basisUrl()).isEqualTo(ADRESSE);
    assertThat(ansicht.projektPfad()).isEqualTo(PFAD);
    assertThat(ansicht.kitKonto()).isEqualTo("kit-alt");
    assertThat(ansicht.statuslabels()).isEqualTo(StatusLabels.STANDARD);
    assertThat(ansicht.angebundenAm()).isEqualTo(Instant.parse("2026-11-01T08:00:00Z"));
    assertThat(ansicht.letzterAbgleichAm()).isEqualTo(Instant.parse("2026-11-15T09:58:00Z"));
    assertThat(ansicht.letzterFehler()).isEqualTo("GitLab ist nicht erreichbar");
    assertThat(ansicht.zugangLesbar()).isFalse();
  }

  @Test
  @DisplayName("nicht entschlüsselbarer Zugang lässt sich neu hinterlegen")
  void nichtEntschluesselbarerZugangLaesstSichNeuHinterlegen() {
    // Given: Nach einem Schlüsselwechsel lässt sich der gespeicherte Zugang nicht mehr lesen.
    when(verbindungen.finde(PROJEKT)).thenReturn(Optional.of(gespeichert()));
    when(schutz.lesbar("v0:alt")).thenReturn(false);
    assertThat(service.lesen(OWNER, PROJEKT).zugangLesbar()).isFalse();

    // When: Der Owner hinterlegt den Zugang neu.
    VerbindungAnsicht ansicht = service.speichern(OWNER, PROJEKT, eingabe(ZUGANG));

    // Then: Gespeichert ist der neu verschlüsselte Zugang, Board und Anbindung bleiben.
    Verbindung neu = gespeicherteVerbindung();
    assertThat(neu.zugangVerschluesselt()).isEqualTo(VERSCHLUESSELT);
    assertThat(neu.boardId()).isEqualTo(BOARD_ID);
    assertThat(neu.angebundenAm()).isEqualTo(Instant.parse("2026-11-01T08:00:00Z"));
    assertThat(neu.letzterAbgleichAm()).isEqualTo(Instant.parse("2026-11-15T09:58:00Z"));
    assertThat(neu.letzterFehler()).isEqualTo("GitLab ist nicht erreichbar");
    assertThat(neu.kitKonto()).isEqualTo("kit-bot");
    assertThat(ansicht.zugangLesbar()).isTrue();
    verify(quelle).geaenderteKarten(any(), any());
    // Die Karten-Prüfung gilt nur beim ersten Anbinden: Der Spiegel hat inzwischen Karten.
    verify(karten, never()).bestand(anyLong());
  }

  @Test
  void ohneNeuenZugangBleibtDerAlteUndKitKontoUndLabelsAendernSich() {
    when(verbindungen.finde(PROJEKT)).thenReturn(Optional.of(gespeichert()));
    StatusLabels eigene = new StatusLabels("status::backlog", "status::ready", "doing", "review");

    service.speichern(
        OWNER,
        PROJEKT,
        new VerbindungEingabe(FremdBoardArt.GITLAB, ADRESSE + "/", PFAD, null, "kit-neu", eigene));

    Verbindung neu = gespeicherteVerbindung();
    assertThat(neu.zugangVerschluesselt()).isEqualTo("v0:alt");
    assertThat(neu.kitKonto()).isEqualTo("kit-neu");
    assertThat(neu.statuslabels()).isEqualTo(eigene);
    verify(quelle, never()).geaenderteKarten(any(), any());
    verify(schutz, never()).verschluesseln(anyString());
  }

  static Stream<Arguments> loesen() {
    return Stream.of(
        Arguments.of("andere Adresse", "https://gitlab.com", PFAD),
        Arguments.of("anderer Pfad", ADRESSE, "gruppe/anderes"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("loesen")
  void loesenUndUmhaengenSindGesperrt(String fall, String adresse, String pfad) {
    when(verbindungen.finde(PROJEKT)).thenReturn(Optional.of(gespeichert()));

    assertThatThrownBy(
            () ->
                service.speichern(
                    OWNER,
                    PROJEKT,
                    new VerbindungEingabe(FremdBoardArt.GITLAB, adresse, pfad, ZUGANG, null, null)))
        .as(fall)
        .isInstanceOf(AnbindenAbgelehntException.class)
        .hasMessageContaining("lösen");
    verify(verbindungen, never()).speichere(any());
  }

  @Test
  void eingabeBereinigtLeereWerteUndSchlussstriche() {
    VerbindungEingabe bereinigt =
        new VerbindungEingabe(
            FremdBoardArt.GITLAB, " https://gitlab.com/ ", " /gruppe/projekt/ ", " ", " ", null);

    assertThat(bereinigt.basisUrl()).isEqualTo("https://gitlab.com");
    assertThat(bereinigt.projektPfad()).isEqualTo("gruppe/projekt");
    assertThat(bereinigt.zugang()).isNull();
    assertThat(bereinigt.kitKonto()).isNull();
    assertThat(bereinigt.statuslabels()).isEqualTo(StatusLabels.STANDARD);
  }
}
