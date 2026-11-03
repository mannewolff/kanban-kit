package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.FestgehalteneVoraussage;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.Stand;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Art;

/**
 * Eine festgehaltene Voraussage an {@code vorhaben_kosten} (Issue #2004, {@code V68}), zweimal
 * eingebettet: mit dem Präfix {@code anforderung_} und mit {@code pakete_}.
 *
 * <p>Muster ist {@link StuckEmbeddable}: Sind alle Spalten {@code NULL}, setzt Hibernate das
 * eingebettete Feld auf {@code null} — die Voraussage ist dann noch nicht festgehalten.
 */
@Embeddable
class VoraussageEmbeddable {

  @Column(name = "stand")
  private String stand;

  @Column(name = "art")
  private @Nullable String art;

  @Column(name = "typisch_usd")
  private @Nullable BigDecimal typischUsd;

  @Column(name = "unten_usd")
  private @Nullable BigDecimal untenUsd;

  @Column(name = "oben_usd")
  private @Nullable BigDecimal obenUsd;

  @Column(name = "festgehalten_at")
  private Instant festgehaltenAt;

  protected VoraussageEmbeddable() {
    // für JPA
  }

  private VoraussageEmbeddable(FestgehalteneVoraussage voraussage) {
    this.stand = voraussage.stand().name();
    Art gewaehlt = voraussage.art();
    this.art = gewaehlt == null ? null : gewaehlt.name();
    this.typischUsd = voraussage.typischUsd();
    this.untenUsd = voraussage.untenUsd();
    this.obenUsd = voraussage.obenUsd();
    this.festgehaltenAt = voraussage.festgehaltenAt();
  }

  static @Nullable VoraussageEmbeddable aus(@Nullable FestgehalteneVoraussage voraussage) {
    return voraussage == null ? null : new VoraussageEmbeddable(voraussage);
  }

  FestgehalteneVoraussage toDomain() {
    return new FestgehalteneVoraussage(
        Stand.valueOf(stand),
        art == null ? null : Art.valueOf(art),
        typischUsd,
        untenUsd,
        obenUsd,
        festgehaltenAt);
  }
}
