package org.mwolff.manban.benachrichtigung.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungProperties;

/**
 * Die Aufbewahrung (Issue #2081, Plan #2070 E14): Der Job rechnet die Grenze aus der Uhr und den
 * eingestellten Tagen. Das Löschen selbst samt Zustellungen belegt {@code AnlassAufbewahrungIT}.
 */
class BenachrichtigungRetentionJobTest {

  private static final Instant JETZT = Instant.parse("2026-11-10T10:00:00Z");
  private static final int TAGE = 30;
  private static final Duration AUFBEWAHRUNG = Duration.ofDays(TAGE);

  private final AnlassRepository anlaesse = mock(AnlassRepository.class);

  private BenachrichtigungRetentionJob job() {
    return new BenachrichtigungRetentionJob(
        anlaesse,
        new BenachrichtigungProperties(null, null, TAGE),
        Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  /** Spielt die Tabelle: Gelöscht wird, was vor der vom Job genannten Grenze liegt. */
  private void bestand(List<Instant> zeilen) {
    when(anlaesse.loescheAelterAls(any()))
        .thenAnswer(
            aufruf -> {
              Instant grenze = aufruf.getArgument(0);
              int vorher = zeilen.size();
              zeilen.removeIf(zeile -> zeile.isBefore(grenze));
              return vorher - zeilen.size();
            });
  }

  @Test
  void zeilenAelterAlsDieAufbewahrungWerdenGeloeschtJuengereBleiben() {
    Instant zuAlt = JETZT.minus(AUFBEWAHRUNG).minusSeconds(1);
    Instant aufDerGrenze = JETZT.minus(AUFBEWAHRUNG);
    Instant jung = JETZT.minus(AUFBEWAHRUNG).plusSeconds(1);
    List<Instant> zeilen = new ArrayList<>(List.of(zuAlt, aufDerGrenze, jung));
    bestand(zeilen);

    job().run();

    assertThat(zeilen).containsExactly(aufDerGrenze, jung);
  }

  @Test
  void ohneAlteZeilenBleibtAllesStehen() {
    Instant jung = JETZT.minusSeconds(1);
    List<Instant> zeilen = new ArrayList<>(List.of(jung));
    bestand(zeilen);

    job().run();

    assertThat(zeilen).containsExactly(jung);
  }
}
