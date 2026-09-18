package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.mwolff.manban.card.application.CardService.LabelMarkView;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;

/**
 * Zuständige und Labels einer Karte: prüfen, lesen, ersetzen (Issue #1051).
 *
 * <p>Modulintern und ausschliesslich von {@link CardService} benutzt — die Klasse steht nicht auf
 * der Fassaden-Whitelist der ArchUnit-Regel {@code CARD_APPLICATION_IST_AUF_FASSADE_BEGRENZT}.
 * Herausgelöst wurde sie, weil der Service mit den drei Ports {@link CardAssigneeRepository},
 * {@link LabelRepository} und {@link CardLabelRepository} plus {@link Label} und den beiden
 * Ablehnungen über der Kopplungsgrenze lag (SonarCloud S6539, Plan #1042).
 *
 * <p>Ohne {@code @Transactional}: Jede Methode läuft in der Transaktion des aufrufenden
 * Use-Case-Verfahrens von {@link CardService} — eine eigene Grenze hier zerschnitte das
 * Alles-oder-nichts der Massenaktionen.
 *
 * <p>Die Prüfungen sind bewusst hier und nicht an den Ports: „Mitglied des Projekts" und „Label
 * dieses Boards" sind fachliche Regeln, die Persistenz kennt sie nicht.
 */
@Service
public final class KartenZuordnung {

  private final CardAssigneeRepository assignees;
  private final LabelRepository labels;
  private final CardLabelRepository cardLabels;
  private final PermissionChecker permissions;

  public KartenZuordnung(
      CardAssigneeRepository assignees,
      LabelRepository labels,
      CardLabelRepository cardLabels,
      PermissionChecker permissions) {
    this.assignees = assignees;
    this.labels = labels;
    this.cardLabels = cardLabels;
    this.permissions = permissions;
  }

  /** Benutzer-IDs der Zuständigen einer Karte, aufsteigend. */
  public List<Long> zustaendigeVon(long cardId) {
    return assignees.findByCardId(cardId);
  }

  /**
   * Zuständige mehrerer Karten in einem Sammelzugriff. Karten ohne Zuständige fehlen in der Map —
   * der Vertrag von {@link CardAssigneeRepository#findByCardIds(Collection)}.
   */
  public Map<Long, List<Long>> zustaendigeJeKarte(Collection<Long> cardIds) {
    return assignees.findByCardIds(cardIds);
  }

  /**
   * Label-IDs einer Karte, aufsteigend.
   *
   * <p>{@code …Von} im Namen, weil das Feld {@code labels} den Label-Port hält: Ein gleichnamiges
   * Paar aus Feld und Methode liest sich wie ein Zugriff auf dasselbe (PMD {@code
   * AvoidFieldNameMatchingMethodName}). {@link #zustaendigeVon(long)} folgt derselben Form.
   */
  public List<Long> labelsVon(long cardId) {
    return cardLabels.findByCardId(cardId);
  }

  /**
   * Labels mehrerer Karten in einem Sammelzugriff. Karten ohne Labels fehlen in der Map — der
   * Vertrag von {@link CardLabelRepository#findByCardIds(Collection)}.
   */
  public Map<Long, List<Long>> labelsJeKarte(Collection<Long> cardIds) {
    return cardLabels.findByCardIds(cardIds);
  }

  /**
   * Prüft und setzt die Zuständigen einer Karte (Duplikate raus; jede ID muss Mitglied des Projekts
   * sein) ohne Aktivitätseintrag — den schreibt der aufrufende Use-Case.
   */
  public void ersetzeZustaendige(long cardId, long projectId, List<Long> assigneeIds) {
    List<Long> distinct = assigneeIds.stream().distinct().toList();
    for (Long assignee : distinct) {
      if (!permissions.isRealProjectMember(assignee, projectId)) {
        throw new InvalidAssigneeException("Kein Projektmitglied: " + assignee);
      }
    }
    assignees.replaceAssignees(cardId, distinct);
  }

  /**
   * Prüft und setzt die Labels einer Karte (Duplikate raus; jede ID muss ein Label desselben Boards
   * sein).
   */
  public void ersetzeLabels(long cardId, long boardId, List<Long> labelIds) {
    List<Long> distinct = labelIds.stream().distinct().toList();
    List<Long> boardLabelIds = boardLabelIds(boardId);
    for (Long labelId : distinct) {
      if (!boardLabelIds.contains(labelId)) {
        throw new InvalidLabelException("Kein Label dieses Boards: " + labelId);
      }
    }
    cardLabels.replaceLabels(cardId, distinct);
  }

  /**
   * Fügt einer Karte <b>ein</b> Label hinzu oder nimmt es ihr ab und lässt die übrigen stehen — der
   * Kern der Label-Massenaktion {@link CardService#bulkLabels}.
   *
   * <p>Auch beim Abnehmen wird das Label geprüft: Ein fremdes Label ist an keiner Karte gesetzt,
   * der Aufruf ginge sonst als stiller Nicht-Treffer durch und meldete Erfolg für etwas, das {@link
   * #ersetzeLabels} abwiese.
   *
   * <p>Eine Karte, die das Label schon trägt (bzw. schon nicht trägt), bleibt unverändert und ist
   * kein Fehler: Die Massenaktion beschreibt einen Zielzustand, keinen Umschalter je Karte.
   */
  public void aendereLabel(long cardId, long boardId, long labelId, LabelAction action) {
    if (!boardLabelIds(boardId).contains(labelId)) {
      throw new InvalidLabelException("Kein Label dieses Boards: " + labelId);
    }
    List<Long> current = cardLabels.findByCardId(cardId);
    List<Long> next =
        action == LabelAction.ADD
            ? Stream.concat(current.stream(), Stream.of(labelId)).distinct().toList()
            : current.stream().filter(id -> id.longValue() != labelId).toList();
    ersetzeLabels(cardId, boardId, next);
  }

  /** Entfernt alle Zuständigen einer Karte — beim Wechsel in ein fremdes Projekt. */
  public void entferneZustaendige(long cardId) {
    assignees.deleteByCardId(cardId);
  }

  /**
   * Die gezählten Label-Marken je Karten-ID — zwei Sammelzugriffe, unabhängig von der Kartenzahl
   * (kein N+1), analog zu {@code depsByCardId}.
   *
   * <p>Vom Server und nicht aus der Kartenliste durchgereicht (Plan #657, E5): Ein Karten-Array
   * durch drei Ebenen zu fädeln, um Namen nachzuschlagen, baute eine zweite Wahrheit über den
   * Zustand neben der ersten.
   *
   * <p>Die Reihenfolge je Karte ist die von {@link CardLabelRepository#findByCardIds} — aufsteigend
   * nach Label-ID. Ohne diese Festlegung wäre die Anzeige nicht deterministisch testbar. Karten
   * ohne gezählte Labels fehlen in der Map; {@code DerivationTree} liest sie mit einer leeren Liste
   * als Vorgabe.
   */
  public Map<Long, List<LabelMarkView>> gezaehlteMarken(long boardId, Collection<Long> cardIds) {
    Map<Long, Label> gezaehlt = new HashMap<>();
    for (Label l : labels.findByBoardId(boardId)) {
      if (l.countOnEpicTile()) {
        gezaehlt.put(l.requireId(), l);
      }
    }
    Map<Long, List<LabelMarkView>> marken = new HashMap<>();
    cardLabels
        .findByCardIds(cardIds)
        .forEach(
            (cardId, labelIds) ->
                marken.put(
                    cardId,
                    labelIds.stream()
                        .map(gezaehlt::get)
                        .filter(Objects::nonNull)
                        .map(l -> new LabelMarkView(l.name(), l.color()))
                        .toList()));
    return marken;
  }

  /** IDs aller Labels eines Boards — die Bezugsmenge jeder Label-Prüfung. */
  private List<Long> boardLabelIds(long boardId) {
    return labels.findByBoardId(boardId).stream().map(Label::requireId).toList();
  }
}
