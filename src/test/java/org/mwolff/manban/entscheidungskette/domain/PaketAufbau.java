package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.AGENT;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.FACHPLAN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PAKET;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PLAN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.TOKEN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.ZUGANG;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.abschnitt;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.um;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.vorhanden;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.LabelEreignis;

/** Eingaben und Abfragen für die Tests der Glieder am Paket (Issue #2116). */
final class PaketAufbau {

  static final long P = 3;
  static final String BERICHT =
      """
      ## Abschlussbericht Issue #30

      ### Änderungen
      - etwas

      Commit: abc1234
      Prüflauf: gruen, 2026-11-10T09:00:00Z
      Umgesetzt von: claude-opus-5-5
      """;
  static final String CODE_REVIEW = "## Code-Review (Schritt 6)\n";

  static KettenAufbau paket() {
    return KettenAufbau.an(PAKET).vorfahr(vorhanden(PLAN)).vorfahr(vorhanden(FACHPLAN));
  }

  static List<Glied> glieder(Entscheidungskette kette, Gliedart art) {
    return abschnitt(kette, Dokumentart.PAKET).glieder().stream()
        .filter(g -> g.art() == art)
        .toList();
  }

  static Glied einziges(Entscheidungskette kette, Gliedart art) {
    List<Glied> alle = glieder(kette, art);
    assertThat(alle).hasSize(1);
    return alle.getFirst();
  }

  static LabelEreignis label(
      long kartenId, String name, Instant zeit, @Nullable Long person, @Nullable String herkunft) {
    return new LabelEreignis(
        kartenId, name, zeit, person, herkunft, TOKEN.equals(herkunft) ? ZUGANG : null);
  }

  static Kommentar vonHand(long id, String kopf, Instant zeit) {
    return new Kommentar(id, P, "von Hand umgeschrieben", zeit, TOKEN, ZUGANG, AGENT, um(99), kopf);
  }

  static String uebertragung(String versuch, String weg, String build, String ausgang) {
    return """
        ## Übertragung auf den Hauptzweig

        Versuch: %s
        Commit: c-%s
        Basis: b-%s
        Weg: %s
        Prüflauf: gruen, 2026-11-10T09:00:00Z
        Build-Dienst: %s
        Quelle: Prüflauf 2026-11-10T09:00:00Z
        Ausgang: %s
        Freigegeben von: Erika Muster, 2026-11-10T10:00:00Z
        Ausgeführt von: claude-opus-5-5, 2026-11-10T11:00:00Z
        """
        .formatted(versuch, versuch, versuch, weg, build, ausgang);
  }

  static String nachtrag(String zeilen) {
    return "## Build-Dienst nachgetragen\n\n" + zeilen;
  }

  static String veroeffentlichung(String version, String ausgang) {
    return """
        ## Veröffentlichung

        Versuch: v-%s
        Version: %s
        Commit: c-%s
        Basis: b-%s
        Ausgang: %s
        Quelle: https://example.org/pr/1
        Freigegeben von: Erika Muster, 2026-11-10T12:00:00Z
        Ausgeführt von: claude-opus-5-5, 2026-11-10T12:30:00Z
        """
        .formatted(version, version, version, version, ausgang);
  }

  private PaketAufbau() {}
}
