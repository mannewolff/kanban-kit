package org.mwolff.manban.fremdboard.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.fremdboard.application.VerbindungRepository;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter des {@link VerbindungRepository}-Ports über {@code fremdboard_verbindung} (Issue #2156).
 *
 * <p>Die Statuslabels baut und liest Postgres selbst ({@code jsonb_build_object}, {@code ->>}): Die
 * Spalte hat vier feste Schlüssel, eine JSON-Bibliothek brächte nur einen Fehlerpfad dazu.
 *
 * <p>Der Anspruch des Abgleichs (Issue #2168, Plan #2152 A7) ist ein bedingtes UPDATE unter einer
 * transaktionsgebundenen Advisory-Sperre je Projekt, wie in {@code KanbanAccessTokenJpaRepository}.
 * Die Bedingung allein trüge schon; die Sperre hält zwei Knoten auch dann hintereinander, wenn die
 * Zeile gerade neu geschrieben wird. Der Schlüssel ist ein Hash über eine feste Vorsilbe und die
 * Projekt-ID; eine Kollision kostet nur Wartezeit.
 */
@Component
class VerbindungRepositoryAdapter implements VerbindungRepository {

  private static final String PROJECT_ID = "projectId";

  private static final String SPALTEN =
      "project_id, art, basis_url, projekt_pfad, zugang_verschluesselt, kit_konto,"
          + " statuslabels->>'backlog' AS label_backlog, statuslabels->>'ready' AS label_ready,"
          + " statuslabels->>'inProgress' AS label_in_progress,"
          + " statuslabels->>'inReview' AS label_in_review, board_id, angebunden_am,"
          + " letzter_abgleich_am, letzter_vollabgleich_am, letzter_fehler";

  private final NamedParameterJdbcTemplate jdbc;

  VerbindungRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void speichere(Verbindung verbindung) {
    StatusLabels labels = verbindung.statuslabels();
    MapSqlParameterSource werte =
        new MapSqlParameterSource()
            .addValue(PROJECT_ID, verbindung.projectId())
            .addValue("art", verbindung.art().name())
            .addValue("basisUrl", verbindung.basisUrl())
            .addValue("projektPfad", verbindung.projektPfad())
            .addValue("zugang", verbindung.zugangVerschluesselt())
            .addValue("kitKonto", verbindung.kitKonto())
            .addValue("backlog", labels.backlog())
            .addValue("ready", labels.ready())
            .addValue("inProgress", labels.inProgress())
            .addValue("inReview", labels.inReview())
            .addValue("boardId", verbindung.boardId())
            .addValue("angebundenAm", zeit(verbindung.angebundenAm()))
            .addValue("letzterAbgleichAm", zeit(verbindung.letzterAbgleichAm()))
            .addValue("letzterVollabgleichAm", zeit(verbindung.letzterVollabgleichAm()))
            .addValue("letzterFehler", verbindung.letzterFehler());
    jdbc.update(
        "INSERT INTO fremdboard_verbindung (project_id, art, basis_url, projekt_pfad,"
            + " zugang_verschluesselt, kit_konto, statuslabels, board_id, angebunden_am,"
            + " letzter_abgleich_am, letzter_vollabgleich_am, letzter_fehler)"
            + " VALUES (:projectId, :art, :basisUrl, :projektPfad, :zugang, :kitKonto,"
            + " jsonb_build_object('backlog', CAST(:backlog AS text),"
            + " 'ready', CAST(:ready AS text), 'inProgress', CAST(:inProgress AS text),"
            + " 'inReview', CAST(:inReview AS text)),"
            + " :boardId, :angebundenAm, :letzterAbgleichAm, :letzterVollabgleichAm,"
            + " :letzterFehler)"
            + " ON CONFLICT (project_id) DO UPDATE SET art = EXCLUDED.art,"
            + " basis_url = EXCLUDED.basis_url, projekt_pfad = EXCLUDED.projekt_pfad,"
            + " zugang_verschluesselt = EXCLUDED.zugang_verschluesselt,"
            + " kit_konto = EXCLUDED.kit_konto, statuslabels = EXCLUDED.statuslabels,"
            + " board_id = EXCLUDED.board_id, angebunden_am = EXCLUDED.angebunden_am,"
            + " letzter_abgleich_am = EXCLUDED.letzter_abgleich_am,"
            + " letzter_vollabgleich_am = EXCLUDED.letzter_vollabgleich_am,"
            + " letzter_fehler = EXCLUDED.letzter_fehler",
        werte);
  }

  @Override
  public Optional<Verbindung> finde(long projectId) {
    return jdbc
        .query(
            "SELECT " + SPALTEN + " FROM fremdboard_verbindung WHERE project_id = :projectId",
            Map.of(PROJECT_ID, projectId),
            (rs, zeile) -> zuVerbindung(rs))
        .stream()
        .findFirst();
  }

  @Override
  public boolean existiert(long projectId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM fremdboard_verbindung WHERE project_id = :projectId)",
            Map.of(PROJECT_ID, projectId),
            Boolean.class));
  }

  @Override
  public List<Long> faellige(Instant grenze) {
    return jdbc.queryForList(
        "SELECT project_id FROM fremdboard_verbindung"
            + " WHERE letzter_abgleich_am IS NULL OR letzter_abgleich_am <= :grenze"
            + " ORDER BY letzter_abgleich_am NULLS FIRST, project_id",
        new MapSqlParameterSource("grenze", zeit(grenze)),
        Long.class);
  }

  @Override
  @Transactional
  public boolean beanspruchen(long projectId, @Nullable Instant bisher, Instant jetzt) {
    jdbc.queryForObject(
        "SELECT 1 FROM (SELECT pg_advisory_xact_lock("
            + "hashtextextended('fremdboard-abgleich:' || :projectId, 0))) gesperrt",
        Map.of(PROJECT_ID, projectId),
        Integer.class);
    return jdbc.update(
            "UPDATE fremdboard_verbindung SET letzter_abgleich_am = :jetzt"
                + " WHERE project_id = :projectId"
                + " AND letzter_abgleich_am IS NOT DISTINCT FROM CAST(:bisher AS timestamptz)",
            new MapSqlParameterSource()
                .addValue(PROJECT_ID, projectId)
                .addValue("bisher", zeit(bisher))
                .addValue("jetzt", zeit(jetzt)))
        == 1;
  }

  @Override
  public void abgleichGelungen(
      long projectId, Instant beansprucht, @Nullable Instant vollabgleichAm) {
    jdbc.update(
        "UPDATE fremdboard_verbindung SET letzter_fehler = NULL,"
            + " letzter_vollabgleich_am ="
            + " COALESCE(CAST(:voll AS timestamptz), letzter_vollabgleich_am),"
            + " stand_am = GREATEST(stand_am, CAST(:beansprucht AS timestamptz))"
            + " WHERE project_id = :projectId",
        new MapSqlParameterSource()
            .addValue(PROJECT_ID, projectId)
            .addValue("beansprucht", zeit(beansprucht))
            .addValue("voll", zeit(vollabgleichAm)));
  }

  @Override
  public void abgleichGescheitert(
      long projectId, Instant beansprucht, @Nullable Instant bisher, String fehler) {
    jdbc.update(
        "UPDATE fremdboard_verbindung SET letzter_fehler = :fehler,"
            + " letzter_abgleich_am = CASE WHEN letzter_abgleich_am = :beansprucht"
            + " THEN CAST(:bisher AS timestamptz) ELSE letzter_abgleich_am END"
            + " WHERE project_id = :projectId",
        new MapSqlParameterSource()
            .addValue(PROJECT_ID, projectId)
            .addValue("fehler", fehler)
            .addValue("beansprucht", zeit(beansprucht))
            .addValue("bisher", zeit(bisher)));
  }

  private static Verbindung zuVerbindung(ResultSet rs) throws SQLException {
    return new Verbindung(
        rs.getLong("project_id"),
        FremdBoardArt.valueOf(rs.getString("art")),
        rs.getString("basis_url"),
        rs.getString("projekt_pfad"),
        rs.getString("zugang_verschluesselt"),
        rs.getString("kit_konto"),
        new StatusLabels(
            rs.getString("label_backlog"),
            rs.getString("label_ready"),
            rs.getString("label_in_progress"),
            rs.getString("label_in_review")),
        rs.getLong("board_id"),
        rs.getObject("angebunden_am", OffsetDateTime.class).toInstant(),
        instant(rs, "letzter_abgleich_am"),
        instant(rs, "letzter_vollabgleich_am"),
        rs.getString("letzter_fehler"));
  }

  private static @Nullable Instant instant(ResultSet rs, String spalte) throws SQLException {
    OffsetDateTime wert = rs.getObject(spalte, OffsetDateTime.class);
    return wert == null ? null : wert.toInstant();
  }

  private static @Nullable OffsetDateTime zeit(@Nullable Instant zeitpunkt) {
    return zeitpunkt == null ? null : zeitpunkt.atOffset(ZoneOffset.UTC);
  }
}
