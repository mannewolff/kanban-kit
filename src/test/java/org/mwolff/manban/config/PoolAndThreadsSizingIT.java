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
 * Verbindungspool und Server-Threads tragen die in {@code application.yml} gesetzten Startwerte,
 * nicht die Vorgaben von HikariCP (10) und Tomcat (200) — Issue #998, Plan #995 E15.
 *
 * <p>Die Kontext-Konfiguration ist bewusst dieselbe wie bei den übrigen {@code
 * WebEnvironment.NONE}-ITs, damit dieser Test keinen weiteren Spring-Kontext samt Verbindungspool
 * eröffnet (Issue #900). Ohne Webserver gibt es keine {@code ServerProperties}-Bean; die Threads
 * werden darum mit demselben {@link Binder} gelesen, mit dem Spring Boot sie an Tomcat bindet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PoolAndThreadsSizingIT extends AbstractIntegrationTest {

  @Autowired private DataSource dataSource;
  @Autowired private Environment environment;

  @Test
  void connectionPool_carriesTheConfiguredStartValues_notHikariDefaults() throws Exception {
    HikariConfigMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariConfigMXBean();

    assertThat(pool.getMaximumPoolSize()).isEqualTo(20);
    assertThat(pool.getMinimumIdle()).isEqualTo(5);
    assertThat(pool.getConnectionTimeout()).isEqualTo(5_000L);
  }

  @Test
  void serverThreads_carryTheConfiguredStartValue_notTomcatDefault() {
    ServerProperties server = Binder.get(environment).bind("server", ServerProperties.class).get();

    assertThat(server.getTomcat().getThreads().getMax()).isEqualTo(600);
  }
}
