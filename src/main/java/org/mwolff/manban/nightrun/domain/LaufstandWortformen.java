package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Lesetabelle des Laufstands in deutscher und englischer Form (Issue #1590, Plan #1582 A5, A6,
 * E1, E12) — die einzige Stelle des Backends, an der die Wortformen des Laufstands stehen.
 *
 * <p>Gelesen werden beide Formen, gespeicherte Laufstände bleiben unberührt (A6). Die englischen
 * Formen folgen dem Glossar des Kits ({@code docs/en/glossar.md}) und den englischen Ziel-Labels;
 * sie sind eine Vorlage für Kit-Plan #1420 (E1). Jede Liste trägt die deutsche Form zuerst.
 */
final class LaufstandWortformen {

  /** Die Kopfzeile, an der ein Laufstand-Kommentar erkannt wird (E12). */
  static final List<String> ANKER = List.of("## Laufstand", "## Run status");

  /** Die Zeile des Ziels der Kette. */
  static final List<String> ZIEL = List.of("Ziel:", "Goal:");

  /** Die Zeile der Prüferzahl. */
  static final List<String> PRUEFER = List.of("Prüfer:", "Reviewers:");

  /** Die Zeile der Projektgrenze. */
  static final List<String> GRENZE = List.of("Grenze:", "Boundary:");

  /** Der Kopf einer Kette, die ihr Ziel erreicht hat. */
  static final List<String> FERTIG_BIS = List.of("fertig bis ", "done up to ");

  /** Der Wartetext des Kits an der Projektgrenze — {@code uebergangNichtFreigegeben}. */
  static final List<Pattern> GRENZ_WARTETEXT =
      List.of(
          Pattern.compile("^wartet: Übergang .+ im Projekt nicht freigegeben", Pattern.CANON_EQ),
          Pattern.compile("^waiting: transition .+ not enabled in project"));

  /** Der Anfang der Zeilen mit der zuletzt begonnenen und der zuletzt abgeschlossenen Stufe. */
  static final List<String> ZULETZT = List.of("zuletzt ", "last ");

  /** Die Zeile mit dem nächsten Schritt. */
  static final List<String> ALS_NAECHSTES = List.of("Als Nächstes:", "Next:");

  /** Die Zeile mit dem Pfad des Protokolls. */
  static final List<String> PROTOKOLL = List.of("Protokoll:", "Log:");

  /** Der Prüfvermerk eines Plans — mit Wert gilt der Plan als geprüft. */
  static final List<String> PLAN_REVIEW = List.of("Plan-Review:", "Plan review:");

  /** Die Werte der Zeile {@link #ZIEL} und die Station, die sie markieren. */
  private static final Map<String, ProgressStage> ZIELE =
      Map.of(
          "plan", ProgressStage.PLAN,
          "pakete", ProgressStage.PAKETE,
          "packages", ProgressStage.PAKETE,
          "umsetzung", ProgressStage.UMSETZUNG,
          "implementation", ProgressStage.UMSETZUNG,
          "push-vorbereitet", ProgressStage.VORBEREITUNG,
          "push-prepared", ProgressStage.VORBEREITUNG);

  /** Je Sprache die Form eines Stufeneintrags: deutsch, dann englisch. */
  private static final List<Eintragsform> EINTRAGSFORMEN =
      List.of(
          new Eintragsform(
              stufenwoerter("plan", "review", "pakete", "abdeckung", "umsetzung", "vorbereitung"),
              "begonnen",
              "fertig",
              "für",
              "um"),
          new Eintragsform(
              stufenwoerter(
                  "plan", "review", "packages", "coverage", "implementation", "preparation"),
              "started",
              "finished",
              "for",
              "at"));

  /** Alle Stufenwörter beider Sprachen. */
  private static final Map<String, ProgressStage> STUFEN = alleStufenwoerter();

  private LaufstandWortformen() {}

  /** Die Stufe zum Stufenwort, wie der Runner es schreibt; leer bei einem unbekannten Wort. */
  static Optional<ProgressStage> stufe(String wort) {
    return Optional.ofNullable(STUFEN.get(wort));
  }

  /** Die Station, die ein Wert der Zeile {@link #ZIEL} markiert; leer bei einem unbekannten. */
  static Optional<ProgressStage> zielStation(String wert) {
    return Optional.ofNullable(ZIELE.get(wert));
  }

  /**
   * Der Rest der Zeile hinter der ersten Form, mit der sie beginnt; leer, wenn sie mit keiner
   * beginnt.
   */
  static Optional<String> wert(List<String> formen, String zeile) {
    return formen.stream()
        .filter(zeile::startsWith)
        .findFirst()
        .map(form -> zeile.substring(form.length()));
  }

  /**
   * Der Stufeneintrag in der Zeile: {@code <stufe> begonnen|fertig für #<ziel> um <zeit>} oder
   * {@code <stage> started|finished for #<ziel> at <zeit>} — die Form von {@code stufeBeginnt}/
   * {@code stufeEndet} in {@code night.mjs}. Die Sprachen mischen sich in einem Eintrag nicht; leer
   * ohne Eintrag.
   */
  static Optional<Stufenzeile> stufenzeile(String zeile) {
    for (Eintragsform form : EINTRAGSFORMEN) {
      Matcher m = form.muster().matcher(zeile);
      if (m.find()) {
        return Optional.of(
            new Stufenzeile(
                stufe(m.group(1)).orElseThrow(),
                form.fertig().equals(m.group(2)),
                Integer.parseInt(m.group(3)),
                m.group(4)));
      }
    }
    return Optional.empty();
  }

  /**
   * Ein gelesener Stufeneintrag.
   *
   * @param zeit der Zeitstempel, wie er steht — ob er lesbar ist, prüft {@link #eintrag}
   */
  record Stufenzeile(ProgressStage stufe, boolean fertig, int ziel, String zeit) {

    /** Der Stufeneintrag mit gelesenem Zeitstempel; leer, wenn der Zeitstempel nicht lesbar ist. */
    Optional<FortschrittErmittlung.StufenEintrag> eintrag() {
      try {
        return Optional.of(
            new FortschrittErmittlung.StufenEintrag(stufe, fertig, ziel, Instant.parse(zeit)));
      } catch (DateTimeParseException _) {
        return Optional.empty();
      }
    }
  }

  /** Die Stufenwörter einer Sprache in der Reihenfolge der Stufen. */
  private static Map<String, ProgressStage> stufenwoerter(String... woerter) {
    Map<String, ProgressStage> jeWort = new HashMap<>();
    ProgressStage[] stufen = ProgressStage.values();
    for (int i = 0; i < stufen.length; i++) {
      jeWort.put(woerter[i], stufen[i]);
    }
    return Map.copyOf(jeWort);
  }

  private static Map<String, ProgressStage> alleStufenwoerter() {
    Map<String, ProgressStage> alle = new HashMap<>();
    EINTRAGSFORMEN.forEach(f -> alle.putAll(f.stufen()));
    return Map.copyOf(alle);
  }

  /**
   * Die Form eines Stufeneintrags in einer Sprache.
   *
   * @param stufen die Stufenwörter der Sprache
   * @param fertig das Wort einer abgeschlossenen Stufe
   * @param muster der ganze Eintrag; Gruppen: Stufenwort, begonnen oder fertig, Ziel, Zeitstempel
   */
  private record Eintragsform(Map<String, ProgressStage> stufen, String fertig, Pattern muster) {

    Eintragsform(
        Map<String, ProgressStage> stufen, String begonnen, String fertig, String fuer, String um) {
      this(stufen, fertig, muster(stufen, begonnen + "|" + fertig, fuer, um));
    }

    private static Pattern muster(
        Map<String, ProgressStage> stufen, String zustaende, String fuer, String um) {
      // Nach Stufe sortiert, damit das Muster in jedem Lauf gleich aussieht.
      Map<ProgressStage, String> jeStufe = new EnumMap<>(ProgressStage.class);
      stufen.forEach((wort, stufe) -> jeStufe.put(stufe, wort));
      return Pattern.compile(
          "(?:^|\\W)("
              + String.join("|", jeStufe.values())
              + ") ("
              + zustaende
              + ") "
              + fuer
              + " #(\\d{1,9}) "
              + um
              + " (\\S+)",
          Pattern.CANON_EQ);
    }
  }
}
