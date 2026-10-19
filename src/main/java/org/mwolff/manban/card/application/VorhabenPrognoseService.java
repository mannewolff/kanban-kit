package org.mwolff.manban.card.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.Durchsatzermittlung;
import org.mwolff.manban.card.domain.Durchsatzermittlung.Durchsatz;
import org.mwolff.manban.card.domain.Durchsatzermittlung.Kartenstand;
import org.mwolff.manban.card.domain.Fertigprognose;
import org.mwolff.manban.card.domain.Prognosetag;
import org.mwolff.manban.card.domain.VorhabenLage;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Prognose der Platte „Vorhaben“ eines Boards (Plan #1819): lädt Karten, Spalten und
 * Aufenthalte des Boards, lässt {@link Durchsatzermittlung} und {@link VorhabenLage} rechnen und
 * bildet die Tage der Prognose auf Daten ab dem laufenden Tag ab. Recht wie das Dashboard: die
 * Mitgliedschaft im Projekt des Boards; ein Nichtmitglied findet das Board nicht.
 *
 * <p>Ein eigener Dienst neben {@link BoardFlussService} und {@link EpicService}: Die Zählung von
 * {@code GET /epics} bleibt unverändert, damit andere Ansichten eines Vorhabens ihre behalten.
 */
@Service
public class VorhabenPrognoseService {

  private final CardFlowStayRepository aufenthalte;
  private final CardRepository cards;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final Clock clock;

  public VorhabenPrognoseService(
      CardFlowStayRepository aufenthalte,
      CardRepository cards,
      BoardService boardService,
      PermissionChecker permissions,
      Clock clock) {
    this.aufenthalte = aufenthalte;
    this.cards = cards;
    this.boardService = boardService;
    this.permissions = permissions;
    this.clock = clock;
  }

  /**
   * Die Prognose je nicht archiviertem Vorhaben in der Zone des Lesers.
   *
   * @param zone Regionszone des Lesers — die Prüfung auf eine Regionszone liegt am Controller
   */
  @Transactional(readOnly = true)
  public EpicForecastView epicForecast(long userId, long boardId, ZoneId zone) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    Instant jetzt = clock.instant();
    // Ohne Papierkorb schon aus der Abfrage; ein zweiter Filter entfällt.
    List<Card> karten = cards.findByBoardId(boardId);
    Map<Long, String> spaltennamen =
        boardService.listColumns(boardId).stream()
            .collect(Collectors.toMap(ColumnView::id, ColumnView::name));
    Map<Long, Kartenstand> heute =
        karten.stream()
            .collect(
                Collectors.toMap(
                    Card::requireId, c -> new Kartenstand(c.status(), c.archived(), false)));
    Durchsatz durchsatz =
        Durchsatzermittlung.ermittle(
            zone, jetzt, boardId, aufenthalte.findByBoardId(boardId), heute);
    List<VorhabenLage.Ergebnis> lagen =
        VorhabenLage.ermittle(
            karten,
            EpicMembership.compute(karten),
            EpicMembership.computeMitArchiv(karten),
            spaltennamen,
            durchsatz);
    LocalDate laufend = Prognosetag.laufenderTag(jetzt, zone);
    return new EpicForecastView(
        durchsatz.tageMitPaketen(), lagen.stream().map(e -> epic(e, laufend)).toList());
  }

  private static EpicForecastView.Epic epic(VorhabenLage.Ergebnis e, LocalDate laufend) {
    Card vorhaben = e.vorhaben();
    Fertigprognose.@Nullable Tage tage = e.tage();
    return new EpicForecastView.Epic(
        vorhaben.requireId(),
        vorhaben.number(),
        vorhaben.title(),
        e.lage(),
        e.total(),
        e.finished(),
        e.open(),
        tage == null ? null : datum(laufend, tage.tag50()),
        tage == null ? null : datum(laufend, tage.tag85()),
        e.earliest(),
        e.ausstehend().stream()
            .map(d -> new EpicForecastView.PendingDocument(d.nummer(), d.titel()))
            .toList(),
        e.otherEpicsSharing());
  }

  /** Tag 1 ist der laufende Tag. */
  private static LocalDate datum(LocalDate laufend, int tag) {
    return laufend.plusDays(tag - 1L);
  }
}
