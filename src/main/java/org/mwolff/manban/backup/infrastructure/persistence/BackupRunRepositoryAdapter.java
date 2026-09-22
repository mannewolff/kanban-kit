package org.mwolff.manban.backup.infrastructure.persistence;

import java.util.Locale;
import java.util.Optional;
import org.mwolff.manban.backup.application.BackupRunRepository;
import org.mwolff.manban.backup.domain.BackupKind;
import org.mwolff.manban.backup.domain.BackupOutcome;
import org.mwolff.manban.backup.domain.BackupRun;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link BackupRunRepository}-Ports (Issue #826).
 *
 * <p>Hier liegt die einzige Stelle, an der die klein geschriebenen Spaltenwerte aus {@code
 * backup_run} auf die Java-Konstanten treffen. Die Umsetzung ist mechanisch (Groß-/Kleinschreibung)
 * statt einer gepflegten Tabelle: Eine Zuordnungsliste könnte eine Konstante vergessen, die
 * Umsetzung kann das nicht — ein unbekannter Wert scheitert laut an {@code valueOf}, und der {@code
 * CHECK} der Migration lässt ihn ohnehin nicht in die Tabelle.
 */
@Component
class BackupRunRepositoryAdapter implements BackupRunRepository {

  private final BackupRunJpaRepository laeufe;

  BackupRunRepositoryAdapter(BackupRunJpaRepository laeufe) {
    this.laeufe = laeufe;
  }

  @Override
  public Optional<BackupRun> latest(BackupKind kind) {
    return laeufe
        .findFirstByKindOrderByStartedAtDescIdDesc(spaltenwert(kind))
        .map(BackupRunRepositoryAdapter::zurDomaene);
  }

  @Override
  public Optional<BackupRun> latestSuccessful(BackupKind kind) {
    return laeufe
        .findFirstByKindAndOutcomeOrderByStartedAtDescIdDesc(
            spaltenwert(kind), spaltenwert(BackupOutcome.ERFOLG))
        .map(BackupRunRepositoryAdapter::zurDomaene);
  }

  private static String spaltenwert(Enum<?> konstante) {
    return konstante.name().toLowerCase(Locale.ROOT);
  }

  private static BackupRun zurDomaene(BackupRunEntity zeile) {
    return new BackupRun(
        BackupKind.valueOf(zeile.getKind().toUpperCase(Locale.ROOT)),
        zeile.getStartedAt(),
        zeile.getFinishedAt(),
        BackupOutcome.valueOf(zeile.getOutcome().toUpperCase(Locale.ROOT)),
        zeile.getDetail(),
        zeile.getBytes());
  }
}
