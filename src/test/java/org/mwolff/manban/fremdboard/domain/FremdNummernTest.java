package org.mwolff.manban.fremdboard.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Die Nummern im fremden System, getrennt nach Vertraulichkeit (Issue #2200). */
class FremdNummernTest {

  @Test
  void beideListenSindUnveraenderlicheKopien() {
    List<Long> sichtbar = new ArrayList<>(List.of(7L));
    List<Long> vertraulich = new ArrayList<>(List.of(9L));
    FremdNummern nummern = new FremdNummern(sichtbar, vertraulich);

    sichtbar.add(8L);
    vertraulich.add(10L);

    assertThat(nummern.sichtbar()).containsExactly(7L);
    assertThat(nummern.vertraulich()).containsExactly(9L);
    assertThatThrownBy(() -> nummern.sichtbar().add(1L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> nummern.vertraulich().add(1L))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
