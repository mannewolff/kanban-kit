package org.mwolff.manban.card.application;

import static org.mwolff.manban.card.application.KartenGrundlage.normalize;
import static org.mwolff.manban.card.application.KartenGrundlage.trimToNull;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Arbeitspaket;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vorhaben und Herkunft (Issue #1393, Plan #1387 E1): Vorhaben anlegen und mit Fortschritt
 * auflisten, den Herkunftsbaum eines Vorhabens, die Zuordnung einer Karte zu einem Vorhaben, ihre
 * Herkunft, die Anforderungskarte eines Vorhabens und das Eröffnen eines Vorgangs aus einer Karte.
 * Vorhaben sind Karten vom Typ {@link CardType#EPIC}: sie erscheinen nicht auf dem Board, halten
 * keine Position und gruppieren Karten über {@code parentId}. Transaktionen und Rechteprüfung sind
 * unverändert die aus {@link CardService} (E3).
 */
@Service
public class EpicService {

  private final CardRepository cards;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final KartenZuordnung zuordnung;
  private final KartenGrundlage grundlage;
  private final KartenSicht sicht;
  private final Clock clock;

  public EpicService(
      CardRepository cards,
      KartenAbhaengigkeiten abhaengigkeiten,
      BoardService boardService,
      PermissionChecker permissions,
      KartenZuordnung zuordnung,
      KartenGrundlage grundlage,
      KartenSicht sicht,
      Clock clock) {
    this.cards = cards;
    this.abhaengigkeiten = abhaengigkeiten;
    this.boardService = boardService;
    this.permissions = permissions;
    this.zuordnung = zuordnung;
    this.grundlage = grundlage;
    this.sicht = sicht;
    this.clock = clock;
  }

  /**
   * Legt ein Vorhaben an. Vorhaben halten keine Board-Position und liegen technisch in der ersten
   * Spalte.
   */
  @Transactional
  public CardView createEpic(
      long userId,
      long boardId,
      String title,
      @Nullable String description,
      @Nullable String shortcode) {
    return doCreateEpic(userId, boardId, title, description, shortcode);
  }

  // Kern-Logik der Vorhaben-Anlage ohne eigene @Transactional: wird von createEpic und
  // openEpicFromCard (je @Transactional) aufgerufen, ohne Self-Invocation über den Proxy
  // (java:S6809).
  private CardView doCreateEpic(
      long userId,
      long boardId,
      String title,
      @Nullable String description,
      @Nullable String shortcode) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.require(userId, projectId, Permission.EPIC_CREATE);

    long columnId = boardService.firstColumn(boardId).id();

    int number = cards.allocateCardNumber(projectId);
    Instant now = clock.instant();
    Card saved =
        cards.save(
            new Card(
                null,
                boardId,
                columnId,
                number,
                title.trim(),
                normalize(description),
                0,
                false,
                null,
                userId,
                now,
                now,
                CardType.EPIC,
                null,
                trimToNull(shortcode),
                null,
                projectId,
                null,
                // Herkunft: kein Schreibpfad hier — der kommt in Issue #604.
                null,
                // Anforderungskarte: kein Schreibpfad hier — der kommt in Issue #639.
                null,
                // Status: ein Vorhaben trägt keinen (Plan #1294, E2).
                null));
    grundlage.publishChanged(boardId, ActivityType.CREATED, saved.requireId());
    return sicht.view(userId, saved);
  }

  /**
   * Herkunftsbaum <b>eines Vorhabens</b> als flache Liste in Präorder mit Tiefe (Issue #643).
   *
   * <p>Bis Issue #645 gab es daneben einen board-weiten Baum. Er stellte alle Ketten des Boards
   * nebeneinander und beantwortete die Frage „was gehört zu diesem Vorhaben" damit nicht; der PO
   * hat ihn abbestellt. Geblieben ist diese Sicht: dieselbe Rechnung auf den Mitgliedern
   * <em>eines</em> Vorhabens — die Zugehörigkeit aus {@link EpicMembership} (#632), der Baum aus
   * {@link DerivationTree} (#609). Eine zweite Graphenrechnung wäre ein zweiter Ort für denselben
   * Fehler.
   *
   * <p><b>Was durch die Einschränkung anders wird:</b> {@code build} weist alles als extern aus,
   * was nicht in der übergebenen Menge liegt. Ein Vorfahr, der auf dem Board liegt, aber kein
   * Mitglied ist — archiviert oder selbst ein Vorhaben —, ist damit extern <em>ohne</em> Nummer,
   * und sein Kind wird zur Wurzel. Ebenso gilt eine Abhängigkeit auf eine Board-Karte ausserhalb
   * des Vorhabens als extern und setzt {@code blocked} nicht. Beides ist gewollt: Was das Vorhaben
   * nicht enthält, kann sein Baum nicht als offen behaupten.
   *
   * @throws CardNotFoundException wenn {@code epicId} kein Vorhaben dieses Boards bezeichnet.
   *     Bewusst nicht als leere Liste: Sonst könnte der Client „existiert nicht" nicht vom
   *     legitimen Leer-Fall unterscheiden. Nicht-Existenz und Fremdzugriff bleiben ununterscheidbar
   *     wie bei {@link CardService#getCard}.
   */
  @Transactional(readOnly = true)
  public List<DerivationNodeView> epicDerivationTree(long userId, long boardId, long epicId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));

    // Ungefiltert in die Zugehörigkeitsrechnung — genau wie in listEpics: EpicMembership filtert
    // Archiviertes selbst heraus, kappt die Kette aber nicht daran. Ein Vorfilter hier ergäbe eine
    // andere Mitgliedermenge als auf der Kachel, und beide Zahlen stammen aus derselben Ansicht.
    List<Card> alle = cards.findByBoardId(boardId);
    Set<Card> mitglieder = EpicMembership.compute(alle).get(epicId);
    if (mitglieder == null) {
      throw new CardNotFoundException();
    }

    // Bewusst UNSORTIERT weitergereicht: `DerivationTree.build` ordnet Geschwister selbst —
    // topologisch, bei Gleichstand nach Nummer. Eine Vorsortierung hier waere nicht nur doppelt,
    // sie verdeckte die eigentliche Ordnung: Mit vorsortierter Eingabe faellt ein Ausfall der
    // Sortierung in `build` nicht mehr auf (PIT-Befund zu Issue #645).
    List<Card> baumKarten = List.copyOf(mitglieder);
    Set<Long> ids = baumKarten.stream().map(Card::requireId).collect(Collectors.toSet());
    return DerivationTree.build(
        baumKarten,
        abhaengigkeiten.abhaengigkeitenJeKarte(ids),
        fremdeVorfahrenNummern(alle),
        zuordnung.gezaehlteMarken(boardId, ids));
  }

  /**
   * Nummern der Vorfahren, die nicht auf diesem Board liegen — ein Sammelzugriff statt einer
   * Abfrage je Kante.
   *
   * <p>Bezugsmenge ist bewusst das <b>Board</b> und nicht die jeweils übergebene Teilmenge: Nur so
   * behält eine board-fremde Herkunft im Vorhaben-Baum ihre Nummer, während board-interne
   * Nicht-Mitglieder ohne Nummer extern bleiben.
   */
  private Map<Long, Integer> fremdeVorfahrenNummern(List<Card> boardCards) {
    Set<Long> imBoard = boardCards.stream().map(Card::requireId).collect(Collectors.toSet());
    Set<Long> fremde =
        boardCards.stream()
            .map(Card::derivedFromCardId)
            .filter(Objects::nonNull)
            .filter(id -> !imBoard.contains(id))
            .collect(Collectors.toSet());
    Map<Long, Integer> nummern = new HashMap<>();
    if (!fremde.isEmpty()) {
      for (Card vorfahr : cards.findByIds(fremde)) {
        nummern.put(vorfahr.requireId(), vorfahr.number());
      }
    }
    return nummern;
  }

  /**
   * Vorhaben eines Boards inkl. Fortschritt.
   *
   * <p><b>Gezählt wird der Nachfahrenbaum, nicht nur die direkte Zuordnung</b> (Issue #633): Wer
   * dem Vorhaben über {@code parentId} zugeordnet ist, bringt alles mit, was über {@code
   * derivedFromCardId} aus ihm entstanden ist. Das wirkt rückwirkend auf den Bestand — die
   * Zugehörigkeit wird gerechnet und nirgends gespeichert (Plan #631, E1). Die Rechnung steht in
   * {@link EpicMembership}; sie filtert Archiviertes und Vorhaben selbst heraus, ohne die Kette an
   * ihnen zu kappen.
   */
  @Transactional(readOnly = true)
  public List<EpicView> listEpics(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));

    List<Card> all = cards.findByBoardId(boardId);
    Map<Long, String> columnNames =
        boardService.listColumns(boardId).stream()
            .collect(Collectors.toMap(ColumnView::id, ColumnView::name));
    Map<Long, Set<Card>> membership = EpicMembership.compute(all);
    Map<Long, Card> nachId =
        all.stream().collect(Collectors.toMap(Card::requireId, Function.identity()));

    return all.stream()
        .filter(c -> c.type() == CardType.EPIC)
        .map(
            epic -> {
              Set<Card> members = membership.getOrDefault(epic.requireId(), Set.of());
              int total = members.size();
              int done =
                  (int)
                      members.stream()
                          .filter(c -> Arbeitspaket.effektivDone(c, columnNames.get(c.columnId())))
                          .count();
              return new EpicView(
                  epic.requireId(),
                  epic.number(),
                  epic.title(),
                  epic.description(),
                  epic.shortcode(),
                  done,
                  total,
                  members.stream().map(Card::number).sorted().toList(),
                  // Wurzeln aus den Mitgliedern heraus, nicht neu aus `all`: So gelten für sie
                  // dieselben Filter, und die Invariante rootNumbers ⊆ memberNumbers hält von
                  // selbst. Das ist kein Zugehörigkeitsfilter mehr — die Zugehörigkeit steht schon
                  // fest —, sondern nur die Kennzeichnung, wer von Hand zugeordnet wurde. Direkt im
                  // Aufruf statt in einer lokalen Variable: Deren Typ zählte PMD als weitere
                  // Kopplung, und EpicService soll ohne CouplingBetweenObjects-Ausnahme auskommen
                  // (Issue #1393).
                  members.stream()
                      .filter(c -> Objects.equals(c.parentId(), epic.requireId()))
                      .map(Card::number)
                      .sorted()
                      .toList(),
                  anforderungsNummer(epic, nachId));
            })
        .toList();
  }

  /**
   * Ordnet eine Karte einem Vorhaben zu ({@code parentId}) oder löst die Zuordnung ({@code null}).
   */
  @Transactional
  public CardView assignParent(long userId, long cardId, @Nullable Long parentId) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten können einem Epic zugeordnet werden");
    }
    Long effective =
        parentId == null
            ? null
            : grundlage.requireEpicInBoard(parentId, card.boardId()).requireId();
    Card saved = cards.save(card.withParent(effective));
    grundlage.publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return sicht.view(userId, saved);
  }

  /**
   * Setzt die Herkunft einer Karte ({@code derivedFrom} als projektweite Kartennummer) oder löscht
   * sie ({@code derivedFrom: null}).
   *
   * <p>Eigener schmaler Endpunkt statt eines Feldes in {@code CardIngestService.updateContent}:
   * Jene Methode ist ein Voll-Update und löscht bei {@code null}, was der Aufrufer nicht
   * mitschickt. In einem Jackson-Record ist ein fehlendes JSON-Feld nicht von {@code null} zu
   * unterscheiden — jeder bestehende Client hätte die Herkunft bei jedem Karten-Edit vernichtet
   * (Issue #607).
   *
   * <p>{@code selfCardId} ist hier <strong>nicht</strong> optional: Beim Anlegen kennt niemand die
   * Nummer der neuen Karte, beim Ändern schon. Ohne die eigene ID greift weder die Selbstbezugs-
   * noch die Zyklusabwehr in {@link DerivedFrom#resolve}.
   */
  @Transactional
  public CardView assignDerivedFrom(long userId, long cardId, @Nullable Integer derivedFrom) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Long herkunft = DerivedFrom.resolve(cards, card.projectId(), derivedFrom, cardId);
    Card saved = cards.save(card.withDerivedFrom(herkunft));
    grundlage.publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return sicht.view(userId, saved);
  }

  /**
   * Eröffnet einen Vorgang: legt ein Vorhaben an, macht die übergebene Karte zu seiner Anforderung
   * und ordnet sie ihm zu — in <b>einem</b> Schritt.
   *
   * <p>Bisher entstand das Vorhaben an einer anderen Stelle als die Anforderung, zu der es gehört:
   * anlegen, dann von Hand zuordnen. Der zweite Schritt ging im Arbeitsfluss unter (Anforderung
   * #636).
   *
   * <p><b>Warum eine Transaktion:</b> Getrennte Aufrufe hinterliessen bei einem Abbruch ein
   * Vorhaben ohne Anforderung — also genau den Zustand, den diese Methode abschaffen soll (Plan
   * #637, E2).
   *
   * <p>Das Vorhaben entsteht auf dem Board der Quellkarte und <b>ohne Beschreibung</b>: Den Inhalt
   * trägt die Anforderungskarte, eine Kopie liefe sofort auseinander.
   *
   * @param shortcode optional, wie bei {@link #createEpic}
   */
  @Transactional
  public CardView openEpicFromCard(
      long userId, long cardId, @Nullable String shortcode, String title) {
    Card quelle = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.require(userId, quelle.projectId(), Permission.EPIC_CREATE);
    requireVorgangEroeffenbar(quelle);

    // Bestehenden Weg wiederverwenden statt nachbauen: Nummernvergabe, erste Spalte, Rechte und
    // das Board-Ereignis haengen alle daran.
    CardView vorhaben = doCreateEpic(userId, quelle.boardId(), title, null, shortcode);

    Card epic = cards.findById(vorhaben.id()).orElseThrow(CardNotFoundException::new);
    Long anforderung = RequirementCard.resolve(cards, epic, quelle.number());
    Card gespeichert = cards.save(epic.withRequirement(anforderung));
    cards.save(quelle.withParent(epic.requireId()));

    return sicht.view(userId, gespeichert);
  }

  /**
   * Die Ablehnungen des Vorgangs-Eröffnens, alle mit Status 400.
   *
   * <p>Sie stehen <b>vor</b> dem Anlegen: Eine Ablehnung danach liefe zwar auch sauber zurueck,
   * verbrauchte aber eine Kartennummer — die Sequenz rollt nicht mit.
   */
  private void requireVorgangEroeffenbar(Card quelle) {
    if (quelle.type() == CardType.EPIC) {
      throw new InvalidDependencyException(
          "Ein Vorhaben eroeffnet keinen Vorgang aus sich selbst: " + quelle.number());
    }
    // Zwei Ruhezustaende, eine Regel: Eine ruhende Karte eroeffnet keinen Vorgang. Der Papierkorb
    // ist im Domain-Record nicht abgebildet — die Nummernsuche filtert ihn, findById nicht.
    if (quelle.archived() || liegtImPapierkorb(quelle)) {
      throw new InvalidDependencyException(
          "Eine ruhende Karte eroeffnet keinen Vorgang: " + quelle.number());
    }
    if (quelle.parentId() != null) {
      // Stillschweigendes Umhaengen entzoege einer bestehenden Gruppierung eine Karte, ohne dass
      // jemand es merkt. Erst loesen, dann eroeffnen.
      throw new InvalidDependencyException(
          "Die Karte ist bereits einem Vorhaben zugeordnet: " + quelle.number());
    }
  }

  /**
   * Ob die Karte im Papierkorb liegt.
   *
   * <p>{@code deletedAt} ist keine Komponente von {@link Card}; die Nummernsuche filtert den
   * Papierkorb dagegen (Port-Zusage von {@code findByProjectIdAndNumber}), {@code findById} nicht.
   * Findet die Suche unter derselben Nummer nichts, ist die Karte geloescht.
   */
  private boolean liegtImPapierkorb(Card karte) {
    return cards.findByProjectIdAndNumber(karte.projectId(), karte.number()).isEmpty();
  }

  /**
   * Setzt oder löscht ({@code null}) die Anforderungskarte eines Vorhabens.
   *
   * <p>Übergeben wird die projektweite <b>Kartennummer</b>, gespeichert die ID — die Nummer ändert
   * sich beim Projektwechsel. Die vier Ablehnungen stehen in {@link RequirementCard}.
   *
   * <p>Eigener schmaler Endpunkt statt eines Feldes im Voll-Update, aus demselben Grund wie bei der
   * Herkunft (#607): Ein Voll-Update kann ein fehlendes Feld nicht von {@code null} unterscheiden
   * und löschte die Zuordnung bei jedem Karten-Edit.
   */
  @Transactional
  public CardView assignRequirement(
      long userId, long cardId, @Nullable Integer requirementCardNumber) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Long anforderung = RequirementCard.resolve(cards, card, requirementCardNumber);
    Card saved = cards.save(card.withRequirement(anforderung));
    grundlage.publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return sicht.view(userId, saved);
  }

  /**
   * Nummer der Anforderungskarte eines Vorhabens, oder {@code null}.
   *
   * <p>Aufgelöst wird ausschliesslich innerhalb der Board-Karten. Das ist keine Einschränkung,
   * sondern die Grenze, die {@link RequirementCard} beim Setzen zieht: Die Anforderung liegt immer
   * auf dem Board des Vorhabens. Fehlt sie hier trotzdem, liegt sie im Papierkorb — dann ist {@code
   * null} die ehrliche Antwort und keine erfundene Nummer.
   */
  private static @Nullable Integer anforderungsNummer(Card epic, Map<Long, Card> nachId) {
    // Optional-Kette statt zweier Null-Vergleiche: Ein vorgeschaltetes `id == null` waere hier
    // redundant — die Map liefert fuer eine unbekannte ID ohnehin nichts —, und PIT kann eine
    // redundante Bedingung nicht toeten, weil beide Zweige dasselbe Ergebnis liefern.
    return Optional.ofNullable(epic.requirementCardId())
        .map(nachId::get)
        .map(Card::number)
        .orElse(null);
  }

  /**
   * Eine Zeile des Herkunftsbaums (Issue #609). Die Liste kommt in Präorder — jede Wurzel
   * unmittelbar gefolgt von ihrem vollständigen Teilbaum —, damit sich der Baum allein aus {@code
   * depth} rekonstruieren lässt.
   *
   * @param derivedFrom Nummer des Vorfahren; {@code null} ohne Herkunft. Bei {@code broken} bleibt
   *     die Nummer gesetzt: Sie beschreibt den gespeicherten Zustand, nicht die Baumposition
   * @param depth 0-basiert; Wurzeln tragen 0
   * @param blocked eine board-interne Abhängigkeit liegt noch nicht in Done. Abgeleitet, nie
   *     gepflegt — externe Abhängigkeiten gehen nicht ein
   * @param dependencies board-interne Abhängigkeits-Nummern
   * @param externalDependencies Abhängigkeits-Nummern, die keine Karte dieses Boards trägt. Jede
   *     gespeicherte Nummer erscheint in genau einer der beiden Listen
   * @param externalOrigin die Herkunft zeigt auf eine Karte außerhalb dieses Boards; die Zeile
   *     erscheint dann als Wurzel, auch ohne Nachfahren
   * @param broken die Zeile hängt an einem Herkunftsring, der nur an der API vorbei entstehen kann
   * @param labels die Labels dieser Karte, die auf der Vorhaben-Kachel gezählt werden ({@code
   *     Label.countOnEpicTile}, Issue #659) — in der Reihenfolge aufsteigender Label-ID. Nur
   *     gezählte reisen mit: Was nicht gezählt wird, muss auch nicht übertragen werden
   */
  public record DerivationNodeView(
      int number,
      String title,
      CardType type,
      @Nullable Integer derivedFrom,
      int depth,
      boolean done,
      boolean blocked,
      List<Integer> dependencies,
      List<Integer> externalDependencies,
      boolean externalOrigin,
      boolean broken,
      List<LabelMarkView> labels) {}

  /**
   * Vorhaben-Darstellung inkl. Fortschritt (zugehörige Karten gesamt / in Done).
   *
   * <p>Die beiden Nummern-Listen machen die gezählte Menge nachprüfbar: Ohne sie wäre eine
   * gestiegene Zahl für den Nutzer nicht nachvollziehbar, weil er die geerbten Karten nirgends
   * sieht (Issue #634 baut die Anzeige darauf).
   *
   * @param done Anzahl zugehöriger Karten in einer Done-Spalte
   * @param total Anzahl zugehöriger Karten; stets {@code memberNumbers.size()}
   * @param memberNumbers Nummern aller zugehörigen Karten, aufsteigend — direkt zugeordnete und
   *     über die Herkunft geerbte gemeinsam
   * @param rootNumbers Nummern der direkt über {@code parentId} zugeordneten Karten, aufsteigend.
   *     Stets eine Teilmenge von {@code memberNumbers}: Sie werden aus derselben Menge gefiltert
   *     und unterliegen damit denselben Regeln
   * @param requirementCardNumber Nummer der Anforderungskarte, oder {@code null}. Nullable, weil
   *     ein Vorhaben auch ohne Herkunftskette zum Gruppieren dienen darf (PO-Entscheidung in #636)
   *     — und weil eine zugeordnete Anforderung im Papierkorb liegen kann, dann ist sie hier nicht
   *     auflösbar. Ausdrücklich <b>nicht</b> 0 oder ein Platzhalter: 0 wäre eine gültige Nummer
   */
  public record EpicView(
      Long id,
      int number,
      String title,
      @Nullable String description,
      @Nullable String shortcode,
      int done,
      int total,
      List<Integer> memberNumbers,
      List<Integer> rootNumbers,
      @Nullable Integer requirementCardNumber) {}
}
