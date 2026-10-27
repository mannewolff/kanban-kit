package org.mwolff.manban.card.application;

import static org.mwolff.manban.card.application.KartenSicht.statusName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardBestandRepository.KarteImBestand;
import org.mwolff.manban.card.application.CardSearchService.Kette;
import org.mwolff.manban.card.application.CardSearchService.KettenGlied;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.Kartenart;

/**
 * Berechnet die Herkunftskette einer Karte (Issue #1580, Plan #1574 E11–E13): Vorfahren bis zur
 * Wurzel, Nachfahren bis zu den Blättern, archivierte Glieder eingeschlossen.
 *
 * <p>Eigene Klasse ohne Ports wie {@link DerivationTree}: Der Graph-Teil ist reine Rechnung und
 * ohne Datenbank vollständig testbar. {@code DerivationTree} selbst passt nicht — er lässt
 * Archiviertes weg und baut den ganzen Board-Baum.
 *
 * <p><b>Sichtbarkeitsgrenze:</b> Eine Lücke nennt nur Nummern, die das Token heute schon sieht —
 * die Nummer eines Vorfahren steht in {@code derivedFrom} (E12). Nachfahren im Papierkorb oder auf
 * einem anderen Board fehlen darum still (E13), ein Nachfahr im Papierkorb samt seinem Teilbaum.
 */
final class HerkunftsKette {

  private final Map<Long, Card> aufBoard = new HashMap<>();
  private final Map<Long, List<Card>> kinder = new HashMap<>();
  private final Function<Long, Optional<Card>> nachlader;
  private final Map<Long, Integer> verloreneNummern;
  private final Map<Long, String> spalten;

  private HerkunftsKette(
      List<Card> boardKarten,
      Function<Long, Optional<Card>> nachlader,
      Map<Long, Integer> verloreneNummern,
      Map<Long, String> spalten) {
    for (Card c : boardKarten) {
      aufBoard.put(c.requireId(), c);
      // Eine Wurzel hat keinen Vorfahren und ist niemandes Kind.
      Optional.ofNullable(c.derivedFromCardId())
          .ifPresent(vorfahr -> kinder.computeIfAbsent(vorfahr, k -> new ArrayList<>()).add(c));
    }
    this.nachlader = nachlader;
    this.verloreneNummern = verloreneNummern;
    this.spalten = spalten;
  }

  /**
   * Die Kette einer Karte des Boards.
   *
   * @param karte die Karte, deren Kette gefragt ist; liegt auf dem Board
   * @param boardKarten alle Karten des Boards ohne Papierkorb, archivierte eingeschlossen
   * @param nachlader Karte zu einer ID außerhalb der Board-Menge — im Papierkorb oder auf einem
   *     anderen Board ({@code findByIds}); leer, wenn es sie nicht gibt
   * @param verloreneNummern Nummer des endgültig gelöschten Vorfahren je Karten-ID (E12)
   * @param spalten Spaltenname je Spalten-ID des Boards
   */
  static Kette berechne(
      Card karte,
      List<Card> boardKarten,
      Function<Long, Optional<Card>> nachlader,
      Map<Long, Integer> verloreneNummern,
      Map<Long, String> spalten) {
    HerkunftsKette kette = new HerkunftsKette(boardKarten, nachlader, verloreneNummern, spalten);
    List<KettenGlied> nachfahren = new ArrayList<>();
    Set<Long> besucht = new HashSet<>();
    besucht.add(karte.requireId());
    kette.absteigen(karte, 1, besucht, nachfahren);
    return new Kette(kette.glied(karte, 0), kette.vorfahren(karte), List.copyOf(nachfahren));
  }

  /**
   * Die Vorfahren aller Karten einer Board-Liste (Issue #1930), nächster Vorfahr zuerst. Anders als
   * {@link #berechne} reicht die Kette über alle Boards desselben Projekts: Die Board-Liste
   * verlangt die Projekt-Mitgliedschaft, und ein Mitglied sieht jedes Board des Projekts.
   *
   * <ul>
   *   <li>Ein Vorfahr im Papierkorb ist eine Lücke {@link HerkunftsGlied.Luecke#PAPIERKORB} ohne
   *       Titel; die Kette geht über ihn weiter.
   *   <li>Ein endgültig gelöschter Vorfahr ist eine Lücke {@link HerkunftsGlied.Luecke#GELOESCHT}
   *       und beendet sie.
   *   <li>Ein Vorfahr in einem anderen Projekt beendet sie ohne Glied.
   *   <li>Ein Ring endet an der ersten schon besuchten Karte.
   * </ul>
   *
   * <p>Nachgeladen wird stufenweise gesammelt: je Stufe <strong>ein</strong> Aufruf des
   * Stapelladers für alle Vorfahren, die weder auf dem Board liegen noch schon geladen sind. Ein
   * Nachlader je Karte ergäbe bei 200 Karten bis zu 200 Abfragen je Stufe.
   *
   * @param boardKarten die Karten der Board-Liste
   * @param stapelLader Karten zu IDs samt Papierkorb und verlorener Nummer ({@code findImBestand});
   *     eine unbekannte ID fehlt
   * @param verloreneNummern Nummer des endgültig gelöschten Vorfahren je Karten-ID des Boards
   * @param projectId Projekt des Boards
   * @return je Karten-ID der Board-Liste die Vorfahren; leer bei einer Karte ohne Herkunft
   */
  static Map<Long, List<HerkunftsGlied>> vorfahrenJeKarte(
      List<Card> boardKarten,
      Function<Set<Long>, List<KarteImBestand>> stapelLader,
      Map<Long, Integer> verloreneNummern,
      long projectId) {
    Map<Long, KarteImBestand> bekannt = new HashMap<>();
    Map<Long, List<HerkunftsGlied>> ergebnis = new HashMap<>();
    List<Aufstieg> offen = new ArrayList<>();
    for (Card c : boardKarten) {
      // Karten des Boards liegen nie im Papierkorb: Die Board-Liste lässt ihn aus.
      KarteImBestand start = new KarteImBestand(c, false, verloreneNummern.get(c.requireId()));
      bekannt.put(c.requireId(), start);
      Aufstieg aufstieg = new Aufstieg(start);
      ergebnis.put(c.requireId(), aufstieg.glieder);
      offen.add(aufstieg);
    }
    while (!offen.isEmpty()) {
      Set<Long> fehlend = new HashSet<>();
      for (Aufstieg a : offen) {
        Long vorfahrId = a.cursor.karte().derivedFromCardId();
        if (vorfahrId != null && !bekannt.containsKey(vorfahrId)) {
          fehlend.add(vorfahrId);
        }
      }
      if (!fehlend.isEmpty()) {
        for (KarteImBestand geladen : stapelLader.apply(fehlend)) {
          bekannt.put(geladen.karte().requireId(), geladen);
        }
      }
      offen.removeIf(a -> !a.schritt(bekannt, projectId));
    }
    return ergebnis;
  }

  /** Der Aufstieg einer Karte der Board-Liste: Stand und schon gefundene Glieder. */
  private static final class Aufstieg {
    private final List<HerkunftsGlied> glieder = new ArrayList<>();
    // Besuchte IDs: Ein Ring aus #607 führte sonst endlos im Kreis.
    private final Set<Long> besucht = new HashSet<>();
    private KarteImBestand cursor;

    Aufstieg(KarteImBestand start) {
      cursor = start;
      besucht.add(start.karte().requireId());
    }

    /**
     * Ein Schritt aufwärts: hängt das nächste Glied an.
     *
     * @return ob der Aufstieg weitergeht
     */
    boolean schritt(Map<Long, KarteImBestand> bekannt, long projectId) {
      Long vorfahrId = cursor.karte().derivedFromCardId();
      if (vorfahrId == null) {
        // Vor der Migration gelöschte Vorfahren haben keine bewahrte Nummer und erscheinen nicht.
        Integer verloren = cursor.verloreneHerkunftsnummer();
        if (verloren != null) {
          glieder.add(new HerkunftsGlied(verloren, null, false, HerkunftsGlied.Luecke.GELOESCHT));
        }
        return false;
      }
      KarteImBestand vorfahr = bekannt.get(vorfahrId);
      // Wertvergleich der Projekt-IDs (Long): Ein fremdes Projekt beendet die Kette ohne Glied.
      if (!besucht.add(vorfahrId)
          || vorfahr == null
          || !Objects.equals(vorfahr.karte().projectId(), projectId)) {
        return false;
      }
      Card karte = vorfahr.karte();
      glieder.add(
          vorfahr.imPapierkorb()
              ? new HerkunftsGlied(karte.number(), null, false, HerkunftsGlied.Luecke.PAPIERKORB)
              : new HerkunftsGlied(karte.number(), karte.title(), karte.archived(), null));
      cursor = vorfahr;
      return true;
    }
  }

  /**
   * Aufstieg über {@code derivedFromCardId} bis zur Wurzel, ohne Stopp an einer Art (E11). Eine
   * Karte desselben Boards im Papierkorb ist eine Lücke, über die der Aufstieg weitergeht; eine
   * Karte eines anderen Boards und ein endgültig gelöschter Vorfahr sind Lücken, die ihn beenden.
   */
  private List<KettenGlied> vorfahren(Card karte) {
    List<KettenGlied> glieder = new ArrayList<>();
    // Besuchte IDs: Ein Ring aus #607 führte sonst endlos im Kreis.
    Set<Long> besucht = new HashSet<>();
    besucht.add(karte.requireId());
    Card cursor = karte;
    int tiefe = 1;
    while (cursor != null) {
      cursor = aufsteigen(cursor, karte.boardId(), tiefe, besucht, glieder);
      tiefe++;
    }
    return List.copyOf(glieder);
  }

  /**
   * Ein Schritt aufwärts: hängt das Glied dieser Tiefe an und liefert die Karte, von der aus der
   * Aufstieg weitergeht — {@code null} am Ende der Kette.
   */
  private @Nullable Card aufsteigen(
      Card cursor, Long boardId, int tiefe, Set<Long> besucht, List<KettenGlied> glieder) {
    Long vorfahrId = cursor.derivedFromCardId();
    if (vorfahrId == null) {
      // Vor der Migration gelöschte Vorfahren haben keine bewahrte Nummer und erscheinen nicht.
      Integer verloren = verloreneNummern.get(cursor.requireId());
      if (verloren != null) {
        glieder.add(luecke(verloren, tiefe));
      }
      return null;
    }
    if (!besucht.add(vorfahrId)) {
      return null;
    }
    Card vorfahr = aufBoard.get(vorfahrId);
    if (vorfahr != null) {
      glieder.add(glied(vorfahr, tiefe));
      return vorfahr;
    }
    Card ausserhalb = nachlader.apply(vorfahrId).orElse(null);
    if (ausserhalb == null) {
      return null;
    }
    glieder.add(luecke(ausserhalb.number(), tiefe));
    // Wertvergleich der Board-IDs (Long), wie in CardSearchService.findOnBoardByNumber: Nur im
    // Papierkorb desselben Boards geht es weiter, ein fremdes Board beendet die Kette.
    return Objects.equals(ausserhalb.boardId(), boardId) ? ausserhalb : null;
  }

  /**
   * Abstieg in Präorder wie {@link DerivationTree}, Geschwister nach Nummer. Kinder kennt nur die
   * Board-Menge: Ein Nachfahr im Papierkorb erzeugt keine Kante, sein Teilbaum ist unerreichbar.
   */
  private void absteigen(Card c, int tiefe, Set<Long> besucht, List<KettenGlied> glieder) {
    List<Card> geschwister = new ArrayList<>(kinder.getOrDefault(c.requireId(), List.of()));
    geschwister.sort(Comparator.comparingInt(Card::number));
    for (Card kind : geschwister) {
      if (besucht.add(kind.requireId())) {
        glieder.add(glied(kind, tiefe));
        absteigen(kind, tiefe + 1, besucht, glieder);
      }
    }
  }

  private KettenGlied glied(Card c, int tiefe) {
    return new KettenGlied(
        c.number(),
        c.title(),
        Kartenart.von(c.type(), c.title()).name(),
        CardIngestService.kanbanSchluessel(statusName(c), spalten.get(c.columnId())),
        c.archived(),
        tiefe,
        false);
  }

  private static KettenGlied luecke(int nummer, int tiefe) {
    return new KettenGlied(nummer, null, null, null, false, tiefe, true);
  }
}
