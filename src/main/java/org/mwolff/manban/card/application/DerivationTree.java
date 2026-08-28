package org.mwolff.manban.card.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardService.DerivationNodeView;
import org.mwolff.manban.card.domain.Card;

/**
 * Berechnet den Herkunftsbaum eines Boards (Issue #609): aus der flachen Kartenmenge und ihren
 * {@code derivedFromCardId}-Kanten eine Liste in Präorder mit Tiefe.
 *
 * <p>Eigene Klasse und nicht eine Methode in {@code CardService}: Der Graph-Teil ist reine Rechnung
 * ohne Ports, und er ist der einzige Teil, den man ohne Datenbank vollständig durchtesten kann.
 * Dieselbe Trennung wie bei {@link DerivedFrom}.
 *
 * <p><b>Der Herkunftsgraph ist funktional</b> — V26 legt genau eine Herkunftsspalte an, jede Karte
 * hat höchstens einen Vorfahren. Daraus folgt der Zuschnitt: Ein Zyklus ist ein geschlossener Ring
 * <em>ohne</em> Wurzel. Ein Abstieg von den Wurzeln erreicht ihn nie, ein „Wiedersehen besuchter
 * IDs" beim Kind-Abstieg kann konstruktiv nicht eintreten — Ringe müssen eigens eingesammelt
 * werden, sonst fehlen ihre Karten still.
 */
final class DerivationTree {

  private DerivationTree() {}

  /**
   * Baut den Baum aus einer bereits geladenen Kartenmenge — ohne weitere Abfragen.
   *
   * @param boardCards Karten dieses Boards ohne Papierkorb und ohne Ideen-Speicher
   * @param depsByCardId Abhängigkeits-Nummern je Karten-ID (ein Sammelzugriff)
   * @param externalAncestorNumbers Nummern der Vorfahren, die <em>nicht</em> auf diesem Board
   *     liegen
   */
  static List<DerivationNodeView> build(
      List<Card> boardCards,
      Map<Long, List<Integer>> depsByCardId,
      Map<Long, Integer> externalAncestorNumbers) {

    Map<Long, Card> byId = new HashMap<>();
    Map<Integer, Card> byNumber = new HashMap<>();
    for (Card c : boardCards) {
      byId.put(c.requireId(), c);
      byNumber.put(c.requireNumber(), c);
    }

    // Kanten über den Vorfahren selbst statt über dessen ID: Fehlt er auf diesem Board (oder fehlt
    // die Herkunft ganz), liefert das Nachschlagen null und die Kante entsteht nicht. Über die ID
    // geschrieben wäre der Guard bloß Kosmetik — die Fremdschlüssel läge dann in einer Map, die
    // ohnehin nur mit IDs dieses Boards befragt wird.
    Map<Long, List<Card>> kinder = new HashMap<>();
    for (Card c : boardCards) {
      Card vorfahr = byId.get(c.derivedFromCardId());
      if (vorfahr != null) {
        kinder.computeIfAbsent(vorfahr.requireId(), k -> new ArrayList<>()).add(c);
      }
    }

    Kontext ctx = new Kontext(byId, byNumber, kinder, depsByCardId, externalAncestorNumbers);

    // Wurzeln (E6): keine board-interne Herkunft, und entweder mit Nachfahren auf diesem Board
    // oder mit board-fremder Herkunft. Die herkunftslose Einzelkarte ist kein Vorhaben.
    List<Card> wurzeln =
        boardCards.stream()
            .filter(c -> !hatBoardInterneHerkunft(c, byId))
            .filter(c -> kinder.containsKey(c.requireId()) || istExtern(c, byId))
            .toList();

    List<DerivationNodeView> zeilen = new ArrayList<>();
    for (Card w : sortiereGeschwister(wurzeln, ctx)) {
      praeorder(w, 0, false, ctx, zeilen);
    }

    // Was von keiner Wurzel erreichbar ist, hängt an einem Ring. Ohne diesen Schritt verschwänden
    // dessen Karten still.
    for (Card ringWurzel : sortiereGeschwister(ringWurzeln(boardCards, ctx), ctx)) {
      praeorder(ringWurzel, 0, true, ctx, zeilen);
    }
    return List.copyOf(zeilen);
  }

  private static boolean hatBoardInterneHerkunft(Card c, Map<Long, Card> byId) {
    return byId.containsKey(c.derivedFromCardId());
  }

  private static boolean istExtern(Card c, Map<Long, Card> byId) {
    Long vorfahr = c.derivedFromCardId();
    return vorfahr != null && !byId.containsKey(vorfahr);
  }

  /**
   * Je Ring die Karte mit der kleinsten Nummer. Ihre Herkunftskante wird für den Aufbau
   * fallengelassen; das Feld {@code derivedFrom} bleibt gesetzt, weil es den gespeicherten Zustand
   * beschreibt und nicht die Baumposition — {@code broken} sagt, dass die Kette nicht trägt.
   *
   * <p>Der Aufstieg merkt sich den zurückgelegten Pfad. Trifft er eine Karte, die auf diesem Pfad
   * schon vorkam, ist der Abschnitt ab dort genau ein Ring — Karten davor gehören nicht dazu, sie
   * hängen nur daran. Derselbe Ring wird von jedem seiner Mitglieder aus gefunden und steht danach
   * mehrfach in der Liste — das bleibt folgenlos, weil {@code praeorder} jede Karte nur beim ersten
   * Mal ausgibt. Eine eigene Dublettensperre wäre eine zweite Wahrheit über dieselbe Frage.
   */
  private static List<Card> ringWurzeln(List<Card> boardCards, Kontext ctx) {
    List<Card> wurzeln = new ArrayList<>();
    for (Card start : boardCards) {
      Map<Long, Integer> pfad = new HashMap<>();
      List<Card> aufstieg = new ArrayList<>();
      Card cursor = start;
      while (cursor != null) {
        Integer bekanntAb = pfad.get(cursor.requireId());
        if (bekanntAb != null) {
          wurzeln.add(kleinsteNummer(aufstieg.subList(bekanntAb, aufstieg.size())));
          break;
        }
        pfad.put(cursor.requireId(), aufstieg.size());
        aufstieg.add(cursor);
        cursor = elternteilImBoard(cursor, ctx);
      }
    }
    return wurzeln;
  }

  private static Card kleinsteNummer(List<Card> ring) {
    return ring.stream().min(Comparator.comparingInt(Card::requireNumber)).orElseThrow();
  }

  private static @Nullable Card elternteilImBoard(Card c, Kontext ctx) {
    return ctx.byId.get(c.derivedFromCardId());
  }

  private static void praeorder(
      Card c, int tiefe, boolean broken, Kontext ctx, List<DerivationNodeView> zeilen) {
    if (!ctx.besucht.add(c.requireId())) {
      return;
    }
    zeilen.add(zeile(c, tiefe, broken, ctx));
    for (Card kind : sortiereGeschwister(ctx.kinder.getOrDefault(c.requireId(), List.of()), ctx)) {
      praeorder(kind, tiefe + 1, broken, ctx, zeilen);
    }
  }

  private static DerivationNodeView zeile(Card c, int tiefe, boolean broken, Kontext ctx) {
    List<Integer> intern = new ArrayList<>();
    List<Integer> extern = new ArrayList<>();
    // Blockiert, solange eine board-interne Abhängigkeit nicht in Done liegt. Externe gehen nicht
    // ein: Was dieses Board nicht auflösen kann, kann es nicht als offen behaupten.
    boolean blocked = false;
    for (Integer nummer : ctx.depsByCardId.getOrDefault(c.requireId(), List.of())) {
      // Extern ist eine Nummer genau dann, wenn keine Karte dieses Boards sie trägt — aufgelöst
      // gegen die Board-Menge, nicht gegen die Baummenge. Eine Abhängigkeit auf eine gewöhnliche
      // Board-Karte ohne Herkunftsbezug ist der Normalfall und wäre sonst fälschlich extern.
      Card ziel = ctx.byNumber.get(nummer);
      if (ziel == null) {
        extern.add(nummer);
      } else {
        intern.add(nummer);
        if (ziel.movedToDoneAt() == null) {
          blocked = true;
        }
      }
    }
    return new DerivationNodeView(
        c.requireNumber(),
        c.title(),
        c.type(),
        herkunftsnummer(c, ctx),
        tiefe,
        c.movedToDoneAt() != null,
        blocked,
        List.copyOf(intern),
        List.copyOf(extern),
        istExtern(c, ctx.byId),
        broken);
  }

  private static @Nullable Integer herkunftsnummer(Card c, Kontext ctx) {
    // Ohne Null-Guard: Fehlt die Herkunft, liefern beide Nachschlagewerke null — ein Guard waere
    // beobachtungsaequivalent und damit untestbar.
    Long vorfahr = c.derivedFromCardId();
    Card imBoard = ctx.byId.get(vorfahr);
    return imBoard != null ? imBoard.number() : ctx.externalAncestorNumbers.get(vorfahr);
  }

  /**
   * Geschwister topologisch: was blockiert, steht vor dem Blockierten. Zweitschlüssel ist die
   * Nummer aufsteigend — eine topologische Sortierung ist nur eine Halbordnung und ohne
   * Zweitschlüssel nicht reproduzierbar.
   *
   * <p>Wirkt <b>nur auf Abhängigkeitskanten zwischen Geschwistern</b>. Eine Kante nach außerhalb
   * der Gruppe ändert {@code blocked}, nicht die Position.
   *
   * <p>Ein Abhängigkeitszyklus ist über die normale API anlegbar ({@code setDependencies} prüft
   * Selbstbezug und Existenz, aber keinen Zyklus). Qualifiziert sich in einem Durchgang keine
   * Karte, bricht die kleinste Nummer den Ring auf — das ist der Fall {@code index = 0}.
   */
  private static List<Card> sortiereGeschwister(List<Card> gruppe, Kontext ctx) {
    // Kein Kurzschluss fuer leere oder einelementige Gruppen: Der allgemeine Weg liefert dort
    // dasselbe, und eine Abkuerzung waere ein zweiter Pfad, den niemand nachrechnet.
    Set<Integer> gruppenNummern = new HashSet<>();
    gruppe.forEach(c -> gruppenNummern.add(c.requireNumber()));

    List<Kandidat> offen = new ArrayList<>();
    for (Card c : gruppe) {
      Set<Integer> blockierer = new HashSet<>();
      for (Integer nummer : ctx.depsByCardId.getOrDefault(c.requireId(), List.of())) {
        if (gruppenNummern.contains(nummer) && nummer != c.requireNumber()) {
          blockierer.add(nummer);
        }
      }
      offen.add(new Kandidat(c, blockierer));
    }
    offen.sort(Comparator.comparingInt(k -> k.karte().requireNumber()));

    List<Card> ergebnis = new ArrayList<>();
    Set<Integer> ausgegeben = new HashSet<>();
    while (!offen.isEmpty()) {
      int index = 0;
      for (int i = 0; i < offen.size(); i++) {
        if (ausgegeben.containsAll(offen.get(i).blockiertVon())) {
          index = i;
          break;
        }
      }
      Kandidat naechste = offen.remove(index);
      ausgegeben.add(naechste.karte().requireNumber());
      ergebnis.add(naechste.karte());
    }
    return ergebnis;
  }

  /** Eine Karte samt der Geschwister-Nummern, die vor ihr stehen müssen. */
  private record Kandidat(Card karte, Set<Integer> blockiertVon) {}

  /** Gemeinsamer Rechenzustand eines Aufbaus — spart fünf Parameter an jeder privaten Methode. */
  private static final class Kontext {
    private final Map<Long, Card> byId;
    private final Map<Integer, Card> byNumber;
    private final Map<Long, List<Card>> kinder;
    private final Map<Long, List<Integer>> depsByCardId;
    private final Map<Long, Integer> externalAncestorNumbers;
    private final Set<Long> besucht = new HashSet<>();

    private Kontext(
        Map<Long, Card> byId,
        Map<Integer, Card> byNumber,
        Map<Long, List<Card>> kinder,
        Map<Long, List<Integer>> depsByCardId,
        Map<Long, Integer> externalAncestorNumbers) {
      this.byId = byId;
      this.byNumber = byNumber;
      this.kinder = kinder;
      this.depsByCardId = depsByCardId;
      this.externalAncestorNumbers = externalAncestorNumbers;
    }
  }
}
