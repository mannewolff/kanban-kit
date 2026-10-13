package org.mwolff.manban.language.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.common.i18n.Sprache;

/** Lesen und Setzen der Sprache der Installation (Issue #1639, Plan #1638 A2/A3). */
class SpracheServiceTest {

  private static final long ADMIN = 1L;
  private static final long NON_ADMIN = 2L;

  private SpracheSpeicher speicher;
  private PlatformAdminChecker platformAdminChecker;
  private SpracheService service;

  @BeforeEach
  void setUp() {
    speicher = mock(SpracheSpeicher.class);
    platformAdminChecker = mock(PlatformAdminChecker.class);
    service = new SpracheService(speicher, platformAdminChecker);
  }

  @Test
  void lesen_liefertDeutsch_wennKeinWertGespeichertIst() {
    when(speicher.lesen()).thenReturn(Optional.empty());

    assertThat(service.lesen()).isEqualTo(Sprache.DE);
  }

  @Test
  void lesen_liefertDenGespeichertenWert() {
    when(speicher.lesen()).thenReturn(Optional.of(Sprache.EN));

    assertThat(service.lesen()).isEqualTo(Sprache.EN);
  }

  @Test
  void setzen_alsPlattformAdmin_schreibtUndLiefertDenWert() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);

    assertThat(service.setzen(ADMIN, Sprache.EN)).isEqualTo(Sprache.EN);

    verify(speicher).schreiben(Sprache.EN);
  }

  @Test
  void setzen_ohnePlattformAdmin_wirdAbgewiesenUndSchreibtNicht() {
    when(platformAdminChecker.isPlatformAdmin(NON_ADMIN)).thenReturn(false);

    assertThatExceptionOfType(AdminAccessDeniedException.class)
        .isThrownBy(() -> service.setzen(NON_ADMIN, Sprache.EN));

    verify(speicher, never()).schreiben(any());
  }

  @Test
  void zugriffLaeuftNurUeberDenSpracheSpeicher() {
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    when(speicher.lesen()).thenReturn(Optional.of(Sprache.DE));

    service.setzen(ADMIN, Sprache.DE);
    service.lesen();

    verify(speicher).schreiben(Sprache.DE);
    verify(speicher).lesen();
    verifyNoMoreInteractions(speicher);
  }
}
