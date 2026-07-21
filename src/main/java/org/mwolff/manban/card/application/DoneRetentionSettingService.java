package org.mwolff.manban.card.application;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltet den global, zur Laufzeit vom Plattform-Admin änderbaren Done-Retention-Override.
 *
 * <p>Semantik: kein gesetzter Override = Env-Default ({@link
 * CleanupProperties#doneRetentionDays()}); ein gesetzter Wert überschreibt ihn; {@code 0} =
 * Auto-Archiv aus. Der Override wird als generische App-Einstellung (Key {@value #KEY}) über den
 * {@link AppSettingRepository}-Port gehalten.
 */
@Service
public class DoneRetentionSettingService {

  static final String KEY = "done.retention.days";

  private final AppSettingRepository settings;
  private final CleanupProperties cleanup;
  private final PlatformAdminChecker platformAdminChecker;

  public DoneRetentionSettingService(
      AppSettingRepository settings,
      CleanupProperties cleanup,
      PlatformAdminChecker platformAdminChecker) {
    this.settings = settings;
    this.cleanup = cleanup;
    this.platformAdminChecker = platformAdminChecker;
  }

  /** Effektiver Wert: gesetzter Override, sonst der Env-Default. Kann {@code 0} sein (= aus). */
  @Transactional(readOnly = true)
  public int effectiveRetentionDays() {
    return override().orElse(cleanup.doneRetentionDays());
  }

  /** Aktueller effektiver Wert plus (evtl. leerer) Override — nur für Plattform-Admins. */
  @Transactional(readOnly = true)
  public RetentionSettings currentFor(long actorUserId) {
    requireAdmin(actorUserId);
    return current();
  }

  /**
   * Setzt den globalen Override (nur Plattform-Admin). {@code 0} ist erlaubt (Auto-Archiv aus),
   * negative Werte werden abgelehnt.
   */
  @Transactional
  public RetentionSettings updateOverride(long actorUserId, int days) {
    requireAdmin(actorUserId);
    if (days < 0) {
      throw new IllegalArgumentException("Done-Retention darf nicht negativ sein");
    }
    settings.save(KEY, Integer.toString(days));
    return current();
  }

  private RetentionSettings current() {
    return new RetentionSettings(effectiveRetentionDays(), override().orElse(null));
  }

  private Optional<Integer> override() {
    return settings.find(KEY).map(Integer::valueOf);
  }

  private void requireAdmin(long actorUserId) {
    if (!platformAdminChecker.isPlatformAdmin(actorUserId)) {
      throw new AdminAccessDeniedException();
    }
  }

  /**
   * Momentaufnahme der Retention-Einstellung.
   *
   * @param effective effektiver Wert (Override oder Env-Default), {@code 0} = aus
   * @param override gesetzter Override oder {@code null}, wenn keiner gesetzt ist
   */
  public record RetentionSettings(int effective, @Nullable Integer override) {}
}
