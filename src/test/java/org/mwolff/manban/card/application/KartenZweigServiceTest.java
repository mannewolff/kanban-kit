package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.EpicService.DerivationNodeView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/**
 * Der Herkunftsbaum im Karten-Detail (Issue #1985, Plan #1980): Recht (E6), Bezugsmenge (E1), leere
 * Antwort (E5), fremde Herkunft (E4) und die Korrektur eines bewusst abgeschnittenen Anfangs (E3).
 * Die Zweigauswahl selbst prüft {@code DerivationTreeTest}.
 */
class KartenZweigServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long USER = 7L;
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private PermissionChecker permissions;
  private KartenAbhaengigkeiten abhaengigkeiten;
  private KartenZuordnung zuordnung;
  private KartenZweigService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    permissions = mock(PermissionChecker.class);
    abhaengigkeiten = mock(KartenAbhaengigkeiten.class);
    zuordnung = mock(KartenZuordnung.class);
    service = new KartenZweigService(cards, permissions, abhaengigkeiten, zuordnung);
  }

  private static Card karte(
      long id, int number, String title, @Nullable Long derivedFrom, boolean archived) {
    return karte(id, number, title, derivedFrom, archived, CardType.CARD, PROJECT);
  }

  private static Card karte(
      long id,
      int number,
      String title,
      @Nullable Long derivedFrom,
      boolean archived,
      CardType type,
      long projectId) {
    return new Card(
        id,
        BOARD,
        20L,
        number,
        title,
        null,
        0,
        archived,
        null,
        1L,
        FIXED,
        FIXED,
        type,
        null,
        null,
        null,
        projectId,
        null,
        derivedFrom,
        null,
        null);
  }

  private void aufDemBoard(Card... karten) {
    for (Card k : karten) {
      when(cards.findById(k.requireId())).thenReturn(Optional.of(k));
    }
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(karten));
  }

  private static DerivationNodeView zeile(List<DerivationNodeView> baum, int nummer) {
    return baum.stream().filter(z -> z.number() == nummer).findFirst().orElseThrow();
  }

  private static List<Integer> nummern(List<DerivationNodeView> baum) {
    return baum.stream().map(DerivationNodeView::number).toList();
  }

  // --- E3: abgeschnittener Vorfahr ------------------------------------------

  /** Die {@code [Idea]} über der Anforderung ist bewusst weggelassen, nicht extern. */
  @Test
  void lebenderVorfahrUeberDerAnforderung_anfangOhneMarkeUndOhneHerkunft() {
    aufDemBoard(
        karte(1L, 1, "[Idea] Idee", null, false),
        karte(2L, 2, "[Requirement] Anforderung", 1L, false),
        karte(3L, 3, "[Plan] Plan", 2L, false));

    List<DerivationNodeView> baum = service.kartenZweig(USER, 3L);

    assertThat(nummern(baum)).containsExactly(2, 3);
    assertThat(zeile(baum, 2).externalOrigin()).isFalse();
    assertThat(zeile(baum, 2).derivedFrom()).isNull();
    assertThat(zeile(baum, 2).depth()).isZero();
    // Nur der Anfang wird korrigiert, die Kante darunter bleibt.
    assertThat(zeile(baum, 3).derivedFrom()).isEqualTo(2);
    assertThat(zeile(baum, 3).depth()).isEqualTo(1);
  }

  /**
   * Der Anfang wird über seine ID gefunden, nicht als erste Zweigkarte: Steht ein Nachfahr in der
   * Board-Reihenfolge vor ihm, verliert trotzdem nur der Anfang seine Herkunft (Issue #2009).
   */
  @Test
  void nachfahrVorDemAnfangInDerBoardReihenfolge_nurDerAnfangWirdKorrigiert() {
    aufDemBoard(
        karte(3L, 3, "[Plan] Plan", 2L, false),
        karte(1L, 1, "[Idea] Idee", null, false),
        karte(2L, 2, "[Requirement] Anforderung", 1L, false));

    List<DerivationNodeView> baum = service.kartenZweig(USER, 3L);

    assertThat(zeile(baum, 2).externalOrigin()).isFalse();
    assertThat(zeile(baum, 2).derivedFrom()).isNull();
    assertThat(zeile(baum, 3).derivedFrom()).isEqualTo(2);
  }

  /** Ein archivierter Vorfahr ist nicht mehr erreichbar: Die Marke bleibt wie im Vorhaben-Baum. */
  @Test
  void archivierterVorfahr_markeBleibt() {
    aufDemBoard(
        karte(1L, 1, "[Idea] Idee", null, true),
        karte(2L, 2, "[Requirement] Anforderung", 1L, false),
        karte(3L, 3, "[Plan] Plan", 2L, false));

    List<DerivationNodeView> baum = service.kartenZweig(USER, 3L);

    assertThat(nummern(baum)).containsExactly(2, 3);
    assertThat(zeile(baum, 2).externalOrigin()).isTrue();
  }

  /** Ein Ring hat keinen Anfang (E7): Keine Zeile verliert ihre Herkunft. */
  @Test
  void ringwurzel_wirdNieKorrigiert() {
    aufDemBoard(karte(1L, 1, "[Plan] X", 2L, false), karte(2L, 2, "[Task] Y", 1L, false));

    List<DerivationNodeView> baum = service.kartenZweig(USER, 1L);

    assertThat(nummern(baum)).containsExactly(1, 2);
    assertThat(baum).allSatisfy(z -> assertThat(z.broken()).isTrue());
    assertThat(zeile(baum, 1).derivedFrom()).isEqualTo(2);
    assertThat(zeile(baum, 2).derivedFrom()).isEqualTo(1);
  }

  // --- E4: Herkunft in einem anderen Projekt --------------------------------

  @Test
  @DisplayName("Szenario: Herkunft in einem anderen Projekt")
  void herkunftInEinemAnderenProjektPlanIstAnfangMitMarkeOhneNummer() {
    Card fremd = karte(77L, 42, "[Requirement] Fremd", null, false, CardType.CARD, 2L);
    aufDemBoard(karte(3L, 3, "[Plan] Plan", 77L, false), karte(4L, 4, "[Task] Paket", 3L, false));
    when(cards.findByIds(Set.of(77L))).thenReturn(List.of(fremd));

    List<DerivationNodeView> baum = service.kartenZweig(USER, 3L);

    assertThat(nummern(baum)).containsExactly(3, 4);
    assertThat(zeile(baum, 3).depth()).isZero();
    assertThat(zeile(baum, 3).externalOrigin()).isTrue();
    assertThat(zeile(baum, 3).derivedFrom()).isNull();
  }

  /** Gegenprobe zu E4: Ein Vorfahr auf einem anderen Board desselben Projekts zeigt die Nummer. */
  @Test
  void herkunftAufAnderemBoardDesselbenProjekts_markeMitNummer() {
    Card fremd = karte(77L, 42, "[Requirement] Anderes Board", null, false);
    aufDemBoard(karte(3L, 3, "[Plan] Plan", 77L, false));
    when(cards.findByIds(Set.of(77L))).thenReturn(List.of(fremd));

    List<DerivationNodeView> baum = service.kartenZweig(USER, 3L);

    assertThat(zeile(baum, 3).externalOrigin()).isTrue();
    assertThat(zeile(baum, 3).derivedFrom()).isEqualTo(42);
  }

  // --- E5: leere Antwort ------------------------------------------------------

  @Test
  void karteOhneKette_liefertLeereListe() {
    aufDemBoard(
        karte(1L, 1, "[Task] Allein", null, false), karte(2L, 2, "[Task] Andere", null, false));

    assertThat(service.kartenZweig(USER, 1L)).isEmpty();
  }

  @Test
  void archivierteKarte_liefertLeereListe() {
    aufDemBoard(
        karte(1L, 1, "[Requirement] Anforderung", null, false),
        karte(2L, 2, "[Plan] Plan", 1L, true));

    assertThat(service.kartenZweig(USER, 2L)).isEmpty();
  }

  @Test
  void vorhaben_liefertLeereListe() {
    aufDemBoard(
        karte(1L, 1, "Vorhaben", null, false, CardType.EPIC, PROJECT),
        karte(2L, 2, "[Plan] Plan", 1L, false));

    assertThat(service.kartenZweig(USER, 1L)).isEmpty();
  }

  // --- Abhängigkeiten und Marken je Sammelzugriff -----------------------------

  @Test
  void abhaengigkeitenUndMarken_kommenAusDemSammelzugriffDerZweigkarten() {
    aufDemBoard(
        karte(1L, 1, "[Requirement] Anforderung", null, false),
        karte(2L, 2, "[Plan] Plan", 1L, false),
        karte(3L, 3, "[Task] Nicht im Zweig", null, false));
    when(abhaengigkeiten.abhaengigkeitenJeKarte(Set.of(1L, 2L)))
        .thenReturn(Map.of(2L, List.of(99)));
    LabelMarkView marke = new LabelMarkView("ux", "#123456");
    when(zuordnung.gezaehlteMarken(BOARD, Set.of(1L, 2L))).thenReturn(Map.of(1L, List.of(marke)));

    List<DerivationNodeView> baum = service.kartenZweig(USER, 2L);

    assertThat(zeile(baum, 2).externalDependencies()).containsExactly(99);
    assertThat(zeile(baum, 1).labels()).containsExactly(marke);
  }

  // --- E6: Recht und Nicht-Existenz -------------------------------------------

  @Test
  void unbekannteKarte_wirftCardNotFound() {
    when(cards.findById(anyLong())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.kartenZweig(USER, 999L))
        .isExactlyInstanceOf(CardNotFoundException.class);
  }

  /** Ein Fremder erfährt nicht, ob es die Karte gibt: dieselbe Ausnahme wie bei Nicht-Existenz. */
  @Test
  void nichtMitglied_wirftDieselbeAusnahmeWieUnbekannteKarte() {
    aufDemBoard(karte(1L, 1, "[Requirement] Anforderung", null, false));
    when(permissions.requireMembership(USER, PROJECT)).thenThrow(new ProjectNotFoundException());

    assertThatThrownBy(() -> service.kartenZweig(USER, 1L))
        .isExactlyInstanceOf(CardNotFoundException.class)
        .hasMessage(new CardNotFoundException().getMessage())
        .hasCauseExactlyInstanceOf(ProjectNotFoundException.class);
  }
}
