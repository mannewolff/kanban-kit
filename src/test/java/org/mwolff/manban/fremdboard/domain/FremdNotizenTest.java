package org.mwolff.manban.fremdboard.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Die Notizen einer Karte im fremden System, getrennt nach Vertraulichkeit (Issue #2200). */
class FremdNotizenTest {

  private static final Instant ZEIT = Instant.parse("2026-11-15T10:00:00Z");

  @Test
  void beideSammlungenSindUnveraenderlicheKopien() {
    FremdNotiz notiz = new FremdNotiz("701", 7, "anna", "Text", ZEIT, ZEIT);
    List<FremdNotiz> sichtbar = new ArrayList<>(List.of(notiz));
    Set<String> vertraulich = new HashSet<>(Set.of("702"));
    FremdNotizen notizen = new FremdNotizen(sichtbar, vertraulich);

    sichtbar.clear();
    vertraulich.add("703");

    assertThat(notizen.sichtbar()).containsExactly(notiz);
    assertThat(notizen.vertraulich()).containsExactly("702");
    assertThatThrownBy(() -> notizen.sichtbar().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> notizen.vertraulich().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
