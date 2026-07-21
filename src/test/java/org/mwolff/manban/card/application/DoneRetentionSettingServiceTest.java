package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;

/** Verhaltenstests des Done-Retention-Overrides (Mockito an Port, Properties und Admin-Check). */
class DoneRetentionSettingServiceTest {

  private static final long ADMIN = 1L;
  private static final long NON_ADMIN = 2L;

  private AppSettingRepository settings;
  private PlatformAdminChecker platformAdminChecker;
  private DoneRetentionSettingService service;

  @BeforeEach
  void setUp() {
    settings = mock(AppSettingRepository.class);
    platformAdminChecker = mock(PlatformAdminChecker.class);
    var cleanup = new CleanupProperties(true, 30, 30, null); // Env-Default 30
    service = new DoneRetentionSettingService(settings, cleanup, platformAdminChecker);
  }

  @Test
  void effectiveRetentionDays_returnsOverride_whenSet() {
    when(settings.find(DoneRetentionSettingService.KEY)).thenReturn(Optional.of("7"));

    assertThat(service.effectiveRetentionDays()).isEqualTo(7);
  }

  @Test
  void effectiveRetentionDays_fallsBackToEnvDefault_whenNoOverride() {
    when(settings.find(DoneRetentionSettingService.KEY)).thenReturn(Optional.empty());

    assertThat(service.effectiveRetentionDays()).isEqualTo(30);
  }

  @Test
  void effectiveRetentionDays_returnsZero_whenOverrideIsZero() {
    when(settings.find(DoneRetentionSettingService.KEY)).thenReturn(Optional.of("0"));

    assertThat(service.effectiveRetentionDays()).isZero();
  }

  @Test
  void currentFor_returnsEffectiveAndOverride_whenSet() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    when(settings.find(DoneRetentionSettingService.KEY)).thenReturn(Optional.of("7"));

    var result = service.currentFor(ADMIN);

    assertThat(result.effective()).isEqualTo(7);
    assertThat(result.override()).isEqualTo(7);
  }

  @Test
  void currentFor_reportsNullOverride_whenUnset() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    when(settings.find(DoneRetentionSettingService.KEY)).thenReturn(Optional.empty());

    var result = service.currentFor(ADMIN);

    assertThat(result.effective()).isEqualTo(30);
    assertThat(result.override()).isNull();
  }

  @Test
  void currentFor_rejectsNonAdmin() {
    when(platformAdminChecker.isPlatformAdmin(NON_ADMIN)).thenReturn(false);

    assertThatExceptionOfType(AdminAccessDeniedException.class)
        .isThrownBy(() -> service.currentFor(NON_ADMIN));
  }

  @Test
  void updateOverride_persistsValue_forAdmin() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    when(settings.find(DoneRetentionSettingService.KEY)).thenReturn(Optional.of("7"));

    var result = service.updateOverride(ADMIN, 7);

    verify(settings).save(DoneRetentionSettingService.KEY, "7");
    assertThat(result.effective()).isEqualTo(7);
    assertThat(result.override()).isEqualTo(7);
  }

  @Test
  void updateOverride_allowsZeroAsOff() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    when(settings.find(DoneRetentionSettingService.KEY)).thenReturn(Optional.of("0"));

    var result = service.updateOverride(ADMIN, 0);

    verify(settings).save(DoneRetentionSettingService.KEY, "0");
    assertThat(result.effective()).isZero();
    assertThat(result.override()).isZero();
  }

  @Test
  void updateOverride_rejectsNegative_withoutPersisting() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);

    assertThatIllegalArgumentException().isThrownBy(() -> service.updateOverride(ADMIN, -1));

    verify(settings, never())
        .save(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void updateOverride_rejectsNonAdmin_withoutPersisting() {
    when(platformAdminChecker.isPlatformAdmin(NON_ADMIN)).thenReturn(false);

    assertThatExceptionOfType(AdminAccessDeniedException.class)
        .isThrownBy(() -> service.updateOverride(NON_ADMIN, 5));

    verify(settings, never())
        .save(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }
}
