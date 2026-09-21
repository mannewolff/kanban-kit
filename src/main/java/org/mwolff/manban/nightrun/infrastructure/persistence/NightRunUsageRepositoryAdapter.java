package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunKind;
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
                    z.getDurationMs(),
                    z.getCardCount(),
                    jeGattung(z),
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
                        z.getNightCostUsd(),
                        z.getNightInputTokens(),
                        z.getNightOutputTokens(),
                        z.getNightCachedInputTokens(),
                        null,
                        null),
                    new NightRunUsage(
                        z.getInteractiveCostUsd(),
                        z.getInteractiveInputTokens(),
                        z.getInteractiveOutputTokens(),
                        z.getInteractiveCachedInputTokens(),
                        null,
                        null)))
        .toList();
  }

  @Override
  public PeriodTotals totals(long projectId, Instant from, Instant to) {
    NightRunUsageJpaRepository.TotalsRow z = abfragen.totals(projectId, from, to);
    return new PeriodTotals(z.getDurationMs(), z.getCardCount(), jeGattung(z));
  }

  @Override
  public LifetimeTotals lifetimeTotals(long projectId) {
    NightRunUsageJpaRepository.TotalsRow z = abfragen.lifetimeTotals(projectId);
    return new LifetimeTotals(z.getCardCount(), jeGattung(z));
  }

  @Override
  public Optional<Instant> oldestRetainedRunStart(long projectId) {
    return abfragen.oldestStartedAt(projectId);
  }

  @Override
  public List<RetainedByKind> retentionBoundary(long projectId) {
    Map<NightRunKind, NightRunUsageJpaRepository.RetentionRow> jeGattung =
        abfragen.retentionByKind(projectId).stream()
            .collect(
                Collectors.toMap(row -> NightRunKind.valueOf(row.getKind()), Function.identity()));
    return Arrays.stream(NightRunKind.values())
        .map(
            kind -> {
              NightRunUsageJpaRepository.RetentionRow row = jeGattung.get(kind);
              return row == null
                  ? new RetainedByKind(kind, 0L, null)
                  : new RetainedByKind(kind, row.getCount(), row.getOldestStart());
            })
        .toList();
  }

  private static TotalsByKind jeGattung(NightRunUsageJpaRepository.UsageColumns z) {
    return new TotalsByKind(nachtlaeufe(z), sitzungen(z));
  }

  private static KindTotals nachtlaeufe(NightRunUsageJpaRepository.UsageColumns z) {
    return new KindTotals(
        z.getNightRunCount(),
        new NightRunUsage(
            z.getNightRunCostUsd(),
            z.getNightRunInputTokens(),
            z.getNightRunOutputTokens(),
            z.getNightRunCachedInputTokens(),
            null,
            null),
        new NightRunUsage(
            z.getNightItemCostUsd(),
            z.getNightItemInputTokens(),
            z.getNightItemOutputTokens(),
            z.getNightItemCachedInputTokens(),
            null,
            null));
  }

  private static KindTotals sitzungen(NightRunUsageJpaRepository.UsageColumns z) {
    return new KindTotals(
        z.getInteractiveRunCount(),
        new NightRunUsage(
            z.getInteractiveRunCostUsd(),
            z.getInteractiveRunInputTokens(),
            z.getInteractiveRunOutputTokens(),
            z.getInteractiveRunCachedInputTokens(),
            null,
            null),
        new NightRunUsage(
            z.getInteractiveItemCostUsd(),
            z.getInteractiveItemInputTokens(),
            z.getInteractiveItemOutputTokens(),
            z.getInteractiveItemCachedInputTokens(),
            null,
            null));
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
