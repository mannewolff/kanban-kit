package org.mwolff.manban.card.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Der Stand je Vorhaben aus der Kartenlage, für die Kostenvoraussage (Issue #2001, Plan #1990 E1,
 * E10; fachlich #1954 AK 3, 5 und 7): seine Pakete, seine Dokumente, ob es abgeschlossen ist und
 * bei welchen Karten seine Kosten zählen. Rein, ohne Datenbank, nach dem Muster von {@link
 * VorhabenLage}.
 *
 * <p>Gerechnet wird über die Zugehörigkeit samt Archiv ({@code EpicMembership.computeMitArchiv}):
 * Der Done-Retention-Job archiviert fertige Pakete, und ein archiviertes Paket ist umgesetzt, nicht
 * verschwunden. Die Aufgabenstufe liest erst die Fassade aus der Beschreibung ({@code
 * card.application.Aufgabenstufe}), weil {@code card.domain} die Anwendungsschicht nicht kennt.
 */
public final class VorhabenStand {

  /**
   * Ein Arbeitspaket des Vorhabens ({@link Arbeitspaket#istArbeitspaket}), auch archiviert.
   *
   * @param nummer projektweite Nummer der Karte
   * @param done ob es nach {@link Arbeitspaket#effektivDone} erledigt ist
   * @param beschreibung Beschreibung der Karte, aus der die Fassade die Aufgabenstufe liest
   * @param angelegt Anlagezeit der Karte
   * @param movedToDoneAt wann die Karte zuletzt nach Done kam; {@code null}, wenn unbekannt
   */
  public record Paket(
      int nummer,
      boolean done,
      @Nullable String beschreibung,
      Instant angelegt,
      @Nullable Instant movedToDoneAt) {}

  /**
   * Ein Fachplan oder Plan des Vorhabens, auch archiviert.
   *
   * @param nummer projektweite Nummer der Karte
   * @param art {@link Kartenart#FACHLICH} oder {@link Kartenart#PLAN}
   * @param offen weder in Done noch archiviert (E10)
   * @param geschnitten ob aus ihm schon ein Paket (Plan) oder ein Plan (Fachplan) entstanden ist
   */
  public record Dokument(int nummer, Kartenart art, boolean offen, boolean geschnitten) {}

  /**
   * Der Stand eines Vorhabens.
   *
   * @param vorhaben die Vorhaben-Karte, auch archiviert
   * @param pakete Arbeitspakete aufsteigend nach Nummer
   * @param dokumente Fachpläne und Pläne aufsteigend nach Nummer
   * @param hatAnforderung ob ein Fachplan dazugehört, auch archiviert
   * @param hatteUngeschnittenesDokument ob überhaupt ein Dokument dazugehört — jedes war vor seinem
   *     Schnitt ungeschnitten; nur ein Vorhaben aus bloßen Paketen hatte nie eines
   * @param abgeschlossen mindestens ein Paket, alle Pakete in Done, kein Dokument offen (E10)
   * @param kostenkarten Nummern der Karten, deren Kosten bei diesem Vorhaben zählen (E1),
   *     aufsteigend
   * @param fruehesteKostenkarteAngelegt früheste Anlagezeit einer Kostenkarte; {@code null} ohne
   */
  public record Ergebnis(
      Card vorhaben,
      List<Paket> pakete,
      List<Dokument> dokumente,
      boolean hatAnforderung,
      boolean hatteUngeschnittenesDokument,
      boolean abgeschlossen,
      List<Integer> kostenkarten,
      @Nullable Instant fruehesteKostenkarteAngelegt) {}

  private VorhabenStand() {}

  /**
   * Der Stand jedes Vorhabens des Boards, archivierte eingeschlossen, aufsteigend nach Nummer.
   *
   * @param boardCards Karten eines Boards samt Archiv, ohne Papierkorb
   * @param zugehoerigeMitArchiv je Vorhaben-ID die Zugehörigen samt Archiv ({@code
   *     EpicMembership.computeMitArchiv})
   * @param spaltennamen Name je Spalten-ID, für Done nach {@link Arbeitspaket#effektivDone}
   */
  public static List<Ergebnis> ermittle(
      List<Card> boardCards,
      Map<Long, Set<Card>> zugehoerigeMitArchiv,
      Map<Long, String> spaltennamen) {
    Map<Integer, Card> kostenvorhaben = Kostenzuordnung.ermittle(boardCards, zugehoerigeMitArchiv);
    return boardCards.stream()
        .filter(c -> c.type() == CardType.EPIC)
        .sorted(Comparator.comparing(Card::number))
        .map(
            epic ->
                stand(
                    epic,
                    zugehoerigeMitArchiv.getOrDefault(epic.requireId(), Set.of()),
                    spaltennamen,
                    kostenkarten(epic, boardCards, kostenvorhaben)))
        .toList();
  }

  private static Ergebnis stand(
      Card epic, Set<Card> mitArchiv, Map<Long, String> spaltennamen, List<Card> kostenkarten) {
    List<Card> sortiert = mitArchiv.stream().sorted(Comparator.comparing(Card::number)).toList();
    List<Paket> pakete = new ArrayList<>();
    List<Dokument> dokumente = new ArrayList<>();
    for (Card c : sortiert) {
      boolean done = Arbeitspaket.effektivDone(c, spaltennamen.get(c.columnId()));
      Kartenart art = Kartenart.von(c.type(), c.title());
      if (Arbeitspaket.istArbeitspaket(c.type(), c.title())) {
        pakete.add(new Paket(c.number(), done, c.description(), c.createdAt(), c.movedToDoneAt()));
      } else if (art == Kartenart.FACHLICH || art == Kartenart.PLAN) {
        dokumente.add(
            new Dokument(
                c.number(),
                art,
                !done && !c.archived(),
                !VorhabenLage.fehltNachfahr(c, mitArchiv)));
      }
    }
    boolean abgeschlossen =
        !pakete.isEmpty()
            && pakete.stream().allMatch(Paket::done)
            && dokumente.stream().noneMatch(Dokument::offen);
    return new Ergebnis(
        epic,
        List.copyOf(pakete),
        List.copyOf(dokumente),
        dokumente.stream().anyMatch(d -> d.art() == Kartenart.FACHLICH),
        !dokumente.isEmpty(),
        abgeschlossen,
        kostenkarten.stream().map(Card::number).toList(),
        kostenkarten.stream().map(Card::createdAt).min(Comparator.naturalOrder()).orElse(null));
  }

  /** Die Karten, deren Kostenvorhaben {@code epic} ist (E1), aufsteigend nach Nummer. */
  private static List<Card> kostenkarten(
      Card epic, List<Card> boardCards, Map<Integer, Card> kostenvorhaben) {
    return boardCards.stream()
        .filter(c -> epic.equals(kostenvorhaben.get(c.number())))
        .sorted(Comparator.comparing(Card::number))
        .toList();
  }
}
