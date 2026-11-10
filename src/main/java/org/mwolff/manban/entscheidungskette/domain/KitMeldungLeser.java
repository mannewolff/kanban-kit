package org.mwolff.manban.entscheidungskette.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.common.KitMeldungen.Wert;
import org.mwolff.manban.common.KitMeldungen.Zeile;

/**
 * Liest die Meldungen des Kits aus Kommentaren und die Zeile {@code Ergebnis:} aus Ideen (Issue
 * #2113, Plan #2108 E3, E9), ohne Datenbank und ohne Uhr.
 *
 * <p>Eine Meldung ist nur, was über einen Projekt-Zugang geschrieben wurde und mit einem festen
 * Kopf beginnt. Der gleiche Wortlaut aus der Oberfläche oder ohne bekannte Herkunft begründet
 * nichts.
 */
public final class KitMeldungLeser {

  private static final String HERKUNFT_KIT = "TOKEN";
  private static final Pattern REVIEWER_KOPF = Pattern.compile("\\s+(\\d{1,9})\\s*:(.*)");
  private static final Pattern ABSCHNITT = Pattern.compile("##[ \\t]+\\S");
  private static final List<String> ZAEUNE = List.of("```", "~~~");
  private static final Set<Wert> WEGE =
      Set.of(Wert.LOKAL, Wert.BUILD_DIENST, Wert.NOTFALL, Wert.VORBEREITET);

  // Der Schluss des Berichts, wie ihn das Kit anhängt: Eine solche Zeile im Text davor zählt nicht.
  private static final List<Zeile> SCHLUSSZEILEN =
      List.of(
          Zeile.COMMIT, Zeile.PRUEFLAUF, Zeile.UMGESETZT_VON, Zeile.KIT_STAND, Zeile.BERICHT_LAUF);

  private KitMeldungLeser() {}

  /**
   * Die Meldung in diesem Kommentar.
   *
   * @param herkunft Herkunft beim Anlegen; nur {@code TOKEN} kann eine Meldung des Kits sein
   * @param text der Kommentartext
   * @return die Meldung, leer bei jedem anderen Kommentar
   */
  public static Optional<KitMeldung> lies(@Nullable String herkunft, String text) {
    if (!HERKUNFT_KIT.equals(herkunft)) {
      return Optional.empty();
    }
    List<String> zeilen = text.lines().toList();
    String kopf = zeilen.stream().filter(zeile -> !zeile.isBlank()).findFirst().orElse("");
    return KitMeldungen.artDerKopfzeile(kopf).map(art -> meldung(art, kopf, zeilen));
  }

  /**
   * Der Wert der Zeile {@code Ergebnis:} unter {@code ## Geklärter Stand}, wörtlich; die letzte
   * gilt, wie beim Kit. Was in einem Code-Zaun steht, zählt nicht.
   */
  public static Optional<String> ideeErgebnis(String body) {
    boolean imStand = false;
    String ergebnis = null;
    for (String zeile : ohneZaeune(body.lines().toList())) {
      if (ABSCHNITT.matcher(zeile).lookingAt()) {
        imStand = Zeile.GEKLAERTER_STAND.rest(zeile).filter(String::isBlank).isPresent();
      } else if (imStand) {
        ergebnis = Zeile.ERGEBNIS.rest(zeile).map(String::strip).map(Angabe::text).orElse(ergebnis);
      }
    }
    return Optional.ofNullable(ergebnis);
  }

  private static List<String> ohneZaeune(List<String> zeilen) {
    List<String> draussen = new ArrayList<>();
    String offen = null;
    for (String zeile : zeilen) {
      String zaun = ZAEUNE.stream().filter(zeile.strip()::startsWith).findFirst().orElse(null);
      if (offen == null && zaun == null) {
        draussen.add(zeile);
      } else if (offen == null) {
        offen = zaun;
      } else if (offen.equals(zaun)) {
        offen = null;
      }
    }
    return draussen;
  }

  private static KitMeldung meldung(Meldungsart art, String kopf, List<String> zeilen) {
    return switch (art) {
      case PLAN_REVIEW, FACHPLAN_REVIEW, ISSUE_REVIEW ->
          new Pruefrunde(art, runde(kopf), reviewer(zeilen), Fundzahlen.aus(zeilen));
      case EINARBEITUNG -> new Einarbeitung(runde(kopf));
      case ABSCHLUSSBERICHT -> abschlussbericht(schluss(zeilen));
      case CODE_REVIEW -> new CodeReview(Fundzahlen.aus(zeilen));
      case UEBERTRAGUNG -> uebertragung(zeilen);
      case BUILD_NACHTRAG ->
          new BuildNachtrag(
              wert(zeilen, Zeile.VERSUCH),
              wert(zeilen, Zeile.COMMIT),
              angabe(zeilen, Zeile.BUILD_DIENST),
              wert(zeilen, Zeile.QUELLE));
      case VEROEFFENTLICHUNG -> veroeffentlichung(zeilen);
    };
  }

  /** Die Zahl am Ende eines Kopfs, dessen Form {@link KitMeldungen} schon geprüft hat. */
  private static int runde(String kopf) {
    String gestutzt = kopf.strip();
    return Integer.parseInt(gestutzt.substring(gestutzt.lastIndexOf(' ') + 1));
  }

  private static List<Reviewer> reviewer(List<String> zeilen) {
    List<Integer> koepfe =
        IntStream.range(0, zeilen.size())
            .filter(i -> reviewerKopf(zeilen.get(i)).isPresent())
            .boxed()
            .toList();
    return IntStream.range(0, koepfe.size())
        .mapToObj(
            k -> {
              int beginn = koepfe.get(k);
              int ende = k + 1 < koepfe.size() ? koepfe.get(k + 1) : zeilen.size();
              Matcher kopf = reviewerKopf(zeilen.get(beginn)).orElseThrow();
              return einReviewer(kopf, zeilen.subList(beginn + 1, ende));
            })
        .toList();
  }

  private static Optional<Matcher> reviewerKopf(String zeile) {
    return Zeile.REVIEWER.rest(zeile).map(REVIEWER_KOPF::matcher).filter(Matcher::matches);
  }

  private static Reviewer einReviewer(Matcher kopf, List<String> block) {
    String angaben = kopf.group(2);
    int komma = angaben.indexOf(',');
    String rolle = komma < 0 ? angaben : angaben.substring(0, komma);
    String modell = komma < 0 ? "" : angaben.substring(komma + 1);
    return new Reviewer(
        Integer.parseInt(kopf.group(1)),
        Angabe.text(rolle.strip()),
        Angabe.text(modell.strip()),
        Fundzahlen.aus(block));
  }

  /**
   * Die Zeilen des festen Schlusses: von hinten, solange eine Zeile leer oder eine von ihnen ist.
   */
  private static List<String> schluss(List<String> zeilen) {
    int beginn = zeilen.size();
    for (int i = zeilen.size() - 1; i >= 0; i--) {
      String zeile = zeilen.get(i);
      if (!zeile.isBlank()) {
        if (SCHLUSSZEILEN.stream().noneMatch(s -> s.rest(zeile).isPresent())) {
          break;
        }
        beginn = i;
      }
    }
    return zeilen.subList(beginn, zeilen.size());
  }

  private static Abschlussbericht abschlussbericht(List<String> schluss) {
    return new Abschlussbericht(
        wert(schluss, Zeile.COMMIT),
        angabe(schluss, Zeile.PRUEFLAUF),
        wert(schluss, Zeile.UMGESETZT_VON));
  }

  private static Uebertragung uebertragung(List<String> zeilen) {
    return new Uebertragung(
        wert(zeilen, Zeile.VERSUCH),
        wert(zeilen, Zeile.COMMIT),
        wert(zeilen, Zeile.BASIS),
        Wert.von(wert(zeilen, Zeile.WEG)).filter(WEGE::contains).orElse(null),
        angabe(zeilen, Zeile.PRUEFLAUF),
        angabe(zeilen, Zeile.BUILD_DIENST),
        wert(zeilen, Zeile.QUELLE),
        wert(zeilen, Zeile.AUSGANG),
        angabe(zeilen, Zeile.FREIGEGEBEN_VON),
        angabe(zeilen, Zeile.AUSGEFUEHRT_VON));
  }

  private static Veroeffentlichung veroeffentlichung(List<String> zeilen) {
    return new Veroeffentlichung(
        wert(zeilen, Zeile.VERSUCH),
        wert(zeilen, Zeile.VERSION),
        wert(zeilen, Zeile.COMMIT),
        wert(zeilen, Zeile.BASIS),
        wert(zeilen, Zeile.AUSGANG),
        wert(zeilen, Zeile.QUELLE),
        angabe(zeilen, Zeile.FREIGEGEBEN_VON),
        angabe(zeilen, Zeile.AUSGEFUEHRT_VON));
  }

  private static @Nullable String wert(List<String> zeilen, Zeile zeile) {
    return Angabe.text(roh(zeilen, zeile));
  }

  private static Angabe angabe(List<String> zeilen, Zeile zeile) {
    return Angabe.aus(roh(zeilen, zeile));
  }

  private static @Nullable String roh(List<String> zeilen, Zeile zeile) {
    return zeilen.stream()
        .map(zeile::rest)
        .flatMap(Optional::stream)
        .map(String::strip)
        .findFirst()
        .orElse(null);
  }
}
