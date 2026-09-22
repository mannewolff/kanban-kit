package org.mwolff.manban.ratelimit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.ratelimit.application.OverloadRejectionReader.StoredRejection;
import org.mwolff.manban.ratelimit.application.OverloadRejectionService.RejectionView;

/**
 * Die Admin-Sicht auf Abweisungen wegen Last (Issue #1003): nur für Plattform-Admins, mit
 * Anzeigenamen aus {@link UserLookup}.
 */
class OverloadRejectionServiceTest {

  private static final long ADMIN = 1L;
  private static final Instant TEN = Instant.parse("2026-09-22T10:00:00Z");
  private static final Instant NINE = Instant.parse("2026-09-22T09:00:00Z");

  private final OverloadRejectionReader reader = mock(OverloadRejectionReader.class);
  private final PlatformAdminChecker admins = mock(PlatformAdminChecker.class);
  private final UserLookup users = mock(UserLookup.class);
  private final OverloadRejectionService service =
      new OverloadRejectionService(reader, admins, users);

  @Test
  void nonAdmin_isRejected_andReadsNothing() {
    when(admins.isPlatformAdmin(2L)).thenReturn(false);

    assertThatThrownBy(() -> service.list(2L)).isInstanceOf(AdminAccessDeniedException.class);
    verifyNoInteractions(reader, users);
  }

  @Test
  void admin_getsTheRowsInReaderOrder_withDisplayNames() {
    when(admins.isPlatformAdmin(ADMIN)).thenReturn(true);
    when(reader.recent(OverloadRejectionService.LIMIT))
        .thenReturn(
            List.of(
                new StoredRejection(7L, TEN, 12),
                new StoredRejection(8L, TEN, 3),
                new StoredRejection(7L, NINE, 5)));
    when(users.findById(7L)).thenReturn(Optional.of(new UserSummary(7L, "a@x", "Alice", true)));
    when(users.findById(8L)).thenReturn(Optional.of(new UserSummary(8L, "b@x", "Bob", true)));

    List<RejectionView> rows = service.list(ADMIN);

    assertThat(rows)
        .containsExactly(
            new RejectionView(7L, "Alice", TEN, 12),
            new RejectionView(8L, "Bob", TEN, 3),
            new RejectionView(7L, "Alice", NINE, 5));
    // Je Person wird der Name einmal nachgeschlagen, nicht je Zeile.
    verify(users, times(1)).findById(7L);
  }

  @Test
  void unknownPerson_isShownWithItsId() {
    when(admins.isPlatformAdmin(ADMIN)).thenReturn(true);
    when(reader.recent(OverloadRejectionService.LIMIT))
        .thenReturn(List.of(new StoredRejection(9L, TEN, 1)));
    when(users.findById(9L)).thenReturn(Optional.empty());

    assertThat(service.list(ADMIN)).containsExactly(new RejectionView(9L, "#9", TEN, 1));
  }

  @Test
  void theListIsBounded() {
    assertThat(OverloadRejectionService.LIMIT).isEqualTo(500);
  }
}
