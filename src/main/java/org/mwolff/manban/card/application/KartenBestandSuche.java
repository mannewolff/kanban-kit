package org.mwolff.manban.card.application;

import static org.mwolff.manban.card.application.KartenSicht.statusName;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardSearchService.KommentarFund;
import org.mwolff.manban.card.application.CardSearchService.SuchSeite;
import org.mwolff.manban.card.application.CardSearchService.SuchTreffer;
import org.mwolff.manban.card.application.CardSearchService.Suchanfrage;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Kartenart;

/**
 * Durchsucht den Bestand eines Boards, archivierte Karten eingeschlossen (Plan #1574, E3–E8): nach
 * Text, Art, Vorhaben und Zeitraum des Anlagedatums, in Teilen über ein Keyset auf der Nummer.
 *
 * <p>Reine Rechnung ohne Ports, wie {@link EpicMembership}: Die Fassade lädt den Bestand, diese
 * Klasse prüft die Filter, filtert, sortiert, schneidet und formt die Treffer. So ist jede Grenze
 * ohne Datenbank testbar.
 *
 * <p><b>Teile (E6):</b> Sortiert wird nach Nummer absteigend; ein Folgeteil beginnt unterhalb der
 * Nummer des letzten Treffers. Weil die Nummer projektweit eindeutig ist, liefert das bei
 * unverändertem Bestand jeden Treffer genau einmal — anders als ein Offset, der sich verschiebt,
 * wenn sich der Bestand zwischen zwei Teilen ändert.
 */
final class KartenBestandSuche {

  /** Höchstlänge des Auszugs in {@code char} (E5). */
  static final int AUSZUG_LAENGE = 200;

  /** Fundstelle im Titel (E4). */
  static final String TITEL = "TITLE";

  /** Fundstelle im Text (E4). */
  static final String TEXT = "BODY";

  /** Fundstelle in einem Kommentar (E4). */
  static final String KOMMENTAR = "COMMENT";

  /** Markiert einen abgeschnittenen Rand des Auszugs (E5). */
  private static final String RAND = "…";

  /** Kleinste zulässige Trefferzahl eines Teils. */
  private static final int MINDEST_LIMIT = 1;

  /** Die Arten nach ihrem Drahtwert, dem Konstantennamen (E2). */
  private static final Map<String, Kartenart> ARTEN =
      Arrays.stream(Kartenart.values())
          .collect(Collectors.toMap(Kartenart::name, Function.identity()));

  private KartenBestandSuche() {}

  /**
   * Die Filter einer Suche; jeder fehlende Filter lässt alles durch, mehrere wirken zusammen.
   *
   * @param q Suchbegriff, getrimmt und nicht leer; {@code null} heißt kein Textfilter
   * @param arten verlangte Arten, ODER-verknüpft; leer heißt jede Art
   * @param epic Nummer eines Vorhabens des Boards; eine andere Nummer ergibt keine Treffer
   * @param von Beginn des Anlagezeitraums, eingeschlossen
   * @param bis Ende des Anlagezeitraums, ausgeschlossen
   * @param cursor Nummer des letzten Treffers des vorigen Teils
   * @param limit höchste Trefferzahl dieses Teils, mindestens 1
   */
  record Anfrage(
      @Nullable String q,
      Set<Kartenart> arten,
      @Nullable Integer epic,
      @Nullable Instant von,
      @Nullable Instant bis,
      @Nullable Integer cursor,
      int limit) {}

  /**
   * Der Auszug eines Treffers.
   *
   * @param text höchstens {@link #AUSZUG_LAENGE} Zeichen, Zeilenumbrüche als Leerzeichen
   * @param quelle Fundstelle: {@code TITLE}, {@code BODY} oder {@code COMMENT} (E4)
   * @param commentId der Kommentar bei {@code COMMENT}, sonst {@code null}
   */
  record Auszug(String text, String quelle, @Nullable Long commentId) {}

  /** Eine gefundene Karte mit ihrer Art und ihrem Auszug. */
  record Treffer(Card karte, Kartenart art, Auszug auszug) {}

  /**
   * Ein Teil des Ergebnisses.
   *
   * @param naechsterCursor Nummer des letzten Treffers, wenn danach noch Treffer folgen, sonst
   *     {@code null}
   */
  record Seite(List<Treffer> treffer, @Nullable Integer naechsterCursor) {}

  /**
   * Prüft die Filter der Fassade und übersetzt sie, bevor etwas geladen wird: Eine Art ohne
   * Konstante dieses Namens endet mit {@link UnknownCardKindException} (E2), ein Beginn, der nicht
   * vor dem Ende liegt, mit {@link InvalidPeriodBoundsException}; eine einzelne Grenze ist erlaubt
   * (E7). Der Suchbegriff wird getrimmt; leer heißt kein Textfilter (E3).
   */
  static Anfrage pruefe(Suchanfrage anfrage) {
    Set<Kartenart> arten = EnumSet.noneOf(Kartenart.class);
    for (String name : anfrage.arten()) {
      Kartenart art = ARTEN.get(name);
      if (art == null) {
        throw new UnknownCardKindException(
            String.format(Locale.ROOT, "Unbekannte Kartenart: %s", name));
      }
      arten.add(art);
    }
    Instant von = anfrage.createdFrom();
    Instant bis = anfrage.createdTo();
    if (von != null && bis != null && !von.isBefore(bis)) {
      throw new InvalidPeriodBoundsException("createdFrom muss vor createdTo liegen");
    }
    return new Anfrage(
        suchbegriff(anfrage.q()),
        arten,
        anfrage.epic(),
        von,
        bis,
        anfrage.cursor(),
        anfrage.limit());
  }

  /** Der getrimmte Suchbegriff; {@code null}, wenn keiner gesetzt oder er leer ist (E3). */
  static @Nullable String suchbegriff(@Nullable String q) {
    if (q == null) {
      return null;
    }
    String getrimmt = q.trim();
    return getrimmt.isEmpty() ? null : getrimmt;
  }

  /**
   * Die Seite in der Form der Fassade: Art als Konstantenname, Spalte als Kanban-Schlüssel nach
   * {@link CardIngestService#kanbanSchluessel}.
   *
   * @param spalten Spaltennamen des Boards je Spalten-ID; eine fehlende Spalte zählt als Backlog
   */
  static SuchSeite alsSicht(Seite seite, Map<Long, String> spalten) {
    return new SuchSeite(
        seite.treffer().stream().map(t -> alsSicht(t, spalten)).toList(), seite.naechsterCursor());
  }

  private static SuchTreffer alsSicht(Treffer t, Map<Long, String> spalten) {
    Card c = t.karte();
    return new SuchTreffer(
        c.requireId(),
        c.number(),
        c.title(),
        t.art().name(),
        CardIngestService.kanbanSchluessel(statusName(c), spalten.get(c.columnId())),
        c.archived(),
        c.createdAt(),
        c.movedToDoneAt(),
        t.auszug().text(),
        t.auszug().quelle(),
        t.auszug().commentId());
  }

  /**
   * Durchsucht die Karten eines Boards.
   *
   * <p>Mit Suchbegriff bleibt nur eine Karte, die eine Fundstelle in Titel, Text oder einem
   * Kommentar hat; die Fundstelle bestimmt den Auszug mit Vorrang Titel vor Text vor Kommentar
   * (E4). Den Abgleich selbst macht die Datenbank (E3), diese Klasse übernimmt sein Ergebnis.
   *
   * @param boardKarten alle Karten des Boards ohne Papierkorb, archivierte eingeschlossen
   * @param textFundstellen Fundstelle {@code TITLE} oder {@code BODY} je Karten-ID; ohne Textsuche
   *     leer
   * @param kommentarFunde erster passender Kommentar je Karten-ID; ohne Textsuche leer
   */
  static Seite suche(
      List<Card> boardKarten,
      Anfrage anfrage,
      Map<Long, String> textFundstellen,
      Map<Long, KommentarFund> kommentarFunde) {
    if (anfrage.limit() < MINDEST_LIMIT) {
      throw new IllegalArgumentException("limit muss mindestens 1 sein: " + anfrage.limit());
    }
    Predicate<Card> imVorhaben = imVorhaben(boardKarten, anfrage.epic());
    List<Treffer> passend =
        boardKarten.stream()
            .filter(
                c ->
                    anfrage.q() == null
                        || textFundstellen.containsKey(c.requireId())
                        || kommentarFunde.containsKey(c.requireId()))
            .filter(c -> anfrage.cursor() == null || c.number() < anfrage.cursor())
            .filter(imVorhaben)
            .filter(c -> anfrage.von() == null || !c.createdAt().isBefore(anfrage.von()))
            .filter(c -> anfrage.bis() == null || c.createdAt().isBefore(anfrage.bis()))
            .sorted(Comparator.comparingInt(Card::number).reversed())
            .map(
                c ->
                    new Treffer(
                        c,
                        Kartenart.von(c.type(), c.title()),
                        auszug(c, anfrage.q(), textFundstellen, kommentarFunde)))
            .filter(t -> anfrage.arten().isEmpty() || anfrage.arten().contains(t.art()))
            .toList();
    if (passend.size() <= anfrage.limit()) {
      return new Seite(passend, null);
    }
    List<Treffer> teil = passend.subList(0, anfrage.limit());
    return new Seite(List.copyOf(teil), teil.getLast().karte().number());
  }

  /**
   * Zugehörigkeit zum Vorhaben mit dieser Nummer, archivierte Mitglieder eingeschlossen (E8). Ist
   * die Nummer kein Vorhaben des Boards, gehört keine Karte dazu.
   */
  private static Predicate<Card> imVorhaben(List<Card> boardKarten, @Nullable Integer epic) {
    if (epic == null) {
      return c -> true;
    }
    Set<Card> mitglieder =
        boardKarten.stream()
            .filter(c -> c.type() == CardType.EPIC && epic.equals(c.number()))
            .findFirst()
            .map(v -> EpicMembership.computeMitArchiv(boardKarten).get(v.requireId()))
            .orElse(Set.of());
    return mitglieder::contains;
  }

  /**
   * Auszug nach E4/E5: aus der Fundstelle mit dem höchsten Vorrang — Titel, Text, Kommentar —,
   * sonst der Anfang des Texts.
   */
  private static Auszug auszug(
      Card c,
      @Nullable String q,
      Map<Long, String> textFundstellen,
      Map<Long, KommentarFund> kommentarFunde) {
    String quelle = textFundstellen.get(c.requireId());
    if (TITEL.equals(quelle)) {
      return new Auszug(fenster(c.title(), q), TITEL, null);
    }
    if (TEXT.equals(quelle)) {
      return new Auszug(fenster(c.description(), q), TEXT, null);
    }
    KommentarFund fund = kommentarFunde.get(c.requireId());
    if (fund != null) {
      return new Auszug(fenster(fund.body(), q), KOMMENTAR, fund.commentId());
    }
    return new Auszug(anfang(c.description()), TEXT, null);
  }

  /**
   * Ein Fenster von höchstens {@link #AUSZUG_LAENGE} Zeichen um die erste Fundstelle von {@code q},
   * Zeilenumbrüche als Leerzeichen, mit {@code …} an jedem abgeschnittenen Rand; die Ränder zählen
   * zur Länge. Findet Java die Stelle nicht — etwa weil Postgres anders klein schreibt als Java —,
   * gilt der Anfang (E5).
   */
  static String fenster(@Nullable String text, @Nullable String q) {
    if (text == null || q == null) {
      return anfang(text);
    }
    String einzeilig = einzeilig(text);
    int stelle = stelle(einzeilig, q);
    if (stelle < 0 || einzeilig.length() <= AUSZUG_LAENGE) {
      return anfang(text);
    }
    return ausschnitt(einzeilig, stelle, q.length());
  }

  /**
   * Der Ausschnitt eines Texts von mehr als {@link #AUSZUG_LAENGE} Zeichen um die Fundstelle, mit
   * Rändern. Ein Zeichen aus zwei {@code char} am Rand fällt ganz weg.
   */
  private static String ausschnitt(String einzeilig, int stelle, int trefferLaenge) {
    int laenge = einzeilig.length();
    // Platz für den Text, wenn beide Ränder abgeschnitten sind.
    int platz = AUSZUG_LAENGE - RAND.length() - RAND.length();
    int beginn = Math.max(0, stelle - Math.max(0, (platz - trefferLaenge) / 2));
    int ende = Math.min(laenge, beginn + platz);
    if (beginn == 0) {
      ende = AUSZUG_LAENGE - RAND.length();
    } else if (ende == laenge) {
      beginn = laenge - (AUSZUG_LAENGE - RAND.length());
    }
    if (beginn > 0 && Character.isLowSurrogate(einzeilig.charAt(beginn))) {
      beginn++;
    }
    if (Character.isHighSurrogate(einzeilig.charAt(ende - 1))) {
      ende--;
    }
    return (beginn > 0 ? RAND : "")
        + einzeilig.substring(beginn, ende)
        + (ende < laenge ? RAND : "");
  }

  /**
   * Erste Stelle von {@code q} in {@code text} ohne Rücksicht auf Groß- und Kleinschreibung, sonst
   * -1. Zeichenweise per {@link String#regionMatches(boolean, int, String, int, int)}, damit die
   * Stelle auch dann im Originaltext liegt, wenn die Kleinschreibung eines Zeichens die Länge
   * änderte.
   */
  private static int stelle(String text, String q) {
    return IntStream.range(0, text.length())
        .filter(i -> text.regionMatches(true, i, q, 0, q.length()))
        .findFirst()
        .orElse(-1);
  }

  private static String einzeilig(String text) {
    return text.replaceAll("\\R", " ");
  }

  /**
   * Die ersten {@link #AUSZUG_LAENGE} Zeichen, Zeilenumbrüche als Leerzeichen. Ein Zeichen aus zwei
   * {@code char} wird nicht halbiert, sondern fällt ganz weg.
   */
  private static String anfang(@Nullable String text) {
    if (text == null) {
      return "";
    }
    String einzeilig = einzeilig(text);
    if (einzeilig.length() <= AUSZUG_LAENGE) {
      return einzeilig;
    }
    int ende =
        Character.isHighSurrogate(einzeilig.charAt(AUSZUG_LAENGE - 1))
            ? AUSZUG_LAENGE - 1
            : AUSZUG_LAENGE;
    return einzeilig.substring(0, ende);
  }
}
