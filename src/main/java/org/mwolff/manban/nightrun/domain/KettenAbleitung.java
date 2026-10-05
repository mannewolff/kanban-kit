package org.mwolff.manban.nightrun.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Karte;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.StufenEintrag;

/**
 * Leitet den Stand der Kette einer Karte aus ihrem Laufstand ab (Issue #1451, Plan #1447 E5, E10,
 * E13) — rein, ohne I/O. Zugang ist {@link FortschrittErmittlung#kettenStand}; die Stufenzeilen
 * liest dieselbe Auswertung wie für die Laufseite.
 */
final class KettenAbleitung {

  /** Label einer Kette, die gerade läuft (Kit-Vorgabe). */
  private static final String LABEL_LAEUFT = "lauf:laeuft";

  /** Label einer abgebrochenen Kette (Kit-Vorgabe). */
  private static final String LABEL_ABGEBROCHEN = "lauf:abgebrochen";

  /** Die Ziele des Kit-Vertrags (E10) und die Station, die sie markieren. */
  private static final Map<String, ProgressStage> ZIELE =
      Map.of(
          "plan", ProgressStage.PLAN,
          "pakete", ProgressStage.PAKETE,
          "umsetzung", ProgressStage.UMSETZUNG,
          "push-vorbereitet", ProgressStage.VORBEREITUNG);

  /** Die Zeilen des Laufstands, aus denen der Kettenstand liest (E13). */
  private static final Pattern ZIEL_ZEILE = Pattern.compile("^Ziel: (\\S+)$");

  private static final Pattern PRUEFER_ZEILE = Pattern.compile("^Prüfer: ([12])$");
  private static final Pattern GRENZE_ZEILE = Pattern.compile("^Grenze: (\\S+)$");
  private static final Pattern FERTIG_BIS = Pattern.compile("^fertig bis \\S");

  /** Der Wartetext des Kits an der Projektgrenze — {@code uebergangNichtFreigegeben}. */
  private static final Pattern GRENZE_WARTETEXT =
      Pattern.compile("^wartet: Übergang .+ im Projekt nicht freigegeben");

  /**
   * Die Zeilen des Laufstands, die Daten tragen und kein Grund sind; die erste andere Zeile ist der
   * Kopf mit dem Grund eines Halts oder Abbruchs.
   */
  private static final List<String> DATENZEILEN =
      List.of(
          "## Laufstand",
          "Ziel:",
          "Prüfer:",
          "Grenze:",
          "zuletzt ",
          "fertig bis ",
          "Als Nächstes:",
          "Protokoll:");

  private KettenAbleitung() {}

  /** Der Kettenstand der Karte aus dem Text ihres Laufstands. */
  static KettenStand stand(Karte karte, String laufstand) {
    return Kettenlage.aus(karte, laufstand).stand();
  }

  /**
   * Was der Laufstand einer Karte über ihre Kette sagt. Eine Station ist erledigt, wenn eine {@code
   * fertig}-Zeile für sie oder eine Zeile einer späteren Station steht oder der Kopf {@code fertig
   * bis} das Ziel meldet. Die erste nicht erledigte Station des Wegs ist die aktuelle.
   *
   * @param erste die erste Station, die der Lauf erbringt — bei einem Plan als Start die Pakete
   * @param ende die letzte Station der Kette — das Ziel, bei {@code pakete} und ohne Ziel die
   *     Abdeckung (E10)
   * @param kopf die erste Zeile, die keine Datenzeile ist; {@code null} ohne sie
   * @param laufLabel das {@code lauf:*}-Label der Karte; {@code null} ohne eines
   */
  private record Kettenlage(
      @Nullable ProgressStage ziel,
      @Nullable Integer pruefer,
      boolean zielErreicht,
      KettenStand.@Nullable Projektgrenze grenze,
      ProgressStage erste,
      ProgressStage ende,
      @Nullable String kopf,
      @Nullable String laufLabel,
      List<StufenEintrag> eintraege) {

    static Kettenlage aus(Karte karte, String body) {
      List<String> zeilen = body.lines().map(String::strip).filter(z -> !z.isEmpty()).toList();
      ProgressStage ziel = gruppe(zeilen, ZIEL_ZEILE).map(ZIELE::get).orElse(null);
      String kopf =
          zeilen.stream()
              .filter(z -> DATENZEILEN.stream().noneMatch(z::startsWith))
              .findFirst()
              .orElse(null);
      KettenStand.Projektgrenze grenze =
          gruppe(zeilen, GRENZE_ZEILE)
              .flatMap(KettenAbleitung::stufeNamens)
              .map(
                  g ->
                      new KettenStand.Projektgrenze(
                          g,
                          zeilen.stream()
                              .filter(z -> GRENZE_WARTETEXT.matcher(z).find())
                              .findFirst()
                              .orElse(null)))
              .orElse(null);
      return new Kettenlage(
          ziel,
          gruppe(zeilen, PRUEFER_ZEILE).map(Integer::valueOf).orElse(null),
          zeilen.stream().anyMatch(z -> FERTIG_BIS.matcher(z).find()),
          grenze,
          FortschrittErmittlung.istPlan(karte) ? ProgressStage.PAKETE : ProgressStage.PLAN,
          ziel == null || ziel == ProgressStage.PAKETE ? ProgressStage.ABDECKUNG : ziel,
          kopf,
          Stream.of(LABEL_LAEUFT, FortschrittErmittlung.LABEL_WARTET, LABEL_ABGEBROCHEN)
              .filter(karte.labels()::contains)
              .findFirst()
              .orElse(null),
          FortschrittErmittlung.eintraege(body));
    }

    KettenStand stand() {
      ProgressStage aktuell =
          Arrays.stream(ProgressStage.values())
              .filter(s -> imWeg(s) && !erledigt(s))
              .findFirst()
              .orElse(null);
      List<StationStand> stationen =
          Arrays.stream(ProgressStage.values()).map(s -> station(s, aktuell)).toList();
      return new KettenStand(ziel, pruefer, zielErreicht, grenze, stationen);
    }

    private boolean imWeg(ProgressStage s) {
      return s.compareTo(erste) >= 0 && s.compareTo(ende) <= 0;
    }

    private boolean erledigt(ProgressStage s) {
      return zielErreicht
          || eintraege.stream()
              .anyMatch(e -> e.stufe().compareTo(s) > 0 || (e.stufe() == s && e.fertig()));
    }

    private StationStand station(ProgressStage s, @Nullable ProgressStage aktuell) {
      if (s.compareTo(erste) < 0) {
        return new StationStand(
            s, StationsZustand.VOR_DEM_LAUF_ERBRACHT, "vor dem Lauf erbracht", null);
      }
      if (s.compareTo(ende) > 0) {
        return new StationStand(s, StationsZustand.NICHT_VORGESEHEN, "nicht vorgesehen", null);
      }
      if (erledigt(s)) {
        String text = zielErreicht && s == ziel ? "Ziel erreicht" : "erledigt";
        return new StationStand(s, StationsZustand.ERLEDIGT, text, null);
      }
      return s == aktuell ? aktuelleStation(s) : steht(s);
    }

    /** Die aktuelle Station nach dem {@code lauf:*}-Label der Karte. */
    private StationStand aktuelleStation(ProgressStage s) {
      if (LABEL_LAEUFT.equals(laufLabel)) {
        String text =
            s == ProgressStage.REVIEW && pruefer != null
                ? "läuft (" + pruefer + " Prüfer)"
                : "läuft";
        return new StationStand(s, StationsZustand.LAEUFT, text, null);
      }
      if (FortschrittErmittlung.LABEL_WARTET.equals(laufLabel)) {
        // Hinter der Grenze wartet die Kette auf das Projekt, davor auf den Menschen.
        return grenze != null && grenze.stufe().compareTo(s) < 0
            ? new StationStand(s, StationsZustand.WARTET, "Projektgrenze", grenze.grund())
            : new StationStand(s, StationsZustand.WARTET, "wartet", kopf);
      }
      if (LABEL_ABGEBROCHEN.equals(laufLabel)) {
        return new StationStand(s, StationsZustand.ABGEBROCHEN, "abgebrochen", kopf);
      }
      return steht(s);
    }

    private static StationStand steht(ProgressStage s) {
      return new StationStand(s, StationsZustand.STEHT_AUS, "steht aus", null);
    }

    /** Die erste Gruppe der ersten Zeile, die zum Muster passt. */
    private static Optional<String> gruppe(List<String> zeilen, Pattern muster) {
      return zeilen.stream()
          .map(muster::matcher)
          .filter(Matcher::find)
          .map(m -> m.group(1))
          .findFirst();
    }
  }

  /** Die Stufe mit diesem Namen in Kleinschreibung, wie der Runner sie schreibt. */
  private static Optional<ProgressStage> stufeNamens(String name) {
    return Arrays.stream(ProgressStage.values())
        .filter(s -> s.name().toLowerCase(Locale.ROOT).equals(name))
        .findFirst();
  }
}
