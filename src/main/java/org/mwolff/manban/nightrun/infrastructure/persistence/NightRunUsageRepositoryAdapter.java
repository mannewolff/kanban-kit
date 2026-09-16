package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.stereotype.Component;

/** Adapter des {@link NightRunUsageRepository}-Ports (Issue #937). */
@Component
class NightRunUsageRepositoryAdapter implements NightRunUsageRepository {

  private final NightRunUsageJpaRepository abfragen;

  NightRunUsageRepositoryAdapter(NightRunUsageJpaRepository abfragen) {
    this.abfragen = abfragen;
  }

  @Override
  public List<NightTotals> totalsPerNight(long projectId, Instant from, Instant to, ZoneId zone) {
    return abfragen.totalsPerNight(projectId, from, to, zone.getId()).stream()
        .map(
            z ->
                new NightTotals(
                    LocalDate.parse(z.getNight()),
                    z.getRunCount(),
                    z.getDurationMs(),
                    z.getCardCount(),
                    laufVerbrauch(z),
                    paketVerbrauch(z),
                    fehlerklassen(z.getErrorClasses())))
        .toList();
  }

  @Override
  public List<CardTotals> totalsPerCard(long projectId, Instant from, Instant to) {
    return abfragen.totalsPerCard(projectId, from, to).stream()
        .map(
            z ->
                new CardTotals(
                    z.getCardNumber(),
                    z.getAttemptCount(),
                    z.getDurationMs(),
                    new NightRunUsage(
                        z.getCostUsd(),
                        z.getInputTokens(),
                        z.getOutputTokens(),
                        z.getCachedInputTokens())))
        .toList();
  }

  @Override
  public PeriodTotals totals(long projectId, Instant from, Instant to) {
    NightRunUsageJpaRepository.TotalsRow z = abfragen.totals(projectId, from, to);
    return new PeriodTotals(
        z.getRunCount(), z.getDurationMs(), z.getCardCount(), laufVerbrauch(z), paketVerbrauch(z));
  }

  @Override
  public Optional<Instant> oldestRetainedRunStart(long projectId) {
    return abfragen.oldestStartedAt(projectId);
  }

  private static NightRunUsage laufVerbrauch(NightRunUsageJpaRepository.UsageColumns z) {
    return new NightRunUsage(
        z.getRunCostUsd(),
        z.getRunInputTokens(),
        z.getRunOutputTokens(),
        z.getRunCachedInputTokens());
  }

  private static NightRunUsage paketVerbrauch(NightRunUsageJpaRepository.UsageColumns z) {
    return new NightRunUsage(
        z.getItemCostUsd(),
        z.getItemInputTokens(),
        z.getItemOutputTokens(),
        z.getItemCachedInputTokens());
  }

  /** {@code string_agg} liefert die Klassen kommagetrennt, ohne Pakete gar nicht. */
  private static Set<NightRunErrorClass> fehlerklassen(@Nullable String kommagetrennt) {
    if (kommagetrennt == null) {
      return Set.of();
    }
    Set<NightRunErrorClass> klassen = EnumSet.noneOf(NightRunErrorClass.class);
    Arrays.stream(kommagetrennt.split(",")).map(NightRunErrorClass::valueOf).forEach(klassen::add);
    return Set.copyOf(klassen);
  }
}
