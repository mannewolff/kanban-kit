package org.mwolff.manban.fremdboard.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.fremdboard.application.EreignisRegister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Das Register der übernommenen Ereignisse gegen echtes PostgreSQL (Issue #2167, Abdeckung Issue
 * #2191): Ein neues Ereignis wird eingetragen, ein schon bekanntes fällt still weg.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EreignisRegisterAdapterIT extends AbstractIntegrationTest {

  @Autowired private EreignisRegister register;
  @Autowired private DataSource dataSource;

  @Test
  void einNeuesEreignisWirdEingetragen() {
    long projekt = projekt();

    assertThat(register.eintragen(projekt, "kommentar", "e-1")).isTrue();
    assertThat(register.bekannte(projekt, List.of("e-1", "e-2"))).containsExactly("e-1");
  }

  @Test
  void einSchonEingetragenesEreignisFaelltStillWeg() {
    long projekt = projekt();
    register.eintragen(projekt, "kommentar", "e-1");

    assertThat(register.eintragen(projekt, "kommentar", "e-1")).isFalse();
  }

  @Test
  void ohneKennungenIstNichtsBekannt() {
    assertThat(register.bekannte(projekt(), List.of())).isEmpty();
  }

  private long projekt() {
    String name = "ereignis-" + UUID.randomUUID();
    long besitzer =
        id(
            "INSERT INTO app_user (email, display_name) VALUES (?, 'N') RETURNING id",
            name + "@example.com");
    return id(
        "INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id", name, besitzer);
  }

  private long id(String sql, Object... argumente) {
    Long erzeugt = new JdbcTemplate(dataSource).queryForObject(sql, Long.class, argumente);
    return erzeugt == null ? 0L : erzeugt;
  }
}
