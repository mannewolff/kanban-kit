package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Verhaltenstests der modulfremden Benutzer-Ports (Issue #460). */
class UserDirectoryServiceTest {

  private AppUserRepository users;
  private UserDirectoryService service;

  private static AppUser user() {
    return new AppUser(7L, "ada@x.de", "hash", "Ada", true, PlatformRole.USER);
  }

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    service = new UserDirectoryService(users);
  }

  @Test
  void findByEmail_mapsAggregateToSummary() {
    // Given
    when(users.findByEmail("ada@x.de")).thenReturn(Optional.of(user()));

    // When
    Optional<UserSummary> found = service.findByEmail("ada@x.de");

    // Then
    assertThat(found).contains(new UserSummary(7L, "ada@x.de", "Ada", true));
  }

  @Test
  void findByEmail_isEmpty_whenUnknown() {
    // Given
    when(users.findByEmail("nobody@x.de")).thenReturn(Optional.empty());

    // When / Then
    assertThat(service.findByEmail("nobody@x.de")).isEmpty();
  }

  @Test
  void findById_mapsAggregateToSummary() {
    // Given
    when(users.findById(7L)).thenReturn(Optional.of(user()));

    // When
    Optional<UserSummary> found = service.findById(7L);

    // Then
    assertThat(found).contains(new UserSummary(7L, "ada@x.de", "Ada", true));
  }

  @Test
  void findById_isEmpty_whenUnknown() {
    // Given
    when(users.findById(7L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(service.findById(7L)).isEmpty();
  }

  @Test
  void findById_reportsPendingUserAsNotApproved() {
    // Given: kanonischer Konstruktor mit approvedAt=null — noch nicht freigegeben.
    when(users.findById(7L))
        .thenReturn(
            Optional.of(
                new AppUser(7L, "ada@x.de", "hash", "Ada", true, PlatformRole.USER, null, null)));

    // When / Then
    assertThat(service.findById(7L)).map(UserSummary::approved).contains(false);
  }

  @Test
  void findById_reportsApprovalTimestampAsApproved() {
    // Given
    when(users.findById(7L))
        .thenReturn(
            Optional.of(
                new AppUser(
                    7L,
                    "ada@x.de",
                    "hash",
                    "Ada",
                    true,
                    PlatformRole.USER,
                    Instant.parse("2026-01-02T03:04:05Z"),
                    1L)));

    // When / Then
    assertThat(service.findById(7L)).map(UserSummary::approved).contains(true);
  }

  @Test
  void updateDisplayName_trimsAndPersists() {
    // Given
    when(users.findById(7L)).thenReturn(Optional.of(user()));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
    service.updateDisplayName(7L, "  Neuer Name  ");

    // Then
    verify(users).save(saved.capture());
    assertThat(saved.getValue().displayName()).isEqualTo("Neuer Name");
  }

  @Test
  void updateDisplayName_returnsUpdatedSummary() {
    // Given
    when(users.findById(7L)).thenReturn(Optional.of(user()));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    Optional<UserSummary> updated = service.updateDisplayName(7L, "  Neuer Name  ");

    // Then
    assertThat(updated).contains(new UserSummary(7L, "ada@x.de", "Neuer Name", true));
  }

  @Test
  void updateDisplayName_returnsEmpty_whenUnknown() {
    // Given: der Port meldet den unbekannten Benutzer als leeres Optional — der Aufrufer setzt
    // daraus sein eigenes 404-Vokabular (Issue #472), ohne eine auth-Exception zu kennen.
    when(users.findById(7L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(service.updateDisplayName(7L, "X")).isEmpty();
    verify(users, never()).save(any(AppUser.class));
  }

  @Test
  void updateDisplayName_doesNotCheckPermissions() {
    // Der Port ist bewusst rechteprüfungsfrei (Issue #460): Er kennt keinen Aufrufer und hat
    // ausser dem Benutzer-Port keine Kollaborateure — die Autorisierung bleibt beim Aufrufer.
    // Ein Regressionsschutz gegen eine nachtraeglich eingebaute Admin-Pruefung: derselbe Aufruf
    // gelingt ohne jeden Akteur-Kontext.
    when(users.findById(7L)).thenReturn(Optional.of(user()));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    assertThat(service.updateDisplayName(7L, "Neuer Name"))
        .map(UserSummary::displayName)
        .contains("Neuer Name");
  }
}
