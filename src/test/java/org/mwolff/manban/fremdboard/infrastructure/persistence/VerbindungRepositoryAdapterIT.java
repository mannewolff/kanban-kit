package org.mwolff.manban.fremdboard.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.fremdboard.application.VerbindungRepository;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.mwolff.manban.project.application.BoardQuelle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die gespeicherte Verbindung zum fremden Board und der Port {@link BoardQuelle} gegen echtes
 * PostgreSQL (Issue #2156, Plan #2152 A10).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VerbindungRepositoryAdapterIT extends AbstractIntegrationTest {

  private static final Instant ANGEBUNDEN = Instant.parse("2026-11-15T09:00:00Z");
  private static final Instant ABGLEICH = Instant.parse("2026-11-15T09:05:00Z");
  private static final Instant VOLLABGLEICH = Instant.parse("2026-11-15T10:00:00Z");
  private static final StatusLabels LABELS =
      new StatusLabels("Backlog", "Ready", "In progress", "In review");

  @Autowired private VerbindungRepository verbindungen;
  @Autowired private BoardQuelle boardQuelle;
  @Autowired private DataSource dataSource;

  @Test
  void eineGespeicherteVerbindungKommtVollstaendigZurueck() {
    Projekt projekt = projektMitBoard("Gespiegelt");
    Verbindung verbindung =
        new Verbindung(
            projekt.id(),
            FremdBoardArt.GITLAB,
            "https://gitlab.example.org",
            "gruppe/projekt",
            "v1:verschluesselt",
            "kit-bot",
            LABELS,
            projekt.board(),
            ANGEBUNDEN,
            ABGLEICH,
            VOLLABGLEICH,
            "zuletzt nicht erreichbar");

    verbindungen.speichere(verbindung);

    assertThat(verbindungen.finde(projekt.id())).contains(verbindung);
  }

  @Test
  void eineVerbindungOhneOptionaleAngabenKommtMitLeerenFeldernZurueck() {
    Projekt projekt = projektMitBoard("Neu");
    Verbindung verbindung = neueVerbindung(projekt);

    verbindungen.speichere(verbindung);

    assertThat(verbindungen.finde(projekt.id())).contains(verbindung);
  }

  @Test
  void erneutesSpeichernErsetztDieVerbindungDesProjekts() {
    Projekt projekt = projektMitBoard("Ersetzt");
    verbindungen.speichere(neueVerbindung(projekt));
    Verbindung nachAbgleich =
        new Verbindung(
            projekt.id(),
            FremdBoardArt.GITLAB,
            "https://gitlab.com",
            "gruppe/anderes",
            "v1:neu",
            "kit-konto",
            new StatusLabels("Offen", "Bereit", "Dran", "Pruefen"),
            projekt.board(),
            ANGEBUNDEN,
            ABGLEICH,
            null,
            null);

    verbindungen.speichere(nachAbgleich);

    assertThat(verbindungen.finde(projekt.id())).contains(nachAbgleich);
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject("SELECT count(*) FROM fremdboard_verbindung", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void ohneVerbindungIstNichtsZuFinden() {
    Projekt projekt = projektMitBoard("Eigen");

    assertThat(verbindungen.finde(projekt.id())).isEmpty();
  }

  @Test
  void istFremdNurFuerEinProjektMitVerbindung() {
    Projekt gespiegelt = projektMitBoard("Gespiegelt");
    Projekt eigen = projektMitBoard("Eigen");
    verbindungen.speichere(neueVerbindung(gespiegelt));

    assertThat(boardQuelle.istFremd(gespiegelt.id())).isTrue();
    assertThat(boardQuelle.istFremd(eigen.id())).isFalse();
    assertThat(boardQuelle.istFremd(Long.MAX_VALUE)).isFalse();
  }

  @Test
  void faellig_sindVerbindungenOhneAbgleichUndMitAbgleichBisZurGrenze_aeltesteZuerst() {
    Projekt nie = projektMitBoard("Nie");
    Projekt alt = projektMitBoard("Alt");
    Projekt grenze = projektMitBoard("Grenze");
    Projekt frisch = projektMitBoard("Frisch");
    verbindungen.speichere(mitAbgleich(nie, null));
    verbindungen.speichere(mitAbgleich(alt, ABGLEICH));
    verbindungen.speichere(mitAbgleich(grenze, VOLLABGLEICH));
    verbindungen.speichere(mitAbgleich(frisch, VOLLABGLEICH.plusSeconds(1)));

    assertThat(verbindungen.faellige(VOLLABGLEICH))
        .containsExactly(nie.id(), alt.id(), grenze.id());
  }

  @Test
  void anspruch_gelingtNurMitDemGelesenenStand() {
    Projekt projekt = projektMitBoard("Anspruch");
    verbindungen.speichere(mitAbgleich(projekt, ABGLEICH));

    assertThat(verbindungen.beanspruchen(projekt.id(), null, VOLLABGLEICH)).isFalse();
    assertThat(verbindungen.beanspruchen(projekt.id(), ABGLEICH, VOLLABGLEICH)).isTrue();
    assertThat(verbindungen.beanspruchen(projekt.id(), ABGLEICH, VOLLABGLEICH)).isFalse();
    assertThat(verbindungen.finde(projekt.id()).orElseThrow().letzterAbgleichAm())
        .isEqualTo(VOLLABGLEICH);
  }

  @Test
  void anspruch_vorDemErstenAbgleich() {
    Projekt projekt = projektMitBoard("Erster");
    verbindungen.speichere(mitAbgleich(projekt, null));

    assertThat(verbindungen.beanspruchen(projekt.id(), null, ABGLEICH)).isTrue();
    assertThat(verbindungen.beanspruchen(projekt.id(), null, ABGLEICH)).isFalse();
  }

  @Test
  void zweiKnoten_beanspruchenGleichzeitig_nurEinerGewinnt() throws Exception {
    Projekt projekt = projektMitBoard("Zwei");
    verbindungen.speichere(mitAbgleich(projekt, ABGLEICH));
    CountDownLatch start = new CountDownLatch(1);
    Callable<Boolean> anspruch =
        () -> {
          start.await();
          return verbindungen.beanspruchen(projekt.id(), ABGLEICH, VOLLABGLEICH);
        };
    try (ExecutorService knoten = Executors.newFixedThreadPool(2)) {
      Future<Boolean> erster = knoten.submit(anspruch);
      Future<Boolean> zweiter = knoten.submit(anspruch);
      start.countDown();

      assertThat(List.of(erster.get(), zweiter.get())).containsExactlyInAnyOrder(true, false);
    }
  }

  @Test
  void gelungen_loeschtDenFehler_undVermerktDenVollabgleichNurWennErLief() {
    Projekt projekt = projektMitBoard("Gelungen");
    Verbindung mitFehler =
        new Verbindung(
            projekt.id(),
            FremdBoardArt.GITLAB,
            "https://gitlab.com",
            "gruppe/projekt",
            "v1:verschluesselt",
            null,
            LABELS,
            projekt.board(),
            ANGEBUNDEN,
            ABGLEICH,
            ANGEBUNDEN,
            "nicht erreichbar");
    verbindungen.speichere(mitFehler);

    verbindungen.abgleichGelungen(projekt.id(), ABGLEICH, null);

    Verbindung inkrementell = verbindungen.finde(projekt.id()).orElseThrow();
    assertThat(inkrementell.letzterFehler()).isNull();
    assertThat(inkrementell.letzterVollabgleichAm()).isEqualTo(ANGEBUNDEN);
    assertThat(inkrementell.letzterAbgleichAm()).isEqualTo(ABGLEICH);

    verbindungen.abgleichGelungen(projekt.id(), VOLLABGLEICH, VOLLABGLEICH);

    assertThat(verbindungen.finde(projekt.id()).orElseThrow().letzterVollabgleichAm())
        .isEqualTo(VOLLABGLEICH);
  }

  @Test
  void gescheitert_setztDenFehler_undGibtDenAnspruchZurueck() {
    Projekt projekt = projektMitBoard("Gescheitert");
    verbindungen.speichere(mitAbgleich(projekt, ABGLEICH));
    verbindungen.beanspruchen(projekt.id(), ABGLEICH, VOLLABGLEICH);

    verbindungen.abgleichGescheitert(projekt.id(), VOLLABGLEICH, ABGLEICH, "nicht erreichbar");

    Verbindung danach = verbindungen.finde(projekt.id()).orElseThrow();
    assertThat(danach.letzterAbgleichAm()).isEqualTo(ABGLEICH);
    assertThat(danach.letzterFehler()).isEqualTo("nicht erreichbar");
  }

  @Test
  void gescheitert_laesstEinenSpaeterenAnspruchStehen() {
    Projekt projekt = projektMitBoard("Spaeter");
    verbindungen.speichere(mitAbgleich(projekt, VOLLABGLEICH));

    verbindungen.abgleichGescheitert(projekt.id(), ABGLEICH, null, "nicht erreichbar");

    Verbindung danach = verbindungen.finde(projekt.id()).orElseThrow();
    assertThat(danach.letzterAbgleichAm()).isEqualTo(VOLLABGLEICH);
    assertThat(danach.letzterFehler()).isEqualTo("nicht erreichbar");
  }

  @Test
  void standAm_bleibtBeiAnspruchUndScheiternUnveraendert() {
    Projekt projekt = projektMitBoard("OhneStand");
    verbindungen.speichere(mitAbgleich(projekt, null));

    verbindungen.beanspruchen(projekt.id(), null, ABGLEICH);
    assertThat(standAm(projekt)).isNull();

    verbindungen.abgleichGescheitert(projekt.id(), ABGLEICH, null, "nicht erreichbar");
    assertThat(standAm(projekt)).isNull();
  }

  @Test
  void standAm_folgtDemGelungenenAbgleich_undBleibtBeimScheiternStehen() {
    Projekt projekt = projektMitBoard("MitStand");
    verbindungen.speichere(mitAbgleich(projekt, null));
    verbindungen.beanspruchen(projekt.id(), null, ABGLEICH);

    verbindungen.abgleichGelungen(projekt.id(), ABGLEICH, ABGLEICH);
    assertThat(standAm(projekt)).isEqualTo(ABGLEICH);

    verbindungen.beanspruchen(projekt.id(), ABGLEICH, VOLLABGLEICH);
    assertThat(standAm(projekt)).isEqualTo(ABGLEICH);
    verbindungen.abgleichGescheitert(projekt.id(), VOLLABGLEICH, ABGLEICH, "nicht erreichbar");
    assertThat(standAm(projekt)).isEqualTo(ABGLEICH);
  }

  @Test
  void standAm_faelltBeiEinemAelterenAnspruchNichtZurueck() {
    Projekt projekt = projektMitBoard("Aelter");
    verbindungen.speichere(mitAbgleich(projekt, VOLLABGLEICH));
    verbindungen.abgleichGelungen(projekt.id(), VOLLABGLEICH, null);

    verbindungen.abgleichGelungen(projekt.id(), ABGLEICH, null);

    assertThat(standAm(projekt)).isEqualTo(VOLLABGLEICH);
  }

  @Test
  void speichern_laesstStandAmStehen() {
    Projekt projekt = projektMitBoard("Gespeichert");
    verbindungen.speichere(mitAbgleich(projekt, ABGLEICH));
    verbindungen.abgleichGelungen(projekt.id(), ABGLEICH, ABGLEICH);

    verbindungen.speichere(mitAbgleich(projekt, VOLLABGLEICH));

    assertThat(standAm(projekt)).isEqualTo(ABGLEICH);
  }

  private @Nullable Instant standAm(Projekt projekt) {
    return new JdbcTemplate(dataSource)
        .queryForObject(
            "SELECT stand_am FROM fremdboard_verbindung WHERE project_id = ?",
            (rs, zeile) -> {
              OffsetDateTime wert = rs.getObject("stand_am", OffsetDateTime.class);
              return wert == null ? null : wert.toInstant();
            },
            projekt.id());
  }

  private static Verbindung mitAbgleich(Projekt projekt, @Nullable Instant letzterAbgleich) {
    return new Verbindung(
        projekt.id(),
        FremdBoardArt.GITLAB,
        "https://gitlab.com",
        "gruppe/projekt",
        "v1:verschluesselt",
        null,
        LABELS,
        projekt.board(),
        ANGEBUNDEN,
        letzterAbgleich,
        null,
        null);
  }

  private static Verbindung neueVerbindung(Projekt projekt) {
    return new Verbindung(
        projekt.id(),
        FremdBoardArt.GITLAB,
        "https://gitlab.com",
        "gruppe/projekt",
        "v1:verschluesselt",
        null,
        LABELS,
        projekt.board(),
        ANGEBUNDEN,
        null,
        null,
        null);
  }

  /** Legt Besitzer, Projekt und Board an; liefert Projekt- und Board-Id. */
  private Projekt projektMitBoard(String name) {
    long besitzer =
        id(
            "INSERT INTO app_user (email, display_name) VALUES (?, 'N') RETURNING id",
            name + "@example.com");
    long projekt =
        id("INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id", name, besitzer);
    long board = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projekt);
    return new Projekt(projekt, board);
  }

  /** Projekt- und Board-Id einer Testeinrichtung. */
  private record Projekt(long id, long board) {}

  private long id(String sql, Object... argumente) {
    Long erzeugt = new JdbcTemplate(dataSource).queryForObject(sql, Long.class, argumente);
    return erzeugt == null ? 0L : erzeugt;
  }
}
