package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.mwolff.manban.nightrun.domain.ModellvergleichErmittlung.Vorgang;
import org.mwolff.manban.nightrun.domain.NightRunAttempt;
import org.mwolff.manban.nightrun.domain.NightRunAttemptStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.springframework.stereotype.Component;

/**
 * Liest die Vorgänge der Pakete eines Projekts (Issue #1947, Plan #1905 E7) — der Lesepfad der
 * Tabelle {@code night_run_attempt}, gerufen vom {@link NightRunRepositoryAdapter} für {@code
 * findAttemptsSince}.
 *
 * <p>Eine eigene Klasse aus demselben Grund wie der {@link NightRunAttemptWriter}: Der Adapter
 * steht an seiner Grenze, und die neue Tabelle bringt ihr Mapping hier mit.
 */
@Component
class NightRunAttemptReader {

  private final NightRunAttemptJpaRepository attempts;

  NightRunAttemptReader(NightRunAttemptJpaRepository attempts) {
    this.attempts = attempts;
  }

  /**
   * Die Vorgänge der Pakete mit Start ab {@code von}, nach Start, Paket und Stelle im Paket. Die
   * Position zählt über das ganze Ergebnis durch und ist damit innerhalb eines Laufs die
   * Meldungsreihenfolge über alle seine Pakete — deren eigene Stellen beginnen je bei 0.
   */
  List<Vorgang> seit(long projectId, Instant von) {
    List<NightRunAttemptJpaRepository.MitPaket> zeilen = attempts.findSince(projectId, von);
    List<Vorgang> vorgaenge = new ArrayList<>(zeilen.size());
    for (int position = 0; position < zeilen.size(); position++) {
      NightRunAttemptJpaRepository.MitPaket zeile = zeilen.get(position);
      NightRunAttemptEntity a = zeile.attempt();
      vorgaenge.add(
          new Vorgang(
              zeile.startedAt(),
              NightRunKind.valueOf(zeile.kind()),
              a.isRunComplete(),
              position,
              new NightRunAttempt(
                  NightRunAttemptStage.valueOf(a.getStage()),
                  a.getCardNumber(),
                  a.getModel(),
                  a.getEffort(),
                  a.isSucceeded(),
                  NightRunRepositoryAdapter.verbrauchLesen(a.getVerbrauch()),
                  List.of(a.getWrittenCardNumbers()))));
    }
    return vorgaenge;
  }
}
