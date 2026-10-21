package org.mwolff.manban.card.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;

/**
 * Ordnet Karten eines Boards in einem Zug einem Vorhaben zu (Issue #1864, Plan #1865 A3): setzt
 * allein die unmittelbare Zuordnung {@code parentId}.
 *
 * <p>Modulintern wie {@link KartenZuordnung}: Die Klasse steht nicht auf der Fassaden-Whitelist der
 * ArchUnit-Regel {@code CARD_APPLICATION_IST_AUF_FASSADE_BEGRENZT}; aufgerufen wird sie über {@link
 * EpicService#assignParentBulk}.
 *
 * <p>Ohne {@code @Transactional}: Die Regel läuft in der Transaktion der Fassade. Jede Ablehnung
 * wirft und rollt damit die ganze Zuordnung zurück — alles oder nichts (AK 5).
 */
@Service
public final class VorhabenMassenzuordnung {

  private final CardRepository cards;
  private final KartenGrundlage grundlage;
  private final PermissionChecker permissions;

  public VorhabenMassenzuordnung(
      CardRepository cards, KartenGrundlage grundlage, PermissionChecker permissions) {
    this.cards = cards;
    this.grundlage = grundlage;
    this.permissions = permissions;
  }

  /**
   * Ordnet die Karten {@code cardIds} dem Vorhaben {@code epicId} zu.
   *
   * <p>Es gilt der Stand bei der Ausführung (AK 10): Eine Karte, die die Zuordnung schon trägt,
   * bleibt unverändert. Eine Karte, die nicht mehr unter den lebenden Karten des Vorhaben-Boards
   * liegt — anderes Board, Papierkorb, nie vorhanden — oder archiviert ist, wird übergangen (Plan
   * #1865, E1).
   *
   * @throws CardNotFoundException wenn es das Vorhaben nicht gibt
   * @throws InvalidDependencyException wenn das Ziel kein zuordenbares Vorhaben des Boards ist oder
   *     die Auswahl ein Vorhaben enthält — dann ist keine Karte geändert
   */
  public Ergebnis ordneZu(long userId, List<Long> cardIds, long epicId) {
    Card epic = cards.findById(epicId).orElseThrow(CardNotFoundException::new);
    // Dasselbe Recht wie die Einzelzuordnung einer Karte (AK 6).
    permissions.require(userId, epic.projectId(), Permission.TICKET_UPDATE);
    long boardId = epic.boardId();
    // Lädt das Vorhaben ein zweites Mal — bewusst: So gelten dieselben Ablehnungen wie bei der
    // Einzelzuordnung (kein Vorhaben, archiviert), statt sie hier nachzubauen.
    grundlage.requireZuordenbaresVorhaben(epicId, boardId, null);

    Set<Long> gewaehlt = new LinkedHashSet<>(cardIds);
    // findByBoardId filtert Board und Papierkorb in einer Abfrage; das Laden nach IDs täte beides
    // nicht (Port-Doku in CardRepository).
    Map<Long, Card> lebend =
        cards.findByBoardId(boardId).stream()
            .collect(Collectors.toMap(Card::requireId, Function.identity()));
    List<Card> getroffen = gewaehlt.stream().map(lebend::get).filter(Objects::nonNull).toList();
    if (getroffen.stream().anyMatch(c -> c.type() != CardType.CARD)) {
      throw InvalidDependencyException.nurKartenImEpic();
    }

    List<Card> zuzuordnen = getroffen.stream().filter(c -> !c.archived()).toList();
    List<Long> geaendert = new ArrayList<>();
    int ueberschrieben = 0;
    String verlauf = "Vorhaben „" + epic.title() + "“ zugeordnet";
    for (Card karte : zuzuordnen) {
      Long bisher = karte.parentId();
      if (Long.valueOf(epicId).equals(bisher)) {
        continue;
      }
      if (bisher != null) {
        ueberschrieben++;
      }
      long id = karte.requireId();
      cards.save(karte.withParent(epicId));
      grundlage.aktivitaet(id, userId, CardActivityType.UPDATED, verlauf);
      grundlage.publishChanged(boardId, ActivityType.UPDATED, id);
      geaendert.add(id);
    }
    return new Ergebnis(
        List.copyOf(geaendert), ueberschrieben, gewaehlt.size() - zuzuordnen.size());
  }

  /**
   * Ergebnis einer Massenzuordnung.
   *
   * @param geaendert IDs der Karten, deren Zuordnung sich geändert hat, in Auswahlreihenfolge
   * @param ueberschrieben davon die Karten, die vorher unmittelbar einem anderen Vorhaben
   *     angehörten
   * @param uebergangen gewählte Karten, die bei der Ausführung nicht mehr da oder archiviert waren
   */
  public record Ergebnis(List<Long> geaendert, int ueberschrieben, int uebergangen) {}
}
