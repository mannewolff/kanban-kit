package org.mwolff.manban.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.common.KitNamen.Art;
import org.mwolff.manban.common.KitNamen.Eintrag;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.common.KitNamen.Schutz;

/** Namen-Register des Kits: Sachen, Schutz, Titel-Präfixe und Namensraum (Issue #1584). */
class KitNamenTest {

  /** Die Tabelle aus Issue #1584 wörtlich: Sache, deutsch, englisch ({@code null} = keiner). */
  private static final List<Zeile> TABELLE =
      List.of(
          new Zeile(Sache.DURCHZIEHEN, "kit:durchziehen", "kit:carry-through", Schutz.KEINER),
          new Zeile(
              Sache.GESCHUETZT, "kit:geschuetzt", "kit:protected", Schutz.NUR_MENSCH_NIMMT_AB),
          new Zeile(Sache.KLAEREN, "kit:klaeren", "kit:clarify", Schutz.NUR_MENSCH_NIMMT_AB),
          new Zeile(Sache.GEKLAERT, "kit:geklaert", "kit:clarified", Schutz.KEINER),
          new Zeile(Sache.NIGHT, "kit:night", null, Schutz.NUR_MENSCH_SETZT),
          new Zeile(Sache.NIGHTRUN, "kit:nightrun", null, Schutz.NUR_MENSCH_SETZT),
          new Zeile(Sache.PRUEFEN, "kit:pruefen", "kit:check", Schutz.KEINER),
          new Zeile(Sache.NIGHTPLAN, "kit:nightplan", null, Schutz.KEINER),
          new Zeile(Sache.NIGHTREVIEW, "kit:nightreview", null, Schutz.KEINER),
          new Zeile(Sache.NIGHTISSUES, "kit:nightissues", null, Schutz.KEINER),
          new Zeile(Sache.LAUF_ABGEBROCHEN, "lauf:abgebrochen", "run:aborted", Schutz.KEINER),
          new Zeile(Sache.LAUF_LAEUFT, "lauf:laeuft", "run:running", Schutz.KEINER),
          new Zeile(Sache.LAUF_WARTET, "lauf:wartet", "run:waiting", Schutz.KEINER),
          new Zeile(Sache.REVIEW_OFFEN, "review:offen", null, Schutz.KEINER),
          new Zeile(Sache.REVIEW_FERTIG, "review:fertig", "review:done", Schutz.KEINER),
          new Zeile(Sache.ZIEL_PLAN, "ziel:plan", "goal:plan", Schutz.KEINER),
          new Zeile(Sache.ZIEL_PAKETE, "ziel:pakete", "goal:packages", Schutz.KEINER),
          new Zeile(Sache.ZIEL_UMSETZUNG, "ziel:umsetzung", "goal:implementation", Schutz.KEINER),
          new Zeile(
              Sache.ZIEL_PUSH_VORBEREITET,
              "ziel:push-vorbereitet",
              "goal:push-prepared",
              Schutz.KEINER),
          new Zeile(Sache.PLANREVIEW_1, "planreview:1", null, Schutz.KEINER),
          new Zeile(Sache.PLANREVIEW_2, "planreview:2", null, Schutz.KEINER));

  private record Zeile(Sache sache, String deutsch, @Nullable String englisch, Schutz schutz) {}

  @Test
  void standard_enthaeltDieTabelleWoertlichInIhrerReihenfolge() {
    // Given: die Einträge der Tabelle, je Zeile erst deutsch, dann englisch.
    List<Eintrag> erwartet = new ArrayList<>();
    for (Zeile zeile : TABELLE) {
      erwartet.add(new Eintrag(zeile.sache(), zeile.deutsch()));
      if (zeile.englisch() != null) {
        erwartet.add(new Eintrag(zeile.sache(), zeile.englisch()));
      }
    }

    // When / Then
    assertThat(KitNamen.STANDARD.eintraege()).containsExactlyElementsOf(erwartet);
    assertThat(Sache.values())
        .containsExactlyElementsOf(TABELLE.stream().map(Zeile::sache).toList());
  }

  @Test
  void standard_nenntJeSacheDenSchutzDerTabelle() {
    for (Zeile zeile : TABELLE) {
      assertThat(zeile.sache().schutz()).as(zeile.sache().name()).isEqualTo(zeile.schutz());
    }
  }

  @Test
  void standard_hatEinundzwanzigSachen() {
    assertThat(Sache.values()).hasSize(21);
  }

  @Test
  void geklaert_traegtBeideNamen_undStehtHinterKlaeren() {
    // Issue #1614: das Gegenstück zu kit:klaeren, wenn eine offene Frage beantwortet ist.
    assertThat(KitNamen.STANDARD.sache("kit:geklaert")).contains(Sache.GEKLAERT);
    assertThat(KitNamen.STANDARD.sache("kit:clarified")).contains(Sache.GEKLAERT);
    assertThat(KitNamen.STANDARD.namen(Sache.GEKLAERT))
        .containsExactly("kit:geklaert", "kit:clarified");
    assertThat(Sache.GEKLAERT.schutz()).isEqualTo(Schutz.KEINER);
    List<String> namen = KitNamen.STANDARD.alleNamen();
    assertThat(namen.indexOf("kit:geklaert")).isEqualTo(namen.indexOf("kit:clarify") + 1);
  }

  @Test
  void namen_liefertDenDeutschenNamenZuerst() {
    for (Zeile zeile : TABELLE) {
      List<String> erwartet =
          zeile.englisch() == null
              ? List.of(zeile.deutsch())
              : List.of(zeile.deutsch(), zeile.englisch());
      assertThat(KitNamen.STANDARD.namen(zeile.sache()))
          .as(zeile.sache().name())
          .containsExactlyElementsOf(erwartet);
    }
  }

  @Test
  void namen_sachenOhneGegenstueckHabenGenauEinenNamen() {
    List<Sache> ohneGegenstueck =
        List.of(
            Sache.NIGHT,
            Sache.NIGHTRUN,
            Sache.NIGHTPLAN,
            Sache.NIGHTREVIEW,
            Sache.NIGHTISSUES,
            Sache.REVIEW_OFFEN,
            Sache.PLANREVIEW_1,
            Sache.PLANREVIEW_2);
    for (Sache sache : ohneGegenstueck) {
      assertThat(KitNamen.STANDARD.namen(sache)).as(sache.name()).hasSize(1);
    }
    assertThat(Arrays.stream(Sache.values()).filter(s -> !ohneGegenstueck.contains(s)))
        .allSatisfy(s -> assertThat(KitNamen.STANDARD.namen(s)).hasSize(2));
  }

  @Test
  void namen_liefertLeereListe_fuerSacheOhneEintrag() {
    // Given: ein Register, das nur eine Sache kennt.
    KitNamen register = KitNamen.aus(List.of(new Eintrag(Sache.KLAEREN, "kit:klaeren")));

    // When / Then
    assertThat(register.namen(Sache.NIGHT)).isEmpty();
  }

  @Test
  void alleNamen_liefertDieNamenInRegisterreihenfolge() {
    assertThat(KitNamen.STANDARD.alleNamen())
        .containsExactlyElementsOf(
            KitNamen.STANDARD.eintraege().stream().map(Eintrag::name).toList())
        .hasSize(34)
        .startsWith("kit:durchziehen", "kit:carry-through", "kit:geschuetzt")
        .endsWith("planreview:1", "planreview:2");
  }

  @Test
  void sache_findetDeutscheUndEnglischeNamen() {
    for (Zeile zeile : TABELLE) {
      assertThat(KitNamen.STANDARD.sache(zeile.deutsch())).contains(zeile.sache());
      if (zeile.englisch() != null) {
        assertThat(KitNamen.STANDARD.sache(zeile.englisch())).contains(zeile.sache());
      }
    }
  }

  @Test
  void sache_trimmtDenNamen() {
    assertThat(KitNamen.STANDARD.sache("  kit:clarify \t")).contains(Sache.KLAEREN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"KIT:klaeren", "Kit:Clarify", "kit:klaeren-neu", "", "   ", "klaeren"})
  void sache_liefertLeer_fuerAndereSchreibungOderUnbekanntenNamen(String name) {
    assertThat(KitNamen.STANDARD.sache(name)).isEmpty();
  }

  @Test
  void sache_liefertLeer_fuerNull() {
    assertThat(KitNamen.STANDARD.sache(null)).isEmpty();
  }

  @Test
  void aus_lehntEinenNamenFuerZweiSachenAb() {
    // Given: derselbe Name für zwei Sachen wäre mehrdeutig.
    List<Eintrag> eintraege =
        List.of(new Eintrag(Sache.KLAEREN, "kit:x"), new Eintrag(Sache.NIGHT, "kit:x"));

    // When / Then
    assertThatThrownBy(() -> KitNamen.aus(eintraege))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kit:x");
  }

  @Test
  void eintraege_sindUnveraenderlich() {
    assertThatThrownBy(() -> KitNamen.STANDARD.eintraege().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> KitNamen.STANDARD.alleNamen().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> KitNamen.STANDARD.namen(Sache.KLAEREN).clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> KitNamen.titelPraefixe().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // --- Namensraum (E7) -------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {
        "kit:night",
        "kit:unbekannt",
        "KIT:night",
        "lauf:x",
        "ziel:x",
        "review:offen",
        "Review: UX",
        "planreview:3",
        "run:x",
        "goal:x",
        "GOAL:Plan",
        "  kit:x"
      })
  void imNamensraum_erkenntJedesPraefixOhneRuecksichtAufSchreibung(String name) {
    assertThat(KitNamen.imNamensraum(name)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"bug", "kit", "kitx:y", "Frontend: kit:night", "", "rev:x", "plan:x"})
  void imNamensraum_lehntNamenAusserhalbAb(String name) {
    assertThat(KitNamen.imNamensraum(name)).isFalse();
  }

  @Test
  void imNamensraum_lehntNullAb() {
    assertThat(KitNamen.imNamensraum(null)).isFalse();
  }

  @Test
  void namensraumPraefixe_nenntDieSiebenPraefixe() {
    assertThat(KitNamen.namensraumPraefixe())
        .containsExactly("kit:", "lauf:", "ziel:", "review:", "planreview:", "run:", "goal:");
  }

  // --- Titel-Präfixe (E2) ----------------------------------------------------------------------

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "[Fachlich] Neue Anforderung | FACHLICH",
        "[Requirement] New requirement | FACHLICH",
        "[Idee] Etwas | IDEE",
        "[Idea] Something | IDEE",
        "[Mensch] Schritt | MENSCH",
        "[Human] Step | MENSCH",
        "[Plan] Umbau | PLAN",
        "[Task] Kleinigkeit | TASK",
        "[fachlich] klein | FACHLICH",
        "[REQUIREMENT] laut | FACHLICH",
        "[iDeA] gemischt | IDEE",
        "[HUMAN] x | MENSCH",
        "[plan] x | PLAN",
        "[TASK] x | TASK",
        "[Task]ohne Leerraum | TASK"
      })
  void artAusTitel_erkenntDasPraefixInBeliebigerSchreibung(String titel, Art art) {
    assertThat(KitNamen.artAusTitel(titel)).contains(art);
  }

  @Test
  void artAusTitel_erlaubtFuehrendenLeerraum() {
    assertThat(KitNamen.artAusTitel("  \t[Requirement] x")).contains(Art.FACHLICH);
    assertThat(KitNamen.artAusTitel("\n[Plan] x")).contains(Art.PLAN);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Ohne Präfix",
        "",
        "Text [Plan] mittendrin",
        "[Planung] x",
        "[Plan x",
        "Plan] x",
        "[Unbekannt] x",
        "[ Plan] x"
      })
  void artAusTitel_liefertLeer_ohnePraefix(String titel) {
    assertThat(KitNamen.artAusTitel(titel)).isEqualTo(Optional.empty());
  }

  @Test
  void artAusTitel_liefertLeer_fuerNull() {
    assertThat(KitNamen.artAusTitel(null)).isEmpty();
  }

  @Test
  void titelPraefixe_nenntDiePraefixeJeArtDeutschZuerst() {
    assertThat(KitNamen.titelPraefixe())
        .containsExactly(
            Map.entry(Art.FACHLICH, List.of("[Fachlich]", "[Requirement]")),
            Map.entry(Art.IDEE, List.of("[Idee]", "[Idea]")),
            Map.entry(Art.MENSCH, List.of("[Mensch]", "[Human]")),
            Map.entry(Art.PLAN, List.of("[Plan]")),
            Map.entry(Art.TASK, List.of("[Task]")));
  }
}
