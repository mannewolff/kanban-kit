package org.mwolff.manban.nightrun.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Karte;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.StufenEintrag;

/**
 * Leitet den Stand der Kette einer Karte aus ihrem Laufstand ab (Issue #1451, Plan #1447 E5, E10,
 * E13) — rein, ohne I/O. Zugang ist {@link FortschrittErmittlung#kettenStand}; die Stufenzeilen
 * liest dieselbe Auswertung wie für die Laufseite.
 *
 * <p>Lauf-Labels kommen als Sachen aus {@link KitNamen}, die Datenzeilen des Laufstands in beiden
 * Sprachen aus {@link LaufstandWortformen} (Issue #1591, Plan #1582 A3, A5, E13).
 */
final class KettenAbleitung {

  /** Die Lauf-Labels als Sachen; welche Namen sie tragen, sagt das Register (A3, E13). */
  private static final List<Sache> LAUF_SACHEN =
      List.of(Sache.LAUF_LAEUFT, Sache.LAUF_WARTET, Sache.LAUF_ABGEBROCHEN);

  /** Die Zeilen des Laufstands, aus denen der Kettenstand liest (E13), in beiden Sprachen (A5). */
  private static final Pattern ZIEL_ZEILE = zeilenmuster(LaufstandWortformen.ZIEL, " (\\S+)$");

  private static final Pattern PRUEFER_ZEILE =
      zeilenmuster(LaufstandWortformen.PRUEFER, " ([12])$");
  private static final Pattern GRENZE_ZEILE = zeilenmuster(LaufstandWortformen.GRENZE, " (\\S+)$");
  private static final Pattern FERTIG_BIS = zeilenmuster(LaufstandWortformen.FERTIG_BIS, "\\S");

  /**
   * Die Zeilen des Laufstands, die Daten tragen und kein Grund sind; die erste andere Zeile ist der
   * Kopf mit dem Grund eines Halts oder Abbruchs.
   */
  private static final List<String> DATENZEILEN =
      Stream.of(
              LaufstandWortformen.ANKER,
              LaufstandWortformen.ZIEL,
              LaufstandWortformen.PRUEFER,
              LaufstandWortformen.GRENZE,
              LaufstandWortformen.ZULETZT,
              LaufstandWortformen.FERTIG_BIS,
              LaufstandWortformen.ALS_NAECHSTES,
              LaufstandWortformen.PROTOKOLL)
          .flatMap(List::stream)
          .toList();

  private KettenAbleitung() {}

  /** Der Kettenstand der Karte aus dem Text ihres Laufstands. */
  static KettenStand stand(Karte karte, String laufstand) {
    return stand(karte, laufstand, KitNamen.STANDARD);
  }

  /** Der Kettenstand der Karte, ihre Lauf-Labels gelesen über das Register {@code namen}. */
  static KettenStand stand(Karte karte, String laufstand, KitNamen namen) {
    return Kettenlage.aus(karte, laufstand, namen).stand();
  }

  /**
   * Das Ziel aus der Zeile {@code Ziel:} des Laufstands als die Station, die es markiert (Issue
   * #1529); leer ohne Zeile oder mit unbekanntem Ziel.
   */
  static Optional<ProgressStage> ziel(String laufstand) {
    return zielAus(zeilen(laufstand));
  }

  private static Optional<ProgressStage> zielAus(List<String> zeilen) {
    return Kettenlage.gruppe(zeilen, ZIEL_ZEILE).flatMap(LaufstandWortformen::zielStation);
  }

  /**
   * Eine Zeile, die mit einer der Formen beginnt und auf {@code rest} endet. Die Formen sind Wörter
   * ohne Sonderzeichen der Musterschreibweise und stehen darum unmaskiert: {@link Pattern#quote}
   * vertrüge sich nicht mit {@link Pattern#CANON_EQ}, das ein zerlegtes „ü“ mitliest.
   */
  private static Pattern zeilenmuster(List<String> formen, String rest) {
    return Pattern.compile("^(?:" + String.join("|", formen) + ")" + rest, Pattern.CANON_EQ);
  }

  private static List<String> zeilen(String body) {
    return body.lines().map(String::strip).filter(z -> !z.isEmpty()).toList();
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
   * @param lauf die Sache des Lauf-Labels der Karte; {@code null} ohne eines
   */
  private record Kettenlage(
      @Nullable ProgressStage ziel,
      @Nullable Integer pruefer,
      boolean zielErreicht,
      KettenStand.@Nullable Projektgrenze grenze,
      ProgressStage erste,
      ProgressStage ende,
      @Nullable String kopf,
      @Nullable Sache lauf,
      List<StufenEintrag> eintraege) {

    static Kettenlage aus(Karte karte, String body, KitNamen namen) {
      List<String> zeilen = zeilen(body);
      ProgressStage ziel = zielAus(zeilen).orElse(null);
      String kopf =
          zeilen.stream()
              .filter(z -> DATENZEILEN.stream().noneMatch(z::startsWith))
              .findFirst()
              .orElse(null);
      KettenStand.Projektgrenze grenze =
          gruppe(zeilen, GRENZE_ZEILE)
              .flatMap(LaufstandWortformen::stufe)
              .map(
                  g ->
                      new KettenStand.Projektgrenze(
                          g,
                          zeilen.stream()
                              .filter(KettenAbleitung::istGrenzWartetext)
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
          LAUF_SACHEN.stream().filter(sache -> karte.traegt(sache, namen)).findFirst().orElse(null),
          FortschrittErmittlung.eintraege(body));
    }

    KettenStand stand() {
      ProgressStage aktuell =
          Arrays.stream(ProgressStage.values())
              .filter(s -> abDerErstenStation(s) && !erledigt(s))
              .findFirst()
              .orElse(null);
      List<StationStand> stationen =
          Arrays.stream(ProgressStage.values()).map(s -> station(s, aktuell)).toList();
      return new KettenStand(ziel, pruefer, zielErreicht, grenze, stationen);
    }

    /**
     * Ob die Station ab der ersten Station des Wegs liegt. Eine obere Grenze braucht es nicht:
     * Stationen hinter dem Ende zeigt {@link #station} schon vorher als nicht vorgesehen, die
     * aktuelle Station wirkt dort nie (Issue #1475 — eine Grenze {@code ende} wäre durch keinen
     * Test zu unterscheiden).
     */
    private boolean abDerErstenStation(ProgressStage s) {
      return s.compareTo(erste) >= 0;
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

    /** Die aktuelle Station nach dem Lauf-Label der Karte. */
    private StationStand aktuelleStation(ProgressStage s) {
      if (lauf == Sache.LAUF_LAEUFT) {
        String text =
            s == ProgressStage.REVIEW && pruefer != null
                ? "läuft (" + pruefer + " Prüfer)"
                : "läuft";
        return new StationStand(s, StationsZustand.LAEUFT, text, null);
      }
      if (lauf == Sache.LAUF_WARTET) {
        // Hinter der Grenze wartet die Kette auf das Projekt, davor auf den Menschen.
        return grenze != null && grenze.stufe().compareTo(s) < 0
            ? new StationStand(s, StationsZustand.WARTET, "Projektgrenze", grenze.grund())
            : new StationStand(s, StationsZustand.WARTET, "wartet", kopf);
      }
      if (lauf == Sache.LAUF_ABGEBROCHEN) {
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

  /** Ob die Zeile der Wartetext des Kits an der Projektgrenze ist, gleich in welcher Sprache. */
  private static boolean istGrenzWartetext(String zeile) {
    return LaufstandWortformen.GRENZ_WARTETEXT.stream().anyMatch(m -> m.matcher(zeile).find());
  }
}
