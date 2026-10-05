package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Arbeitspaket;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lese-Abfragen für Läufe (Issue #1396, Plan #1387 E1/E10): Vorhaben je Kartennummer, vorhandene
 * Kartennummern, Kartenaktivitäten eines Nachtlaufs und die Karten zur Fortschrittsermittlung. Ein
 * schmaler, eigener Zugang im Karten-Modul, weil zwei der Abfragen Fachregeln der Karten tragen
 * ({@link EpicMembership}, {@link Arbeitspaket#istArbeitspaket}), die sonst an zwei Orten gepflegt
 * würden.
 *
 * <p>Ohne Rechteprüfung: Alle Methoden sind Vertrag für fremde Module (heute {@code nightrun}), die
 * ihre eigene Prüfung bereits vorgenommen haben.
 */
@Service
public class CardRunQueryService {

  private final CardRepository cards;
  private final CardActivityRepository activity;
  private final KartenZuordnung zuordnung;

  public CardRunQueryService(
      CardRepository cards, CardActivityRepository activity, KartenZuordnung zuordnung) {
    this.cards = cards;
    this.activity = activity;
    this.zuordnung = zuordnung;
  }

  /**
   * Zu jeder genannten Kartennummer des Projekts die Vorhaben, zu denen ihre Karte gehört (Issue
   * #936, Plan #933 E10).
   *
   * <p>Die Zugehörigkeit ist die aus {@link EpicMembership} — {@code parentId} plus Herkunftskette
   * — und kein eigener Begriff. Gerechnet wird je Board, wie überall, wo {@code EpicMembership}
   * gilt, und über alle Boards des Projekts vereinigt: Kartennummern sind projektweit eindeutig,
   * und der Aufrufer kennt nur Nummern.
   *
   * <p><b>Eine Karte kann zu mehreren Vorhaben gehören</b> (Plan #631, E10) und steht dann mit
   * mehreren {@link EpicRef} im Ergebnis. Wer über Vorhaben summiert, zählt ihre Werte deshalb
   * mehrfach — die Summen der Vorhaben dürfen sich überschneiden und ergeben zusammen nicht die
   * Gesamtsumme.
   *
   * <p>Eine Nummer ohne Karte oder ohne Vorhaben fehlt im Ergebnis; die Methode wirft dafür nicht.
   * Eine leere Nummernmenge fragt die Datenbank nicht.
   *
   * <p>Ohne Rechteprüfung wie {@link CardService#requireProjectId}: Die Methode ist Vertrag für
   * fremde Module, die ihre eigene Prüfung bereits vorgenommen haben — sie liefert keine
   * Karteninhalte, nur die Zuordnung zu den Nummern, die der Aufrufer schon kennt.
   *
   * @return je Kartennummer die Menge ihrer Vorhaben; Nummern ohne Vorhaben fehlen
   */
  @Transactional(readOnly = true)
  public Map<Integer, Set<EpicRef>> epicsByCardNumber(
      long projectId, Collection<Integer> cardNumbers) {
    if (cardNumbers.isEmpty()) {
      return Map.of();
    }
    Set<Integer> gesucht = Set.copyOf(cardNumbers);
    Map<Long, List<Card>> jeBoard =
        cards.findByProjectId(projectId).stream().collect(Collectors.groupingBy(Card::boardId));

    Map<Integer, Set<EpicRef>> ergebnis = new HashMap<>();
    for (List<Card> boardKarten : jeBoard.values()) {
      Map<Long, Card> nachId =
          boardKarten.stream().collect(Collectors.toMap(Card::requireId, Function.identity()));
      EpicMembership.compute(boardKarten)
          .forEach(
              (epicId, mitglieder) -> {
                // Die Schluessel von compute sind die Vorhaben genau dieser Kartenmenge.
                Card epic = Objects.requireNonNull(nachId.get(epicId));
                EpicRef ref = new EpicRef(epicId, epic.shortcode(), epic.title());
                mitglieder.stream()
                    .map(Card::number)
                    .filter(gesucht::contains)
                    .forEach(n -> ergebnis.computeIfAbsent(n, k -> new HashSet<>()).add(ref));
              });
    }
    return ergebnis.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
  }

  /**
   * Die Teilmenge der genannten Kartennummern, zu denen es im Projekt eine Karte gibt (Issue
   * #1169).
   *
   * <p>Eine schmale Abfrage: Sie beantwortet nur ja/nein zu Nummern, die der Aufrufer schon kennt,
   * und liefert <b>keine Karteninhalte</b>. Gedacht für Aufrufer, die zu vielen Nummern zugleich
   * wissen müssen, ob der Zugriff darauf eine Karte fände — ein Abruf je Nummer wäre dort eine
   * Anfragelawine.
   *
   * <p>Sichtbarkeit wie bei {@code findByProjectIdAndNumber}: archivierte Karten zählen mit,
   * Papierkorb-Karten nicht. Unbekannte Nummern fehlen im Ergebnis; die Methode wirft dafür nicht.
   * Eine leere Nummernmenge fragt die Datenbank nicht.
   *
   * <p>Ohne Rechteprüfung wie {@link #epicsByCardNumber}: Die Methode ist Vertrag für fremde
   * Module, die ihre eigene Prüfung bereits vorgenommen haben — sie liefert keine Karteninhalte,
   * nur die Existenz zu den Nummern, die der Aufrufer schon kennt.
   *
   * @return die vorhandenen unter den gefragten Nummern
   */
  @Transactional(readOnly = true)
  public Set<Integer> existingCardNumbers(long projectId, Collection<Integer> cardNumbers) {
    if (cardNumbers.isEmpty()) {
      return Set.of();
    }
    return cards.findExistingNumbers(projectId, Set.copyOf(cardNumbers));
  }

  /**
   * Die Kartenaktivitäten eines Nachtlaufs im Zeitfenster (Issue #1373, Plan #1372 E2): Herkunft
   * {@code TOKEN} mit diesem Token-Namen, gesetztes {@code agent}, Zeitpunkt in {@code [von, bis]}.
   * Chronologisch; je Eintrag nur Karte, Art und Zeitpunkt.
   *
   * <p>Ohne Rechteprüfung wie {@link #existingCardNumbers}: Vertrag für das Modul {@code nightrun},
   * das die Projekt-Rolle vor dem Aufruf selbst prüft.
   */
  @Transactional(readOnly = true)
  public List<TokenActivityView> tokenActivitiesInWindow(
      long projectId, String tokenName, Instant von, Instant bis) {
    return activity.findTokenActivitiesInWindow(projectId, tokenName, von, bis).stream()
        .map(
            a -> {
              CardStatus statusAfter = a.statusAfter();
              return new TokenActivityView(
                  a.cardId(),
                  a.type().name(),
                  a.createdAt(),
                  a.laufStart(),
                  statusAfter == null ? null : statusAfter.name());
            })
        .toList();
  }

  /**
   * Die Karten zu den genannten IDs mit allem, was die Ermittlung des Lauf-Fortschritts braucht
   * (Issue #1373): Nummer, Titel, Board, Status, Labelnamen, Herkunft, Typ und Beschreibung — dazu,
   * ob die Karte ein Arbeitspaket ist, damit der Aufrufer {@code card.domain} nicht kennen muss.
   *
   * <p>Unbekannte IDs fehlen im Ergebnis; die Reihenfolge folgt der Eingabe. Gelesen wird wie bei
   * {@link CardRepository#findByIds}, also auch archivierte Karten und Karten im Papierkorb. Ohne
   * Rechteprüfung wie {@link #tokenActivitiesInWindow}; eine leere Eingabe fragt keinen Port.
   */
  @Transactional(readOnly = true)
  public List<LaufKarteView> cardsByIds(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<Long, Card> jeId =
        cards.findByIds(Set.copyOf(ids)).stream()
            .collect(Collectors.toMap(Card::requireId, Function.identity()));
    List<Card> gefunden = ids.stream().map(jeId::get).filter(Objects::nonNull).toList();
    Map<Long, Long> boardJeKarte = new LinkedHashMap<>();
    gefunden.forEach(c -> boardJeKarte.put(c.requireId(), c.boardId()));
    Map<Long, List<String>> labelNamen = zuordnung.labelNamenJeKarte(boardJeKarte);
    return gefunden.stream()
        .map(
            c -> {
              CardStatus status = c.status();
              return new LaufKarteView(
                  c.requireId(),
                  c.number(),
                  c.title(),
                  c.boardId(),
                  status == null ? null : status.name(),
                  labelNamen.getOrDefault(c.requireId(), List.of()),
                  c.derivedFromCardId(),
                  c.type().name(),
                  Arbeitspaket.istArbeitspaket(c.type(), c.title()),
                  c.description());
            })
        .toList();
  }

  /**
   * Eine Kartenaktivität eines Nachtlaufs als Fassaden-Sicht (Issue #1373).
   *
   * @param cardId Karte, an der die Aktivität stattfand
   * @param type Konstantenname von {@code CardActivityType}, etwa {@code CREATED} oder {@code
   *     MOVED}
   * @param createdAt Zeitpunkt der Aktivität
   * @param laufStart Laufkennung des Nachtlaufs, der die Aktivität auslöste (Issue #1426); {@code
   *     null}, wenn er sich nicht ausgewiesen hat
   * @param statusAfter Konstantenname von {@code CardStatus} nach einer Bewegung ({@code MOVED},
   *     {@code STATUS_CHANGED}, Issue #1427); sonst {@code null}
   */
  public record TokenActivityView(
      long cardId,
      String type,
      Instant createdAt,
      @Nullable Instant laufStart,
      @Nullable String statusAfter) {}

  /**
   * Eine Karte, wie die Ermittlung des Lauf-Fortschritts sie braucht (Issue #1373).
   *
   * @param status Konstantenname von {@code CardStatus}; {@code null} bei Vorhaben und
   *     Dokumentarten
   * @param labels Labelnamen der Karte, alphabetisch
   * @param derivedFromCardId ID der Karte, aus der diese entstanden ist; {@code null} ohne Herkunft
   * @param type Konstantenname von {@code CardType}
   * @param arbeitspaket ob die Karte ein Arbeitspaket ist ({@code Arbeitspaket.istArbeitspaket})
   * @param description Markdown-Beschreibung (trägt etwa die Zeile {@code Plan-Review:})
   */
  public record LaufKarteView(
      long id,
      int number,
      String title,
      long boardId,
      @Nullable String status,
      List<String> labels,
      @Nullable Long derivedFromCardId,
      String type,
      boolean arbeitspaket,
      @Nullable String description) {}
}
