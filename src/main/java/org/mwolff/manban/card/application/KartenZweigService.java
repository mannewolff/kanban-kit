package org.mwolff.manban.card.application;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.mwolff.manban.card.application.DerivationTree.Zweig;
import org.mwolff.manban.card.application.EpicService.DerivationNodeView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Herkunftsbaum im Karten-Detail (Issue #1985, Plan #1980): der Zweig einer Karte ab der obersten
 * Anforderung ihrer Herkunftslinie, in den Zeilen des Vorhaben-Baums. Eigener Dienst statt eines
 * weiteren Ablaufs im Karten-Dienst (#1386); er lädt die Board-Karten einmal und rechnet im
 * Speicher (#763).
 */
@Service
public class KartenZweigService {

  private final CardRepository cards;
  private final PermissionChecker permissions;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final KartenZuordnung zuordnung;

  public KartenZweigService(
      CardRepository cards,
      PermissionChecker permissions,
      KartenAbhaengigkeiten abhaengigkeiten,
      KartenZuordnung zuordnung) {
    this.cards = cards;
    this.permissions = permissions;
    this.abhaengigkeiten = abhaengigkeiten;
    this.zuordnung = zuordnung;
  }

  /**
   * Der Zweig der Karte als flache Liste in Präorder mit Tiefe.
   *
   * @return leer, wenn die Karte zu keiner Kette gehört, archiviert oder ein Vorhaben ist (E5)
   * @throws CardNotFoundException wenn es die Karte nicht gibt oder der Aufrufer kein Mitglied
   *     ihres Projekts ist — beides gleich, damit ein Fremder nichts über die Existenz erfährt (E6)
   */
  @Transactional(readOnly = true)
  public List<DerivationNodeView> kartenZweig(long userId, long cardId) {
    Card karte = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    try {
      permissions.requireMembership(userId, karte.projectId());
    } catch (ProjectNotFoundException e) {
      throw new CardNotFoundException(e);
    }

    // Bezugsmenge nach E1: derselbe Filter wie die Zugehörigkeit zum Vorhaben. Die Nummern fremder
    // Vorfahren rechnen dagegen auf dem ganzen Board — ein archivierter Vorfahr auf dem Board
    // bleibt so extern ohne Nummer, wie im Baum des Vorhabens.
    List<Card> alle = cards.findByBoardId(karte.boardId());
    List<Card> bezugsmenge = alle.stream().filter(EpicMembership::zaehlt).toList();
    Zweig zweig = DerivationTree.zweig(bezugsmenge, cardId);

    Set<Long> ids = zweig.karten().stream().map(Card::requireId).collect(Collectors.toSet());
    List<DerivationNodeView> zeilen =
        DerivationTree.build(
            zweig.karten(),
            abhaengigkeiten.abhaengigkeitenJeKarte(ids),
            FremdeVorfahren.nummern(cards, karte.projectId(), alle),
            zuordnung.gezaehlteMarken(karte.boardId(), ids));
    if (!zweig.anfangAbgeschnitten()) {
      return zeilen;
    }
    return ohneAbgeschnitteneHerkunft(zeilen, zweig);
  }

  /**
   * E3: Der Vorfahr des Anfangs liegt lebend auf dem Board und wurde nur bewusst weggelassen —
   * {@code build} hielte ihn sonst für extern. Ein Ring hat keinen Anfang und kommt hier nie an.
   */
  private static List<DerivationNodeView> ohneAbgeschnitteneHerkunft(
      List<DerivationNodeView> zeilen, Zweig zweig) {
    int anfangNummer =
        zweig.karten().stream()
            .filter(c -> c.requireId().equals(zweig.anfangId()))
            .findFirst()
            .orElseThrow()
            .number();
    return zeilen.stream()
        .map(
            z ->
                z.number() == anfangNummer
                    ? new DerivationNodeView(
                        z.number(),
                        z.title(),
                        z.type(),
                        null,
                        z.depth(),
                        z.done(),
                        z.inReview(),
                        z.blocked(),
                        z.dependencies(),
                        z.externalDependencies(),
                        false,
                        z.broken(),
                        z.labels())
                    : z)
        .toList();
  }
}
