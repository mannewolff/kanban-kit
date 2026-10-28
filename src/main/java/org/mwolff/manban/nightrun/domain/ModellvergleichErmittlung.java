package org.mwolff.manban.nightrun.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Wochenfenster;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Kopf;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Stufe;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Zeile;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Aktivitaet;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.BoardKarte;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Kartengruppe;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.LabelEreignis;

/**
 * Ermittelt den Modellvergleich der Runs an einem Board (Issue #1946, Plan #1905) — rein, ohne I/O.
 * Karten, Laufbewegungen und Label-Ereignisse tragen dieselben Typen wie bei {@link
 * NacharbeitErmittlung}; die Signale stehen in {@link NacharbeitSignale}.
 *
 * <p><b>Was zählt (E7):</b> ein Vorgang eines Laufs der Gattung {@link NightRunKind#NIGHT}, dessen
 * Laufstart in einem der zwölf {@link Wochenfenster} liegt, dessen Karte eine Karte des Boards ist
 * und dessen Lauf sein Ende gemeldet hat ({@code run_complete}). In die Zeilen gehen davon nur die
 * Vorgänge mit Modell.
 *
 * <p><b>Erfolg (E8, E15):</b> Ein Vorgang ist erfolgreich, wenn er gelungen ist und ihn im selben
 * Run keine Nacharbeit trifft; der Run ist der auf Millisekunden gekürzte Laufstart. {@code n}
 * Rückzüge eines Pakets im Run treffen die ersten {@code n} Umsetzungen an ihm nach {@code
 * position}. Ein Halt an einem Dokument {@code K} trifft unter den Vorgängen der Stufen Plan,
 * Arbeitspakete und Abdeckung des Runs den letzten, der {@code K} geschrieben hat, sonst den
 * letzten an {@code K} mit geschriebenen Karten, sonst keinen. Eine Prüfung trifft nichts.
 *
 * <p><b>Werte (E10):</b> Medians über die Vorgänge, die den Wert tragen; bei gerader Anzahl das
 * Mittel der beiden mittleren. Unter {@link #MINDESTZAHL} Vorgängen hat eine Zeile zu wenig Daten.
 */
// PMD.CouplingBetweenObjects: Die reine Ermittlung zieht die Eingabetypen der Platte „Nacharbeit“,
// die drei Ergebnistypen und die Sammlungen für die Zurechnung im Run zusammen; eine Aufteilung
// trennte die Zählregeln E7, E8 und E10 voneinander, die nur zusammen lesbar sind.
@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class ModellvergleichErmittlung {

  /** Mindestzahl der Vorgänge einer Zeile, ab der ihre Erfolgsquote steht (E10, AK 4). */
  public static final int MINDESTZAHL = 5;

  /** Die Stufen, deren Vorgänge Dokumente schreiben und ein Halt treffen kann (E8). */
  private static final Set<NightRunAttemptStage> SCHREIBENDE =
      EnumSet.of(
          NightRunAttemptStage.PLAN, NightRunAttemptStage.PAKETE, NightRunAttemptStage.ABDECKUNG);

  private static final BigDecimal ZWEI = BigDecimal.valueOf(2);

  private static final Comparator<Zeile> ORDNUNG =
      Comparator.comparing(Zeile::model)
          .thenComparing(Zeile::effort, Comparator.nullsFirst(Comparator.naturalOrder()));

  private ModellvergleichErmittlung() {}

  /**
   * Ein gemeldeter Vorgang mit den Angaben seines Pakets.
   *
   * @param laufStart Startzeitpunkt des Laufs, wie ihn das Paket trägt
   * @param kind Gattung des Laufs
   * @param laufAbgeschlossen der Wert {@code run_complete} des Vorgangs
   * @param position Stelle des Vorgangs in der Reihenfolge der Ausführung
   */
  public record Vorgang(
      Instant laufStart,
      NightRunKind kind,
      boolean laufAbgeschlossen,
      int position,
      NightRunAttempt attempt) {}

  /**
   * Die Eingaben der Ermittlung.
   *
   * @param karten die Karten des Boards, ohne Papierkorb, mit Archiv
   * @param vorgaenge die gemeldeten Vorgänge des Projekts
   * @param aktivitaeten Bewegungen mit Laufkennung
   * @param labelEreignisse Labels, die ein Lauf setzte
   */
  public record Eingaben(
      List<BoardKarte> karten,
      List<Vorgang> vorgaenge,
      List<Aktivitaet> aktivitaeten,
      List<LabelEreignis> labelEreignisse) {}

  /** Der Modellvergleich zu den Eingaben, mit den zwölf Wochenfenstern bis {@code jetzt}. */
  public static Modellvergleich ermittle(Eingaben eingaben, Instant jetzt) {
    Map<Long, BoardKarte> karten =
        eingaben.karten().stream().collect(Collectors.toMap(BoardKarte::id, Function.identity()));
    Set<Integer> nummern =
        eingaben.karten().stream().map(BoardKarte::nummer).collect(Collectors.toSet());
    Set<Vorgang> getroffen = nacharbeit(eingaben, karten);

    List<Vorgang> gezaehlt =
        eingaben.vorgaenge().stream()
            .filter(
                v ->
                    v.kind() == NightRunKind.NIGHT
                        && v.laufAbgeschlossen()
                        && Wochenfenster.index(v.laufStart(), jetzt).isPresent()
                        && nummern.contains(v.attempt().cardNumber()))
            .toList();
    List<Vorgang> mitModell = gezaehlt.stream().filter(v -> v.attempt().model() != null).toList();

    List<Stufe> stufen =
        EnumSet.allOf(NightRunAttemptStage.class).stream()
            .map(stufe -> new Stufe(stufe, zeilen(stufe, mitModell, getroffen)))
            .toList();
    return new Modellvergleich(kopf(gezaehlt, mitModell, jetzt), stufen);
  }

  private static Kopf kopf(List<Vorgang> gezaehlt, List<Vorgang> mitModell, Instant jetzt) {
    Optional<Instant> beginn =
        mitModell.stream().map(Vorgang::laufStart).min(Comparator.naturalOrder());
    if (beginn.isEmpty()) {
      return new Kopf(null, false, 0, 0);
    }
    Instant datenbeginn = beginn.get();
    int ohneModell =
        (int)
            gezaehlt.stream()
                .filter(v -> v.attempt().model() == null)
                .filter(v -> !v.laufStart().isBefore(datenbeginn))
                .count();
    boolean letzte12Wochen = Wochenfenster.index(datenbeginn, jetzt).orElse(-1) == 0;
    return new Kopf(datenbeginn, letzte12Wochen, ohneModell, mitModell.size());
  }

  /** Die Vorgänge, die im eigenen Run ein Rückzug oder ein Halt trifft (E8, E15). */
  private static Set<Vorgang> nacharbeit(Eingaben eingaben, Map<Long, BoardKarte> karten) {
    Map<Instant, List<Vorgang>> jeRun =
        eingaben.vorgaenge().stream()
            .sorted(Comparator.comparingInt(Vorgang::position))
            .collect(
                Collectors.groupingBy(
                    v -> laufkennung(v.laufStart()), LinkedHashMap::new, Collectors.toList()));
    // Gleiche Vorgänge an verschiedenen Paketen sind verschiedene Versuche: Identität, nicht
    // equals.
    Set<Vorgang> getroffen = Collections.newSetFromMap(new IdentityHashMap<>());

    Map<RunKarte, Integer> rueckzuege = new HashMap<>();
    for (Aktivitaet aktivitaet : eingaben.aktivitaeten()) {
      BoardKarte karte = karten.get(aktivitaet.cardId());
      if (karte != null
          && karte.gruppe() == Kartengruppe.PAKET
          && NacharbeitSignale.istRueckzug(aktivitaet.statusVorher(), aktivitaet.statusNachher())) {
        rueckzuege.merge(
            new RunKarte(laufkennung(aktivitaet.laufStart()), karte.nummer()), 1, Integer::sum);
      }
    }
    rueckzuege.forEach(
        (rk, anzahl) ->
            jeRun.getOrDefault(rk.lauf(), List.of()).stream()
                .filter(v -> v.attempt().stage() == NightRunAttemptStage.UMSETZUNG)
                .filter(v -> v.attempt().cardNumber() == rk.karte())
                .limit(anzahl)
                .forEach(getroffen::add));

    Set<RunKarte> halte = new HashSet<>();
    for (LabelEreignis ereignis : eingaben.labelEreignisse()) {
      BoardKarte karte = karten.get(ereignis.cardId());
      if (karte != null
          && karte.gruppe() == Kartengruppe.DOKUMENT
          && NacharbeitSignale.istHalt(ereignis.labelName())) {
        halte.add(new RunKarte(laufkennung(ereignis.laufStart()), karte.nummer()));
      }
    }
    for (RunKarte halt : halte) {
      List<Vorgang> schreibende =
          jeRun.getOrDefault(halt.lauf(), List.of()).stream()
              .filter(v -> SCHREIBENDE.contains(v.attempt().stage()))
              .toList();
      letzter(schreibende, v -> v.attempt().writtenCards().contains(halt.karte()))
          .or(
              () ->
                  letzter(
                      schreibende,
                      v ->
                          v.attempt().cardNumber() == halt.karte()
                              && !v.attempt().writtenCards().isEmpty()))
          .ifPresent(getroffen::add);
    }
    return getroffen;
  }

  private static Optional<Vorgang> letzter(List<Vorgang> vorgaenge, Predicate<Vorgang> passt) {
    return vorgaenge.stream().filter(passt).reduce((frueher, spaeter) -> spaeter);
  }

  private static Instant laufkennung(Instant laufStart) {
    return laufStart.truncatedTo(ChronoUnit.MILLIS);
  }

  private static List<Zeile> zeilen(
      NightRunAttemptStage stufe, List<Vorgang> mitModell, Set<Vorgang> getroffen) {
    Map<Schluessel, List<Vorgang>> jeSchluessel =
        mitModell.stream()
            .filter(v -> v.attempt().stage() == stufe)
            .collect(
                Collectors.groupingBy(
                    v ->
                        new Schluessel(
                            Objects.requireNonNull(v.attempt().model()), v.attempt().effort())));
    return jeSchluessel.entrySet().stream()
        .map(e -> zeile(e.getKey(), e.getValue(), getroffen))
        .sorted(ORDNUNG)
        .toList();
  }

  private static Zeile zeile(
      Schluessel schluessel, List<Vorgang> vorgaenge, Set<Vorgang> getroffen) {
    int erfolgreich =
        (int)
            vorgaenge.stream()
                .filter(v -> v.attempt().succeeded() && !getroffen.contains(v))
                .count();
    List<BigDecimal> kosten = werte(vorgaenge, NightRunUsage::costUsd).sorted().toList();
    List<Long> modellzeiten = werte(vorgaenge, NightRunUsage::modelDurationMs).sorted().toList();
    return new Zeile(
        schluessel.model(),
        schluessel.effort(),
        vorgaenge.size(),
        erfolgreich,
        vorgaenge.size() >= MINDESTZAHL,
        median(kosten, ModellvergleichErmittlung::kostenMittel),
        kosten.size(),
        median(modellzeiten, ModellvergleichErmittlung::modellzeitMittel),
        modellzeiten.size());
  }

  private static <T> Stream<T> werte(
      List<Vorgang> vorgaenge, Function<NightRunUsage, @Nullable T> wert) {
    return vorgaenge.stream()
        .map(v -> v.attempt().usage())
        .filter(Objects::nonNull)
        .map(wert)
        .filter(Objects::nonNull);
  }

  /**
   * Der Median einer sortierten Liste; {@code null}, wenn sie leer ist. Bei gerader Anzahl bildet
   * {@code mittel} aus den beiden mittleren Werten das Ergebnis.
   */
  private static <T> @Nullable T median(List<T> sortiert, BinaryOperator<T> mittel) {
    int n = sortiert.size();
    if (n == 0) {
      return null;
    }
    T oben = sortiert.get(n / 2);
    return n % 2 == 0 ? mittel.apply(sortiert.get(n / 2 - 1), oben) : oben;
  }

  /** Die Hälfte einer endlichen Dezimalzahl ist endlich: {@code divide} bleibt exakt. */
  private static BigDecimal kostenMittel(BigDecimal a, BigDecimal b) {
    return a.add(b).divide(ZWEI);
  }

  /** Auf ganze Millisekunden, die Hälfte aufgerundet. */
  private static Long modellzeitMittel(Long a, Long b) {
    return (a + b + 1) / 2;
  }

  /** Eine Karte in einem Run, der über seine Laufkennung bestimmt ist. */
  private record RunKarte(Instant lauf, int karte) {}

  /** Der Schlüssel einer Zeile (E12). */
  private record Schluessel(String model, @Nullable String effort) {}
}
