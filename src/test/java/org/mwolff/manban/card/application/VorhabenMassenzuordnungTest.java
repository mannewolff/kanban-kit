package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verhaltenstests der Vorhaben-Massenzuordnung (Issue #1866, Plan #1865 A3) an den Ports. {@link
 * KartenGrundlage} entsteht echt aus denselben Port-Mocks wie in {@link CardServiceAufbau}: So
 * sieht der Test jeden Verlaufseintrag und jedes Ereignis am Port.
 */
class VorhabenMassenzuordnungTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long USER = 7L;
  private static final long BOARD = 10L;
  private static final long FREMDES_BOARD = 11L;
  private static final long PROJECT = 1L;
  private static final long EPIC = 30L;
  private static final long ANDERES_EPIC = 31L;

  private CardRepository cards;
  private PermissionChecker permissions;
  private CardActivityRepository activity;
  private ApplicationEventPublisher events;
  private ActorContext.ActorStamp stamp;
  private VorhabenMassenzuordnung regel;

  private static Card karte(
      long id, long boardId, boolean archived, CardType type, Long parentId, String title) {
    return new Card(
        id, boardId, 100L, (int) id, title, null, 0, archived, null, 1L, FIXED, FIXED, type,
        parentId, null, null, PROJECT, null, null, null, null);
  }

  private static Card karte(long id, Long parentId) {
    return karte(id, BOARD, false, CardType.CARD, parentId, "Karte " + id);
  }

  private static Card vorhaben(long id, boolean archived) {
    return karte(id, BOARD, archived, CardType.EPIC, null, "Nice to have");
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    permissions = mock(PermissionChecker.class);
    activity = mock(CardActivityRepository.class);
    events = mock(ApplicationEventPublisher.class);
    ActorContext actor = mock(ActorContext.class);
    stamp = ActorContext.ActorStamp.unknown();
    when(actor.current()).thenReturn(stamp);
    KartenZuordnung zuordnung = mock(KartenZuordnung.class);
    CardDependencyRepository dependencies = mock(CardDependencyRepository.class);
    KartenGrundlage grundlage =
        new KartenGrundlage(
            cards,
            new KartenAbhaengigkeiten(dependencies, cards),
            mock(BoardService.class),
            permissions,
            mock(CardColumnTransitionRepository.class),
            zuordnung,
            activity,
            actor,
            events,
            Clock.fixed(FIXED, ZoneOffset.UTC));
    regel = new VorhabenMassenzuordnung(cards, grundlage, permissions);
    when(cards.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private void aufDemBoard(Card... imBoard) {
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(imBoard));
  }

  private void ziel(Card epic) {
    when(cards.findById(epic.requireId())).thenReturn(Optional.of(epic));
  }

  @Test
  void unveraenderteKarte_bleibtOhneSpeichernUndOhneVerlauf() {
    ziel(vorhaben(EPIC, false));
    aufDemBoard(vorhaben(EPIC, false), karte(1L, EPIC));

    VorhabenMassenzuordnung.Ergebnis ergebnis = regel.ordneZu(USER, List.of(1L), EPIC);

    assertThat(ergebnis).isEqualTo(new VorhabenMassenzuordnung.Ergebnis(List.of(), 0, 0));
    verify(cards, never()).save(any());
    verify(activity, never()).add(anyLong(), anyLong(), any(), anyString(), any(), any());
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void karteMitAnderemVorhaben_wirdGeaendertUndAlsUeberschriebenGezaehlt() {
    ziel(vorhaben(EPIC, false));
    aufDemBoard(vorhaben(EPIC, false), karte(1L, ANDERES_EPIC));

    VorhabenMassenzuordnung.Ergebnis ergebnis = regel.ordneZu(USER, List.of(1L), EPIC);

    assertThat(ergebnis).isEqualTo(new VorhabenMassenzuordnung.Ergebnis(List.of(1L), 1, 0));
    ArgumentCaptor<Card> gespeichert = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(gespeichert.capture());
    assertThat(gespeichert.getValue().requireId()).isEqualTo(1L);
    assertThat(gespeichert.getValue().parentId()).isEqualTo(EPIC);
    ArgumentCaptor<CardBoardActivityEvent> ereignis =
        ArgumentCaptor.forClass(CardBoardActivityEvent.class);
    verify(events).publishEvent(ereignis.capture());
    assertThat(ereignis.getValue())
        .isEqualTo(new CardBoardActivityEvent(BOARD, ActivityType.UPDATED, 1L));
  }

  @Test
  void karteOhneUnmittelbaresVorhaben_zaehltNichtAlsUeberschrieben() {
    // Eine über die Herkunft geerbte Mitgliedschaft steht nicht in parentId (AK 2).
    ziel(vorhaben(EPIC, false));
    aufDemBoard(vorhaben(EPIC, false), karte(1L, null), karte(2L, ANDERES_EPIC));

    VorhabenMassenzuordnung.Ergebnis ergebnis = regel.ordneZu(USER, List.of(1L, 2L), EPIC);

    assertThat(ergebnis).isEqualTo(new VorhabenMassenzuordnung.Ergebnis(List.of(1L, 2L), 1, 0));
    verify(cards, times(2)).save(any());
  }

  @Test
  void archivierteKarte_wirdUebergangen() {
    ziel(vorhaben(EPIC, false));
    aufDemBoard(vorhaben(EPIC, false), karte(1L, BOARD, true, CardType.CARD, null, "alt"));

    VorhabenMassenzuordnung.Ergebnis ergebnis = regel.ordneZu(USER, List.of(1L), EPIC);

    assertThat(ergebnis).isEqualTo(new VorhabenMassenzuordnung.Ergebnis(List.of(), 0, 1));
    verify(cards, never()).save(any());
  }

  @Test
  void karteImPapierkorb_wirdUebergangen() {
    // findByBoardId filtert den Papierkorb: Die Karte fehlt dort, auch wenn findById sie kennt.
    ziel(vorhaben(EPIC, false));
    when(cards.findById(1L)).thenReturn(Optional.of(karte(1L, null)));
    aufDemBoard(vorhaben(EPIC, false));

    VorhabenMassenzuordnung.Ergebnis ergebnis = regel.ordneZu(USER, List.of(1L), EPIC);

    assertThat(ergebnis).isEqualTo(new VorhabenMassenzuordnung.Ergebnis(List.of(), 0, 1));
    verify(cards, never()).save(any());
  }

  @Test
  void karteAufFremdemBoard_wirdUebergangen() {
    ziel(vorhaben(EPIC, false));
    when(cards.findById(1L))
        .thenReturn(Optional.of(karte(1L, FREMDES_BOARD, false, CardType.CARD, null, "fremd")));
    aufDemBoard(vorhaben(EPIC, false), karte(2L, null));

    VorhabenMassenzuordnung.Ergebnis ergebnis = regel.ordneZu(USER, List.of(1L, 2L), EPIC);

    assertThat(ergebnis).isEqualTo(new VorhabenMassenzuordnung.Ergebnis(List.of(2L), 0, 1));
    verify(cards, times(1)).save(any());
  }

  @Test
  void nichtGefundeneKarte_wirdUebergangen() {
    ziel(vorhaben(EPIC, false));
    aufDemBoard(vorhaben(EPIC, false), karte(2L, null));

    VorhabenMassenzuordnung.Ergebnis ergebnis = regel.ordneZu(USER, List.of(99L, 2L), EPIC);

    assertThat(ergebnis).isEqualTo(new VorhabenMassenzuordnung.Ergebnis(List.of(2L), 0, 1));
  }

  @Test
  void vorhabenInDerAuswahl_wirdAbgelehntOhneJedesSpeichern() {
    ziel(vorhaben(EPIC, false));
    aufDemBoard(vorhaben(EPIC, false), karte(1L, null), vorhaben(ANDERES_EPIC, false));

    assertThatThrownBy(() -> regel.ordneZu(USER, List.of(1L, ANDERES_EPIC), EPIC))
        .isInstanceOfSatisfying(
            InvalidDependencyException.class,
            e ->
                assertThat(e.schluessel())
                    .isEqualTo(InvalidDependencyException.nurKartenImEpic().schluessel()));
    verify(cards, never()).save(any());
    verify(activity, never()).add(anyLong(), anyLong(), any(), anyString(), any(), any());
  }

  @Test
  void archiviertesZiel_wirdAbgelehnt() {
    ziel(vorhaben(EPIC, true));
    aufDemBoard(vorhaben(EPIC, true), karte(1L, null));

    assertThatThrownBy(() -> regel.ordneZu(USER, List.of(1L), EPIC))
        .isInstanceOfSatisfying(
            InvalidDependencyException.class,
            e ->
                assertThat(e.schluessel())
                    .isEqualTo(InvalidDependencyException.vorhabenArchiviert(EPIC).schluessel()));
    verify(cards, never()).findByBoardId(anyLong());
    verify(cards, never()).save(any());
  }

  @Test
  void zielOhneVorhaben_wirdAbgelehnt() {
    when(cards.findById(EPIC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> regel.ordneZu(USER, List.of(1L), EPIC))
        .isInstanceOf(CardNotFoundException.class);
    verify(cards, never()).save(any());
  }

  @Test
  void fehlendesRecht_lehntAbVorJedemLadenDerKarten() {
    ziel(vorhaben(EPIC, false));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(USER, PROJECT, Permission.TICKET_UPDATE);

    assertThatThrownBy(() -> regel.ordneZu(USER, List.of(1L), EPIC))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cards, never()).findByBoardId(anyLong());
    verify(cards, never()).save(any());
  }

  @Test
  void doppelteIds_zaehlenEinmal() {
    ziel(vorhaben(EPIC, false));
    aufDemBoard(vorhaben(EPIC, false), karte(1L, ANDERES_EPIC));

    VorhabenMassenzuordnung.Ergebnis ergebnis =
        regel.ordneZu(USER, List.of(1L, 1L, 99L, 99L), EPIC);

    assertThat(ergebnis).isEqualTo(new VorhabenMassenzuordnung.Ergebnis(List.of(1L), 1, 1));
    verify(cards, times(1)).save(any());
  }

  @Test
  void geaenderteKarte_bekommtVerlaufseintragMitDemVorhaben() {
    ziel(vorhaben(EPIC, false));
    aufDemBoard(vorhaben(EPIC, false), karte(1L, null));

    regel.ordneZu(USER, List.of(1L), EPIC);

    verify(permissions).require(USER, PROJECT, Permission.TICKET_UPDATE);
    verify(activity)
        .add(
            1L, USER, CardActivityType.UPDATED, "Vorhaben „Nice to have“ zugeordnet", FIXED, stamp);
  }
}
