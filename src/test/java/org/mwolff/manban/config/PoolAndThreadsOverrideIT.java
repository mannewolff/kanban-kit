package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * Jede der vier Stellschrauben aus Issue #998 ist über ihre {@code MANBAN_*}-Variable
 * überschreibbar — ein Selbstbetreiber passt sie an seine Ausstattung an, ohne das Image neu zu
 * bauen.
 *
 * <p>Eigener Spring-Kontext, weil die Überschreibung genau das ist, was geprüft wird. Der Pool ist
 * dabei bewusst klein gehalten, damit dieser Kontext das Verbindungsbudget des geteilten
 * Postgres-Containers kaum belastet (Issue #900).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "MANBAN_DB_POOL_MAX=3",
      "MANBAN_DB_POOL_MIN_IDLE=1",
      "MANBAN_DB_CONNECTION_TIMEOUT_MS=2500",
      "MANBAN_SERVER_THREADS_MAX=42"
    })
class PoolAndThreadsOverrideIT extends AbstractIntegrationTest {

  @Autowired private DataSource dataSource;
  @Autowired private Environment environment;

  @Test
  void connectionPool_followsTheManbanVariables() throws Exception {
    HikariConfigMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariConfigMXBean();

    assertThat(pool.getMaximumPoolSize()).isEqualTo(3);
    assertThat(pool.getMinimumIdle()).isEqualTo(1);
    assertThat(pool.getConnectionTimeout()).isEqualTo(2_500L);
  }

  @Test
  void serverThreads_followTheManbanVariable() {
    ServerProperties server = Binder.get(environment).bind("server", ServerProperties.class).get();

    assertThat(server.getTomcat().getThreads().getMax()).isEqualTo(42);
  }
}
