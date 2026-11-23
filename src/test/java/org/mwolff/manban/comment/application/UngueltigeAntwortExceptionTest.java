package org.mwolff.manban.comment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

/** Die Ausnahme bleibt samt Meldungsargumenten serialisierbar (Issue #2258, Sonar java:S1948). */
class UngueltigeAntwortExceptionTest {

  @Test
  void ueberstehtSerialisierungMitSchluesselUndArgumenten() throws Exception {
    UngueltigeAntwortException kopie = rundreise(UngueltigeAntwortException.fuerFrage(3));

    assertThat(kopie.schluessel()).isEqualTo("comment.antwort.frage");
    assertThat(kopie.argumente()).containsExactly(3);
    assertThat(kopie).hasMessage("Die Antwort auf Frage 3 passt nicht zur Rückfrage");
  }

  @Test
  void argumenteSindEineKopie() {
    UngueltigeAntwortException ausnahme = UngueltigeAntwortException.fuerFrage(7);

    ausnahme.argumente()[0] = 99;

    assertThat(ausnahme.argumente()).containsExactly(7);
  }

  private static UngueltigeAntwortException rundreise(UngueltigeAntwortException ausnahme)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream aus = new ObjectOutputStream(bytes)) {
      aus.writeObject(ausnahme);
    }
    try (ObjectInputStream ein =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (UngueltigeAntwortException) ein.readObject();
    }
  }
}
