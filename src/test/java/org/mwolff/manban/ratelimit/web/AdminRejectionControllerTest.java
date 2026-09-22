package org.mwolff.manban.ratelimit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.application.OverloadRejectionService;
import org.mwolff.manban.ratelimit.application.OverloadRejectionService.RejectionView;

/** Der Controller reicht die angemeldete Person an den Service durch (Issue #1003). */
class AdminRejectionControllerTest {

  @Test
  void list_delegatesWithTheSessionUser() {
    OverloadRejectionService service = mock(OverloadRejectionService.class);
    List<RejectionView> rows =
        List.of(new RejectionView(7L, "Alice", Instant.parse("2026-09-22T10:00:00Z"), 2));
    when(service.list(1L)).thenReturn(rows);

    assertThat(new AdminRejectionController(service).list(1L)).isSameAs(rows);
  }
}
