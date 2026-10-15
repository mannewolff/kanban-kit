package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.common.Wochenfenster;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Karteneintrag;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Wochenwert;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Zustand;

/**
 * Ermittelt die Nacharbeit der Runs an einem Board (Issue #1756, Plan #1752) — rein, ohne I/O. Die
 * Eingaben bildet der Dienst aus den Sichten der Fassaden von {@code card} und {@code comment} ab;
 * die Ermittlung kennt nur ihre eigenen Typen, {@link KitNamen} und {@link Wochenfenster}.
 *
 * <p><b>Run-Zuordnung (E4, AK 13):</b> Eine Spur zählt nur, wenn ihre Laufkennung dem auf
 * Millisekunden gekürzten Start eines aufbewahrten NIGHT-Laufs gleicht und die Karte zu den
 * übergebenen Board-Karten gehört (E10, AK 12). Sie fällt in das Wochenfenster ihres Laufstarts,
 * nicht ihres eigenen Zeitpunkts (AK 11).
 *
 * <p><b>Signale:</b> Ein Paket ist zurückgezogen, wenn eine Bewegung des Runs es nach Ready oder
 * Backlog brachte und der bekannte Status davor In Progress war (E1). Ein Dokument ist angehalten,
 * wenn der Run ein Label der Sachen {@code KLAEREN} oder {@code LAUF_WARTET} setzte, gleich in
 * welcher Sprache (E3); je Run zählt das einmal (E9).
 *
 * <p><b>Bearbeitet (E5, AK 10):</b> ein Paket mit einer Bewegung des Runs nach In Progress; ein
 * Dokument mit einem Kommentar, einem Label oder einer Aktivität außer {@code CREATED} des Runs.
 * Eine Karte mit Signal ist immer bearbeitet. Je Woche und über den Zeitraum zählt jede Karte
 * höchstens einmal.
 *
 * <p><b>Datenbeginn (E7):</b> Pakete ab dem ältesten aufbewahrten NIGHT-Laufstart, Dokumente ab dem
 * späteren von diesem und dem Beginn der Label-Erfassung. Ein Wochenfenster, das vor diesem Beginn
 * endet, hat keine Daten; seine Spuren zählen auch oben nicht (AK 14).
 */
public final class NacharbeitErmittlung {

  private static final String IN_PROGRESS = "IN_PROGRESS";
  private static final Set<String> RUECKZUG_ZIELE = Set.of("READY", "BACKLOG");
  private static final String ANGELEGT = "CREATED";
  private static final Set<Sache> HALT = EnumSet.of(Sache.KLAEREN, Sache.LAUF_WARTET);

  private NacharbeitErmittlung() {}

  /** Gruppe einer Board-Karte (E5). */
  public enum Kartengruppe {
    /** Ein Arbeitspaket. */
    PAKET,
    /** Ein Fachplan oder Plan. */
    DOKUMENT,
    /** Jede übrige Karte; sie zählt in keiner Zeile. */
    ANDERE
  }

  /**
   * Eine Karte des Boards, ohne Papierkorb, mit Archiv (E10).
   *
   * @param nummer projektweite Nummer der Karte
   */
  public record BoardKarte(long id, int nummer, String titel, Kartengruppe gruppe) {}

  /**
   * Eine Aktivität an einer Karte, die eine Laufkennung trägt.
   *
   * @param art Konstantenname von {@code CardActivityType}, etwa {@code MOVED} oder {@code CREATED}
   * @param laufStart Laufkennung des Laufs, der sie auslöste
   * @param statusNachher Konstantenname von {@code CardStatus} nach einer Bewegung; sonst {@code
   *     null}
   * @param statusVorher bekannter Status der Karte davor; {@code null}, wenn unbekannt
   */
  public record Aktivitaet(
      long cardId,
      String art,
      Instant laufStart,
      @Nullable String statusNachher,
      @Nullable String statusVorher) {}

  /**
   * Ein Kommentar mit Laufkennung — beim Laufstand die des letzten Schreibers (E5).
   *
   * @param laufStart Laufkennung des Laufs, der ihn schrieb
   */
  public record Kommentar(long cardId, Instant laufStart) {}

  /**
   * Ein Label, das ein Lauf an eine Karte setzte.
   *
   * @param labelName Name des Labels beim Setzen
   * @param laufStart Laufkennung des Laufs, der es setzte
   */
  public record LabelEreignis(long cardId, String labelName, Instant laufStart) {}

  /**
   * Die Eingaben der Ermittlung.
   *
   * @param karten die Karten des Boards
   * @param laufstarts Starts aller aufbewahrten NIGHT-Läufe des Projekts (E4)
   * @param erfassungSeit Beginn der Label-Erfassung (V55, E7)
   */
  public record Eingaben(
      List<BoardKarte> karten,
      Collection<Instant> laufstarts,
      Instant erfassungSeit,
      List<Aktivitaet> aktivitaeten,
      List<Kommentar> kommentare,
      List<LabelEreignis> labelEreignisse) {}

  /** Die Nacharbeit zu den Eingaben, mit den zwölf Wochenfenstern bis {@code jetzt}. */
  public static Nacharbeit ermittle(Eingaben eingaben, Instant jetzt) {
    Set<Instant> laufstarts =
        eingaben.laufstarts().stream()
            .map(start -> start.truncatedTo(ChronoUnit.MILLIS))
            .collect(Collectors.toSet());
    Optional<Instant> aeltester = laufstarts.stream().min(Comparator.naturalOrder());
    Zaehlung pakete = new Zaehlung(aeltester.orElse(null), false, jetzt);
    Zaehlung dokumente =
        new Zaehlung(
            aeltester.map(a -> spaeter(a, eingaben.erfassungSeit())).orElse(null), true, jetzt);
    Zuordnung zuordnung = new Zuordnung(eingaben.karten(), laufstarts, jetzt);

    for (Aktivitaet aktivitaet : eingaben.aktivitaeten()) {
      zuordnung
          .spur(aktivitaet.cardId(), aktivitaet.laufStart())
          .ifPresent(spur -> aktivitaetZaehlen(aktivitaet, spur, pakete, dokumente));
    }
    for (Kommentar kommentar : eingaben.kommentare()) {
      zuordnung
          .spur(kommentar.cardId(), kommentar.laufStart())
          .filter(spur -> spur.gruppe() == Kartengruppe.DOKUMENT)
          .ifPresent(dokumente::bearbeitet);
    }
    for (LabelEreignis ereignis : eingaben.labelEreignisse()) {
      zuordnung
          .spur(ereignis.cardId(), ereignis.laufStart())
          .filter(spur -> spur.gruppe() == Kartengruppe.DOKUMENT)
          .ifPresent(
              spur -> {
                if (istHalt(ereignis.labelName())) {
                  dokumente.signal(spur);
                } else {
                  dokumente.bearbeitet(spur);
                }
              });
    }

    List<Nacharbeit.Woche> wochen = new ArrayList<>();
    for (int i = 0; i < Wochenfenster.ANZAHL; i++) {
      wochen.add(
          new Nacharbeit.Woche(
              Wochenfenster.beginn(i, jetzt), pakete.woche(i), dokumente.woche(i)));
    }
    boolean runsImZeitraum =
        laufstarts.stream().anyMatch(start -> Wochenfenster.index(start, jetzt).isPresent());
    return new Nacharbeit(runsImZeitraum, pakete.zeile(), dokumente.zeile(), wochen);
  }

  private static void aktivitaetZaehlen(
      Aktivitaet aktivitaet, Spur spur, Zaehlung pakete, Zaehlung dokumente) {
    if (spur.gruppe() == Kartengruppe.PAKET) {
      if (istRueckzug(aktivitaet)) {
        pakete.signal(spur);
      } else if (IN_PROGRESS.equals(aktivitaet.statusNachher())) {
        pakete.bearbeitet(spur);
      }
    } else if (spur.gruppe() == Kartengruppe.DOKUMENT && !ANGELEGT.equals(aktivitaet.art())) {
      dokumente.bearbeitet(spur);
    }
  }

  private static boolean istRueckzug(Aktivitaet aktivitaet) {
    return IN_PROGRESS.equals(aktivitaet.statusVorher())
        && RUECKZUG_ZIELE.contains(aktivitaet.statusNachher());
  }

  private static boolean istHalt(String labelName) {
    return KitNamen.STANDARD.sache(labelName).filter(HALT::contains).isPresent();
  }

  private static Instant spaeter(Instant a, Instant b) {
    return a.isAfter(b) ? a : b;
  }

  /** Eine Spur, die einem aufbewahrten Run und einer Board-Karte zugeordnet ist. */
  private record Spur(BoardKarte karte, int woche, Instant laufStart) {
    Kartengruppe gruppe() {
      return karte.gruppe();
    }
  }

  /** Ordnet Spuren über Board-Karten und Laufstarts zu (E4, E10). */
  private record Zuordnung(Map<Long, BoardKarte> karten, Set<Instant> laufstarts, Instant jetzt) {

    Zuordnung(List<BoardKarte> karten, Set<Instant> laufstarts, Instant jetzt) {
      this(
          karten.stream().collect(Collectors.toMap(BoardKarte::id, Function.identity())),
          laufstarts,
          jetzt);
    }

    Optional<Spur> spur(long cardId, Instant laufStart) {
      BoardKarte karte = karten.get(cardId);
      if (karte == null || !laufstarts.contains(laufStart)) {
        return Optional.empty();
      }
      return Wochenfenster.index(laufStart, jetzt).stream()
          .mapToObj(woche -> new Spur(karte, woche, laufStart))
          .findFirst();
    }
  }

  /** Die Zählung einer Gruppe über die Wochen mit Daten. */
  private static final class Zaehlung {
    private final @Nullable Instant datenbeginn;
    private final boolean jeLauf;
    private final Instant jetzt;
    private final List<Set<Long>> bearbeitetJeWoche = leereWochen();
    private final List<Set<Long>> betroffenJeWoche = leereWochen();
    private final Map<BoardKarte, List<Instant>> signaleJeKarte = new HashMap<>();

    /**
     * Eine leere Zählung über die zwölf Wochenfenster bis {@code jetzt}.
     *
     * @param jeLauf ob die Kartenliste je Run zählt (Dokumente) statt je Ereignis (Pakete, E9)
     */
    Zaehlung(@Nullable Instant datenbeginn, boolean jeLauf, Instant jetzt) {
      this.datenbeginn = datenbeginn;
      this.jeLauf = jeLauf;
      this.jetzt = jetzt;
    }

    private static List<Set<Long>> leereWochen() {
      return Stream.<Set<Long>>generate(HashSet::new).limit(Wochenfenster.ANZAHL).toList();
    }

    void bearbeitet(Spur spur) {
      if (hatDaten(spur.woche())) {
        bearbeitetJeWoche.get(spur.woche()).add(spur.karte().id());
      }
    }

    void signal(Spur spur) {
      if (hatDaten(spur.woche())) {
        long id = spur.karte().id();
        bearbeitetJeWoche.get(spur.woche()).add(id);
        betroffenJeWoche.get(spur.woche()).add(id);
        signaleJeKarte.computeIfAbsent(spur.karte(), k -> new ArrayList<>()).add(spur.laufStart());
      }
    }

    /**
     * Ob das Fenster {@code woche} Daten hat: Es endet nicht vor dem Datenbeginn. Das Fenster endet
     * mit dem Beginn des nächsten; {@link Wochenfenster#index} ordnet diesen Grenzpunkt noch dem
     * Fenster selbst zu.
     */
    private boolean hatDaten(int woche) {
      return datenbeginn != null && !Wochenfenster.beginn(woche + 1, jetzt).isBefore(datenbeginn);
    }

    Wochenwert woche(int woche) {
      if (!hatDaten(woche)) {
        return new Wochenwert(Zustand.KEINE_DATEN, 0, 0);
      }
      int bearbeitet = bearbeitetJeWoche.get(woche).size();
      Zustand zustand = bearbeitet == 0 ? Zustand.KEINE_KARTE : Zustand.QUOTE;
      return new Wochenwert(zustand, betroffenJeWoche.get(woche).size(), bearbeitet);
    }

    Nacharbeit.Zeile zeile() {
      List<Karteneintrag> liste =
          signaleJeKarte.entrySet().stream()
              .map(
                  e -> {
                    BoardKarte karte = e.getKey();
                    int anzahl = jeLauf ? new HashSet<>(e.getValue()).size() : e.getValue().size();
                    return new Karteneintrag(karte.nummer(), karte.titel(), anzahl);
                  })
              .sorted(
                  Comparator.comparingInt(Karteneintrag::anzahl)
                      .reversed()
                      .thenComparingInt(Karteneintrag::nummer))
              .toList();
      return new Nacharbeit.Zeile(
          datenbeginn, vereinigt(betroffenJeWoche), vereinigt(bearbeitetJeWoche), liste);
    }

    private static int vereinigt(List<Set<Long>> jeWoche) {
      return (int) jeWoche.stream().flatMap(Set::stream).distinct().count();
    }
  }
}
