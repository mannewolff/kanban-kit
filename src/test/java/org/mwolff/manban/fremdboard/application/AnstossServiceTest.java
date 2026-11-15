package org.mwolff.manban.fremdboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.fremdboard.domain.FremdAbrufException;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/** Anstoß mit Sperrzeit je Karte (Issue #2167, Plan #2152 A6). */
class AnstossServiceTest {

  private static final Instant JETZT = Instant.parse("2026-11-15T10:00:00Z");
  private static final Verbindung VERBINDUNG =
      new Verbindung(
          1L,
          FremdBoardArt.GITLAB,
          "https://gitlab.example.org",
          "gruppe/projekt",
          "geheim",
          "kit-bot",
          StatusLabels.STANDARD,
          10L,
          JETZT,
          null,
          null,
          null);

  private PermissionChecker permissions;
  private VerbindungRepository verbindungen;
  private AbgleichService abgleich;
  private Instant uhrzeit;
  private AnstossService service;

  @BeforeEach
  void setUp() {
    permissions = mock(PermissionChecker.class);
    verbindungen = mock(VerbindungRepository.class);
    abgleich = mock(AbgleichService.class);
    uhrzeit = JETZT;
    Clock clock =
        new Clock() {
          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return uhrzeit;
          }
        };
    service = new AnstossService(permissions, verbindungen, abgleich, clock);
    when(verbindungen.finde(1L)).thenReturn(Optional.of(VERBINDUNG));
  }

  @Test
  void ersterAnstoss_liestDieKarte() {
    assertThat(service.anstossen(5L, 1L, 7)).isTrue();

    verify(permissions).requireMembership(5L, 1L);
    verify(abgleich).karteAbgleichen(VERBINDUNG, 7);
  }

  @Test
  void zweiterAnstossInDerSperrzeit_liestNicht_andereKarteSchon() {
    service.anstossen(5L, 1L, 7);
    uhrzeit = JETZT.plus(AnstossService.SPERRZEIT).minusMillis(1);

    assertThat(service.anstossen(5L, 1L, 7)).isFalse();
    assertThat(service.anstossen(5L, 1L, 8)).isTrue();
    verify(abgleich, times(1)).karteAbgleichen(VERBINDUNG, 7);
  }

  @Test
  void nachDerSperrzeit_liestWieder() {
    service.anstossen(5L, 1L, 7);
    uhrzeit = JETZT.plus(AnstossService.SPERRZEIT);

    assertThat(service.anstossen(5L, 1L, 7)).isTrue();
    verify(abgleich, times(2)).karteAbgleichen(VERBINDUNG, 7);
  }

  @Test
  void sperrzeitGiltJeProjekt() {
    Verbindung andere =
        new Verbindung(
            2L,
            FremdBoardArt.GITLAB,
            "https://gitlab.example.org",
            "gruppe/anderes",
            "geheim",
            null,
            StatusLabels.STANDARD,
            11L,
            JETZT,
            null,
            null,
            null);
    when(verbindungen.finde(2L)).thenReturn(Optional.of(andere));
    service.anstossen(5L, 1L, 7);

    assertThat(service.anstossen(5L, 2L, 7)).isTrue();
  }

  @Test
  void ohneMitgliedschaft_liestNichts() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireMembership(5L, 1L);

    assertThatThrownBy(() -> service.anstossen(5L, 1L, 7))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(abgleich, never()).karteAbgleichen(any(), anyInt());
  }

  @Test
  void ohneFremdesBoard_konflikt() {
    when(verbindungen.finde(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.anstossen(5L, 1L, 7))
        .isInstanceOf(KeinFremdesBoardException.class)
        .extracting(e -> ((KeinFremdesBoardException) e).schluessel())
        .isEqualTo("fremdboard.kein-fremdes-board");
  }

  @Test
  void gescheiterterAbruf_nichtErreichbarMitGrund_undSperrzeitBleibt() {
    FremdAbrufException fehler = new FremdAbrufException("GitLab antwortet nicht");
    doThrow(fehler).when(abgleich).karteAbgleichen(VERBINDUNG, 7);

    assertThatThrownBy(() -> service.anstossen(5L, 1L, 7))
        .isInstanceOf(FremdBoardNichtErreichbarException.class)
        .hasCause(fehler)
        .satisfies(
            e -> {
              FremdBoardNichtErreichbarException n = (FremdBoardNichtErreichbarException) e;
              assertThat(n.schluessel()).isEqualTo("fremdboard.nicht-erreichbar");
              assertThat(n.argumente()).containsExactly("GitLab antwortet nicht");
            });
    assertThat(service.anstossen(5L, 1L, 7)).isFalse();
  }
}
