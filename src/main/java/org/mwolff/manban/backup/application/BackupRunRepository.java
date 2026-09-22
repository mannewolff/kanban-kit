package org.mwolff.manban.backup.application;

import java.util.Optional;
import org.mwolff.manban.backup.domain.BackupKind;
import org.mwolff.manban.backup.domain.BackupRun;

/**
 * Ausgehender Port auf das Protokoll der Sicherungsläufe (Issue #826).
 *
 * <p>Nur lesend: Geschrieben wird {@code backup_run} vom Sicherungs-Container, nicht von der
 * Anwendung (Plan #825 E2).
 */
public interface BackupRunRepository {

  /** Der jüngste Lauf dieser Art, gleich wie er ausging. */
  Optional<BackupRun> latest(BackupKind kind);

  /** Der jüngste <em>gelungene</em> Lauf dieser Art — an ihm hängt die Alterung. */
  Optional<BackupRun> latestSuccessful(BackupKind kind);
}
