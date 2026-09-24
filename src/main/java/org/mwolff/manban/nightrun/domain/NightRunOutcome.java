package org.mwolff.manban.nightrun.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
 *     bei {@link Verdict#SUCCEEDED}, {@link Verdict#RUNNING} und {@link Verdict#NO_WORK}. Beim
 *     <em>abgebrochenen</em> Lauf steht es, sobald die Pakete eines hergeben (Issue #1143)
 * @param noWorkReason Grund, warum der Lauf nichts abgearbeitet hat (Issue #1068); durchgereicht,
 *     nicht formuliert, und {@code null}, wenn der Lauf gearbeitet hat
 * @param abortReason Grund, warum der Lauf hart abgebrochen ist (Issue #1143); durchgereicht wie
 *     {@code noWorkReason} und {@code null}, wenn der Lauf nicht abgebrochen ist. Er steht im
 *     Befund und nicht nur an der Sicht, weil der Plattform-Leitstand seine Störzeile allein aus
 *     dem Befund bildet.
 */
public record NightRunOutcome(
    Verdict verdict,
    @Nullable DecisiveItem decisiveItem,
    @Nullable String noWorkReason,
    @Nullable String abortReason) {

  /** Rang eines Pakets, das den Ausgang nicht bestimmen kann. */
  private static final int NICHT_MASSGEBLICH = Integer.MAX_VALUE;

  /**
   * Rückfalltext für einen Lauf ohne Arbeit, der keinen Grund meldet (Issue #1068, AK 2 der
   * fachlichen Quelle #1060). Er entsteht am Server und nicht im Frontend (Plan #1067, E4):
   * Laufplatte, Laufband und „Letzter Lauf" lesen denselben Wert, drei Einsetzstellen liefen
   * auseinander.
   *
   * <p><b>Ein reiner Anzeigetext</b> seit Issue #1185 (es löst #1121 ab): Am Wortlaut hängt keine
   * Aussage über den Ausgang mehr — jeder Grund ist {@link Verdict#NO_WORK}, gemeldet oder nicht.
   * Der Vergleich, der ihn bis dahin von einem gemeldeten Grund unterschied, ist mit #1185
   * entfallen; gesetzt wird er weiterhin allein im Dienst.
   *
   * <p><b>Er bleibt trotzdem hier</b> und wandert nicht zurück in den Dienst: Er ist öffentlich und
   * wird von zwei Testklassen gelesen ({@code NightRunOutcomeTest}, {@code DisruptionServiceTest}),
   * und er ist der Wortlaut eines <em>Ausgangs</em> dieser Domäne. Ihn in den Dienst zu schieben,
   * hieße, den Text der Schicht zu geben, die ihn einsetzt, statt der, die ihn bedeutet — und ein
   * Test der Domäne müsste ihn dann von dort holen.
   */
  public static final String GRUND_UNBEKANNT = "Nichts abgearbeitet — Grund unbekannt";

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

    /**
     * Nicht gelungen: hartes Scheitern, ein verstummter Lauf oder ein Lauf, der seinen
     * <b>Abbruch</b> selbst gemeldet hat (Issue #1143).
     *
     * <p><b>Ein Lauf ohne Arbeit gehört seit Issue #1185 nicht mehr dazu</b> — auch nicht der ohne
     * gemeldeten Grund ({@link NightRunOutcome#GRUND_UNBEKANNT}). Wer nichts vorfand, hat nichts
     * falsch gemacht; was hinter dem Rückfall an echtem Problem steckte, sagen jetzt die Pakete.
     */
    FAILED,

    /** Abgeschlossen, aber ein Paket wartet auf etwas — zurückgestellt oder auf einen Menschen. */
    WAITING,

    /**
     * Abgeschlossen, keine Arbeit vorgefunden — <b>kein Mangel des Laufs</b> (Issue #1121).
     *
     * <p>Der Lauf lief an, fand nichts Freigegebenes und meldete das mit seinem Grund. Wer ein
     * Projekt nachts bewusst ruhen lässt, soll dafür keine Störung quittieren müssen.
     *
     * <p><b>Seit Issue #1185 gehört der Lauf ohne gemeldeten Grund dazu</b> ({@link
     * NightRunOutcome#GRUND_UNBEKANNT}): Ein alter Runner, der Upload-Weg oder ein Lauf, der den
     * Grund nicht mitschickte, ist derselbe ruhige Lauf — nur mit blasserer Auskunft.
     */
    NO_WORK,

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
   *   <li><b>Hart abgebrochen</b> schlägt den Lauf ohne Arbeit und die Pakete (Issue #1143, Plan
   *       #1139 E5). Ein Lauf, der abbrach, ist nie gelungen — auch nicht nach drei grünen Paketen.
   *       Die Stelle <em>hinter</em> den beiden davor ist dieselbe Aussage wie dort: Ein unfertiger
   *       Lauf hat noch nichts zu melden, ein verstummter meldet gar nichts mehr. <b>Das
   *       maßgebliche Paket bleibt</b> und wird weiter aus den Paketen bestimmt: „Karte #1112:
   *       harter Abbruch" ist die genauere Auskunft als der Abbruchgrund allein, und sie ist seit
   *       #1123 eigens geschärft.
   *   <li><b>Rot vor Gelb vor Grau-mit-Fehlerklasse</b>, innerhalb einer Farbe das erste in
   *       Laufreihenfolge — <b>bei einer Kette das zuletzt gerissene</b> (Issue #1123, siehe {@link
   *       #auswahlreihenfolge}).
   *   <li><b>Ohne Arbeit</b> steht zuletzt (Issue #1185, Plan #1181 E2/E6) — und stand bis dahin
   *       eine Stufe höher. Zwei Läufe zeigten, dass das die falsche Reihenfolge war: Wer alle
   *       Pakete zurückstellte, meldet keinen Grund und wurde über den Rückfall rot statt „mit
   *       Vorbehalt"; wer einen Grund meldete und ein rotes Paket trug, fiel als ruhiger Lauf aus
   *       der Störungsliste. Das maßgebliche Paket ist die genauere Auskunft, wo es eines gibt;
   *       erst wo keines ist, gilt der Grund. Er macht den Lauf zu {@link Verdict#NO_WORK} — gleich
   *       ob der Runner ihn meldete oder der Server auf {@link #GRUND_UNBEKANNT} zurückfiel.
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
   * @param abortReason Grund eines harten Abbruchs; {@code null}, wenn der Lauf nicht abbrach. Ein
   *     <em>gesetzter</em> Wert genügt — dass ein leerer Text wie ein fehlender gilt, entscheidet
   *     schon der Dienst beim Übernehmen (Issue #1142), und ein zweites Mal hier wäre dieselbe
   *     Regel an zwei Stellen.
   * @param mode die Laufart — sie entscheidet unter gleichrangigen Paketen (Issue #1123)
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
      @Nullable String abortReason,
      NightRunMode mode,
      List<NightRunItem> items,
      Instant startedAt,
      @Nullable Instant updatedAt,
      Instant jetzt,
      Duration stilleFrist) {
    if (!complete && verstummt(startedAt, updatedAt, jetzt, stilleFrist)) {
      return new NightRunOutcome(Verdict.FAILED, null, null, null);
    }
    if (!complete) {
      return new NightRunOutcome(Verdict.RUNNING, null, null, null);
    }
    // Einmal bestimmt, zweimal gebraucht (Issue #1185): Der Abbruch-Zweig braucht dasselbe Paket
    // wie die Rangfolge darunter, und zweimal ausgewaehlt liefen die beiden auseinander.
    Optional<NightRunItem> massgeblich = massgeblich(mode, items);
    if (abortReason != null) {
      return new NightRunOutcome(
          Verdict.FAILED,
          massgeblich.map(NightRunOutcome::decisiveItem).orElse(null),
          null,
          abortReason);
    }
    return massgeblich
        .map(NightRunOutcome::ausPaket)
        .orElseGet(() -> ohneMassgeblichesPaket(noWorkReason));
  }

  /**
   * Der Befund eines Laufs, dessen Pakete keines hergeben, das den Ausgang bestimmt: entweder ein
   * Lauf ohne Arbeit oder ein gelungener (Issue #1185).
   *
   * <p>Ein gesetzter Grund ist {@link Verdict#NO_WORK} — gleich <em>welcher</em>: Seit #1185 hängt
   * am Wortlaut keine Aussage über den Ausgang mehr. Was #1121 am Rückfall festmachte, dahinter
   * könne ein echtes Problem stecken, entscheiden jetzt die Pakete: Ein zurückgestelltes Paket ist
   * maßgeblich und kommt hier nie an.
   *
   * <p>Ein leerer Grund gilt wie ein fehlender. Der Dienst lässt ihn ohnehin nicht entstehen (er
   * fällt auf {@link #GRUND_UNBEKANNT} zurück); ein Bestand, der ihn trägt, wäre sonst ein Lauf
   * ohne Arbeit ohne Auskunft.
   */
  private static NightRunOutcome ohneMassgeblichesPaket(@Nullable String noWorkReason) {
    if (noWorkReason == null || noWorkReason.isBlank()) {
      return new NightRunOutcome(Verdict.SUCCEEDED, null, null, null);
    }
    return new NightRunOutcome(Verdict.NO_WORK, null, noWorkReason, null);
  }

  /**
   * Das Paket, das den Ausgang bestimmt — oder keines, wenn alle grün oder übergangen sind.
   *
   * <p>Eigene Methode seit Issue #1143: Der abgebrochene Lauf braucht dieselbe Auswahl, aber ein
   * anderes Urteil. Zweimal ausgeschrieben liefen die beiden Auswahlen auseinander, sobald die
   * Rangfolge sich wieder ändert.
   */
  private static Optional<NightRunItem> massgeblich(NightRunMode mode, List<NightRunItem> items) {
    return auswahlreihenfolge(mode, items).stream()
        .filter(item -> rang(item) != NICHT_MASSGEBLICH)
        .min(Comparator.comparingInt(NightRunOutcome::rang));
  }

  /**
   * Die Reihenfolge, in der gleichrangige Pakete um das maßgebliche streiten — in einer Kette
   * umgekehrt (Issue #1123).
   *
   * <p><strong>Warum umgekehrt.</strong> Ein Kettenlauf trägt neben seinen Paketen die
   * Ketten-Einheit selbst, und die steht immer zuerst. Ihren Abbruch hat sie <em>geerbt</em>
   * („Stufe umsetzung: harter Stopp in der Runde zu Paket #1112"); gerissen ist die Kette an dem
   * Paket, das danach kam. Unter gleichrangigen Paketen ist deshalb das letzte der konkrete Bruch —
   * und genau das gehört in die Störzeile, nicht die Einheit, die es nur weiterreicht.
   *
   * <p>Die Regel kommt ohne Wissen darüber aus, <em>welches</em> Paket die Kette ist: Riss sie
   * schon in der Planung, ist die Ketten-Einheit das einzige rote Paket, und die umgekehrte
   * Reihenfolge wählt sie. Ein Ausschluss nach Kartennummer bräuchte dafür eine zweite Angabe am
   * Lauf.
   *
   * <p>Gedreht wird nur die <em>Reihenfolge</em>, nicht die Rangfolge: Rot schlägt Gelb auch in
   * einer Kette, weil {@link #rang} vor der Position entscheidet.
   */
  private static List<NightRunItem> auswahlreihenfolge(
      NightRunMode mode, List<NightRunItem> items) {
    return mode == NightRunMode.CHAIN ? items.reversed() : items;
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

  /**
   * Ob der Befund eine Störung im Sinne von AK 4 ist — sie gehört dann auf den Leitstand.
   *
   * <p>{@link Verdict#NO_WORK} gehört ausdrücklich nicht dazu (Issue #1121): Eine ruhige Nacht muss
   * niemand quittieren.
   */
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
   * <p>{@code min} nimmt bei Gleichstand das erste Element — das erste der {@link
   * #auswahlreihenfolge}, also die Laufreihenfolge und bei einer Kette ihre Umkehrung.
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
    return new NightRunOutcome(verdict, decisiveItem(item), null, null);
  }

  private static DecisiveItem decisiveItem(NightRunItem item) {
    return new DecisiveItem(item.cardNumber(), item.state(), item.errorClass());
  }
}
