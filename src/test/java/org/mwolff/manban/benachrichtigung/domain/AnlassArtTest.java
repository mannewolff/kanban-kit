package org.mwolff.manban.benachrichtigung.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;

/** Welche Kit-Labels welchen Anlass bezeichnen (Issue #2073, Plan #2070 E4). */
class AnlassArtTest {

  private static final Map<Sache, AnlassArt> ERWARTET =
      Map.of(
          Sache.KLAEREN, AnlassArt.KARTE_FRAGE,
          Sache.LAUF_WARTET, AnlassArt.LAUF_WARTET,
          Sache.GESCHUETZT, AnlassArt.KARTE_GESCHUETZT);

  @Test
  @DisplayName("Beide Label-Namen eines Paars ergeben dieselbe Art")
  void beideNamenEinesPaars() {
    ERWARTET.forEach(
        (sache, art) -> {
          assertThat(KitNamen.STANDARD.namen(sache)).as("Paar von %s", sache).hasSize(2);
          assertThat(KitNamen.STANDARD.namen(sache))
              .allSatisfy(
                  name ->
                      assertThat(KitNamen.STANDARD.sache(name).flatMap(AnlassArt::ausSache))
                          .as("Label %s", name)
                          .contains(art));
        });
  }

  @Test
  @DisplayName("Jede andere Sache ist kein Anlass")
  void andereSachen() {
    Set<Sache> andere = EnumSet.allOf(Sache.class);
    andere.removeAll(ERWARTET.keySet());

    assertThat(andere).allSatisfy(sache -> assertThat(AnlassArt.ausSache(sache)).isEmpty());
  }
}
