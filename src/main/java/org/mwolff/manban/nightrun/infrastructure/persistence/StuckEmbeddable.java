package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunStuck;

/**
 * Die fünf Angaben eines festgefahrenen Pakets an {@code night_run_item} (Issue #1546, {@code V50})
 * — ausschließlich für den Lesepfad.
 *
 * <p>Ein Embeddable nach dem Muster von {@link VerbrauchEmbeddable}: Sind alle fünf Spalten {@code
 * NULL}, setzt Hibernate das eingebettete Feld auf {@code null} — das Paket trägt dann keine
 * Angaben, statt eines Records aus lauter {@code null}.
 */
@Embeddable
class StuckEmbeddable {

  @Column(name = "stuck_check")
  private @Nullable String check;

  @Column(name = "stuck_error")
  private @Nullable String error;

  @Column(name = "stuck_attempts")
  private @Nullable Integer attempts;

  @Column(name = "stuck_session_limit_ms")
  private @Nullable Long sessionLimitMs;

  @Column(name = "stuck_session_id")
  private @Nullable String sessionId;

  protected StuckEmbeddable() {
    // für JPA
  }

  NightRunStuck toDomain() {
    return new NightRunStuck(check, error, attempts, sessionLimitMs, sessionId);
  }
}
