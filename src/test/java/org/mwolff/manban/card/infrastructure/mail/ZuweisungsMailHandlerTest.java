package org.mwolff.manban.card.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.PayloadFields;

/** Zustellung der vorgemerkten Nachricht an neu Zuständige (Issue #1916). */
class ZuweisungsMailHandlerTest {

  private final JavaMailZuweisungsMailer delivery = mock(JavaMailZuweisungsMailer.class);
  private final ZuweisungsMailHandler handler = new ZuweisungsMailHandler(delivery);

  @Test
  void eventTypeEntsprichtDemDerVormerkung() {
    assertThat(handler.eventType()).isEqualTo("mail.card-assigned");
  }

  @Test
  void handleStelltDieDekodiertenFelderZu() {
    handler.handle(
        PayloadFields.join(
            "bo@example.org",
            "1916",
            "Titel\nmit Umbruch",
            "Projekt P",
            "Board B",
            "Manne",
            "https://app/projects/9/karten/1916"));

    verify(delivery)
        .sendZuweisungsEmail(
            new JavaMailZuweisungsMailer.Zuweisung(
                "bo@example.org",
                "1916",
                "Titel\nmit Umbruch",
                "Projekt P",
                "Board B",
                "Manne",
                "https://app/projects/9/karten/1916"));
  }

  @Test
  void handleWeistEineKaputtePayloadOhneZustellungAb() {
    assertThatThrownBy(() -> handler.handle(PayloadFields.join("a", "b", "c")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(delivery);
  }
}
