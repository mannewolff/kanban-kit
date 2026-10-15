package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.ReadyBewertung.Eintrag;
import org.mwolff.manban.nightrun.domain.ReadyBewertung.Grund;
import org.mwolff.manban.nightrun.domain.RunnerLage.AktiverLauf;
import org.mwolff.manban.nightrun.domain.RunnerLage.Gesamt;
import org.mwolff.manban.nightrun.domain.RunnerLage.LaufZustand;
import org.mwolff.manban.nightrun.domain.Wartegrund.Laufgrund;
import org.mwolff.manban.nightrun.domain.Wartegrund.LaufgrundArt;
import org.mwolff.manban.nightrun.domain.Wartegrund.Vermerk;
import org.mwolff.manban.nightrun.domain.Wartegrund.VermerkArt;

/** Warum ein Paket in Ready wartet (Issue #1728, Plan #1724 E8 bis E11). */
class WartegrundTest {

  private static final int KARTE = 1695;
  private static final Instant JETZT = Instant.parse("2026-10-14T02:00:00Z");
  private static final Instant BEWERTET = JETZT.minus(Duration.ofMinutes(10));
  private static final Instant VOR_BEWERTUNG = BEWERTET.minus(Duration.ofHours(1));

  private static RunnerStufe stufe(int karte, String stage, boolean haelt) {
    return new RunnerStufe(karte, stage, JETZT.minusSeconds(600), null, "SESSION", haelt);
  }

  private static AktiverLauf lauf(
      long id,
      NightRunMode mode,
      LaufZustand zustand,
      List<RunnerStufe> stufen,
      @Nullable RunnerStufe letzte) {
    return new AktiverLauf(
        id,
        mode,
        JETZT.minusSeconds(3600),
        "runner",
        JETZT.minusSeconds(120),
        zustand,
        stufen,
        letzte);
  }

  private static RunnerLage lage(AktiverLauf... laeufe) {
    return new RunnerLage(
        laeufe.length == 0 ? Gesamt.KEIN_RUNNER : Gesamt.AKTIV, List.of(laeufe), null);
  }

  /** Eine laufende Umsetzungsnacht ohne Belegung — der ruhige Normalfall. */
  private static AktiverLauf nachtLaeuft() {
    return lauf(1L, NightRunMode.IMPLEMENTATION, LaufZustand.LAEUFT, List.of(), null);
  }

  private static Optional<ReadyBewertung> bewertung(Eintrag... eintraege) {
    return Optional.of(new ReadyBewertung(BEWERTET, null, "kit:nightrun", List.of(eintraege)));
  }

  private static Grund grund(String code, String text) {
    return new Grund(code, null, List.of(), List.of(), text);
  }

  @Test
  @DisplayName("Szenario: Eine Kette belegt die Umsetzung")
  void eineKetteBelegtDieUmsetzung() {
    AktiverLauf kette =
        lauf(
            2L,
            NightRunMode.CHAIN,
            LaufZustand.LAEUFT,
            List.of(stufe(1665, "VORBEREITUNG", true)),
            null);

    Wartegrund w =
        Wartegrund.ableiten(
            KARTE, lage(kette), bewertung(new Eintrag(KARTE, 1, List.of())), VOR_BEWERTUNG);

    assertThat(w.laufgruende())
        .containsExactly(
            new Laufgrund(LaufgrundArt.KEINE_UMSETZUNGSNACHT, null, null, null, null),
            new Laufgrund(LaufgrundArt.BELEGT, null, "VORBEREITUNG", NightRunMode.CHAIN, 1665));
    assertThat(w.startbereit()).isFalse();
  }

  @Test
  @DisplayName("Szenario: Mehrere Gründe zugleich")
  void mehrereGruendeZugleich() {
    Grund abhaengigkeit =
        new Grund(
            "DEPENDENCY", null, List.of(1698), List.of(), "Abhaengigkeit #1698 nicht erfuellt");
    Grund ohneLabel = grund("NO_LABEL", "kein Label 'kit:nightrun'");

    Wartegrund w =
        Wartegrund.ableiten(
            KARTE,
            lage(nachtLaeuft()),
            bewertung(new Eintrag(KARTE, null, List.of(abhaengigkeit, ohneLabel))),
            VOR_BEWERTUNG);

    assertThat(w.kartengruende()).containsExactly(abhaengigkeit, ohneLabel);
    assertThat(w.laufgruende()).isEmpty();
    assertThat(w.paketeDavor()).isNull();
    assertThat(w.startbereit()).isFalse();
  }

  @Test
  @DisplayName("Szenario: Startbereit")
  void startbereit() {
    Wartegrund w =
        Wartegrund.ableiten(
            KARTE, lage(nachtLaeuft()), bewertung(new Eintrag(KARTE, 1, List.of())), VOR_BEWERTUNG);

    assertThat(w).isEqualTo(new Wartegrund(List.of(), List.of(), 0, List.of(), true));
  }

  @Test
  @DisplayName("Szenario: Der Runner verstummt")
  void derRunnerVerstummt() {
    RunnerStufe letzte = stufe(1700, "UMSETZUNG", true);
    AktiverLauf mitStufe =
        lauf(3L, NightRunMode.IMPLEMENTATION, LaufZustand.VERSTUMMT, List.of(letzte), letzte);
    AktiverLauf ohneStufe =
        lauf(4L, NightRunMode.IMPLEMENTATION, LaufZustand.VERSTUMMT, List.of(), null);

    Wartegrund w =
        Wartegrund.ableiten(
            KARTE,
            lage(mitStufe, ohneStufe),
            bewertung(new Eintrag(KARTE, 1, List.of())),
            VOR_BEWERTUNG);

    Instant seit = JETZT.minusSeconds(120);
    assertThat(w.laufgruende())
        .containsExactly(
            new Laufgrund(
                LaufgrundArt.VERSTUMMT, seit, "UMSETZUNG", NightRunMode.IMPLEMENTATION, null),
            new Laufgrund(LaufgrundArt.VERSTUMMT, seit, null, NightRunMode.IMPLEMENTATION, null));
    assertThat(w.startbereit()).isFalse();
  }

  /**
   * Eine verstummte Kette zählt nur, wenn sie laut ihrem letzten {@code current} die Umsetzung
   * hielt.
   */
  @Test
  void eineVerstummteKette_zaehltNurMitGehaltenerUmsetzung() {
    RunnerStufe haelt = stufe(1665, "VORBEREITUNG", true);
    AktiverLauf kettHaelt =
        lauf(5L, NightRunMode.CHAIN, LaufZustand.VERSTUMMT, List.of(haelt), haelt);
    RunnerStufe frei = stufe(1666, "PLAN", false);
    AktiverLauf ketteFrei =
        lauf(6L, NightRunMode.CHAIN, LaufZustand.VERSTUMMT, List.of(frei), frei);

    Wartegrund w =
        Wartegrund.ableiten(
            KARTE,
            lage(nachtLaeuft(), kettHaelt, ketteFrei),
            bewertung(new Eintrag(KARTE, 1, List.of())),
            VOR_BEWERTUNG);

    assertThat(w.laufgruende())
        .containsExactly(
            new Laufgrund(
                LaufgrundArt.VERSTUMMT,
                JETZT.minusSeconds(120),
                "VORBEREITUNG",
                NightRunMode.CHAIN,
                null));
  }

  /**
   * Eine verstummte Umsetzungsnacht gilt als laufend: Sie ergibt „verstummt“, nicht zusätzlich
   * „keine Umsetzungsnacht aktiv“.
   */
  @Test
  void eineVerstummteUmsetzungsnacht_istKeineFehlendeUmsetzungsnacht() {
    AktiverLauf nacht =
        lauf(3L, NightRunMode.IMPLEMENTATION, LaufZustand.VERSTUMMT, List.of(), null);

    Wartegrund w = Wartegrund.ableiten(KARTE, lage(nacht), Optional.empty(), VOR_BEWERTUNG);

    assertThat(w.laufgruende()).extracting(Laufgrund::art).containsExactly(LaufgrundArt.VERSTUMMT);
  }

  /** Belegt die Umsetzung die betrachtete Karte selbst, ist das kein Grund. */
  @Test
  void dieEigeneKarte_belegtNicht() {
    AktiverLauf nacht =
        lauf(
            1L,
            NightRunMode.IMPLEMENTATION,
            LaufZustand.LAEUFT,
            List.of(stufe(KARTE, "UMSETZUNG", true), stufe(1700, "UMSETZUNG", false)),
            null);

    Wartegrund w =
        Wartegrund.ableiten(
            KARTE, lage(nacht), bewertung(new Eintrag(KARTE, 1, List.of())), VOR_BEWERTUNG);

    assertThat(w.laufgruende()).isEmpty();
    assertThat(w.startbereit()).isTrue();
  }

  /** Eine Umsetzungsnacht, die die Umsetzung hält, nennt keine Stufe — nur eine Kette tut das. */
  @Test
  void eineBelegendeUmsetzungsnacht_nenntKarteUndArt() {
    AktiverLauf andere =
        lauf(
            7L,
            NightRunMode.IMPLEMENTATION,
            LaufZustand.LAEUFT,
            List.of(stufe(1700, "UMSETZUNG", true)),
            null);

    Wartegrund w = Wartegrund.ableiten(KARTE, lage(andere), Optional.empty(), VOR_BEWERTUNG);

    assertThat(w.laufgruende())
        .containsExactly(
            new Laufgrund(
                LaufgrundArt.BELEGT, null, "UMSETZUNG", NightRunMode.IMPLEMENTATION, 1700));
  }

  @Test
  void ohneLauf_keineUmsetzungsnacht() {
    Wartegrund w =
        Wartegrund.ableiten(
            KARTE, lage(), bewertung(new Eintrag(KARTE, 1, List.of())), VOR_BEWERTUNG);

    assertThat(w.laufgruende())
        .containsExactly(new Laufgrund(LaufgrundArt.KEINE_UMSETZUNGSNACHT, null, null, null, null));
    assertThat(w.startbereit()).isFalse();
  }

  @Test
  void fehltDieKarteInDerBewertung_kommtNichtBewertet() {
    Wartegrund w =
        Wartegrund.ableiten(
            KARTE, lage(nachtLaeuft()), bewertung(new Eintrag(1700, 1, List.of())), VOR_BEWERTUNG);

    assertThat(w.vermerke()).containsExactly(new Vermerk(VermerkArt.NICHT_BEWERTET, BEWERTET));
    assertThat(w.kartengruende()).isEmpty();
    assertThat(w.paketeDavor()).isNull();
    assertThat(w.startbereit()).isFalse();
  }

  /** Ohne jede Bewertung fehlt auch die Zeit der letzten. */
  @Test
  void ohneBewertung_kommtNichtBewertetOhneZeit() {
    Wartegrund w = Wartegrund.ableiten(KARTE, lage(nachtLaeuft()), Optional.empty(), VOR_BEWERTUNG);

    assertThat(w.vermerke()).containsExactly(new Vermerk(VermerkArt.NICHT_BEWERTET, null));
    assertThat(w.startbereit()).isFalse();
  }

  @Test
  void nachDerBewertungGeaendert_kommtDerVermerk_undNichtStartbereit() {
    Wartegrund w =
        Wartegrund.ableiten(
            KARTE,
            lage(nachtLaeuft()),
            bewertung(new Eintrag(KARTE, 1, List.of())),
            BEWERTET.plusSeconds(1));

    assertThat(w.vermerke())
        .containsExactly(new Vermerk(VermerkArt.SEIT_BEWERTUNG_GEAENDERT, BEWERTET));
    assertThat(w.kartengruende()).isEmpty();
    assertThat(w.laufgruende()).isEmpty();
    assertThat(w.startbereit()).isFalse();
  }

  /** Genau zum Zeitpunkt der Bewertung geändert gilt als bewertet. */
  @Test
  void genauZurBewertungGeaendert_istNichtSeitherGeaendert() {
    Wartegrund w =
        Wartegrund.ableiten(
            KARTE, lage(nachtLaeuft()), bewertung(new Eintrag(KARTE, 1, List.of())), BEWERTET);

    assertThat(w.vermerke()).isEmpty();
    assertThat(w.startbereit()).isTrue();
  }

  @Test
  void position3_ergibtZweiPaketeDavor() {
    Wartegrund w =
        Wartegrund.ableiten(
            KARTE, lage(nachtLaeuft()), bewertung(new Eintrag(KARTE, 3, List.of())), VOR_BEWERTUNG);

    assertThat(w.paketeDavor()).isEqualTo(2);
    assertThat(w.startbereit()).isTrue();
  }

  @Test
  void einUnbekannterCode_wirdMitWortlautDurchgereicht() {
    Grund neu = grund("GANZ_NEU", "ein Grund, den das Board noch nicht kennt");

    Wartegrund w =
        Wartegrund.ableiten(
            KARTE,
            lage(nachtLaeuft()),
            bewertung(new Eintrag(KARTE, null, List.of(neu))),
            VOR_BEWERTUNG);

    assertThat(w.kartengruende()).containsExactly(neu);
    assertThat(w.startbereit()).isFalse();
  }

  @Test
  void dieListenSindUnveraenderlich() {
    List<Laufgrund> laufgruende =
        new ArrayList<>(
            List.of(new Laufgrund(LaufgrundArt.KEINE_UMSETZUNGSNACHT, null, null, null, null)));
    List<Grund> kartengruende = new ArrayList<>(List.of(grund("NO_LABEL", "x")));
    List<Vermerk> vermerke = new ArrayList<>(List.of(new Vermerk(VermerkArt.NICHT_BEWERTET, null)));
    Wartegrund w = new Wartegrund(laufgruende, kartengruende, null, vermerke, false);
    laufgruende.clear();
    kartengruende.clear();
    vermerke.clear();

    assertThat(w.laufgruende()).hasSize(1);
    assertThat(w.kartengruende()).hasSize(1);
    assertThat(w.vermerke()).hasSize(1);
  }
}
