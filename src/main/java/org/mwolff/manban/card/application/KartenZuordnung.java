package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;

/**
 * Zuständige und Labels einer Karte: prüfen, lesen, ersetzen (Issue #1051).
 *
 * <p>Modulintern, benutzt von {@link CardService}, {@link KartenGrundlage} und {@link KartenSicht}
 * (Issue #1389) — die Klasse steht nicht auf der Fassaden-Whitelist der ArchUnit-Regel {@code
 * CARD_APPLICATION_IST_AUF_FASSADE_BEGRENZT}. Herausgelöst wurde sie, weil der Service mit den drei
 * Ports {@link CardAssigneeRepository}, {@link LabelRepository} und {@link CardLabelRepository}
 * plus {@link Label} und den beiden Ablehnungen über der Kopplungsgrenze lag (SonarCloud S6539,
 * Plan #1042).
 *
 * <p>Ohne {@code @Transactional}: Jede Methode läuft in der Transaktion des aufrufenden
 * Use-Case-Verfahrens — eine eigene Grenze hier zerschnitte das Alles-oder-nichts der
 * Massenaktionen.
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
   * Labelnamen mehrerer Karten, je Karte alphabetisch (Issue #1373). Karten ohne Labels fehlen in
   * der Map.
   *
   * <p>Ein Sammelzugriff für die Zuordnung und einer je Board, das eine der Karten mit Labels trägt
   * — nicht einer je Karte. Eine Label-ID, die das Board nicht (mehr) kennt, fällt heraus.
   *
   * @param boardJeKarte Board jeder Karte, in der Reihenfolge der Karten
   */
  public Map<Long, List<String>> labelNamenJeKarte(Map<Long, Long> boardJeKarte) {
    Map<Long, List<Long>> labelIds = cardLabels.findByCardIds(List.copyOf(boardJeKarte.keySet()));
    Map<Long, String> namen = new HashMap<>();
    labelIds.keySet().stream()
        .map(boardJeKarte::get)
        .distinct()
        .forEach(
            board -> labels.findByBoardId(board).forEach(l -> namen.put(l.requireId(), l.name())));
    Map<Long, List<String>> ergebnis = new HashMap<>();
    labelIds.forEach(
        (karte, ids) ->
            ergebnis.put(
                karte, ids.stream().map(namen::get).filter(Objects::nonNull).sorted().toList()));
    return ergebnis;
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
   *
   * <p>Auch die Richtung der Freigabe-Labels wird hier geprüft (Issue #1421) — an der einen Stelle,
   * durch die jedes Ersetzen läuft, damit kein Schreibweg sie vergisst. Geprüft wird nur, was
   * gegenüber der bisherigen Zuordnung hinzukommt oder wegfällt.
   *
   * @param herkunft serververifizierte Herkunft des Aufrufs, siehe {@link FreigabeLabels}
   */
  public void ersetzeLabels(
      long cardId, long boardId, List<Long> labelIds, @Nullable CardActivityOrigin herkunft) {
    ersetze(cardId, boardLabelNamen(boardId), cardLabels.findByCardId(cardId), labelIds, herkunft);
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
  public void aendereLabel(
      long cardId,
      long boardId,
      long labelId,
      LabelAction action,
      @Nullable CardActivityOrigin herkunft) {
    Map<Long, String> namen = boardLabelNamen(boardId);
    if (!namen.containsKey(labelId)) {
      throw new InvalidLabelException("Kein Label dieses Boards: " + labelId);
    }
    List<Long> current = cardLabels.findByCardId(cardId);
    List<Long> next =
        action == LabelAction.ADD
            ? Stream.concat(current.stream(), Stream.of(labelId)).distinct().toList()
            : current.stream().filter(id -> id.longValue() != labelId).toList();
    ersetze(cardId, namen, current, next, herkunft);
  }

  private void ersetze(
      long cardId,
      Map<Long, String> namen,
      List<Long> vorher,
      List<Long> labelIds,
      @Nullable CardActivityOrigin herkunft) {
    List<Long> distinct = labelIds.stream().distinct().toList();
    for (Long labelId : distinct) {
      if (!namen.containsKey(labelId)) {
        throw new InvalidLabelException("Kein Label dieses Boards: " + labelId);
      }
    }
    FreigabeLabels.pruefeWechsel(herkunft, namen, vorher, distinct);
    cardLabels.replaceLabels(cardId, distinct);
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

  /** Namen aller Labels eines Boards je ID — die Bezugsmenge jeder Label-Prüfung. */
  private Map<Long, String> boardLabelNamen(long boardId) {
    Map<Long, String> namen = new HashMap<>();
    labels.findByBoardId(boardId).forEach(l -> namen.put(l.requireId(), l.name()));
    return namen;
  }
}
