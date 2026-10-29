package org.mwolff.manban.push.application;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.nightrun.domain.Meldegrund.Grund;

/**
 * Eine geplante Nachricht zu einem Lauf an ein Abo — die Payload zwischen {@link LaufMeldeService}
 * und dem Outbox-Handler (Issue #1998, Plan #1989 E12, E17, E21).
 *
 * <p>Sie trägt Referenzen und den Titel, kein Geheimnis: Endpunkt und Schlüssel des Geräts liest
 * der Handler beim Senden aus dem Abo. Den Text in der Sprache der Installation bildet er ebenfalls
 * erst dann, aus Grund und Karte.
 *
 * @param laufId Kennung des Laufs
 * @param aboId Kennung des Abos
 * @param userId Konto, dem das Abo beim Planen gehörte
 * @param projectId Kennung des Projekts des Laufs
 * @param titel „Projekt · Board“, ohne Board nur „Projekt“ (E17)
 * @param grund was mit dem Lauf ist
 * @param karte Karte des Pakets, das den Grund trägt; {@code null} beim verstummten Lauf
 * @param gemeldetAt erstes Auftreten des Grunds
 */
public record LaufNachricht(
    long laufId,
    long aboId,
    long userId,
    long projectId,
    String titel,
    Grund grund,
    @Nullable Integer karte,
    Instant gemeldetAt) {

  /** Ereignistyp in der Outbox. */
  public static final String TYP = "push.lauf";

  private static final int FELDER = 8;

  /** Der Titel nach E17: „Projekt · Board“, ohne Board nur „Projekt“. */
  public static String titel(String projekt, @Nullable String board) {
    return board == null ? projekt : projekt + " · " + board;
  }

  /** Die Adresse, die ein Tipp auf die Nachricht öffnet (E17). */
  public String adresse() {
    return "/mobil/laeufe/" + laufId;
  }

  /** Der Idempotenzschlüssel: höchstens eine Nachricht je Lauf und Abo (E12). */
  public String schluessel() {
    return "push-lauf:" + laufId + ":" + aboId;
  }

  /** Die Nachricht als Outbox-Payload. */
  public String payload() {
    return PayloadFields.join(
        Long.toString(laufId),
        Long.toString(aboId),
        Long.toString(userId),
        Long.toString(projectId),
        titel,
        grund.name(),
        karte == null ? "" : karte.toString(),
        gemeldetAt.toString());
  }

  /**
   * Liest eine mit {@link #payload()} erzeugte Payload.
   *
   * @throws RuntimeException wenn sie nicht lesbar ist ({@link IllegalArgumentException} oder
   *     {@link java.time.format.DateTimeParseException}) — eine kaputte Payload endet als
   *     Fehlversuch, nicht als falsche Nachricht
   */
  public static LaufNachricht aus(String payload) {
    List<String> f = PayloadFields.split(payload, FELDER);
    return new LaufNachricht(
        Long.parseLong(f.get(0)),
        Long.parseLong(f.get(1)),
        Long.parseLong(f.get(2)),
        Long.parseLong(f.get(3)),
        f.get(4),
        Grund.valueOf(f.get(5)),
        f.get(6).isEmpty() ? null : Integer.valueOf(f.get(6)),
        Instant.parse(f.get(7)));
  }
}
