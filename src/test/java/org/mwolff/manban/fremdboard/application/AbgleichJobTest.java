package org.mwolff.manban.fremdboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.fremdboard.domain.FremdAbrufException;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.slf4j.LoggerFactory;

/**
 * Der regelmäßige Abgleich (Issue #2168, Plan #2152 A7, A8, A9) gegen einen nachgebildeten Speicher
 * der Verbindungen: Anspruch, zwei Knoten, Vollabgleich nach Abstand, Fehler. Die Sperre selbst
 * prüft {@code VerbindungRepositoryAdapterIT} gegen echtes PostgreSQL.
 */
class AbgleichJobTest {

  private static final Instant ANGEBUNDEN = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant JETZT = Instant.parse("2026-11-15T12:00:00Z");
  private static final FremdboardProperties PROPERTIES =
      new FremdboardProperties(null, null, null, null, null);

  private Speicher speicher;
  private AbgleichService abgleich;
  private AbgleichJob job;
  private final ListAppender<ILoggingEvent> protokoll = new ListAppender<>();

  @BeforeEach
  void setUp() {
    speicher = new Speicher();
    abgleich = mock(AbgleichService.class);
    job = neuerJob(abgleich);
    protokoll.start();
    ((Logger) LoggerFactory.getLogger(AbgleichJob.class)).addAppender(protokoll);
  }

  /** Der Logger ist global — ohne Abmelden sammelte der Appender über Testklassen hinweg weiter. */
  @AfterEach
  void tearDown() {
    ((Logger) LoggerFactory.getLogger(AbgleichJob.class)).detachAppender(protokoll);
  }

  private AbgleichJob neuerJob(AbgleichService dienst) {
    return new AbgleichJob(speicher, dienst, PROPERTIES, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  @Test
  void ersterAbgleich_istVoll_abDerAnbindung_undVermerktBeideZeitpunkte() {
    // Given
    Verbindung verbindung = speicher.lege(verbindung(1L, null, null, "alter Fehler"));

    // When
    job.run();

    // Then
    verify(abgleich).vollAbgleichen(verbindung, ANGEBUNDEN);
    verify(abgleich, never()).inkrementellAbgleichen(any(), any());
    Verbindung danach = speicher.finde(1L).orElseThrow();
    assertThat(danach.letzterAbgleichAm()).isEqualTo(JETZT);
    assertThat(danach.letzterVollabgleichAm()).isEqualTo(JETZT);
    assertThat(danach.letzterFehler()).isNull();
    assertThat(speicher.standAm).containsEntry(1L, JETZT);
  }

  @Test
  void faelligerAbgleich_istInkrementell_seitDemLetztenMitUeberlappung() {
    // Given: zuletzt vor 3 Minuten abgeglichen, voll vor 30 Minuten
    Instant zuletzt = JETZT.minus(Duration.ofMinutes(3));
    Instant voll = JETZT.minus(Duration.ofMinutes(30));
    Verbindung verbindung = speicher.lege(verbindung(1L, zuletzt, voll, null));

    // When
    job.run();

    // Then
    verify(abgleich).inkrementellAbgleichen(verbindung, zuletzt.minus(AbgleichJob.UEBERLAPPUNG));
    verify(abgleich, never()).vollAbgleichen(any(), any());
    Verbindung danach = speicher.finde(1L).orElseThrow();
    assertThat(danach.letzterAbgleichAm()).isEqualTo(JETZT);
    assertThat(danach.letzterVollabgleichAm()).isEqualTo(voll);
    assertThat(speicher.standAm).containsEntry(1L, JETZT);
  }

  @Test
  void nachDemEingestelltenAbstand_folgtWiederEinVollabgleich() {
    // Given: der letzte Vollabgleich liegt genau 60 Minuten zurück
    Instant voll = JETZT.minus(Duration.ofMinutes(60));
    Verbindung verbindung =
        speicher.lege(verbindung(1L, JETZT.minus(Duration.ofMinutes(5)), voll, null));

    // When
    job.run();

    // Then
    verify(abgleich).vollAbgleichen(verbindung, voll.minus(AbgleichJob.UEBERLAPPUNG));
    assertThat(speicher.finde(1L).orElseThrow().letzterVollabgleichAm()).isEqualTo(JETZT);
  }

  @Test
  void innerhalbDesMindestabstands_gibtEsKeinenAnspruchUndKeinenAbruf() {
    // Given: vor einer Minute abgeglichen, Mindestabstand 2 Minuten
    speicher.lege(verbindung(1L, JETZT.minus(Duration.ofMinutes(1)), JETZT, null));

    // When
    job.run();

    // Then
    verifyNoInteractions(abgleich);
    assertThat(speicher.ansprueche).isZero();
  }

  @Test
  void anspruch_scheitert_wennEinAndererKnotenZwischenLesenUndAnspruchAbgeglichenHat() {
    // Given: Zwischen dem Lesen der Verbindung und dem Anspruch hat ein anderer Knoten beansprucht.
    speicher.lege(verbindung(1L, JETZT.minus(Duration.ofMinutes(5)), JETZT, null));
    speicher.vorDemAnspruch = () -> speicher.setzeAbgleich(1L, JETZT.minusSeconds(1));

    // When
    job.run();

    // Then
    verifyNoInteractions(abgleich);
    assertThat(speicher.finde(1L).orElseThrow().letzterAbgleichAm())
        .isEqualTo(JETZT.minusSeconds(1));
  }

  @Test
  void anspruch_scheitert_auchVorDemErstenAbgleich_wennEinAndererKnotenZuvorBeansprucht() {
    // Given: noch nie abgeglichen; zwischen Lesen und Anspruch beansprucht ein anderer Knoten.
    speicher.lege(verbindung(1L, null, null, null));
    speicher.vorDemAnspruch = () -> speicher.setzeAbgleich(1L, JETZT.minusSeconds(1));

    // When
    job.run();

    // Then
    verifyNoInteractions(abgleich);
    assertThat(speicher.ansprueche).isZero();
    assertThat(speicher.finde(1L).orElseThrow().letzterAbgleichAm())
        .isEqualTo(JETZT.minusSeconds(1));
  }

  @Test
  void zweiKnoten_gleichenEinProjektNurEinmalAb() {
    // Given: zwei Knoten mit demselben Speicher; der erste gleicht ab, während der zweite startet.
    speicher.lege(verbindung(1L, JETZT.minus(Duration.ofMinutes(5)), JETZT, null));
    AbgleichService zweiterDienst = mock(AbgleichService.class);
    AbgleichJob zweiterKnoten = neuerJob(zweiterDienst);
    doAnswer(
            aufruf -> {
              // der zweite Knoten läuft, während der erste noch bei GitLab liest
              zweiterKnoten.run();
              return null;
            })
        .when(abgleich)
        .inkrementellAbgleichen(any(), any());

    // When
    job.run();

    // Then
    verify(abgleich).inkrementellAbgleichen(any(), any());
    verifyNoInteractions(zweiterDienst);
    assertThat(speicher.ansprueche).isEqualTo(1);
  }

  @Test
  void gitLabAntwortetNicht_fehlerSteht_undDerLetzteAbgleichBleibt() {
    // Given
    Instant zuletzt = JETZT.minus(Duration.ofMinutes(5));
    Verbindung verbindung = speicher.lege(verbindung(1L, zuletzt, JETZT, null));
    doThrow(new FremdAbrufException("GitLab ist nicht erreichbar"))
        .when(abgleich)
        .inkrementellAbgleichen(eq(verbindung), any());

    // When
    job.run();

    // Then
    Verbindung danach = speicher.finde(1L).orElseThrow();
    assertThat(danach.letzterAbgleichAm()).isEqualTo(zuletzt);
    assertThat(danach.letzterVollabgleichAm()).isEqualTo(JETZT);
    assertThat(danach.letzterFehler()).isEqualTo("GitLab ist nicht erreichbar");
    assertThat(speicher.standAm).doesNotContainKey(1L);
  }

  @Test
  void ersterVollabgleichScheitert_derLetzteAbgleichBleibtLeer_undEinOhneGrundGemeldeterFehler() {
    // Given: Die Meldung ist leer und länger als die Spalte erlaubt, wo sie einen Grund trägt.
    Verbindung ohneGrund = speicher.lege(verbindung(1L, null, null, null));
    Verbindung langerGrund = speicher.lege(verbindung(2L, null, null, null));
    doThrow(new FremdAbrufException(null)).when(abgleich).vollAbgleichen(eq(ohneGrund), any());
    doThrow(new FremdAbrufException("x".repeat(1200)))
        .when(abgleich)
        .vollAbgleichen(eq(langerGrund), any());

    // When
    job.run();

    // Then
    Verbindung erste = speicher.finde(1L).orElseThrow();
    assertThat(erste.letzterAbgleichAm()).isNull();
    assertThat(erste.letzterVollabgleichAm()).isNull();
    assertThat(erste.letzterFehler()).isEqualTo(AbgleichJob.FEHLER_OHNE_GRUND);
    assertThat(speicher.finde(2L).orElseThrow().letzterFehler()).hasSize(1000);
  }

  @Test
  void einUnerwarteterFehler_wirdAlsFehlerVermerkt_undDieUebrigenProjekteLaufenWeiter() {
    // Given
    Verbindung kaputt =
        speicher.lege(verbindung(1L, JETZT.minus(Duration.ofHours(1)), JETZT, null));
    Verbindung gesund =
        speicher.lege(verbindung(2L, JETZT.minus(Duration.ofMinutes(5)), JETZT, null));
    doThrow(new IllegalStateException("Spiegel-Board ohne Spalte"))
        .when(abgleich)
        .inkrementellAbgleichen(eq(kaputt), any());

    // When
    job.run();

    // Then
    assertThat(speicher.finde(1L).orElseThrow().letzterFehler())
        .isEqualTo(AbgleichJob.FEHLER_INTERN);
    assertThat(speicher.finde(1L).orElseThrow().letzterAbgleichAm())
        .isEqualTo(JETZT.minus(Duration.ofHours(1)));
    verify(abgleich).inkrementellAbgleichen(eq(gesund), any());
    assertThat(speicher.finde(2L).orElseThrow().letzterAbgleichAm()).isEqualTo(JETZT);
  }

  @Test
  void einFehlerDesSpeichers_haeltDieUebrigenProjekteNichtAuf() {
    // Given
    speicher.lege(verbindung(1L, null, null, null));
    Verbindung gesund = speicher.lege(verbindung(2L, null, null, null));
    speicher.kaputt = 1L;

    // When
    job.run();

    // Then
    verify(abgleich).vollAbgleichen(gesund, ANGEBUNDEN);
  }

  @Test
  void eineLeereMeldungDesFremdenSystems_wirdAlsFehlerOhneGrundVermerkt() {
    // Given: Die Meldung besteht nur aus Leerzeichen.
    Verbindung verbindung = speicher.lege(verbindung(1L, null, null, null));
    doThrow(new FremdAbrufException("   ")).when(abgleich).vollAbgleichen(eq(verbindung), any());

    // When
    job.run();

    // Then
    assertThat(speicher.finde(1L).orElseThrow().letzterFehler())
        .isEqualTo(AbgleichJob.FEHLER_OHNE_GRUND);
  }

  @Test
  void eineMeldungMitGenauSpaltenlaenge_bleibtVollstaendig() {
    // Given
    Verbindung verbindung = speicher.lege(verbindung(1L, null, null, null));
    String meldung = "y".repeat(999) + "z";
    doThrow(new FremdAbrufException(meldung)).when(abgleich).vollAbgleichen(eq(verbindung), any());

    // When
    job.run();

    // Then
    assertThat(speicher.finde(1L).orElseThrow().letzterFehler()).isEqualTo(meldung);
  }

  @Test
  void einAndererKnotenGleichtZwischenAuswahlUndLesenAb_keinAnspruchUndKeinAbruf() {
    // Given: Bei der Auswahl war das Projekt fällig; vor dem Lesen hat ein anderer Knoten es
    // gerade abgeglichen, der Stand liegt also innerhalb des Mindestabstands.
    speicher.lege(verbindung(1L, JETZT.minus(Duration.ofMinutes(5)), JETZT, null));
    speicher.vorDemLesen = () -> speicher.setzeAbgleich(1L, JETZT.minusSeconds(10));

    // When
    job.run();

    // Then
    verifyNoInteractions(abgleich);
    assertThat(speicher.ansprueche).isZero();
  }

  @Test
  void eineZwischenzeitlichEntfernteVerbindung_wirdUebersprungen() {
    // Given
    speicher.lege(verbindung(1L, null, null, null));
    speicher.vorDemLesen = () -> speicher.verbindungen.remove(1L);

    // When
    job.run();

    // Then: übersprungen, nicht gescheitert — es steht keine Warnung im Protokoll.
    verifyNoInteractions(abgleich);
    assertThat(protokoll.list).isEmpty();
  }

  private static Verbindung verbindung(
      long projectId,
      @Nullable Instant letzterAbgleich,
      @Nullable Instant letzterVoll,
      @Nullable String fehler) {
    return new Verbindung(
        projectId,
        FremdBoardArt.GITLAB,
        "https://gitlab.example.org",
        "gruppe/projekt",
        "geheim",
        "kit-bot",
        StatusLabels.STANDARD,
        projectId * 10,
        ANGEBUNDEN,
        letzterAbgleich,
        letzterVoll,
        fehler);
  }

  /**
   * Speicher der Verbindungen im Arbeitsspeicher mit derselben Bedingung wie der Adapter: Ein
   * Anspruch gelingt nur, solange {@code letzter_abgleich_am} noch den gelesenen Wert trägt. Neben
   * der Verbindung führt er {@code stand_am} je Projekt (Issue #2222), das nur ein gelungener
   * Abgleich setzt.
   */
  private static final class Speicher implements VerbindungRepository {

    private final Map<Long, Verbindung> verbindungen = new HashMap<>();
    private final Map<Long, Instant> standAm = new HashMap<>();
    private int ansprueche;
    private Runnable vorDemAnspruch = () -> {};
    private Runnable vorDemLesen = () -> {};
    private @Nullable Long kaputt;

    Verbindung lege(Verbindung verbindung) {
      verbindungen.put(verbindung.projectId(), verbindung);
      return verbindung;
    }

    void setzeAbgleich(long projectId, @Nullable Instant zeitpunkt) {
      Verbindung v = verbindungen.get(projectId);
      lege(mit(v, zeitpunkt, v.letzterVollabgleichAm(), v.letzterFehler()));
    }

    @Override
    public void speichere(Verbindung verbindung) {
      lege(verbindung);
    }

    @Override
    public Optional<Verbindung> finde(long projectId) {
      vorDemLesen.run();
      if (Objects.equals(kaputt, projectId)) {
        throw new IllegalStateException("Datenbank weg");
      }
      return Optional.ofNullable(verbindungen.get(projectId));
    }

    @Override
    public boolean existiert(long projectId) {
      return verbindungen.containsKey(projectId);
    }

    @Override
    public List<Long> faellige(Instant grenze) {
      List<Verbindung> faellig = new ArrayList<>();
      for (Verbindung v : verbindungen.values()) {
        if (v.letzterAbgleichAm() == null || !v.letzterAbgleichAm().isAfter(grenze)) {
          faellig.add(v);
        }
      }
      faellig.sort(Comparator.comparingLong(Verbindung::projectId));
      return faellig.stream().map(Verbindung::projectId).toList();
    }

    @Override
    public boolean beanspruchen(long projectId, @Nullable Instant bisher, Instant jetzt) {
      vorDemAnspruch.run();
      vorDemAnspruch = () -> {};
      Verbindung v = verbindungen.get(projectId);
      if (v == null || !Objects.equals(v.letzterAbgleichAm(), bisher)) {
        return false;
      }
      ansprueche++;
      setzeAbgleich(projectId, jetzt);
      return true;
    }

    @Override
    public void abgleichGelungen(
        long projectId, Instant beansprucht, @Nullable Instant vollabgleichAm) {
      standAm.merge(projectId, beansprucht, (alt, neu) -> neu.isAfter(alt) ? neu : alt);
      Verbindung v = verbindungen.get(projectId);
      lege(
          mit(
              v,
              v.letzterAbgleichAm(),
              vollabgleichAm == null ? v.letzterVollabgleichAm() : vollabgleichAm,
              null));
    }

    @Override
    public void abgleichGescheitert(
        long projectId, Instant beansprucht, @Nullable Instant bisher, String fehler) {
      Verbindung v = verbindungen.get(projectId);
      Instant abgleich =
          Objects.equals(v.letzterAbgleichAm(), beansprucht) ? bisher : v.letzterAbgleichAm();
      lege(mit(v, abgleich, v.letzterVollabgleichAm(), fehler));
    }

    private static Verbindung mit(
        Verbindung v, @Nullable Instant abgleich, @Nullable Instant voll, @Nullable String fehler) {
      return new Verbindung(
          v.projectId(),
          v.art(),
          v.basisUrl(),
          v.projektPfad(),
          v.zugangVerschluesselt(),
          v.kitKonto(),
          v.statuslabels(),
          v.boardId(),
          v.angebundenAm(),
          abgleich,
          voll,
          fehler);
    }
  }
}
