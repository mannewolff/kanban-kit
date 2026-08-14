package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.PruefbefundVertragMaterial.START_PROTOKOLL;
import static org.mwolff.manban.PruefbefundVertragMaterial.START_VORSCHLAG;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.PruefbefundVertragMaterial.Fall;
import org.mwolff.manban.common.TextLimits;

/**
 * Hält das Vertragsmaterial für Vorschlags- und Protokollblöcke (Fassung 1) widerspruchsfrei:
 * {@code docs/pruefbefund-vertrag-v1.md}, das Manifest und die kanonischen Testdatensätze müssen
 * dasselbe sagen (Issue #585).
 *
 * <p><strong>Dies ist bewusst kein Parser-Test.</strong> Die Grammatik wird hier nicht validiert —
 * ein Parser wäre Produktivcode und ist Gegenstand eines Folgepakets. Geprüft wird ausschließlich,
 * ob das Material vollständig und in sich widerspruchsfrei ist: Zu jeder Zeile der Fehlertabelle
 * gibt es einen Datensatz, die Kennungen der gültigen Datensätze halten die dokumentierten Regeln,
 * die Markerliste der Doku entspricht der abgestimmten Liste, und die Grenzdatensätze liegen exakt
 * an der Längengrenze.
 *
 * @see PruefbefundVertragMaterial Zugriff auf Manifest und Datensätze
 * @see PruefbefundVertragDoku Zugriff auf die normative Doku
 */
class PruefbefundVertragV1Test {

  /**
   * Die abschließende Liste der geschützten Kennzeichnungszeilen (Issue #585, Punkt 2). Sie steht
   * hier als Referenz, gegen die die Doku geprüft wird — nicht umgekehrt: Eine Liste, die sich aus
   * der Doku selbst ableitet, könnte nichts belegen.
   */
  private static final List<String> GESCHUETZTE_MARKER =
      List.of(
          "Autor-Modell:",
          "Plan-Modell:",
          "Fachliche Quelle:",
          "Pruefung:",
          "Fachplan-Review:",
          "Plan-Review:",
          "Issue-Review:");

  /** {@code Pruefung-Stand:} wird beim Schreiben neu berechnet und ist deshalb nicht geschützt. */
  private static final String NICHT_GESCHUETZT = "Pruefung-Stand:";

  private static final String GUELTIG = "GUELTIG";
  private static final String KEIN_BLOCK = "KEIN_VERTRAGSBLOCK";
  private static final String DUPLIKAT = "DUPLICATE_PROPOSAL_ID";
  private static final String UNBEKANNTE_KENNUNG = "UNKNOWN_PROPOSAL_ID";
  private static final String ZU_LANG = "COMMENT_TOO_LONG";

  private static final Pattern COMMIT_HASH = Pattern.compile("\\b[0-9a-f]{40}\\b");

  // --- Fehlertabelle und Manifest --------------------------------------------------------------

  @Test
  void jedeZeileDerFehlertabelleHatMindestensEinenTestdatensatz() {
    Set<String> abgedeckt = new HashSet<>();
    for (Fall fall : PruefbefundVertragMaterial.faelle()) {
      abgedeckt.add(fall.erwartet());
    }

    List<String> ohneDatensatz =
        PruefbefundVertragDoku.fehlercodes().stream()
            .filter(code -> !abgedeckt.contains(code))
            .toList();

    assertThat(ohneDatensatz).as("Fehlercodes der Doku ohne Testdatensatz").isEmpty();
  }

  @Test
  void jederErwarteteWertDesManifestsIstInDerDokuBelegt() {
    Set<String> erlaubt = new LinkedHashSet<>(PruefbefundVertragDoku.fehlercodes());
    erlaubt.add(GUELTIG);
    erlaubt.add(KEIN_BLOCK);

    List<String> unbekannt =
        PruefbefundVertragMaterial.faelle().stream()
            .map(Fall::erwartet)
            .filter(erwartet -> !erlaubt.contains(erwartet))
            .distinct()
            .toList();

    assertThat(unbekannt).as("Erwartungswerte ohne Zeile in der Fehlertabelle").isEmpty();
  }

  @Test
  void dieFehlertabelleNenntDieSiebenImIssueFestgelegtenCodes() {
    assertThat(PruefbefundVertragDoku.fehlercodes())
        .as("normativ geforderte Fehlercodes")
        .contains(
            "UNKNOWN_FORMAT_VERSION",
            DUPLIKAT,
            "TARGET_MISSING",
            "TARGET_NOT_FOUND",
            "TARGET_AMBIGUOUS",
            "EXPECTED_TEXT_MISMATCH",
            ZU_LANG);
  }

  @Test
  void jederManifestEintragVerweistAufVorhandeneDateien() {
    List<String> vorhanden = PruefbefundVertragMaterial.dateinamenImVerzeichnis();

    List<String> fehlend = new ArrayList<>();
    for (Fall fall : PruefbefundVertragMaterial.faelle()) {
      if (!vorhanden.contains(fall.datei())) {
        fehlend.add(fall.datei());
      }
      if (fall.zieltext() != null && !vorhanden.contains(fall.zieltext())) {
        fehlend.add(fall.zieltext());
      }
    }
    for (String zieltext : PruefbefundVertragMaterial.zieltexte()) {
      if (!vorhanden.contains(zieltext)) {
        fehlend.add(zieltext);
      }
    }

    assertThat(fehlend).as("im Manifest genannte, aber fehlende Dateien").isEmpty();
  }

  @Test
  void keinTestdatensatzLiegtUnbenanntImVerzeichnis() {
    Set<String> genannt = new LinkedHashSet<>(PruefbefundVertragMaterial.zieltexte());
    genannt.add(PruefbefundVertragMaterial.MANIFEST_PFAD.getFileName().toString());
    for (Fall fall : PruefbefundVertragMaterial.faelle()) {
      genannt.add(fall.datei());
      if (fall.zieltext() != null) {
        genannt.add(fall.zieltext());
      }
    }

    List<String> verwaist =
        PruefbefundVertragMaterial.dateinamenImVerzeichnis().stream()
            .filter(name -> !genannt.contains(name))
            .toList();

    assertThat(verwaist).as("Dateien ohne Eintrag im Manifest").isEmpty();
  }

  // --- Kennungen -------------------------------------------------------------------------------

  @Test
  void vorschlagskennungenSindJeLaufEindeutig() {
    Map<String, Set<String>> gesehen = new LinkedHashMap<>();
    List<String> verletzungen = new ArrayList<>();
    for (Fall fall : PruefbefundVertragMaterial.faelle()) {
      JsonNode rumpf = PruefbefundVertragMaterial.rumpfOderNull(fall, START_VORSCHLAG);
      if (rumpf == null || DUPLIKAT.equals(fall.erwartet())) {
        continue;
      }
      String lauf = PruefbefundVertragMaterial.lauf(rumpf);
      gesehen.putIfAbsent(lauf, new HashSet<>());
      Set<String> kennungen = gesehen.get(lauf);
      for (String kennung : PruefbefundVertragMaterial.kennungen(rumpf)) {
        if (!kennungen.add(kennung)) {
          verletzungen.add(fall.datei() + ": " + kennung);
        }
      }
    }

    assertThat(verletzungen).as("doppelte Vorschlagskennung innerhalb eines Laufs").isEmpty();
  }

  @Test
  void derDuplikatDatensatzVerletztGenauSeineRegel() {
    Fall fall = PruefbefundVertragMaterial.fallMit(DUPLIKAT);
    JsonNode rumpf = PruefbefundVertragMaterial.rumpfOderNull(fall, START_VORSCHLAG);

    assertThat(rumpf).as("Vorschlagsblock in %s", fall.datei()).isNotNull();
    List<String> kennungen = PruefbefundVertragMaterial.kennungen(rumpf);

    assertThat(kennungen)
        .as("Kennungen im Duplikat-Datensatz")
        .hasSizeGreaterThan(new HashSet<>(kennungen).size());
  }

  /**
   * Vorschläge und Protokoll desselben Laufs liegen im Regelfall in <em>verschiedenen</em>
   * Kommentaren — genau dafür gibt es die Lauf-Kennung. Die bekannten Kennungen werden deshalb über
   * alle Datensätze hinweg je Lauf gesammelt, nicht je Datei.
   */
  @Test
  void protokolleintraegeVerweisenNurAufKennungenDesselbenLaufs() {
    Map<String, Set<String>> jeLauf = PruefbefundVertragMaterial.vorschlagskennungenJeLauf();
    Map<String, Set<String>> entschieden = new LinkedHashMap<>();
    List<String> verletzungen = new ArrayList<>();
    for (Fall fall : PruefbefundVertragMaterial.faelle()) {
      JsonNode protokoll = PruefbefundVertragMaterial.rumpfOderNull(fall, START_PROTOKOLL);
      if (protokoll == null || UNBEKANNTE_KENNUNG.equals(fall.erwartet())) {
        continue;
      }
      String lauf = PruefbefundVertragMaterial.lauf(protokoll);
      Set<String> bekannt = jeLauf.getOrDefault(lauf, Set.of());
      entschieden.putIfAbsent(lauf, new HashSet<>());
      Set<String> bereits = entschieden.get(lauf);
      for (String kennung : PruefbefundVertragMaterial.protokollierteKennungen(protokoll)) {
        if (!bekannt.contains(kennung)) {
          verletzungen.add(fall.datei() + ": " + kennung + " ohne Vorschlag desselben Laufs");
        }
        if (!bereits.add(kennung)) {
          verletzungen.add(
              fall.datei() + ": " + kennung + " mehrfach im selben Lauf protokolliert");
        }
      }
    }

    assertThat(verletzungen).as("Protokolleinträge ohne passenden Vorschlag").isEmpty();
  }

  @Test
  void derUnbekannteKennungDatensatzVerletztGenauSeineRegel() {
    Fall fall = PruefbefundVertragMaterial.fallMit(UNBEKANNTE_KENNUNG);
    JsonNode protokoll = PruefbefundVertragMaterial.rumpfOderNull(fall, START_PROTOKOLL);

    assertThat(protokoll).as("Protokollblock in %s", fall.datei()).isNotNull();

    String lauf = PruefbefundVertragMaterial.lauf(protokoll);
    Set<String> bekannt =
        PruefbefundVertragMaterial.vorschlagskennungenJeLauf().getOrDefault(lauf, Set.of());
    assertThat(bekannt)
        .as("Der Lauf %s ist bekannt — allein die Kennung ist es nicht", lauf)
        .isNotEmpty();

    List<String> unbekannt =
        PruefbefundVertragMaterial.protokollierteKennungen(protokoll).stream()
            .filter(kennung -> !bekannt.contains(kennung))
            .toList();

    assertThat(unbekannt).as("nicht auflösbare Kennungen im Protokoll").isNotEmpty();
  }

  // --- Geschützte Kennzeichnungszeilen ---------------------------------------------------------

  @Test
  void dieDokuNenntGenauDieSiebenGeschuetztenKennzeichnungszeilen() {
    assertThat(PruefbefundVertragDoku.geschuetzteMarker())
        .as("normativ geschützte Kennzeichnungszeilen in %s", PruefbefundVertragDoku.PFAD)
        .containsExactlyInAnyOrderElementsOf(GESCHUETZTE_MARKER);
  }

  @Test
  void pruefungStandStehtNichtInDerGeschuetztenListe() {
    assertThat(PruefbefundVertragDoku.geschuetzteMarker())
        .as("%s wird beim Schreiben neu berechnet und darf nicht geschützt sein", NICHT_GESCHUETZT)
        .doesNotContain(NICHT_GESCHUETZT);
    assertThat(PruefbefundVertragDoku.text())
        .as("Sonderregel für %s", NICHT_GESCHUETZT)
        .contains(NICHT_GESCHUETZT);
  }

  @Test
  void dieDokuNenntDenCommitHashDerQuelle() {
    assertThat(COMMIT_HASH.matcher(PruefbefundVertragDoku.text()).find())
        .as("vollständiger Commit-Hash des claude-workflow-kit in %s", PruefbefundVertragDoku.PFAD)
        .isTrue();
  }

  @Test
  void dieDokuFuehrtDieErkennungOhneDatenbankTypisierungAlsEigenenAbschnitt() {
    assertThat(PruefbefundVertragDoku.ueberschriften())
        .as("Abschnitt zur Erkennbarkeit allein aus dem Kommentartext")
        .anyMatch(ueberschrift -> ueberschrift.contains("ohne Datenbank-Typisierung"));
  }

  // --- Längengrenze ----------------------------------------------------------------------------

  @Test
  void derGrenzdatensatzLiegtExaktAufDerObergrenze() {
    Fall fall = PruefbefundVertragMaterial.fallMitDatei("grenze-50000.md");

    assertThat(PruefbefundVertragMaterial.inhalt(fall.datei()).length())
        .as("serialisierter Kommentar in %s", fall.datei())
        .isEqualTo(TextLimits.MAX_TEXT);
    assertThat(fall.erwartet()).as("Erwartung für den Grenzfall").isEqualTo(GUELTIG);
  }

  @Test
  void derUeberlangeDatensatzLiegtGenauEineEinheitDarueber() {
    Fall fall = PruefbefundVertragMaterial.fallMit(ZU_LANG);

    assertThat(PruefbefundVertragMaterial.inhalt(fall.datei()).length())
        .as("serialisierter Kommentar in %s", fall.datei())
        .isEqualTo(TextLimits.MAX_TEXT + 1);
  }

  // --- Rückwärtskompatibilität -----------------------------------------------------------------

  @Test
  void historischerFliesstextUndKennzeichnungImCodeblockErgebenKeinenVertragsblock() {
    List<Fall> ohneBlock =
        PruefbefundVertragMaterial.faelle().stream()
            .filter(fall -> KEIN_BLOCK.equals(fall.erwartet()))
            .toList();

    assertThat(ohneBlock).as("Datensätze ohne Vertragsblock").hasSizeGreaterThanOrEqualTo(2);
    for (Fall fall : ohneBlock) {
      assertThat(PruefbefundVertragMaterial.rumpfOderNull(fall, START_VORSCHLAG))
          .as("Vorschlagsblock in %s", fall.datei())
          .isNull();
      assertThat(PruefbefundVertragMaterial.rumpfOderNull(fall, START_PROTOKOLL))
          .as("Protokollblock in %s", fall.datei())
          .isNull();
    }
    assertThat(PruefbefundVertragMaterial.inhalt("kein-block-kennzeichnung-im-codeblock.md"))
        .as("die Kennzeichnung steht im Datensatz, zählt dort aber nicht")
        .contains(START_VORSCHLAG);
  }
}
