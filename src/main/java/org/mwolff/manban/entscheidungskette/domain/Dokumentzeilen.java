package org.mwolff.manban.entscheidungskette.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Die Kopfzeilen der Dokumente, die den Weg einer Karte belegen, deutsch und englisch (Plan #2108
 * E12, E13): {@code Plan:}, {@code Fachliche Quelle:}/{@code Requirement:}, {@code
 * Plan-Modell:}/{@code Plan-Model:} und {@code Autor-Modell:}/{@code Author-Model:}. Die Muster
 * folgen {@code Card} und der Lese-Fassade der Kette.
 */
final class Dokumentzeilen {

  private static final String NUMMER = ":[ \\t]*(?:Issue[ \\t]+)?#(\\d{1,9})(?!\\d)";
  private static final Pattern PLAN = Pattern.compile("^[ \\t]*Plan" + NUMMER, Pattern.MULTILINE);
  private static final Pattern QUELLE =
      Pattern.compile("^[ \\t]*(?:Fachliche Quelle|Requirement)" + NUMMER, Pattern.MULTILINE);
  private static final Pattern QUELLE_DA =
      Pattern.compile("^[ \\t]*(?:Fachliche Quelle|Requirement):", Pattern.MULTILINE);
  private static final Pattern PLAN_MODELL_DA =
      Pattern.compile("^[ \\t]*Plan-Model(?:l)?:", Pattern.MULTILINE);
  private static final List<Pattern> MODELL_ZEILEN =
      List.of(modellZeile("Plan-Model(?:l)?"), modellZeile("(?:Autor-Modell|Author-Model)"));
  private static final Pattern ERGEBNIS_NUMMER =
      Pattern.compile("(?:Issue[ \\t]+)?#(\\d{1,9})(?!\\d).*", Pattern.DOTALL);

  private Dokumentzeilen() {}

  /** Die Nummern der Zeilen {@code Plan:}, in Textfolge. */
  static List<Integer> planNummern(@Nullable String text) {
    return nummern(PLAN, text);
  }

  /** Die Nummern der Zeilen {@code Fachliche Quelle:}/{@code Requirement:}, in Textfolge. */
  static List<Integer> quellNummern(@Nullable String text) {
    return nummern(QUELLE, text);
  }

  /** Ob der Text eine Zeile {@code Fachliche Quelle:}/{@code Requirement:} trägt. */
  static boolean traegtFachlicheQuelle(@Nullable String text) {
    return text != null && QUELLE_DA.matcher(text).find();
  }

  /** Ob der Text eine Zeile {@code Plan-Modell:}/{@code Plan-Model:} trägt. */
  static boolean traegtPlanModell(@Nullable String text) {
    return text != null && PLAN_MODELL_DA.matcher(text).find();
  }

  /**
   * Das Modell, das das Dokument geschrieben hat: erst {@code Plan-Modell:}, dann {@code
   * Autor-Modell:}.
   */
  static Optional<String> modell(@Nullable String text) {
    if (text == null) {
      return Optional.empty();
    }
    return MODELL_ZEILEN.stream()
        .map(muster -> muster.matcher(text))
        .filter(Matcher::find)
        .map(m -> m.group(1))
        .findFirst();
  }

  /** Die Kartennummer, auf die das Ergebnis einer Idee verweist, etwa {@code Issue #12}. */
  static Optional<Integer> ergebnisNummer(String ergebnis) {
    Matcher m = ERGEBNIS_NUMMER.matcher(ergebnis);
    return m.matches() ? Optional.of(Integer.parseInt(m.group(1))) : Optional.empty();
  }

  private static List<Integer> nummern(Pattern muster, @Nullable String text) {
    List<Integer> nummern = new ArrayList<>();
    if (text != null) {
      Matcher m = muster.matcher(text);
      while (m.find()) {
        nummern.add(Integer.parseInt(m.group(1)));
      }
    }
    return nummern;
  }

  private static Pattern modellZeile(String name) {
    return Pattern.compile(
        "^[ \\t]*" + name + ":[ \\t]*(\\S[^\\r\\n]*?)[ \\t]*$", Pattern.MULTILINE);
  }
}
