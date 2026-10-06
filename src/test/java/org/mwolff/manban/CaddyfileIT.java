package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

/**
 * Prüft das {@code Caddyfile} des Repositorys mit genau dem Caddy, der es im Betrieb liest (Issue
 * #1448).
 *
 * <p>Anlass: Seit #901 stand {@code header_up} auf oberster Ebene des Site-Blocks. Caddy verwarf
 * damit die ganze Konfiguration und endete mit Exit 1, während alle übrigen Dienste grün liefen —
 * drei Wochen lang, weil keine Prüfung das Caddyfile las.
 *
 * <p>Das Image kommt aus der {@code image:}-Zeile des Dienstes {@code caddy} in {@code
 * docker-compose.yml}, samt Digest: Eine zweite Angabe hier drifte beim nächsten Image-Update
 * auseinander, und der Test prüfte dann ein anderes Caddy als der Betrieb. Ohne Spring-Kontext,
 * darum nicht von {@link AbstractIntegrationTest} abgeleitet.
 */
class CaddyfileIT {

  private static final Path CADDYFILE = Path.of("Caddyfile");
  private static final Path BASIS_STACK = Path.of("docker-compose.yml");
  private static final String PFAD_IM_CONTAINER = "/etc/caddy/Caddyfile";

  private static final Pattern DIENST_KOPF = Pattern.compile("^ {2}(\\S+):\\s*$");
  private static final Pattern IMAGE_ZEILE = Pattern.compile("^ {4}image:\\s*(\\S+)\\s*$");

  /**
   * Der Fehler aus #901 in kleinster Form: {@code header_up} außerhalb von {@code reverse_proxy}.
   */
  private static final String UNGUELTIGES_CADDYFILE =
      """
      localhost {
      \theader_up X-Forwarded-For {remote_host}
      \treverse_proxy manban-api:8080
      }
      """;

  @Test
  void caddyfileDesRepositorysIstFuerDasGepinnteImageGueltig() throws Exception {
    ExecResult ergebnis = validiere(Files.readString(CADDYFILE));

    assertThat(ergebnis.getExitCode())
        .as("caddy validate meldet:%n%s%s", ergebnis.getStdout(), ergebnis.getStderr())
        .isZero();
  }

  @Test
  void headerUpAufObersterEbeneWirdAbgewiesen() throws Exception {
    ExecResult ergebnis = validiere(UNGUELTIGES_CADDYFILE);

    assertThat(ergebnis.getExitCode()).isNotZero();
    assertThat(ergebnis.getStdout() + ergebnis.getStderr())
        .contains("unrecognized directive: header_up");
  }

  private static ExecResult validiere(String inhalt) throws Exception {
    try (GenericContainer<?> caddy =
        new GenericContainer<>(caddyImage())
            .withCommand("sleep", "3600")
            .withCopyToContainer(Transferable.of(inhalt), PFAD_IM_CONTAINER)) {
      caddy.start();
      return caddy.execInContainer(
          "caddy", "validate", "--config", PFAD_IM_CONTAINER, "--adapter", "caddyfile");
    }
  }

  /** Die {@code image:}-Zeile des Dienstes {@code caddy} aus dem Basis-Stack. */
  static String caddyImage() throws IOException {
    List<String> zeilen = Files.readAllLines(BASIS_STACK);
    String dienst = null;
    for (String zeile : zeilen) {
      Matcher kopf = DIENST_KOPF.matcher(zeile);
      if (kopf.matches()) {
        dienst = kopf.group(1);
        continue;
      }
      Matcher image = IMAGE_ZEILE.matcher(zeile);
      if ("caddy".equals(dienst) && image.matches()) {
        return image.group(1);
      }
    }
    throw new IllegalStateException("Keine image:-Zeile für den Dienst caddy in " + BASIS_STACK);
  }
}
