package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Status 400 der Zeitraum-Ablehnung (Plan #1539, E12) entsteht im {@code
 * GlobalExceptionHandler} aus {@code @ResponseStatus}. Ohne die Annotation käme eine halbe oder
 * umgekehrte Grenze als 500 an.
 *
 * <p>Eigener Unit-Test, weil PIT nur Unit-Tests misst und der Controller-Test ohne MockMvc läuft.
 */
class InvalidPeriodBoundsExceptionTest {

  @Test
  void traegtStatusBadRequest() {
    ResponseStatus status = InvalidPeriodBoundsException.class.getAnnotation(ResponseStatus.class);

    assertThat(status).isNotNull();
    assertThat(status.value()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void behaeltDieMeldung() {
    assertThat(new InvalidPeriodBoundsException("from ohne to")).hasMessage("from ohne to");
  }
}
