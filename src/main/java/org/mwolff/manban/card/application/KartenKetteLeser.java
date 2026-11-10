package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBestandRepository.KarteImBestand;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.Kartenart;
import org.mwolff.manban.card.domain.LabelEreignisMitPerson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lese-Fassade der Entscheidungskette (Issue #2114, Plan #2108 E1, E6, E10, E12): Sie liefert zu
 * einer Karte die Karten ihrer Kette, die Ideen, die auf sie verweisen, und deren Aktivitäten und
 * Label-Ereignisse — als eigene Records, damit {@code card.domain} modulintern bleibt.
 *
 * <p><b>Ohne Rechteprüfung:</b> Der Aufrufer hat das Leserecht an der Karte bereits geprüft. Was
 * jenseits des Projekts liegt, gibt die Fassade trotzdem nicht preis: Ein Glied in einem anderen
 * Projekt trägt weder Nummer noch Titel, eine Karte im Papierkorb nur ihre Nummer.
 *
 * <p><b>Begrenzt geladen:</b> Die Kette wird bei jedem Öffnen einer Karte berechnet. Je Stufe der
 * Vorfahren und der Nachfahren gibt es einen Sammelzugriff, dazu einen für die Ideen, einen für die
 * Aktivitäten und einen für die Label-Ereignisse — beide nur für die Karten der Kette — und je
 * Board einen für die Spaltennamen.
 */
// PMD.CouplingBetweenObjects: Die Fassade ist der eine Zugang der Entscheidungskette zu den Karten
// (Plan #2108 E1). Sie kennt darum die drei Ports, die Spalten des Boards und je Quelle ihre Sicht;
// eine Aufteilung gäbe dem fremden Modul einen zweiten Zugang, ohne eine Abfrage einfacher zu
// machen.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
public class KartenKetteLeser {

  /**
   * Herkunftszeilen in der Reihenfolge, in der sie Vorfahren nennen: erst der Plan, dann die
   * Quelle.
   */
  private static final List<Pattern> HERKUNFTS_ZEILEN =
      List.of(zeile("Plan"), zeile("Fachliche Quelle|Requirement"));

  private final CardBestandRepository bestand;
  private final CardActivityRepository activity;
  private final CardLabelEventRepository labelEvents;
  private final BoardService boards;

  public KartenKetteLeser(
      CardBestandRepository bestand,
      CardActivityRepository activity,
      CardLabelEventRepository labelEvents,
      BoardService boards) {
    this.bestand = bestand;
    this.activity = activity;
    this.labelEvents = labelEvents;
    this.boards = boards;
  }

  /**
   * Die Kette einer Karte.
   *
   * @throws CardNotFoundException wenn es die Karte nicht gibt oder sie im Papierkorb liegt
   */
  @Transactional(readOnly = true)
  public KartenKette lade(long kartenId) {
    KarteImBestand start =
        bestand.findImBestand(List.of(kartenId)).stream()
            .filter(k -> !k.imPapierkorb())
            .findFirst()
            .orElseThrow(CardNotFoundException::new);
    long projekt = start.karte().projectId();
    Map<Long, Map<Long, String>> spalten = new HashMap<>();
    List<KettenGlied> vorfahren = new Aufstieg(start, projekt, spalten).laufe();
    List<KettenGlied> nachfahren = nachfahren(start, projekt, spalten);

    Set<Integer> nummern = new HashSet<>();
    nummern.add(start.karte().number());
    for (KettenGlied g : verbinde(vorfahren, nachfahren)) {
      Integer nummer = g.nummer();
      if (nummer != null) {
        nummern.add(nummer);
      }
    }
    List<KettenKarte> ideen =
        bestand.findMitErgebnisNummer(projekt, nummern).stream()
            .filter(c -> Kartenart.von(c.type(), c.title()) == Kartenart.IDEE)
            .map(c -> sicht(c, spalten))
            .toList();

    Set<Long> ids = new LinkedHashSet<>();
    ids.add(start.karte().requireId());
    for (KettenGlied g : verbinde(vorfahren, nachfahren)) {
      KettenKarte karte = g.karte();
      if (karte != null) {
        ids.add(karte.id());
      }
    }
    ideen.forEach(i -> ids.add(i.id()));

    return new KartenKette(
        sicht(start.karte(), spalten),
        vorfahren,
        nachfahren,
        ideen,
        activity.findByCardIds(ids).stream().map(KartenKetteLeser::aktivitaet).toList(),
        labelEvents.findMitPerson(ids).stream().map(KartenKetteLeser::labelEreignis).toList());
  }

  /**
   * Ob das Projekt eine Karte trägt, die das Kit geschrieben hat: über ein Token angelegt und mit
   * {@code Autor-Modell:} oder {@code Plan-Modell:} (Plan #2108 E6, deutsch und englisch).
   */
  @Transactional(readOnly = true)
  public boolean traegtKitKarte(long projektId) {
    return bestand.hatUeberTokenAngelegteKitKarte(projektId);
  }

  /**
   * Abstieg über die Herkunft, je Stufe ein Sammelzugriff, je Stufe nach Nummer geordnet. Eine
   * Karte im Papierkorb ist ein Glied mit Nummer, über das es weitergeht; eine Karte in einem
   * anderen Projekt ist ein Glied ohne Angaben, an dem es endet.
   */
  private List<KettenGlied> nachfahren(
      KarteImBestand start, long projekt, Map<Long, Map<Long, String>> spalten) {
    List<KettenGlied> glieder = new ArrayList<>();
    Set<Long> besucht = new HashSet<>();
    besucht.add(start.karte().requireId());
    List<KarteImBestand> stufe = List.of(start);
    while (!stufe.isEmpty()) {
      List<Long> eltern = stufe.stream().map(k -> k.karte().requireId()).toList();
      List<KarteImBestand> kinder = new ArrayList<>(bestand.findKinderImBestand(eltern));
      kinder.sort(Comparator.comparingInt(k -> k.karte().number()));
      List<KarteImBestand> naechste = new ArrayList<>();
      for (KarteImBestand kind : kinder) {
        if (!besucht.add(kind.karte().requireId())) {
          continue;
        }
        if (kind.karte().projectId() != projekt) {
          glieder.add(KettenGlied.anderesProjekt());
          continue;
        }
        glieder.add(glied(kind, spalten));
        naechste.add(kind);
      }
      stufe = naechste;
    }
    return List.copyOf(glieder);
  }

  /** Aufstieg über die Herkunft, fehlt sie, über die Herkunftszeilen; je Stufe Sammelzugriffe. */
  private final class Aufstieg {
    private final long projekt;
    private final Map<Long, Map<Long, String>> spalten;
    private final List<KettenGlied> glieder = new ArrayList<>();
    // Besuchte IDs und Nummern: Ein Ring über die Herkunft oder über die Zeilen endet so.
    private final Set<Long> ids = new HashSet<>();
    private final Set<Integer> nummern = new HashSet<>();
    private List<KarteImBestand> stufe;
    private List<KarteImBestand> naechste = new ArrayList<>();

    Aufstieg(KarteImBestand start, long projekt, Map<Long, Map<Long, String>> spalten) {
      this.projekt = projekt;
      this.spalten = spalten;
      ids.add(start.karte().requireId());
      nummern.add(start.karte().number());
      stufe = List.of(start);
    }

    List<KettenGlied> laufe() {
      while (!stufe.isEmpty()) {
        Set<Long> herkunft = new HashSet<>();
        Set<Integer> genannt = new HashSet<>();
        for (KarteImBestand k : stufe) {
          Long vorfahrId = k.karte().derivedFromCardId();
          if (vorfahrId != null) {
            herkunft.add(vorfahrId);
          } else {
            genannt.addAll(zeilenNummern(k.karte().description()));
          }
        }
        genannt.removeAll(nummern);
        Map<Long, KarteImBestand> nachId =
            bestand.findImBestand(herkunft).stream()
                .collect(Collectors.toMap(k -> k.karte().requireId(), k -> k));
        Map<Integer, KarteImBestand> nachNummer =
            bestand.findImBestandNachNummern(projekt, genannt).stream()
                .collect(Collectors.toMap(k -> k.karte().number(), k -> k));
        naechste = new ArrayList<>();
        for (KarteImBestand k : stufe) {
          Long vorfahrId = k.karte().derivedFromCardId();
          if (vorfahrId != null) {
            ueberHerkunft(nachId.get(vorfahrId));
          } else {
            ueberZeilen(k, nachNummer);
          }
        }
        stufe = naechste;
      }
      return List.copyOf(glieder);
    }

    private void ueberHerkunft(@Nullable KarteImBestand vorfahr) {
      // Fehlt der Vorfahr, wurde er zwischen den Zugriffen gelöscht; nichts ist bekannt.
      if (vorfahr == null || !ids.add(vorfahr.karte().requireId())) {
        return;
      }
      if (vorfahr.karte().projectId() != projekt) {
        glieder.add(KettenGlied.anderesProjekt());
        return;
      }
      uebernimm(vorfahr);
    }

    /**
     * Erst die bewahrte Nummer eines endgültig gelöschten Vorfahren, dann jede Zeile, die eine noch
     * nicht genannte Nummer trägt — so stehen höhere Glieder über einem gelöschten weiter da.
     */
    private void ueberZeilen(KarteImBestand k, Map<Integer, KarteImBestand> nachNummer) {
      Integer verloren = k.verloreneHerkunftsnummer();
      if (verloren != null && nummern.add(verloren)) {
        glieder.add(KettenGlied.geloescht(verloren));
      }
      for (int nummer : zeilenNummern(k.karte().description())) {
        if (!nummern.contains(nummer)) {
          KarteImBestand vorfahr = nachNummer.get(nummer);
          if (vorfahr == null) {
            nummern.add(nummer);
            glieder.add(KettenGlied.geloescht(nummer));
          } else {
            ids.add(vorfahr.karte().requireId());
            uebernimm(vorfahr);
          }
        }
      }
    }

    private void uebernimm(KarteImBestand vorfahr) {
      nummern.add(vorfahr.karte().number());
      glieder.add(glied(vorfahr, spalten));
      naechste.add(vorfahr);
    }
  }

  private KettenGlied glied(KarteImBestand k, Map<Long, Map<Long, String>> spalten) {
    return k.imPapierkorb()
        ? new KettenGlied(GliedZustand.PAPIERKORB, k.karte().number(), null)
        : new KettenGlied(GliedZustand.VORHANDEN, k.karte().number(), sicht(k.karte(), spalten));
  }

  private KettenKarte sicht(Card c, Map<Long, Map<Long, String>> spalten) {
    String spalte = spalten.computeIfAbsent(c.boardId(), this::spaltenDesBoards).get(c.columnId());
    String status = KartenSicht.statusName(c);
    return new KettenKarte(
        c.requireId(),
        c.number(),
        c.title(),
        c.description(),
        Kartenart.von(c.type(), c.title()).name(),
        spalte,
        // Ohne Rückfall auf Backlog: Eine eigene Spalte trägt keinen Schlüssel.
        status != null ? status : CardIngestService.spaltenSchluessel(spalte).orElse(null),
        c.archived(),
        c.wegBelegGeaendertAm());
  }

  private Map<Long, String> spaltenDesBoards(long boardId) {
    return boards.listColumns(boardId).stream()
        .collect(Collectors.toMap(ColumnView::id, ColumnView::name));
  }

  /** Die Nummern der Herkunftszeilen, erst {@code Plan:}, dann die Quelle, je in Textfolge. */
  private static List<Integer> zeilenNummern(@Nullable String beschreibung) {
    if (beschreibung == null) {
      return List.of();
    }
    List<Integer> nummern = new ArrayList<>();
    for (Pattern zeile : HERKUNFTS_ZEILEN) {
      Matcher m = zeile.matcher(beschreibung);
      while (m.find()) {
        nummern.add(Integer.parseInt(m.group(1)));
      }
    }
    return nummern;
  }

  /** Höchstens neun Ziffern: Eine längere Zahl ist keine Kartennummer und passt in kein int. */
  private static Pattern zeile(String namen) {
    return Pattern.compile(
        "^[ \\t]*(?:" + namen + "):[ \\t]*(?:Issue[ \\t]+)?#(\\d{1,9})(?!\\d)", Pattern.MULTILINE);
  }

  private static List<KettenGlied> verbinde(List<KettenGlied> a, List<KettenGlied> b) {
    List<KettenGlied> beide = new ArrayList<>(a);
    beide.addAll(b);
    return beide;
  }

  private static KettenAktivitaet aktivitaet(CardActivity a) {
    return new KettenAktivitaet(
        a.cardId(),
        a.actorUserId(),
        a.type().name(),
        a.detail(),
        a.createdAt(),
        a.origin() == null ? null : a.origin().name(),
        a.tokenName(),
        a.agent(),
        CardService.spaltenSchluessel(a));
  }

  private static KettenLabelEreignis labelEreignis(LabelEreignisMitPerson e) {
    return new KettenLabelEreignis(
        e.cardId(),
        e.labelName(),
        e.createdAt(),
        e.actorUserId(),
        e.origin() == null ? null : e.origin().name(),
        e.tokenName());
  }

  /** Was die Fassade über ein Glied der Kette preisgibt. */
  public enum GliedZustand {
    /** Die Karte liegt im Projekt, archiviert oder nicht; ihre Angaben stehen in der Sicht. */
    VORHANDEN,
    /** Die Karte liegt im Papierkorb; bekannt ist nur ihre Nummer. */
    PAPIERKORB,
    /** Die Karte ist endgültig gelöscht oder unbekannt; bekannt ist nur die bewahrte Nummer. */
    GELOESCHT,
    /** Die Karte liegt in einem anderen Projekt; bekannt ist nur diese Tatsache. */
    ANDERES_PROJEKT
  }

  /**
   * Die Kette einer Karte.
   *
   * @param karte die geöffnete Karte
   * @param vorfahren nächster Vorfahr zuerst
   * @param nachfahren stufenweise, je Stufe nach Nummer
   * @param ideen Ideen des Projekts, deren Zeile {@code Ergebnis:} eine Nummer der Kette nennt
   * @param aktivitaeten der Karte, ihrer sichtbaren Vorfahren und Nachfahren und der Ideen,
   *     chronologisch
   * @param labelEreignisse derselben Karten, chronologisch
   */
  public record KartenKette(
      KettenKarte karte,
      List<KettenGlied> vorfahren,
      List<KettenGlied> nachfahren,
      List<KettenKarte> ideen,
      List<KettenAktivitaet> aktivitaeten,
      List<KettenLabelEreignis> labelEreignisse) {}

  /**
   * Ein Glied der Kette.
   *
   * @param nummer bei {@link GliedZustand#ANDERES_PROJEKT} {@code null}
   * @param karte nur bei {@link GliedZustand#VORHANDEN} gesetzt
   */
  public record KettenGlied(
      GliedZustand zustand, @Nullable Integer nummer, @Nullable KettenKarte karte) {

    static KettenGlied geloescht(int nummer) {
      return new KettenGlied(GliedZustand.GELOESCHT, nummer, null);
    }

    static KettenGlied anderesProjekt() {
      return new KettenGlied(GliedZustand.ANDERES_PROJEKT, null, null);
    }
  }

  /**
   * Eine Karte der Kette.
   *
   * @param art Konstantenname der Kartenart ({@code PLAN}, {@code ARBEITSPAKET}, …)
   * @param spalte Name ihrer Spalte; {@code null}, wenn unbekannt
   * @param spaltenSchluessel eigener Status, sonst Schlüssel der Prozessspalte; ohne Rückfall
   * @param wegBelegGeaendertAm letzte Änderung am Wegbeleg (Plan #2108 E14)
   */
  public record KettenKarte(
      long id,
      int nummer,
      String titel,
      @Nullable String beschreibung,
      String art,
      @Nullable String spalte,
      @Nullable String spaltenSchluessel,
      boolean archiviert,
      @Nullable Instant wegBelegGeaendertAm) {}

  /**
   * Ein Eintrag des Aktivitätsverlaufs.
   *
   * @param spaltenSchluessel bei einem Zug der Schlüssel der Zielspalte; {@code null}, wenn er sich
   *     nicht sicher bestimmen lässt (Plan #2108 E10)
   */
  public record KettenAktivitaet(
      long kartenId,
      @Nullable Long actorUserId,
      String type,
      String detail,
      Instant zeitpunkt,
      @Nullable String origin,
      @Nullable String tokenName,
      @Nullable String agent,
      @Nullable String spaltenSchluessel) {}

  /** Ein Label, das einer Karte der Kette hinzugefügt wurde, mit der Person (Plan #2108 E5). */
  public record KettenLabelEreignis(
      long kartenId,
      String labelName,
      Instant zeitpunkt,
      @Nullable Long actorUserId,
      @Nullable String origin,
      @Nullable String tokenName) {}
}
