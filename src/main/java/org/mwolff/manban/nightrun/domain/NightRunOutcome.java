package org.mwolff.manban.nightrun.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Der Befund eines Laufs: ob er vollständig gelungen ist, und woran es sonst lag (Issue #1078, Plan
 * #1072 E2/E4).
 *
 * <p><strong>Warum im Server.</strong> Der Maßstab wurde in #1060 festgelegt und lebte bis hierher
 * im Browser ({@code frontend/src/lib/leitstand.ts}, {@code laufMelder}). Der Plattform-Leitstand
 * geht über alle Projekte und kann die Läufe nicht einzeln im Browser auswerten; AK 5 der
 * fachlichen Quelle verlangt ausdrücklich <em>eine</em> Wahrheit. Ein zweitgeschriebener Maßstab,
 * durch einen Sync-Test gleichgehalten, wären zwei Rechnungen — nicht eine Quelle.
 *
 * <p><strong>Daten, kein Text.</strong> Der Befund trägt das maßgebliche Paket und den Grund eines
 * Laufs ohne Arbeit, aber keine Formulierung. Die Texttabellen liegen im Frontend ({@code
 * nightRunHandoff.ts}); ein zweiter Satz hier wäre genau die zweite Formulierung desselben
 * Sachverhalts, die AK 6 verbietet.
 *
 * <p><strong>Die Stillefrist gehört hierher</strong> (Issue #1091, AK 6 der fachlichen Quelle
 * #1086). Ein Lauf, dessen Runner abgeschossen wurde, meldet sich nie als abgeschlossen und bliebe
 * sonst für immer „läuft". Wer über die Frist hinweg kein Lebenszeichen gibt, gilt als nicht
 * gelungen. Die Regel steht im Befund, weil es nach AK 10 <em>genau eine</em> Wahrheit über den
 * Ausgang eines Laufs geben muss (Plan #1088 E1).
 *
 * <p>Sie ist eine <strong>Leseregel</strong>, keine Zustandsänderung — und löst damit AK 8
 * nebenbei: Meldet sich ein totgesagter Lauf doch noch, ist sein Lebenszeichen wieder frisch und er
 * ist ohne jede Korrektur wieder {@link Verdict#RUNNING}.
 *
 * @param verdict der Ausgang des Laufs
 * @param decisiveItem das Paket, das den Ausgang bestimmt; {@code null}, wenn keines ihn bestimmt —
 *     bei {@link Verdict#SUCCEEDED}, {@link Verdict#RUNNING} und beim Lauf ohne Arbeit
 * @param noWorkReason Grund, warum der Lauf nichts abgearbeitet hat (Issue #1068); durchgereicht,
 *     nicht formuliert, und {@code null}, wenn der Lauf gearbeitet hat
 */
public record NightRunOutcome(
    Verdict verdict, @Nullable DecisiveItem decisiveItem, @Nullable String noWorkReason) {

  /** Rang eines Pakets, das den Ausgang nicht bestimmen kann. */
  private static final int NICHT_MASSGEBLICH = Integer.MAX_VALUE;

  /**
   * Der Ausgang eines Laufs.
   *
   * <p>Bewusst nicht {@code Kind} genannt: {@link NightRunKind} ist im selben Paket die Gattung
   * (Nachtlauf oder interaktive Sitzung), und zwei Begriffe namens „Kind" nebeneinander wären eine
   * Verwechslung mit Ansage.
   */
  public enum Verdict {

    /** Vollständig gelungen — keine Störung. */
    SUCCEEDED,

    /** Nicht gelungen: hartes Scheitern, Vorbehalt oder ein Lauf ohne Arbeit. */
    FAILED,

    /** Abgeschlossen, aber ein Paket wartet auf etwas — zurückgestellt oder auf einen Menschen. */
    WAITING,

    /** Noch nicht abgeschlossen; der Ausgang steht nicht fest. */
    RUNNING
  }

  /**
   * Das Paket, das den Ausgang bestimmt — als Daten für den Grundtext einer Störzeile.
   *
   * @param cardNumber projektweite Kartennummer
   * @param state Ausgang des Pakets
   * @param errorClass Grund des nicht-grünen Ausgangs; bei einem maßgeblichen Paket stets gesetzt
   */
  public record DecisiveItem(
      int cardNumber, NightRunState state, @Nullable NightRunErrorClass errorClass) {}

  /**
   * Bildet den Befund aus dem Lauf und seinen Paketen.
   *
   * <p>Die Reihenfolge der Prüfungen trägt eine Aussage:
   *
   * <ol>
   *   <li><b>Verstummt</b> schlägt alles. Ein unfertiger Lauf ohne Lebenszeichen über die Frist
   *       hinaus ist nicht gelungen — ohne maßgebliches Paket und ohne Grund, denn er hat sein
   *       Ergebnis nie gemeldet. Genau <em>auf</em> der Frist ist er noch nicht tot, erst darüber:
   *       Die Frist ist die zugesagte Stille, nicht ihr Überschreiten.
   *   <li><b>Läuft noch</b> schlägt das Übrige. Ein Lauf ohne Abschluss hat noch nichts zu melden —
   *       er wird nicht rot, auch nicht mit einem roten Paket (dieselbe Begründung, die {@code
   *       laufMelder} seit #1069 trägt).
   *   <li><b>Ohne Arbeit</b> schlägt die Pakete. Der Grund ist der Text selbst; ein Paket daneben
   *       wäre eine zweite Begründung für denselben Lauf.
   *   <li><b>Rot vor Gelb vor Grau-mit-Fehlerklasse</b>, innerhalb einer Farbe das erste in
   *       Laufreihenfolge.
   * </ol>
   *
   * <p>Grau <em>ohne</em> Fehlerklasse ist ein übergangenes Paket — der Lauf hat es nicht
   * angefasst, und das ist kein Mangel des Laufs.
   *
   * <p>Die Zeit kommt als Parameter, nicht als {@code Clock}-Feld: Unter {@code ..domain..} ist
   * keine Spring-Abhängigkeit erlaubt (Plan #1088 E2).
   *
   * @param complete ob der Lauf sich als abgeschlossen gemeldet hat
   * @param noWorkReason Grund eines Laufs ohne Arbeit; {@code null} oder leer, wenn er gearbeitet
   *     hat
   * @param items die Pakete des Laufs, in Laufreihenfolge
   * @param startedAt Startzeitpunkt des Laufs — das Lebenszeichen eines Laufs, der nie
   *     fortgeschrieben wurde (der Upload-Weg lässt {@code updatedAt} bewusst leer)
   * @param updatedAt Zeitpunkt der letzten Meldung des Laufs; {@code null}, wenn er nie
   *     fortgeschrieben wurde
   * @param jetzt der Zeitpunkt, gegen den das Lebenszeichen gemessen wird
   * @param stilleFrist die Stille, die ein unfertiger Lauf sich erlauben darf
   */
  public static NightRunOutcome of(
      boolean complete,
      @Nullable String noWorkReason,
      List<NightRunItem> items,
      Instant startedAt,
      @Nullable Instant updatedAt,
      Instant jetzt,
      Duration stilleFrist) {
    if (!complete && verstummt(startedAt, updatedAt, jetzt, stilleFrist)) {
      return new NightRunOutcome(Verdict.FAILED, null, null);
    }
    if (!complete) {
      return new NightRunOutcome(Verdict.RUNNING, null, null);
    }
    if (noWorkReason != null && !noWorkReason.isBlank()) {
      return new NightRunOutcome(Verdict.FAILED, null, noWorkReason);
    }
    return items.stream()
        .filter(item -> rang(item) != NICHT_MASSGEBLICH)
        .min(Comparator.comparingInt(NightRunOutcome::rang))
        .map(NightRunOutcome::ausPaket)
        .orElseGet(() -> new NightRunOutcome(Verdict.SUCCEEDED, null, null));
  }

  /**
   * Ob das letzte Lebenszeichen des Laufs länger her ist als die zugesagte Stille.
   *
   * <p>Gemessen wird an {@code updatedAt}, und ohne es an {@code startedAt}: Der Upload-Weg
   * schreibt einen Lauf nie fort, dort ist der Start das einzige Lebenszeichen, das es gibt.
   */
  private static boolean verstummt(
      Instant startedAt, @Nullable Instant updatedAt, Instant jetzt, Duration stilleFrist) {
    Instant lebenszeichen = updatedAt == null ? startedAt : updatedAt;
    return Duration.between(lebenszeichen, jetzt).compareTo(stilleFrist) > 0;
  }

  /** Ob der Befund eine Störung im Sinne von AK 4 ist — sie gehört dann auf den Leitstand. */
  public boolean isDisruption() {
    return verdict == Verdict.FAILED || verdict == Verdict.WAITING;
  }

  /**
   * Rangfolge für die Auswahl des maßgeblichen Pakets: kleiner ist schwerer, {@link
   * #NICHT_MASSGEBLICH} heißt „bestimmt den Ausgang nicht".
   *
   * <p>Auswahl <em>und</em> Ausschluss stehen bewusst in derselben Methode. Getrennt — ein Prädikat
   * „ist maßgeblich" neben einer Rangfolge — wüssten beide dasselbe über die Zustände, und die
   * Rangfolge trüge einen Zweig für Grün, den das Prädikat nie durchlässt: eine Verzweigung, die
   * kein Test erreichen kann.
   *
   * <p>Grün bestimmt nie etwas, Grau nur mit Fehlerklasse — ohne sie ist das Paket übergangen, und
   * dass der Lauf es nicht angefasst hat, ist kein Mangel des Laufs.
   *
   * <p>{@code min} nimmt bei Gleichstand das erste Element — genau die gewünschte Laufreihenfolge.
   */
  private static int rang(NightRunItem item) {
    return switch (item.state()) {
      case RED -> 0;
      case YELLOW -> 1;
      case GREY -> item.errorClass() == null ? NICHT_MASSGEBLICH : 2;
      case GREEN -> NICHT_MASSGEBLICH;
    };
  }

  private static NightRunOutcome ausPaket(NightRunItem item) {
    Verdict verdict = item.state() == NightRunState.GREY ? Verdict.WAITING : Verdict.FAILED;
    return new NightRunOutcome(
        verdict, new DecisiveItem(item.cardNumber(), item.state(), item.errorClass()), null);
  }
}
