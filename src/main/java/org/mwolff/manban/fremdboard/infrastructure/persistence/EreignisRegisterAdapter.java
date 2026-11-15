package org.mwolff.manban.fremdboard.infrastructure.persistence;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.mwolff.manban.fremdboard.application.EreignisRegister;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link EreignisRegister}-Ports über {@code fremdboard_ereignis} (Issue #2167). Der
 * Primärschlüssel hält jedes Ereignis einmal fest; ein zweiter Eintrag derselben Kennung wartet auf
 * die Transaktion des ersten und fällt danach still weg.
 */
@Component
class EreignisRegisterAdapter implements EreignisRegister {

  private final NamedParameterJdbcTemplate jdbc;

  EreignisRegisterAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Set<String> bekannte(long projectId, Collection<String> kennungen) {
    if (kennungen.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT extern_id FROM fremdboard_ereignis"
                + " WHERE project_id = :projectId AND extern_id IN (:kennungen)",
            Map.of("projectId", projectId, "kennungen", kennungen),
            String.class));
  }

  @Override
  public boolean eintragen(long projectId, String art, String kennung) {
    return jdbc.update(
            "INSERT INTO fremdboard_ereignis (project_id, extern_id, art)"
                + " VALUES (:projectId, :kennung, :art) ON CONFLICT DO NOTHING",
            new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("kennung", kennung)
                .addValue("art", art))
        == 1;
  }
}
