package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Permission;

class ProjectStartNumberServiceTest {

  private static final long USER = 7L;
  private static final long PROJECT = 1L;

  private CardRepository cards;
  private ProjectRepository projects;
  private PermissionChecker permissions;
  private ProjectStartNumberService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    projects = mock(ProjectRepository.class);
    permissions = mock(PermissionChecker.class);
    service = new ProjectStartNumberService(cards, projects, permissions);
  }

  @Test
  void effectiveNextCardNumber_returnsFloor_andRequiresMembership() {
    when(cards.nextCardNumber(PROJECT)).thenReturn(13457);

    assertThat(service.effectiveNextCardNumber(USER, PROJECT)).isEqualTo(13457);

    verify(permissions).requireMembership(USER, PROJECT);
  }

  @Test
  void setNextCardNumber_setsAndReturnsEffective_whenAboveHighest() {
    // Grenzwert: höchste vergebene Nummer 13456, gesetzt wird 13457 (= höchste + 1) → gültig.
    when(cards.highestNumberInProject(PROJECT)).thenReturn(13456);
    when(cards.nextCardNumber(PROJECT)).thenReturn(13457);

    assertThat(service.setNextCardNumber(USER, PROJECT, 13457)).isEqualTo(13457);

    verify(permissions).require(USER, PROJECT, Permission.PROJECT_EDIT);
    verify(projects).setNextCardNumber(PROJECT, 13457);
  }

  @Test
  void setNextCardNumber_throws_whenAtOrBelowHighest() {
    // Grenzwert: gesetzt wird exakt die höchste vergebene Nummer → ungültig (bereits vergeben).
    when(cards.highestNumberInProject(PROJECT)).thenReturn(13457);

    assertThatThrownBy(() -> service.setNextCardNumber(USER, PROJECT, 13457))
        .isInstanceOf(InvalidCardNumberException.class);

    verify(projects, never()).setNextCardNumber(anyLong(), anyInt());
  }
}
