package org.mwolff.manban.fremdboard.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.card.application.FremdKartenBestand;
import org.mwolff.manban.card.application.FremdKartenSchreiber;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremdeKarte;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremderAkteur;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.SpiegelNotiz;
import org.mwolff.manban.fremdboard.domain.FremdAbrufException;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.FremdBoardQuelle;
import org.mwolff.manban.fremdboard.domain.FremdEreignis;
import org.mwolff.manban.fremdboard.domain.FremdEreignis.Art;
import org.mwolff.manban.fremdboard.domain.FremdKarte;
import org.mwolff.manban.fremdboard.domain.FremdNotiz;
import org.mwolff.manban.fremdboard.domain.FremdNotizen;
import org.mwolff.manban.fremdboard.domain.FremdNummern;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Abgleich einer Karte gegen gemockte Quelle und Schreibwege (Issue #2167, Plan #2152 A4, A6, A11,
 * A12, A19). Gegen echte Datenbank und nachgestelltes GitLab prüft {@code AbgleichServiceIT}.
 */
// PMD.ExcessiveImports: Der Abgleich übersetzt vom Port des fremden Systems auf die Fassaden von
// card und comment (wie AbgleichService selbst); der Test braucht die Werte beider Seiten, seit
// Issue #2200 auch die nach Vertraulichkeit getrennten Nummern und Notizen.
@SuppressWarnings("PMD.ExcessiveImports")
class AbgleichServiceTest {

  private static final long PROJEKT = 1L;
  private static final long BOARD = 10L;
  private static final Instant VORHER = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant ANGEBUNDEN = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant T1 = Instant.parse("2026-11-02T09:00:00Z");
  private static final Instant T2 = Instant.parse("2026-11-02T10:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-11-15T12:00:00Z");
  private static final String ADRESSE = "https://gitlab.example.org/gruppe/projekt/-/issues/7";
  private static final Verbindung VERBINDUNG =
      new Verbindung(
          PROJEKT,
          FremdBoardArt.GITLAB,
          "https://gitlab.example.org",
          "gruppe/projekt",
          "geheim",
          "kit-bot",
          StatusLabels.STANDARD,
          BOARD,
          ANGEBUNDEN,
          null,
          null,
          null);
  private static final FremderAkteur UNBEKANNT = FremderAkteur.von(null, "kit-bot");
  private static final FremderAkteur ANNA = FremderAkteur.von("anna", "kit-bot");
  private static final FremderAkteur KIT = FremderAkteur.von("kit-bot", "kit-bot");
  private static final FremdNotizen KEINE_NOTIZEN = new FremdNotizen(List.of(), Set.of());

  private FremdBoardQuelle quelle;
  private FremdKartenBestand bestand;
  private FremdKartenSchreiber schreiber;
  private CommentService kommentare;
  private EreignisRegister register;
  private AbgleichService service;

  @BeforeEach
  void setUp() {
    quelle = mock(FremdBoardQuelle.class);
    bestand = mock(FremdKartenBestand.class);
    schreiber = mock(FremdKartenSchreiber.class);
    kommentare = mock(CommentService.class);
    register = mock(EreignisRegister.class);
    when(quelle.art()).thenReturn(FremdBoardArt.GITLAB);
    when(quelle.adresse(VERBINDUNG, 7)).thenReturn(ADRESSE);
    when(register.bekannte(anyLong(), any())).thenReturn(Set.of());
    when(register.eintragen(anyLong(), anyString(), anyString())).thenReturn(true);
    when(schreiber.anlegen(anyLong(), any(), any(), any())).thenReturn(70L);
    service =
        new AbgleichService(
            List.of(quelle),
            bestand,
            schreiber,
            kommentare,
            register,
            mock(PlatformTransactionManager.class),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static FremdNummern sichtbar(Long... nummern) {
    return new FremdNummern(List.of(nummern), List.of());
  }

  private static FremdKarte karte(
      String titel, String text, List<String> labels, boolean zu, Instant angelegt) {
    return new FremdKarte(7, titel, text, labels, zu, angelegt, T2);
  }

  private static FremdEreignis ereignis(
      String kennung, Art art, @Nullable String label, Instant zeit, @Nullable String akteur) {
    return new FremdEreignis(kennung, 7, art, label, zeit, akteur);
  }

  private void gitlab(FremdKarte karte, List<FremdEreignis> ereignisse, List<FremdNotiz> notizen) {
    when(quelle.karte(VERBINDUNG, 7)).thenReturn(Optional.of(karte));
    when(quelle.ereignisse(VERBINDUNG, 7)).thenReturn(ereignisse);
    when(quelle.notizen(VERBINDUNG, 7)).thenReturn(new FremdNotizen(notizen, Set.of()));
  }

  @Test
  void fehlendeKarte_bleibtUnberuehrt() {
    when(quelle.karte(VERBINDUNG, 7)).thenReturn(Optional.empty());

    service.karteAbgleichen(VERBINDUNG, 7);

    verifyNoInteractions(schreiber, bestand, kommentare, register);
  }

  @Test
  void neueKarte_trittImStandVorDenNeuenWechselnEin_undFolgtJedemZeitpunkt() {
    // Given: vor der Anbindung angelegt; ein altes Label, dann zwei Zeitpunkte nach der Anbindung
    gitlab(
        karte("Export", "", List.of("kit:nightrun", "In progress"), false, VORHER),
        List.of(
            ereignis("label-1", Art.LABEL_GESETZT, "kit:nightrun", VORHER, "anna"),
            ereignis("label-2", Art.LABEL_GESETZT, "Ready", T1, "anna"),
            ereignis("label-3", Art.LABEL_ENTFERNT, "Ready", T2, "anna"),
            ereignis("label-4", Art.LABEL_GESETZT, "In progress", T2, "kit-bot")),
        List.of());
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.empty());

    // When
    service.karteAbgleichen(VERBINDUNG, 7);

    // Then: nur die Wechsel ab der Anbindung zählen, eingetreten in Backlog zur Anbindung
    verify(register).bekannte(PROJEKT, List.of("label-2", "label-3", "label-4"));
    InOrder reihenfolge = inOrder(schreiber, register);
    reihenfolge
        .verify(schreiber)
        .anlegen(
            BOARD, new FremdeKarte(7, "Export", "", "BACKLOG", ADRESSE), ANGEBUNDEN, UNBEKANNT);
    reihenfolge.verify(schreiber).labelSetzen(BOARD, 7, "kit:nightrun", ANGEBUNDEN, UNBEKANNT);
    reihenfolge.verify(register).eintragen(PROJEKT, "LABEL_GESETZT", "label-2");
    reihenfolge.verify(schreiber).wechseln(BOARD, 7, "READY", T1, ANNA);
    reihenfolge.verify(register).eintragen(PROJEKT, "LABEL_ENTFERNT", "label-3");
    reihenfolge.verify(register).eintragen(PROJEKT, "LABEL_GESETZT", "label-4");
    reihenfolge.verify(schreiber).wechseln(BOARD, 7, "IN_PROGRESS", T2, KIT);
    reihenfolge.verify(schreiber).wechseln(BOARD, 7, "IN_PROGRESS", T2, UNBEKANNT);
    reihenfolge.verify(schreiber).herkunftSetzen(BOARD, 7, null);
    verify(schreiber, never()).labelSetzen(eq(BOARD), eq(7), eq("Ready"), any(), any());
    verify(schreiber, never()).labelEntfernen(anyLong(), anyInt(), anyString());
    verify(schreiber, never()).aktualisieren(anyLong(), anyInt(), any(), any(), any(), any());
  }

  @Test
  void spaeterAngelegteKarte_trittMitIhremAnlagezeitpunktEin() {
    gitlab(karte("Export", "", List.of(), false, T1), List.of(), List.of());
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.empty());

    service.karteAbgleichen(VERBINDUNG, 7);

    verify(schreiber)
        .anlegen(BOARD, new FremdeKarte(7, "Export", "", "BACKLOG", ADRESSE), T1, UNBEKANNT);
  }

  @Test
  void vorhandeneKarte_wirdAktualisiert_bekannteWechselZaehlenNicht() {
    // Given
    gitlab(
        karte("Export", "Text", List.of("kit:clarify"), true, VORHER),
        List.of(
            ereignis("label-2", Art.LABEL_GESETZT, "kit:clarify", T1, "kit-bot"),
            ereignis("state-9", Art.GESCHLOSSEN, null, T2, "anna")),
        List.of());
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));
    when(register.bekannte(PROJEKT, List.of("label-2", "state-9"))).thenReturn(Set.of("label-2"));

    // When
    service.karteAbgleichen(VERBINDUNG, 7);

    // Then
    verify(schreiber).aktualisieren(BOARD, 7, "Export", "Text", ADRESSE, T2);
    verify(schreiber, never()).anlegen(anyLong(), any(), any(), any());
    verify(register, never()).eintragen(PROJEKT, "LABEL_GESETZT", "label-2");
    verify(register).eintragen(PROJEKT, "GESCHLOSSEN", "state-9");
    verify(schreiber, never()).labelSetzen(anyLong(), anyInt(), anyString(), any(), any());
    verify(schreiber).wechseln(BOARD, 7, "DONE", T2, ANNA);
  }

  @Test
  void schonUebernommenerZeitpunkt_bleibtUnberuehrt() {
    gitlab(
        karte("Export", "", List.of("Ready", "kit:night"), false, VORHER),
        List.of(ereignis("label-2", Art.LABEL_GESETZT, "kit:night", T1, "anna")),
        List.of());
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));
    when(register.eintragen(PROJEKT, "LABEL_GESETZT", "label-2")).thenReturn(false);

    service.karteAbgleichen(VERBINDUNG, 7);

    verify(schreiber, never()).labelSetzen(anyLong(), anyInt(), anyString(), any(), any());
    verify(schreiber, never()).wechseln(eq(BOARD), eq(7), anyString(), eq(T1), any());
    verify(schreiber).wechseln(BOARD, 7, "READY", T2, UNBEKANNT);
  }

  @Test
  void labelsAusserhalbDesStatus_werdenGesetztUndAbgenommen() {
    gitlab(
        karte("Export", "", List.of("kit:night"), false, VORHER),
        List.of(
            ereignis("label-1", Art.LABEL_GESETZT, "kit:night", T1, "anna"),
            ereignis("label-2", Art.LABEL_ENTFERNT, "kit:clarify", T1, "anna"),
            ereignis("label-3", Art.LABEL_GESETZT, null, T1, "anna"),
            ereignis("state-4", Art.WIEDER_GEOEFFNET, null, T1, null)),
        List.of());
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));

    service.karteAbgleichen(VERBINDUNG, 7);

    verify(schreiber).labelSetzen(BOARD, 7, "kit:night", T1, ANNA);
    verify(schreiber).labelEntfernen(BOARD, 7, "kit:clarify");
    verify(schreiber).wechseln(BOARD, 7, "BACKLOG", T1, UNBEKANNT);
  }

  @Test
  void schliessenMitLabel_spiegeltKeinLabel() {
    // Given: ein Schließen, das wider die Regel ein gewöhnliches Label mitträgt (Issue #2187)
    gitlab(
        karte("Export", "", List.of(), true, VORHER),
        List.of(ereignis("state-1", Art.GESCHLOSSEN, "kit:night", T1, "anna")),
        List.of());
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));

    // When
    service.karteAbgleichen(VERBINDUNG, 7);

    // Then: der Status folgt, das Label bleibt außen vor
    verify(register).eintragen(PROJEKT, "GESCHLOSSEN", "state-1");
    verify(schreiber, never()).labelSetzen(anyLong(), anyInt(), anyString(), any(), any());
    verify(schreiber, never()).labelEntfernen(anyLong(), anyInt(), anyString());
    verify(schreiber).wechseln(BOARD, 7, "DONE", T1, ANNA);
  }

  @Test
  void dokument_bekommtKeinenWechsel() {
    gitlab(
        karte("[Plan] Export", "Fachliche Quelle: Issue #2", List.of("Ready"), false, VORHER),
        List.of(ereignis("label-1", Art.LABEL_GESETZT, "Ready", T1, "anna")),
        List.of());
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.empty());

    service.karteAbgleichen(VERBINDUNG, 7);

    verify(schreiber)
        .anlegen(
            BOARD,
            new FremdeKarte(7, "[Plan] Export", "Fachliche Quelle: Issue #2", "BACKLOG", ADRESSE),
            ANGEBUNDEN,
            UNBEKANNT);
    verify(register).eintragen(PROJEKT, "LABEL_GESETZT", "label-1");
    verify(schreiber, never()).wechseln(anyLong(), anyInt(), anyString(), any(), any());
    verify(schreiber).herkunftSetzen(BOARD, 7, 2);
  }

  @Test
  void herkunft_planVorFachlicherQuelle_undZuGrosseNummerZaehltNicht() {
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));
    gitlab(
        karte("Export", "Fachliche Quelle: Issue #2\nPlan: #3", List.of(), false, VORHER),
        List.of(),
        List.of());

    service.karteAbgleichen(VERBINDUNG, 7);

    verify(schreiber).herkunftSetzen(BOARD, 7, 3);

    gitlab(karte("Export", "Plan: #2147483648", List.of(), false, VORHER), List.of(), List.of());
    service.karteAbgleichen(VERBINDUNG, 7);

    verify(schreiber).herkunftSetzen(BOARD, 7, null);
  }

  @Test
  void herkunft_groessteNummerZaehlt() {
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));
    gitlab(karte("Export", "Plan: #2147483647", List.of(), false, VORHER), List.of(), List.of());

    service.karteAbgleichen(VERBINDUNG, 7);

    verify(schreiber).herkunftSetzen(BOARD, 7, Integer.MAX_VALUE);
  }

  @Test
  void angleichen_mitAenderungszeitpunkt_fruehestensZurAnbindung() {
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));
    when(quelle.karte(VERBINDUNG, 7))
        .thenReturn(
            Optional.of(new FremdKarte(7, "Export", "", List.of("Ready"), false, VORHER, VORHER)));
    when(quelle.ereignisse(VERBINDUNG, 7)).thenReturn(List.of());
    when(quelle.notizen(VERBINDUNG, 7)).thenReturn(KEINE_NOTIZEN);

    service.karteAbgleichen(VERBINDUNG, 7);

    verify(schreiber).wechseln(BOARD, 7, "READY", ANGEBUNDEN, UNBEKANNT);
  }

  @Test
  void notizen_werdenGespiegelt_undFehlendeGekennzeichnet() {
    // Given
    FremdNotiz kit = new FremdNotiz("701", 7, "kit-bot", "## Kette angehalten", T1, T2);
    FremdNotiz anna = new FremdNotiz("702", 7, "anna", "Antwort", T2, T2);
    gitlab(karte("Export", "", List.of(), false, VORHER), List.of(), List.of(kit, anna));
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));

    // When
    service.karteAbgleichen(VERBINDUNG, 7);

    // Then
    verify(kommentare).spiegeln(70L, new SpiegelNotiz("701", KIT, "## Kette angehalten", T1, T2));
    verify(kommentare).spiegeln(70L, new SpiegelNotiz("702", ANNA, "Antwort", T2, T2));
    verify(kommentare).spiegelFehlendeKennzeichnen(70L, Set.of("701", "702"), JETZT);
  }

  @Test
  void notizen_interneWerdenVorDemSpiegelnEntfernt_undZaehlenNichtAlsVorhanden() {
    // Given: Notiz 702 ist in GitLab intern geworden; die Quelle nennt nur ihre Kennung.
    FremdNotiz anna = new FremdNotiz("701", 7, "anna", "Freigegeben", T1, T1);
    when(quelle.karte(VERBINDUNG, 7))
        .thenReturn(Optional.of(karte("Export", "", List.of(), false, VORHER)));
    when(quelle.ereignisse(VERBINDUNG, 7)).thenReturn(List.of());
    when(quelle.notizen(VERBINDUNG, 7)).thenReturn(new FremdNotizen(List.of(anna), Set.of("702")));
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));

    // When
    service.karteAbgleichen(VERBINDUNG, 7);

    // Then
    InOrder reihenfolge = inOrder(kommentare);
    reihenfolge.verify(kommentare).spiegelVertraulicheEntfernen(70L, Set.of("702"));
    reihenfolge
        .verify(kommentare)
        .spiegeln(70L, new SpiegelNotiz("701", ANNA, "Freigegeben", T1, T1));
    reihenfolge.verify(kommentare).spiegelFehlendeKennzeichnen(70L, Set.of("701"), JETZT);
  }

  @Test
  void neueKarte_notizenAnDieNeueId() {
    FremdNotiz kit = new FremdNotiz("701", 7, "kit-bot", "Meldung", T1, T1);
    gitlab(karte("Export", "", List.of(), false, VORHER), List.of(), List.of(kit));
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.empty());

    service.karteAbgleichen(VERBINDUNG, 7);

    verify(kommentare).spiegeln(70L, new SpiegelNotiz("701", KIT, "Meldung", T1, T1));
  }

  @Test
  void ohneQuelleDerArt_scheitert() {
    AbgleichService ohne =
        new AbgleichService(
            List.of(),
            bestand,
            schreiber,
            kommentare,
            register,
            mock(PlatformTransactionManager.class),
            Clock.fixed(JETZT, ZoneOffset.UTC));

    assertThatThrownBy(() -> ohne.karteAbgleichen(VERBINDUNG, 7))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Keine Quelle für GITLAB");
  }

  @Test
  void wechselGleicherZeit_sindEinZeitpunkt_ungeordneteWerdenSortiert() {
    gitlab(
        karte("Export", "", List.of("In review"), false, VORHER),
        List.of(
            ereignis("label-3", Art.LABEL_GESETZT, "In review", T2, "kit-bot"),
            ereignis("label-1", Art.LABEL_GESETZT, "Ready", T1, "anna"),
            ereignis("label-2", Art.LABEL_ENTFERNT, "Ready", T2, "kit-bot")),
        List.of());
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));

    service.karteAbgleichen(VERBINDUNG, 7);

    InOrder reihenfolge = inOrder(schreiber);
    reihenfolge.verify(schreiber).wechseln(BOARD, 7, "READY", T1, ANNA);
    reihenfolge.verify(schreiber).wechseln(BOARD, 7, "IN_REVIEW", T2, KIT);
    verify(schreiber, never()).wechseln(eq(BOARD), eq(7), eq("BACKLOG"), any(), any());
    // Einzeln gebucht stünde die Karte zu T2 kurz in Ready und Review zugleich — also in Ready.
    verify(schreiber, never()).wechseln(BOARD, 7, "READY", T2, KIT);
  }

  @Test
  void neueKarte_statuslabelsStehenAlsSpalte_nichtAlsLabel() {
    // Given: alle vier Statuslabels und ein gewöhnliches Label, schon vor der Anbindung gesetzt
    gitlab(
        karte(
            "Export",
            "",
            List.of("Backlog", "Ready", "In progress", "In review", "kit:night"),
            false,
            VORHER),
        List.of(),
        List.of());
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.empty());

    // When
    service.karteAbgleichen(VERBINDUNG, 7);

    // Then: nur das gewöhnliche Label wird gespiegelt
    verify(schreiber)
        .anlegen(
            BOARD, new FremdeKarte(7, "Export", "", "BACKLOG", ADRESSE), ANGEBUNDEN, UNBEKANNT);
    verify(schreiber).labelSetzen(BOARD, 7, "kit:night", ANGEBUNDEN, UNBEKANNT);
    verify(schreiber).labelSetzen(anyLong(), anyInt(), anyString(), any(), any());
  }

  @Test
  void quelleAndererArt_zaehltNicht() {
    FremdBoardQuelle andere = mock(FremdBoardQuelle.class);
    AbgleichService mitAnderer =
        new AbgleichService(
            List.of(andere),
            bestand,
            schreiber,
            kommentare,
            register,
            mock(PlatformTransactionManager.class),
            Clock.fixed(JETZT, ZoneOffset.UTC));

    assertThatThrownBy(() -> mitAnderer.karteAbgleichen(VERBINDUNG, 7))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Keine Quelle für GITLAB");
    verifyNoInteractions(schreiber);
  }

  @Test
  void inkrementell_gleichtJedeGeaenderteKarteAb_ohneSieErneutZuLesen() {
    // Given: Karte 7 hat sich geändert, ihre Wechsel und Notizen liest der Abgleich je Karte.
    FremdKarte geaendert = karte("Export", "", List.of("Ready"), false, VORHER);
    when(quelle.geaenderteKarten(VERBINDUNG, T1)).thenReturn(List.of(geaendert));
    when(quelle.ereignisse(VERBINDUNG, 7))
        .thenReturn(List.of(ereignis("label-1", Art.LABEL_GESETZT, "Ready", T2, "anna")));
    when(quelle.notizen(VERBINDUNG, 7)).thenReturn(KEINE_NOTIZEN);
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));

    // When
    service.inkrementellAbgleichen(VERBINDUNG, T1);

    // Then
    verify(quelle, never()).karte(any(), anyLong());
    verify(quelle, never()).nummern(any());
    verify(schreiber).wechseln(BOARD, 7, "READY", T2, ANNA);
    verify(schreiber, never()).alsGeloeschtMarkieren(anyLong(), anyInt(), any());
  }

  @Test
  void voll_liestFehlendeKarten_einzeln_undMarkiertVerschwundeneAlsGeloescht() {
    // Given: hier gespiegelt sind 7 und 9; in GitLab gibt es 7 (geändert) und 8 (noch nicht
    // gespiegelt), 9 ist gelöscht.
    when(bestand.nummern(PROJEKT)).thenReturn(List.of(7, 9));
    when(quelle.nummern(VERBINDUNG)).thenReturn(sichtbar(7L, 8L));
    when(quelle.geaenderteKarten(VERBINDUNG, T1))
        .thenReturn(List.of(karte("Export", "", List.of(), false, VORHER)));
    FremdKarte acht = new FremdKarte(8, "Import", "", List.of(), false, VORHER, VORHER);
    when(quelle.karte(VERBINDUNG, 8)).thenReturn(Optional.of(acht));
    when(quelle.ereignisse(eq(VERBINDUNG), anyLong())).thenReturn(List.of());
    when(quelle.notizen(eq(VERBINDUNG), anyLong())).thenReturn(KEINE_NOTIZEN);
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.of(70L));
    when(bestand.kartenId(PROJEKT, 8)).thenReturn(Optional.empty());

    // When
    service.vollAbgleichen(VERBINDUNG, T1);

    // Then
    verify(quelle, never()).karte(VERBINDUNG, 7);
    verify(schreiber).aktualisieren(BOARD, 7, "Export", "", ADRESSE, T2);
    verify(schreiber)
        .anlegen(
            eq(BOARD),
            eq(new FremdeKarte(8, "Import", "", "BACKLOG", null)),
            eq(ANGEBUNDEN),
            any());
    verify(schreiber).alsGeloeschtMarkieren(BOARD, 9, JETZT);
    verify(schreiber, never()).alsGeloeschtMarkieren(BOARD, 7, JETZT);
    verify(schreiber, never()).alsGeloeschtMarkieren(BOARD, 8, JETZT);
  }

  @Test
  void voll_entferntVertraulicheKarten_vorJedemWeiterenAbruf_stattSieAlsGeloeschtZuMarkieren() {
    // Given: hier gespiegelt sind 7 und 9; in GitLab ist 9 inzwischen vertraulich. Eine Nummer
    // jenseits des Zahlenbereichs der Karten kann es hier nicht geben.
    when(bestand.nummern(PROJEKT)).thenReturn(List.of(7, 9));
    when(quelle.nummern(VERBINDUNG))
        .thenReturn(new FremdNummern(List.of(7L), List.of(9L, Integer.MAX_VALUE + 1L)));
    when(quelle.geaenderteKarten(VERBINDUNG, T1)).thenReturn(List.of());

    // When
    service.vollAbgleichen(VERBINDUNG, T1);

    // Then
    InOrder reihenfolge = inOrder(schreiber, quelle);
    reihenfolge.verify(schreiber).vertraulicheEntfernen(BOARD, List.of(9));
    reihenfolge.verify(quelle).geaenderteKarten(VERBINDUNG, T1);
    verify(schreiber, never()).alsGeloeschtMarkieren(anyLong(), anyInt(), any());
    verify(quelle, never()).karte(any(), anyLong());
  }

  @Test
  void voll_ohneVertraulicheKarten_entferntNichts() {
    when(bestand.nummern(PROJEKT)).thenReturn(List.of(7));
    when(quelle.nummern(VERBINDUNG)).thenReturn(sichtbar(7L));
    when(quelle.geaenderteKarten(VERBINDUNG, T1)).thenReturn(List.of());

    service.vollAbgleichen(VERBINDUNG, T1);

    verifyNoInteractions(schreiber);
  }

  @Test
  void voll_liestDenBestandVorDerListe_undLoeschtNichtsWennDieListeScheitert() {
    // Given
    when(bestand.nummern(PROJEKT)).thenReturn(List.of(7));
    when(quelle.nummern(VERBINDUNG))
        .thenThrow(new FremdAbrufException("GitLab ist nicht erreichbar"));

    // When / Then
    assertThatThrownBy(() -> service.vollAbgleichen(VERBINDUNG, T1))
        .isInstanceOf(FremdAbrufException.class);
    InOrder reihenfolge = inOrder(bestand, quelle);
    reihenfolge.verify(bestand).nummern(PROJEKT);
    reihenfolge.verify(quelle).nummern(VERBINDUNG);
    verifyNoInteractions(schreiber);
  }

  @Test
  void voll_liestEinzelnNur_wasWederGeaendertNochHierGespiegeltIst() {
    // Given: 7 ist dort neu und geändert, 8 ist hier gespiegelt und unverändert, 9 fehlt hier
    when(bestand.nummern(PROJEKT)).thenReturn(List.of(8));
    when(quelle.nummern(VERBINDUNG)).thenReturn(sichtbar(7L, 8L, 9L));
    when(quelle.geaenderteKarten(VERBINDUNG, T1))
        .thenReturn(List.of(karte("Export", "", List.of(), false, VORHER)));
    when(quelle.karte(VERBINDUNG, 9)).thenReturn(Optional.empty());
    when(quelle.ereignisse(eq(VERBINDUNG), anyLong())).thenReturn(List.of());
    when(quelle.notizen(eq(VERBINDUNG), anyLong())).thenReturn(KEINE_NOTIZEN);
    when(bestand.kartenId(PROJEKT, 7)).thenReturn(Optional.empty());

    // When
    service.vollAbgleichen(VERBINDUNG, T1);

    // Then
    verify(quelle, never()).karte(VERBINDUNG, 7);
    verify(quelle, never()).karte(VERBINDUNG, 8);
    verify(quelle).karte(VERBINDUNG, 9);
    verify(schreiber, never()).alsGeloeschtMarkieren(anyLong(), anyInt(), any());
  }

  @Test
  void voll_eineEinzelnGeleseneKarteDieInzwischenFehlt_bleibtUnberuehrt() {
    when(bestand.nummern(PROJEKT)).thenReturn(List.of());
    when(quelle.nummern(VERBINDUNG)).thenReturn(sichtbar(8L));
    when(quelle.geaenderteKarten(VERBINDUNG, T1)).thenReturn(List.of());
    when(quelle.karte(VERBINDUNG, 8)).thenReturn(Optional.empty());

    service.vollAbgleichen(VERBINDUNG, T1);

    verifyNoInteractions(schreiber);
  }
}
