package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.CardActivityOrigin;

/**
 * Unit-Test des Herkunfts-Stempels (#517).
 *
 * <p>Der leere Stempel wird bisher nur benutzt — von Produktivcode als Rückfallwert und von
 * Test-Setups als Mock-Antwort —, aber nirgends geprüft. Ein Mutant, der {@code unknown()} auf
 * {@code null} umbiegt, überlebte deshalb: Setup und Erwartung stammen aus derselben Quelle und
 * werden gemeinsam mutiert. Der Vertrag lautet „nie null" (siehe {@link ActorContext#current()});
 * genau das hält dieser Test fest.
 */
class ActorContextTest {

  @Test
  void unknown_isAnEmptyStamp_neverNull() {
    ActorStamp stamp = ActorStamp.unknown();

    assertThat(stamp).isNotNull();
    assertThat(stamp.origin()).isNull();
    assertThat(stamp.tokenName()).isNull();
    assertThat(stamp.agent()).isNull();
  }

  @Test
  void unknown_returnsTheSameInstanceEveryTime() {
    // Konstante, kein neu erzeugter Stempel je Aufruf — die Aktivitätsschreibung ruft sie oft.
    assertThat(ActorStamp.unknown()).isSameAs(ActorStamp.unknown());
  }

  @Test
  void originName_liefertDenKonstantennamenDerHerkunft() {
    // Issue #1620: comment liest die Herkunft als Text, ohne card.domain zu berühren.
    assertThat(new ActorStamp(CardActivityOrigin.SESSION, null, null, null).originName())
        .isEqualTo("SESSION");
    assertThat(new ActorStamp(CardActivityOrigin.TOKEN, "kit", "opus", null).originName())
        .isEqualTo("TOKEN");
  }

  @Test
  void originName_ohneHerkunft_istNull() {
    assertThat(ActorStamp.unknown().originName()).isNull();
  }
}
