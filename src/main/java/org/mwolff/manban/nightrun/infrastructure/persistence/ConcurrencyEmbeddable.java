package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunConcurrency;

/**
 * Die Angaben zur gleichzeitigen Umsetzung an {@code night_run_item} (Issue #1877, {@code V60}) —
 * ausschließlich für den Lesepfad.
 *
 * <p>Ein Embeddable nach dem Muster von {@link StuckEmbeddable}, aber mit Marker: Die Spalte {@code
 * concurrency_reported} ist bei jedem Paket {@code true}, das mindestens eines der drei Felder
 * führt. Ohne sie läse Hibernate ein Paket mit drei kaputt gemeldeten Werten (alle Wertspalten
 * {@code NULL}) wie eines ohne Angaben als {@code null} (Plan #1876 E5).
 */
@Embeddable
class ConcurrencyEmbeddable {

  @Column(name = "concurrency_reported")
  private @Nullable Boolean reported;

  @Column(name = "concurrency_concurrent")
  private @Nullable Boolean concurrent;

  @Column(name = "concurrency_check_wait_ms")
  private @Nullable Long checkWaitMs;

  @Column(name = "concurrency_deferred_at_merge")
  private @Nullable Boolean deferredAtMerge;

  protected ConcurrencyEmbeddable() {
    // für JPA
  }

  NightRunConcurrency toDomain() {
    return new NightRunConcurrency(concurrent, checkWaitMs, deferredAtMerge);
  }
}
