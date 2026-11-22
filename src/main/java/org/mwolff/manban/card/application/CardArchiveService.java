package org.mwolff.manban.card.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archiv und Papierkorb (Issue #1394, Plan #1387 E1): Karten archivieren und wiederherstellen,
 * einzeln und gesammelt in den Papierkorb legen, daraus zurückholen, endgültig entfernen und den
 * Papierkorb eines Boards auflisten. Transaktionen, Rechteprüfung und Ereignisse sind unverändert
 * die aus {@link CardService} (E3); die Abhängigkeiten einer endgültig entfernten Karte räumt
 * {@link KartenAbhaengigkeiten} (E11). Wird eine Karte aus Archiv oder Papierkorb zurückgeholt,
 * kehrt ein archiviertes Vorhaben, das damit wieder eine mitzählende Karte hat, in derselben
 * Transaktion mit zurück ({@link VorhabenArchivierung#holeZurueck(long)}, Plan #1504 A5).
 */
@Service
public class CardArchiveService {

  private final CardRepository cards;
  private final CardBestandRepository bestand;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final KartenGrundlage grundlage;
  private final KartenSicht sicht;
  private final VorhabenArchivierung vorhaben;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final Anbietersicht anbieter;

  public CardArchiveService(
      CardRepository cards,
      CardBestandRepository bestand,
      KartenAbhaengigkeiten abhaengigkeiten,
      BoardService boardService,
      PermissionChecker permissions,
      KartenGrundlage grundlage,
      KartenSicht sicht,
      VorhabenArchivierung vorhaben,
      ApplicationEventPublisher events,
      Clock clock,
      Anbietersicht anbieter) {
    this.cards = cards;
    this.bestand = bestand;
    this.abhaengigkeiten = abhaengigkeiten;
    this.boardService = boardService;
    this.permissions = permissions;
    this.grundlage = grundlage;
    this.sicht = sicht;
    this.vorhaben = vorhaben;
    this.events = events;
    this.clock = clock;
    this.anbieter = anbieter;
  }

  @Transactional
  public CardView archive(long userId, long cardId) {
    return doArchive(userId, cardId);
  }

  private CardView doArchive(long userId, long cardId) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    grundlage.aktivitaet(
        card.requireId(), userId, CardActivityType.ARCHIVED, "Archiviert", clock.instant());
    CardView result = sicht.view(userId, cards.save(card.asArchived()));
    grundlage.publishChanged(card.boardId(), ActivityType.ARCHIVED, card.requireId());
    return result;
  }

  /**
   * Archiviert mehrere Karten in einer Transaktion (alles-oder-nichts). Nutzt je Karte die
   * Einzel-Logik von {@link #archive(long, long)} inklusive Rechteprüfung; fehlt an einer Karte das
   * Recht oder existiert sie nicht, rollt der gesamte Batch zurück. Kein Positions-Reindex nötig,
   * da archivierte Karten über {@code active_position = NULL} aus dem Namespace fallen.
   */
  @Transactional
  public List<CardView> bulkArchive(long userId, List<Long> cardIds) {
    return cardIds.stream().map(cardId -> doArchive(userId, cardId)).toList();
  }

  @Transactional
  public CardView restore(long userId, long cardId) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    int position = cards.allocateActivePosition(card.columnId());
    grundlage.aktivitaet(
        card.requireId(), userId, CardActivityType.RESTORED, "Wiederhergestellt", clock.instant());
    CardView result = sicht.view(userId, cards.save(card.asRestored(position)));
    vorhaben.holeZurueck(card.boardId());
    grundlage.publishChanged(card.boardId(), ActivityType.RESTORED, card.requireId());
    return result;
  }

  /**
   * Verschiebt eine Karte in den Papierkorb (Soft-Delete, reversibel). Recht: TICKET/EPIC_DELETE.
   */
  @Transactional
  public void delete(long userId, long cardId) {
    doDelete(userId, cardId);
  }

  private void doDelete(long userId, long cardId) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    // Beim Löschen eines Vorhabens die Kinder lösen — die DB-„ON DELETE SET NULL"-Kaskade auf
    // parent_id feuert nur beim Hard-Delete, nicht beim Soft-Delete.
    if (card.type() == CardType.EPIC) {
      cards.findByBoardId(card.boardId()).stream()
          .filter(c -> Objects.equals(c.parentId(), card.requireId()))
          .forEach(child -> cards.save(child.withParent(null)));
    }
    cards.softDelete(card.requireId(), clock.instant());
    grundlage.publishChanged(card.boardId(), ActivityType.DELETED, card.requireId());
  }

  /**
   * Verschiebt mehrere Karten in einer Transaktion in den Papierkorb (alles-oder-nichts). Nutzt je
   * Karte die Einzel-Logik von {@link #delete(long, long)} inklusive Rechteprüfung und Lösen der
   * Vorhaben-Kinder; fehlt an einer Karte das Recht oder existiert sie nicht, rollt der gesamte
   * Batch zurück.
   */
  @Transactional
  public void bulkDelete(long userId, List<Long> cardIds) {
    cardIds.forEach(cardId -> doDelete(userId, cardId));
  }

  /**
   * Holt eine Karte aus dem Papierkorb zurück (ans Spaltenende). Recht wie Löschen (Member und
   * aufwärts) — so kann ein Member eine versehentlich gelöschte Karte selbst wiederherstellen.
   */
  @Transactional
  public CardView restoreFromTrash(long userId, long cardId) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    int position = cards.allocateActivePosition(card.columnId());
    cards.restoreFromTrash(card.requireId(), position);
    vorhaben.holeZurueck(card.boardId());
    grundlage.aktivitaet(
        card.requireId(),
        userId,
        CardActivityType.RESTORED,
        "Aus Papierkorb wiederhergestellt",
        clock.instant());
    grundlage.publishChanged(card.boardId(), ActivityType.RESTORED, card.requireId());
    // View aus der bereits geladenen Karte mit neuer Position — der JDBC-Restore hat die DB-Zeile
    // geändert; ein erneutes findById käme aus dem JPA-Cache noch mit dem alten Stand.
    return sicht.view(userId, card.asRestored(position));
  }

  /**
   * Entfernt eine Karte endgültig (Hard-Delete). Nur für Board-Verwalter (Projekt-Admin/Owner,
   * Recht {@link Permission#BOARD_DELETE}) — bewusst restriktiver als das reversible Löschen.
   */
  @Transactional
  public void purge(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    grundlage.pruefeSichtbar(userId, card);
    long projectId = boardService.requireProjectId(card.boardId());
    permissions.require(userId, projectId, Permission.BOARD_DELETE);
    grundlage.pruefeSchreibbar(projectId);
    endgueltigEntfernen(card);
  }

  /**
   * Entfernt die Karten des Projekts mit diesen Nummern endgültig, ohne Rechteprüfung und ohne
   * Schreibsperre: auf dem Board, im Archiv und im Papierkorb des Boards. Allein für den Spiegel
   * eines fremden Boards, wenn die Karten dort vertraulich sind (Issue #2200, {@link
   * FremdKartenSchreiber#vertraulicheEntfernen}).
   *
   * @return wie viele Karten entfernt wurden
   */
  int vertraulicheEntfernen(long projectId, long boardId, List<Integer> nummern) {
    int entfernt = 0;
    for (List<Card> karten :
        List.of(cards.findByProjectId(projectId), cards.findTrashByBoardId(boardId))) {
      for (Card karte : karten) {
        if (karte.number() != null && nummern.contains(karte.number())) {
          endgueltigEntfernen(karte);
          entfernt++;
        }
      }
    }
    return entfernt;
  }

  /**
   * Entfernt die Karte endgültig, ohne Rechteprüfung und ohne Schreibsperre — auch aus dem
   * Papierkorb. Kommentare, Verlauf und Messungen gehen mit (Datenbank-Cascade).
   */
  private void endgueltigEntfernen(Card card) {
    // Vor dem Delete publizieren (Issue #503): Nachgelagerte Module (Anhänge) planen ihre
    // Aufräum-Aufträge ein, solange die Metadaten existieren — die Cascade nimmt sie gleich mit.
    events.publishEvent(new CardsPurgedEvent(List.of(card.requireId())));
    abhaengigkeiten.entferne(card.requireId());
    // Plan #1574, E18: Die FK setzt die Herkunft der Kinder beim Delete auf NULL — vorher die
    // Nummer an ihnen bewahren.
    bestand.bewahreHerkunftsnummern(List.of(card.requireId()));
    cards.deleteById(card.requireId());
    grundlage.publishChanged(card.boardId(), ActivityType.DELETED, card.requireId());
  }

  /**
   * Karten im Papierkorb eines Boards. Erfordert Board-Mitgliedschaft (Leserecht). Karten, die der
   * Aufrufer nicht sieht, fehlen, und Verweise auf sie entfallen (Issue #2211).
   */
  @Transactional(readOnly = true)
  public List<CardView> listTrash(long userId, long boardId) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.requireMembership(userId, projectId);
    boolean darfStatusSetzen = sicht.darfStatusSetzen(userId, projectId);
    Set<Integer> verborgen = anbieter.verborgeneNummern(userId, projectId);
    return Anbietersicht.ohneVerborgene(cards.findTrashByBoardId(boardId), verborgen).stream()
        .filter(c -> c.type() == CardType.CARD)
        .map(c -> anbieter.ohneVerborgeneVerweise(sicht.view(c, darfStatusSetzen), verborgen))
        .toList();
  }
}
