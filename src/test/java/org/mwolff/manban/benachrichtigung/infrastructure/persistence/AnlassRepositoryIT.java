package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TransactionRace;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.FaelligeZustellung;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LabelAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LaufAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.NeueZustellung;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungenRepository;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungenRepository.EingeschaltetePerson;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Das Anlegen von Anlass und Zustellung gegen echtes PostgreSQL (Issue #2077, Plan #2070 E4):
 * {@code ON CONFLICT DO NOTHING} lässt parallele Takte nebeneinander bestehen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AnlassRepositoryIT extends AbstractIntegrationTest {

  private static final Instant EINTRITT = Instant.parse("2026-11-09T08:00:00Z");
  private static final Instant TAKT = Instant.parse("2026-11-09T08:01:00Z");

  @Autowired private AnlassRepository anlaesse;
  @Autowired private BenachrichtigungEinstellungenRepository einstellungen;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private long person;
  private long projekt;
  private long karte;
  private long lauf;

  @BeforeEach
  void legeBestandAn() {
    jdbc = new JdbcTemplate(dataSource);
    person = konto("person@example.com");
    projekt = id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", person);
    long board = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projekt);
    long spalte =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Ready', 0)"
                + " RETURNING id",
            board);
    karte =
        id(
            "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
                + " VALUES (?, ?, 1, 'K', 0) RETURNING id",
            board,
            spalte);
    lauf =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at)"
                + " VALUES (?, now(), 'IMPLEMENTATION', 1000, 1, 0, 0, now()) RETURNING id",
            projekt);
  }

  @Test
  void zweiGleichzeitigeAnlageVersucheErgebenEineZeileUndKeinenFehler() throws Exception {
    LaufAnlass anlass = laufAnlass(AnlassArt.STOERUNG, null);

    TransactionRace.Result rennen =
        new TransactionRace(transactionManager, dataSource)
            .run(
                () -> anlaesse.legeAnlassZumLaufAn(anlass),
                () -> anlaesse.legeAnlassZumLaufAn(anlass));

    assertThat(rennen.firstFailure()).isNull();
    assertThat(rennen.secondFailure()).isNull();
    assertThat(anzahl("benachrichtigung_anlass")).isEqualTo(1);
  }

  @Test
  void dieAnlageLiefertDieKennungUndSpeichertAlleWerte() {
    Optional<Long> kennung = imTakt(laufAnlass(AnlassArt.LAUF_WARTET, karte));

    assertThat(kennung).isPresent();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM benachrichtigung_anlass WHERE id = ? AND art = 'LAUF_WARTET'"
                    + " AND project_id = ? AND lauf_id = ? AND card_id = ?"
                    + " AND eingetreten_at = ? AND angelegt_at = ? AND label_event_id IS NULL",
                Integer.class,
                kennung.orElseThrow(),
                projekt,
                lauf,
                karte,
                java.sql.Timestamp.from(EINTRITT),
                java.sql.Timestamp.from(TAKT)))
        .isEqualTo(1);
  }

  @Test
  void einAnlassOhneKarteBleibtOhneKarte() {
    imTakt(laufAnlass(AnlassArt.UEBERSICHT, null));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM benachrichtigung_anlass WHERE card_id IS NULL",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void einZweiterAnlassDerselbenArtLiefertNichtsEineAndereArtIstEigen() {
    Optional<Long> erster = imTakt(laufAnlass(AnlassArt.STOERUNG, null));

    assertThat(imTakt(laufAnlass(AnlassArt.STOERUNG, null))).isEmpty();
    assertThat(imTakt(laufAnlass(AnlassArt.UEBERSICHT, null))).isPresent().isNotEqualTo(erster);
    assertThat(anzahl("benachrichtigung_anlass")).isEqualTo(2);
  }

  @Test
  void eineZustellungStartetOffenMitIhrenWerten() {
    long anlass = imTakt(laufAnlass(AnlassArt.UEBERSICHT, null)).orElseThrow();

    zustellung(new NeueZustellung(anlass, person, EINTRITT, true));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM benachrichtigung_zustellung WHERE anlass_id = ?"
                    + " AND user_id = ? AND faellig_ab = ? AND verschoben AND zustand = 'OFFEN'",
                Integer.class,
                anlass,
                person,
                java.sql.Timestamp.from(EINTRITT)))
        .isEqualTo(1);
  }

  @Test
  void eineZweiteZustellungAnDieselbePersonWirdStillUebergangen() {
    long anlass = imTakt(laufAnlass(AnlassArt.UEBERSICHT, null)).orElseThrow();

    zustellung(new NeueZustellung(anlass, person, EINTRITT, false));
    zustellung(new NeueZustellung(anlass, person, TAKT, true));

    assertThat(anzahl("benachrichtigung_zustellung")).isEqualTo(1);
  }

  @Test
  void dieEingeschaltetenPersonenEinesProjektsMitIhremZeitpunkt() {
    long andere = konto("andere@example.com");
    long fremdesProjekt =
        id("INSERT INTO project (name, owner_user_id) VALUES ('F', ?) RETURNING id", andere);
    Instant seit = EINTRITT.truncatedTo(ChronoUnit.MICROS);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              einstellungen.schaltePersonEin(person, projekt, seit);
              einstellungen.schaltePersonEin(andere, fremdesProjekt, seit);
            });

    List<EingeschaltetePerson> personen = einstellungen.eingeschaltetePersonen(projekt);

    assertThat(personen).containsExactly(new EingeschaltetePerson(person, seit));
  }

  @Test
  void einLabelAnlassLiefertDieKennungUndSpeichertAlleWerte() {
    long ereignis = labelEreignis();

    Optional<Long> kennung = imLabelTakt(labelAnlass(ereignis));

    assertThat(kennung).isPresent();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM benachrichtigung_anlass WHERE id = ? AND art = 'KARTE_FRAGE'"
                    + " AND project_id = ? AND label_event_id = ? AND card_id = ?"
                    + " AND eingetreten_at = ? AND angelegt_at = ? AND lauf_id IS NULL",
                Integer.class,
                kennung.orElseThrow(),
                projekt,
                ereignis,
                karte,
                java.sql.Timestamp.from(EINTRITT),
                java.sql.Timestamp.from(TAKT)))
        .isEqualTo(1);
  }

  @Test
  void einZweiterAnlassZumSelbenLabelEreignisLiefertNichts() {
    LabelAnlass anlass = labelAnlass(labelEreignis());

    assertThat(imLabelTakt(anlass)).isPresent();
    assertThat(imLabelTakt(anlass)).isEmpty();
    assertThat(anzahl("benachrichtigung_anlass")).isEqualTo(1);
  }

  @Test
  void zweiLabelEreignisseSindZweiAnlaesse() {
    assertThat(imLabelTakt(labelAnlass(labelEreignis()))).isPresent();
    assertThat(imLabelTakt(labelAnlass(labelEreignis()))).isPresent();
    assertThat(anzahl("benachrichtigung_anlass")).isEqualTo(2);
  }

  @Test
  void einAnlassZaehltNurFuerSeineKarteUndArtAuchAusEinemLauf() {
    assertThat(anlaesse.gibtAnlassZurKarte(karte, AnlassArt.LAUF_WARTET)).isFalse();

    imTakt(laufAnlass(AnlassArt.LAUF_WARTET, karte));

    assertThat(anlaesse.gibtAnlassZurKarte(karte, AnlassArt.LAUF_WARTET)).isTrue();
    assertThat(anlaesse.gibtAnlassZurKarte(karte, AnlassArt.KARTE_FRAGE)).isFalse();
    assertThat(anlaesse.gibtAnlassZurKarte(karte + 1, AnlassArt.LAUF_WARTET)).isFalse();
  }

  @Test
  void faelligSindOffeneZustellungenBisJetztMitIhrenAnlassWerten() {
    long ausLauf = imTakt(laufAnlass(AnlassArt.LAUF_WARTET, karte)).orElseThrow();
    long ausLabel = imLabelTakt(labelAnlass(labelEreignis())).orElseThrow();
    long spaeter = imTakt(laufAnlass(AnlassArt.UEBERSICHT, null)).orElseThrow();
    zustellung(new NeueZustellung(ausLauf, person, EINTRITT, true));
    zustellung(new NeueZustellung(ausLabel, person, TAKT, false));
    zustellung(new NeueZustellung(spaeter, person, TAKT.plusSeconds(1), false));

    List<FaelligeZustellung> faellig =
        new TransactionTemplate(transactionManager)
            .execute(status -> anlaesse.faelligeZustellungen(TAKT));

    assertThat(faellig)
        .containsExactly(
            new FaelligeZustellung(
                zustellungsId(ausLauf), person, true, AnlassArt.LAUF_WARTET, projekt, lauf, karte),
            new FaelligeZustellung(
                zustellungsId(ausLabel),
                person,
                false,
                AnlassArt.KARTE_FRAGE,
                projekt,
                null,
                karte));
  }

  @Test
  void eineAndereTransaktionUeberspringtGesperrteZustellungen() {
    long anlass = imTakt(laufAnlass(AnlassArt.UEBERSICHT, null)).orElseThrow();
    zustellung(new NeueZustellung(anlass, person, EINTRITT, false));
    TransactionTemplate neue = new TransactionTemplate(transactionManager);
    neue.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    List<FaelligeZustellung> daneben =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  assertThat(anlaesse.faelligeZustellungen(TAKT)).hasSize(1);
                  return neue.execute(innen -> anlaesse.faelligeZustellungen(TAKT));
                });

    assertThat(daneben).isEmpty();
  }

  @Test
  void eingeplantUndEntfallenGeltenNurFuerOffeneZeilen() {
    long eins = imTakt(laufAnlass(AnlassArt.UEBERSICHT, null)).orElseThrow();
    long zwei = imTakt(laufAnlass(AnlassArt.STOERUNG, null)).orElseThrow();
    zustellung(new NeueZustellung(eins, person, EINTRITT, false));
    zustellung(new NeueZustellung(zwei, person, EINTRITT, false));
    long ersteZeile = zustellungsId(eins);
    long zweiteZeile = zustellungsId(zwei);

    TransactionTemplate takt = new TransactionTemplate(transactionManager);
    takt.executeWithoutResult(status -> anlaesse.setzeEingeplant(List.of(ersteZeile)));
    takt.executeWithoutResult(status -> anlaesse.setzeEntfallen(List.of(zweiteZeile)));
    takt.executeWithoutResult(status -> anlaesse.setzeEntfallen(List.of(ersteZeile)));
    takt.executeWithoutResult(status -> anlaesse.setzeEingeplant(List.of(zweiteZeile)));

    assertThat(zustand(ersteZeile)).isEqualTo("EINGEPLANT");
    assertThat(zustand(zweiteZeile)).isEqualTo("ENTFALLEN");
    List<FaelligeZustellung> faellig = takt.execute(status -> anlaesse.faelligeZustellungen(TAKT));
    assertThat(faellig).isEmpty();
  }

  @Test
  void eineLeereSammlungAendertNichts() {
    long anlass = imTakt(laufAnlass(AnlassArt.UEBERSICHT, null)).orElseThrow();
    zustellung(new NeueZustellung(anlass, person, EINTRITT, false));

    TransactionTemplate takt = new TransactionTemplate(transactionManager);
    takt.executeWithoutResult(status -> anlaesse.setzeEingeplant(List.of()));
    takt.executeWithoutResult(status -> anlaesse.setzeEntfallen(List.of()));

    assertThat(zustand(zustellungsId(anlass))).isEqualTo("OFFEN");
  }

  private long zustellungsId(long anlass) {
    return id("SELECT id FROM benachrichtigung_zustellung WHERE anlass_id = ?", anlass);
  }

  private String zustand(long zustellung) {
    return jdbc.queryForObject(
        "SELECT zustand FROM benachrichtigung_zustellung WHERE id = ?", String.class, zustellung);
  }

  private LabelAnlass labelAnlass(long ereignis) {
    return new LabelAnlass(AnlassArt.KARTE_FRAGE, projekt, ereignis, karte, EINTRITT, TAKT);
  }

  private long labelEreignis() {
    return id(
        "INSERT INTO card_label_event (card_id, label_name, created_at) VALUES (?, 'kit:klaeren',"
            + " now()) RETURNING id",
        karte);
  }

  private Optional<Long> imLabelTakt(LabelAnlass anlass) {
    return new TransactionTemplate(transactionManager)
        .execute(status -> anlaesse.legeAnlassZumLabelAn(anlass));
  }

  private LaufAnlass laufAnlass(AnlassArt art, @Nullable Long cardId) {
    return new LaufAnlass(art, projekt, lauf, cardId, EINTRITT, TAKT);
  }

  private Optional<Long> imTakt(LaufAnlass anlass) {
    return new TransactionTemplate(transactionManager)
        .execute(status -> anlaesse.legeAnlassZumLaufAn(anlass));
  }

  private void zustellung(NeueZustellung zustellung) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> anlaesse.legeZustellungAn(zustellung));
  }

  private long konto(String email) {
    return id("INSERT INTO app_user (email, display_name) VALUES (?, 'N') RETURNING id", email);
  }

  private int anzahl(String tabelle) {
    Integer anzahl = jdbc.queryForObject("SELECT count(*) FROM " + tabelle, Integer.class);
    return anzahl == null ? 0 : anzahl;
  }

  private long id(String sql, Object... argumente) {
    Long generated = jdbc.queryForObject(sql, Long.class, argumente);
    return generated == null ? 0L : generated;
  }
}
