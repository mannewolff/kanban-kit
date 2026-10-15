package org.mwolff.manban.card.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;

/**
 * Rekonstruiert die Zustandsaufenthalte einer Karte aus ihrem Spaltenverlauf (Plan #1765, E6–E8):
 * die reine Regel des einmaligen Nachtrags, ohne Datenbank. Die Rohdaten liefert der Port {@code
 * FlussNachtragQuelle} als die Eingabe-Records dieser Klasse.
 *
 * <p>Der Zustand einer Spaltenzeile ist der Status, den ihr Spaltenname nennt — das fängt auch die
 * Zeilen reiner Statuswechsel, die {@code CardMoveService.setStatus} mit dem Prozessnamen benennt
 * —, sonst der nach der Lage der Spalte in der heutigen Spaltenfolge ihres Boards. Aufeinander
 * folgende Zeilen mit gleichem Zustand und Board verschmelzen (E4). Unbekannt (Status {@code null},
 * E7) sind die Zeit vor der ersten Spaltenzeile einer älteren Karte, eine gelöschte Spalte und die
 * Zeit vor einer Rückholung aus dem Papierkorb bis zum letzten bekannten Zeitpunkt davor.
 */
// PMD.CouplingBetweenObjects, PMD.GodClass: Die Klasse ist die eine Regel des Nachtrags (E6–E8)
// samt ihren Eingabe-Records — die Kopplung zählt diese Records, die Zwischenstände und die
// java.util-Sammlungen. Eine Aufteilung verteilte die eine Regel auf Klassen, die dieselben Typen
// weiter kennen müssten, ohne dass ein Schritt einfacher würde.
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.GodClass"})
public final class FlussRekonstruktion {

  /** Detail-Text der Rückholung aus dem Archiv ({@code CardArchiveService.restore}). */
  public static final String AUS_DEM_ARCHIV = "Wiederhergestellt";

  /**
   * Detail-Text der Rückholung aus dem Papierkorb ({@code CardArchiveService.restoreFromTrash}).
   */
  public static final String AUS_DEM_PAPIERKORB = "Aus Papierkorb wiederhergestellt";

  /**
   * Der heutige Stand der Karte.
   *
   * @param status Zustand; {@code null} bei Dokumenten und Vorhaben
   * @param movedToDoneAt letzter Done-Eintritt einer fertigen Karte
   * @param deletedAt seit wann die Karte im Papierkorb liegt; {@code null} außerhalb
   */
  public record Kartenstand(
      long cardId,
      @Nullable CardStatus status,
      Instant createdAt,
      @Nullable Instant movedToDoneAt,
      @Nullable Instant deletedAt) {}

  /** Wo eine Spalte steht: ihre ID und das Board, zu dem sie gehört. */
  public record Spaltenort(long columnId, long boardId) {}

  /**
   * Eine Zeile aus {@code card_column_transition}.
   *
   * @param ort Spalte samt Board; {@code null}, wenn die Spalte gelöscht wurde
   * @param columnName Name der Spalte zum Eintrittszeitpunkt
   */
  public record Spaltenzeile(
      @Nullable Spaltenort ort, String columnName, Instant enteredAt, @Nullable Instant leftAt) {}

  /** Eine Spalte der heutigen Spaltenfolge. */
  public record Spalte(long id, String name) {}

  /** Die heutige Spaltenfolge eines Boards, von links nach rechts. */
  public record Spaltenfolge(long boardId, List<Spalte> spalten) {}

  /**
   * Ein Archiv- oder Rückhol-Ereignis aus {@code card_activity}. {@code RESTORED} steht für beide
   * Rückholungen; der Detail-Text unterscheidet sie ({@link #AUS_DEM_ARCHIV}, {@link
   * #AUS_DEM_PAPIERKORB}).
   *
   * @param typ {@code ARCHIVED} oder {@code RESTORED}
   */
  public record Archivereignis(CardActivityType typ, Instant at, String detail) {}

  private record Spanne(Instant von, @Nullable Instant bis) {
    boolean enthaelt(Instant zeitpunkt) {
      return !zeitpunkt.isBefore(von) && (bis == null || zeitpunkt.isBefore(bis));
    }
  }

  /** Ein Abschnitt des Verlaufs; Board und Zustand können unbekannt sein. */
  private record Abschnitt(Spanne spanne, @Nullable Long boardId, @Nullable CardStatus status) {}

  /** Ein Stück im Fluss mit aufgelöstem Board. */
  private record Stueck(
      Instant von,
      @Nullable Instant bis,
      long boardId,
      @Nullable CardStatus status,
      boolean doneEintritt) {}

  private FlussRekonstruktion() {}

  /**
   * Die Aufenthalte der Karte, chronologisch, mit unbekannten Spannen und offenem letzten
   * Aufenthalt; leer bei einer Karte ohne Zustand oder ohne Spaltenverlauf.
   *
   * @param folgen heutige Spaltenfolgen der Boards, auf denen die Karte lag
   */
  public static List<FlussStay> rekonstruiere(
      Kartenstand karte,
      List<Spaltenzeile> zeilen,
      List<Archivereignis> ereignisse,
      List<Spaltenfolge> folgen) {
    if (karte.status() == null || zeilen.isEmpty()) {
      return List.of();
    }
    List<Spaltenzeile> verlauf =
        zeilen.stream().sorted(Comparator.comparing(Spaltenzeile::enteredAt)).toList();
    List<Archivereignis> chronik =
        ereignisse.stream().sorted(Comparator.comparing(Archivereignis::at)).toList();
    List<Abschnitt> abschnitte = abschnitte(karte, verlauf, folgen);
    List<Spanne> archiv = archivSpannen(chronik);
    List<Spanne> unbekannt = papierkorbRueckholungen(karte, verlauf, chronik);
    Instant geloeschtAb = karte.deletedAt();
    List<Spanne> papierkorb =
        geloeschtAb == null ? List.of() : List.of(new Spanne(geloeschtAb, null));

    NavigableSet<Instant> grenzen = new TreeSet<>();
    Stream.of(abschnitte.stream().map(Abschnitt::spanne), archiv.stream(), unbekannt.stream())
        .flatMap(s -> s)
        .forEach(
            spanne -> {
              grenzen.add(spanne.von());
              Optional.ofNullable(spanne.bis()).ifPresent(grenzen::add);
            });
    papierkorb.forEach(spanne -> grenzen.add(spanne.von()));

    List<Abschnitt> teile = new ArrayList<>();
    for (Instant von : grenzen) {
      Spanne spanne = new Spanne(von, grenzen.higher(von));
      if (!liegtIn(papierkorb, von)) {
        abschnitte.stream()
            .filter(a -> a.spanne().enthaelt(von))
            .findFirst()
            .filter(a -> a.status() == CardStatus.DONE || !liegtIn(archiv, von))
            .map(
                a ->
                    new Abschnitt(spanne, a.boardId(), liegtIn(unbekannt, von) ? null : a.status()))
            .ifPresent(teile::add);
      }
    }
    List<Stueck> stuecke = verschmolzen(mitBoard(teile));
    if (stuecke.isEmpty()) {
      return List.of();
    }
    return aufenthalte(karte.cardId(), mitDoneEintritt(stuecke, karte.movedToDoneAt()));
  }

  private static List<Abschnitt> abschnitte(
      Kartenstand karte, List<Spaltenzeile> verlauf, List<Spaltenfolge> folgen) {
    List<Abschnitt> abschnitte = new ArrayList<>();
    Instant ersterEintritt = verlauf.getFirst().enteredAt();
    // Anlage vor V9: Der Backfill öffnete die erste Zeile erst bei der Migration (E7 a). Liegt
    // die Anlage nicht vor der ersten Zeile, enthält die Spanne keinen Zeitpunkt, und eine Grenze
    // an ihr teilt höchstens einen Aufenthalt, den das Verschmelzen wieder zusammenfügt. Darum
    // ohne Fallunterscheidung — jede ergäbe hier einen äquivalenten Mutanten (Issue #1790).
    abschnitte.add(new Abschnitt(new Spanne(karte.createdAt(), ersterEintritt), null, null));
    for (Spaltenzeile zeile : verlauf) {
      Spaltenort ort = zeile.ort();
      abschnitte.add(
          new Abschnitt(
              new Spanne(zeile.enteredAt(), zeile.leftAt()),
              ort == null ? null : ort.boardId(),
              ort == null ? null : zustand(ort, zeile.columnName(), folgen)));
    }
    return abschnitte;
  }

  private static @Nullable CardStatus zustand(
      Spaltenort ort, String spaltenname, List<Spaltenfolge> folgen) {
    Optional<CardStatus> benannt = Arbeitspaket.statusVonSpalte(spaltenname);
    if (benannt.isPresent()) {
      return benannt.get();
    }
    for (Spaltenfolge folge : folgen) {
      if (folge.boardId() == ort.boardId()) {
        List<String> bisZiel = new ArrayList<>();
        for (Spalte spalte : folge.spalten()) {
          bisZiel.add(spalte.name());
          if (spalte.id() == ort.columnId()) {
            return Arbeitspaket.statusNachLage(bisZiel);
          }
        }
      }
    }
    return null;
  }

  /** Die Zeiten im Archiv, je von der Archivierung bis zur Rückholung aus dem Archiv. */
  private static List<Spanne> archivSpannen(List<Archivereignis> chronik) {
    List<Spanne> spannen = new ArrayList<>();
    Instant archiviertSeit = null;
    for (Archivereignis ereignis : chronik) {
      if (ereignis.typ() == CardActivityType.ARCHIVED) {
        archiviertSeit = ereignis.at();
      } else if (archiviertSeit != null && AUS_DEM_ARCHIV.equals(ereignis.detail())) {
        spannen.add(new Spanne(archiviertSeit, ereignis.at()));
        archiviertSeit = null;
      }
    }
    if (archiviertSeit != null) {
      spannen.add(new Spanne(archiviertSeit, null));
    }
    return spannen;
  }

  /**
   * Je Rückholung aus dem Papierkorb die unbekannte Spanne davor (E7 c): Wann die Karte in den
   * Papierkorb kam, ist nicht gespeichert, sicher ist nur der letzte bekannte Zeitpunkt davor.
   */
  private static List<Spanne> papierkorbRueckholungen(
      Kartenstand karte, List<Spaltenzeile> verlauf, List<Archivereignis> chronik) {
    List<Instant> bekannt =
        Stream.of(
                Stream.of(karte.createdAt()),
                verlauf.stream().map(Spaltenzeile::enteredAt),
                verlauf.stream().map(Spaltenzeile::leftAt).flatMap(Stream::ofNullable),
                chronik.stream().map(Archivereignis::at))
            .flatMap(s -> s)
            .toList();
    return chronik.stream()
        .filter(e -> e.typ() == CardActivityType.RESTORED && AUS_DEM_PAPIERKORB.equals(e.detail()))
        .map(
            e ->
                new Spanne(
                    bekannt.stream()
                        .filter(zeitpunkt -> zeitpunkt.isBefore(e.at()))
                        .max(Comparator.naturalOrder())
                        .orElse(e.at()),
                    e.at()))
        .toList();
  }

  private static boolean liegtIn(List<Spanne> spannen, Instant zeitpunkt) {
    return spannen.stream().anyMatch(spanne -> spanne.enthaelt(zeitpunkt));
  }

  /**
   * Löst ein unbekanntes Board auf das des benachbarten bekannten Teils auf, zuerst des vorigen (E7
   * b); ohne bekanntes Board entfällt der Teil — {@code board_id} ist Pflicht.
   */
  private static List<Stueck> mitBoard(List<Abschnitt> teile) {
    List<Stueck> stuecke = new ArrayList<>();
    for (int i = 0; i < teile.size(); i++) {
      Abschnitt teil = teile.get(i);
      Long board = teil.boardId();
      for (int j = i - 1; board == null && j >= 0; j--) {
        board = teile.get(j).boardId();
      }
      for (int j = i + 1; board == null && j < teile.size(); j++) {
        board = teile.get(j).boardId();
      }
      if (board != null) {
        stuecke.add(
            new Stueck(teil.spanne().von(), teil.spanne().bis(), board, teil.status(), false));
      }
    }
    return stuecke;
  }

  /** Verschmilzt lückenlos aufeinander folgende Stücke mit gleichem Board und Zustand (E4). */
  private static List<Stueck> verschmolzen(List<Stueck> stuecke) {
    List<Stueck> ergebnis = new ArrayList<>();
    for (Stueck stueck : stuecke) {
      if (!ergebnis.isEmpty() && setztFort(ergebnis.getLast(), stueck)) {
        Stueck vorher = ergebnis.removeLast();
        ergebnis.add(
            new Stueck(vorher.von(), stueck.bis(), vorher.boardId(), vorher.status(), false));
      } else {
        ergebnis.add(stueck);
      }
    }
    return ergebnis;
  }

  private static boolean setztFort(Stueck vorher, Stueck stueck) {
    return schliesstAn(vorher, stueck)
        && vorher.boardId() == stueck.boardId()
        && vorher.status() == stueck.status();
  }

  private static boolean schliesstAn(Stueck vorher, Stueck stueck) {
    return stueck.von().equals(vorher.bis());
  }

  /**
   * Bei einer fertigen Karte ist {@code moved_to_done_at} der bekannte Done-Eintritt (E8): Liegt er
   * nicht nach dem Beginn des letzten Done-Aufenthalts, beginnt dieser dort, und was davor lag,
   * endet spätestens dort.
   */
  private static List<Stueck> mitDoneEintritt(List<Stueck> stuecke, @Nullable Instant doneSeit) {
    Stueck letztes = stuecke.getLast();
    if (doneSeit == null
        || letztes.status() != CardStatus.DONE
        || doneSeit.isAfter(letztes.von())) {
      return stuecke;
    }
    List<Stueck> ergebnis = new ArrayList<>();
    for (Stueck stueck : stuecke.subList(0, stuecke.size() - 1)) {
      if (stueck.von().isBefore(doneSeit)) {
        // Offen ist höchstens das letzte Stück — nur das letzte Intervall hat keine obere Grenze.
        Instant bis = Objects.requireNonNull(stueck.bis());
        Instant gekuerzt = bis.isAfter(doneSeit) ? doneSeit : bis;
        ergebnis.add(new Stueck(stueck.von(), gekuerzt, stueck.boardId(), stueck.status(), false));
      }
    }
    ergebnis.add(new Stueck(doneSeit, letztes.bis(), letztes.boardId(), letztes.status(), true));
    return ergebnis;
  }

  /**
   * Formt die Stücke zu Aufenthalten. Ein Aufenthalt endet mit {@code BOARD} oder {@code STATUS},
   * wenn der nächste lückenlos anschließt, sonst mit {@code EXIT}. Sein Eintritt ist unbekannt,
   * wenn er unmittelbar auf eine unbekannte Spanne folgt (E8).
   */
  private static List<FlussStay> aufenthalte(long cardId, List<Stueck> stuecke) {
    List<FlussStay> aufenthalte = new ArrayList<>();
    for (int i = 0; i < stuecke.size(); i++) {
      Stueck stueck = stuecke.get(i);
      boolean nachUnbekannt =
          i > 0 && schliesstAn(stuecke.get(i - 1), stueck) && stuecke.get(i - 1).status() == null;
      boolean bekannt = stueck.status() != null && (stueck.doneEintritt() || !nachUnbekannt);
      aufenthalte.add(
          new FlussStay(
              null,
              cardId,
              stueck.boardId(),
              stueck.status(),
              stueck.von(),
              stueck.bis(),
              bekannt,
              endedBy(stueck, i + 1 < stuecke.size() ? stuecke.get(i + 1) : null)));
    }
    return aufenthalte;
  }

  private static @Nullable EndedBy endedBy(Stueck stueck, @Nullable Stueck naechstes) {
    if (stueck.bis() == null) {
      return null;
    }
    if (naechstes == null || !schliesstAn(stueck, naechstes)) {
      return EndedBy.EXIT;
    }
    return naechstes.boardId() == stueck.boardId() ? EndedBy.STATUS : EndedBy.BOARD;
  }
}
