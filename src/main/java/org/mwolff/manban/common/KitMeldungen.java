package org.mwolff.manban.common;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Register der festen Meldungen des claude-workflow-kits (Issue #2113, Plan #2108 E2, E3).
 *
 * <p>Herkunft: {@code MARKER}, {@code MARKER_ALTNAMEN}, {@code UEBERSCHRIFTEN} und {@code
 * MELDUNG_WERTE} aus {@code kit/board/namen.mjs}, Kit 6.1.3. Geführt wird, was die
 * Entscheidungskette liest, je Eintrag deutsch, englisch und frühere Wortlaute. Gelesen werden
 * immer alle Namen: Ein Projekt kann die Sprache wechseln, und alte Meldungen bleiben stehen
 * (Kriterium 16).
 *
 * <p>Frameworkfrei und ohne Modulabhängigkeit wie {@link KitNamen}.
 */
public final class KitMeldungen {

  /** Eine feste Zeile, verglichen mit ihrem Anfang. */
  public enum Zeile {
    PLAN_REVIEW_RUNDE("## Plan-Review, Runde", "## Plan-Review, Round"),
    FACHPLAN_REVIEW_RUNDE("## Fachplan-Review, Runde", "## Requirement-Review, Round"),
    ISSUE_REVIEW_RUNDE("## Issue-Review, Runde", "## Issue-Review, Round"),
    EINARBEITUNG_RUNDE("## Einarbeitung, Runde", "## Incorporation, Round"),
    REVIEWER("### Reviewer"),
    ABSCHLUSSBERICHT("## Abschlussbericht Issue #", "## Completion report issue #"),
    STAND_DES_VORHABENS("## Stand des Vorhabens", "## State of the project"),
    CODE_REVIEW(
        "## Code-Review (Schritt 6)",
        "## Code review (step 6)",
        "## Code-Review (Schritt 7)",
        "## Code review (step 7)"),
    UEBERTRAGUNG("## Übertragung auf den Hauptzweig", "## Push to main"),
    BUILD_NACHGETRAGEN("## Build-Dienst nachgetragen", "## Build service recorded"),
    VEROEFFENTLICHUNG("## Veröffentlichung", "## Release"),
    COMMIT("Commit:"),
    PRUEFLAUF("Prüflauf:", "Check run:"),
    UMGESETZT_VON("Umgesetzt von:", "Implemented by:"),
    KIT_STAND("Kit-Stand:", "Kit-Revision:"),
    BERICHT_LAUF("Bericht-Lauf:", "Report-Run:"),
    VERSUCH("Versuch:", "Attempt:"),
    BASIS("Basis:", "Base:"),
    WEG("Weg:", "Way:"),
    BUILD_DIENST("Build-Dienst:", "Build service:"),
    QUELLE("Quelle:", "Source:"),
    AUSGANG("Ausgang:", "Outcome:"),
    FREIGEGEBEN_VON("Freigegeben von:", "Approved by:"),
    AUSGEFUEHRT_VON("Ausgeführt von:", "Executed by:"),
    VERSION("Version:"),
    GEKLAERTER_STAND("## Geklärter Stand", "## Clarified state"),
    ERGEBNIS("Ergebnis:", "Result:");

    // List.of ist unveränderlich; das Prüfprogramm erkennt das am Typ List nicht.
    @SuppressWarnings("ImmutableEnumChecker")
    private final List<String> alleNamen;

    Zeile(String... namen) {
      this.alleNamen = List.of(namen);
    }

    /** Alle Namen der Zeile: deutsch, englisch, frühere Wortlaute. */
    public List<String> namen() {
      return alleNamen;
    }

    /**
     * Was hinter dem Anfang der Zeile steht, in ihrer Schreibweise; leer, wenn sie nicht so
     * beginnt. Führender Leerraum und Groß- und Kleinschreibung zählen nicht, wie bei {@code
     * leseMarker} im Kit.
     */
    public Optional<String> rest(@Nullable String zeile) {
      if (zeile == null) {
        return Optional.empty();
      }
      String roh = zeile.stripLeading();
      return alleNamen.stream()
          .filter(name -> roh.regionMatches(true, 0, name, 0, name.length()))
          .findFirst()
          .map(name -> roh.substring(name.length()));
    }
  }

  /** Ein fester Wert hinter einer Zeile einer Meldung. */
  public enum Wert {
    GRUEN("gruen", "green"),
    ROT("rot", "red"),
    KEINE("keine", "none"),
    AUSSTEHEND("ausstehend", "pending"),
    NICHT_ERFASST("nicht erfasst", "not recorded"),
    LOKAL("lokal", "local"),
    BUILD_DIENST("build-dienst", "build-service"),
    NOTFALL("notfall", "emergency"),
    VORBEREITET("vorbereitet", "prepared"),
    UEBERTRAGEN("übertragen", "pushed"),
    NICHT_UEBERTRAGEN("nicht übertragen", "not pushed"),
    PULL_REQUEST_ANGELEGT("Pull Request angelegt", "pull request opened"),
    NICHT_VEROEFFENTLICHT("nicht veröffentlicht", "not released"),
    PRUEFLAUF("Prüflauf", "check run");

    // List.of ist unveränderlich; das Prüfprogramm erkennt das am Typ List nicht.
    @SuppressWarnings("ImmutableEnumChecker")
    private final List<String> namen;

    Wert(String... namen) {
      this.namen = List.of(namen);
    }

    /** Ob der Text, ohne umgebenden Leerraum, einer der Namen dieses Werts ist. */
    public boolean bezeichnet(@Nullable String text) {
      return text != null && namen.contains(text.strip());
    }

    /** Der Wert zum Text in beliebiger Sprache, sonst leer. */
    public static Optional<Wert> von(@Nullable String text) {
      return Arrays.stream(values()).filter(wert -> wert.bezeichnet(text)).findFirst();
    }
  }

  /** Was hinter dem festen Anfang einer Kopfzeile stehen darf. */
  private enum Rest {
    NICHTS("\\s*"),
    RUNDE(" \\d{1,9}\\s*"),
    KARTENNUMMER("\\d{1,9}.*");

    private final Pattern muster;

    Rest(String ausdruck) {
      this.muster = Pattern.compile(ausdruck);
    }

    boolean passt(String rest) {
      return muster.matcher(rest).matches();
    }
  }

  private record Kopf(Zeile zeile, Rest erlaubt) {

    boolean erkennt(@Nullable String kopfzeile) {
      return zeile.rest(kopfzeile).filter(erlaubt::passt).isPresent();
    }
  }

  /** Die Art einer Meldung des Kits, erkennbar an ihrer ersten nichtleeren Zeile. */
  public enum Meldungsart {
    PLAN_REVIEW(new Kopf(Zeile.PLAN_REVIEW_RUNDE, Rest.RUNDE)),
    FACHPLAN_REVIEW(new Kopf(Zeile.FACHPLAN_REVIEW_RUNDE, Rest.RUNDE)),
    ISSUE_REVIEW(new Kopf(Zeile.ISSUE_REVIEW_RUNDE, Rest.RUNDE)),
    EINARBEITUNG(new Kopf(Zeile.EINARBEITUNG_RUNDE, Rest.RUNDE)),
    // Unbeaufsichtigt beginnt der Bericht des letzten Pakets mit dem Stand des Vorhabens.
    ABSCHLUSSBERICHT(
        new Kopf(Zeile.ABSCHLUSSBERICHT, Rest.KARTENNUMMER),
        new Kopf(Zeile.STAND_DES_VORHABENS, Rest.NICHTS)),
    CODE_REVIEW(new Kopf(Zeile.CODE_REVIEW, Rest.NICHTS)),
    UEBERTRAGUNG(new Kopf(Zeile.UEBERTRAGUNG, Rest.NICHTS)),
    BUILD_NACHTRAG(new Kopf(Zeile.BUILD_NACHGETRAGEN, Rest.NICHTS)),
    VEROEFFENTLICHUNG(new Kopf(Zeile.VEROEFFENTLICHUNG, Rest.NICHTS));

    // List.of ist unveränderlich; das Prüfprogramm erkennt das am Typ List nicht.
    @SuppressWarnings("ImmutableEnumChecker")
    private final List<Kopf> koepfe;

    Meldungsart(Kopf... koepfe) {
      this.koepfe = List.of(koepfe);
    }
  }

  private KitMeldungen() {}

  /**
   * Die Art der Meldung, deren Kopf diese Zeile ist; sonst leer. Ein Kopf ohne veränderlichen Rest
   * muss allein auf der Zeile stehen: {@code ## Release notes} ist keine Veröffentlichung.
   */
  public static Optional<Meldungsart> artDerKopfzeile(@Nullable String zeile) {
    return Arrays.stream(Meldungsart.values())
        .filter(art -> art.koepfe.stream().anyMatch(kopf -> kopf.erkennt(zeile)))
        .findFirst();
  }

  /** Alle festen Anfänge der Meldungsköpfe, wörtlich und in jeder Sprache. */
  public static List<String> festeAnfaenge() {
    return Arrays.stream(Meldungsart.values())
        .flatMap(art -> art.koepfe.stream())
        .flatMap(kopf -> kopf.zeile().namen().stream())
        .toList();
  }
}
