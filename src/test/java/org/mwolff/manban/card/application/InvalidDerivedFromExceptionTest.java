package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.FieldScopedException;

/**
 * Der Feldname der Herkunfts-Ablehnung ist Teil des Drahtvertrags: Die Kartenmaske (Issue #608)
 * liest die Meldung aus {@code fieldErrors.derivedFrom}. Ein Tippfehler hier bliebe stumm — der
 * Fehler käme weiterhin mit Status 400 an, nur ohne Zuordnung zum Feld.
 *
 * <p>Eigener Unit-Test, obwohl der Pfad end-to-end getestet ist: PIT misst nur Unit-Tests, und eine
 * ausschliesslich per Integrationstest belegte Stelle bleibt für die Mutationsabdeckung unsichtbar
 * (Befund aus Issue #605).
 */
class InvalidDerivedFromExceptionTest {

  @Test
  void nenntDasFeldDerivedFrom() {
    FieldScopedException ex = new InvalidDerivedFromException("Unbekannte Kartennummer");

    assertThat(ex.field()).isEqualTo("derivedFrom");
  }

  @Test
  void istEinAbhaengigkeitsfehler_damitBestandserwartungenGelten() {
    // Erbt bewusst: Aufrufer und Tests, die auf die Oberklasse pruefen, bleiben gueltig.
    assertThat(new InvalidDerivedFromException("x")).isInstanceOf(InvalidDependencyException.class);
  }
}
