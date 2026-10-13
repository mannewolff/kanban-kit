package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Status 400 einer unbekannten Kartenart (Plan #1574, E2) entsteht im {@code
 * GlobalExceptionHandler} aus {@code @ResponseStatus}; ohne die Annotation käme sie als 500 an.
 */
class UnknownCardKindExceptionTest {

  @Test
  void traegtStatusBadRequest() {
    ResponseStatus status = UnknownCardKindException.class.getAnnotation(ResponseStatus.class);

    assertThat(status).isNotNull();
    assertThat(status.value()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void behaeltDieMeldung() {
    assertThat(UnknownCardKindException.unbekannt("X")).hasMessage("Unbekannte Kartenart: X");
  }
}
