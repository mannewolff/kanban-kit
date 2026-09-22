package org.mwolff.manban.ratelimit.domain;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Unveränderliches Kontingent einer Person für die Durchsatzbremse samt der Zeitregeln darauf —
 * Issue #999, Plan #995 (E3, E17).
 *
 * <p><strong>Form der Grenze:</strong> ein Token-Bucket für „60 je Minute" und daneben ein Zähler
 * laufender Aufrufe für „davon 10 gleichzeitig". Ein starres Kalenderminuten-Fenster ließe 120
 * Befehle in zwei Sekunden über die Fenstergrenze durch und bestrafte danach gerade die zugesagte
 * Dauerlast (E3). Der Eimer füllt dagegen kontinuierlich nach und weist nur den Überschuss ab.
 *
 * <p><strong>Warum der Füllstand eine Dauer ist:</strong> Ein Aufruf kostet genau ein
 * Nachfüllintervall ({@link Limits#costPerCall()}, bei 60 je Minute eine Sekunde). Das Guthaben ist
 * die angesparte Zeit, gedeckelt auf {@code Eimergröße × Intervall}. So bleibt die Rechnung exakt:
 * Ein Bruchteil eines Befehls als Gleitkommazahl gerät an der Sekundengrenze leicht auf 0,9999999
 * und wiese dann ausgerechnet den Aufruf ab, der die Dauerlast gerade einhält.
 *
 * <p>Wie {@link OriginAttempts} framework-frei und ohne eigene Uhr: Jede Methode bekommt den
 * Zeitpunkt und die Grenzwerte herein.
 *
 * @param credit Füllstand des Eimers als angesparte Zeit; ein Aufruf kostet {@link
 *     Limits#costPerCall()}
 * @param refilledAt Zeitpunkt der letzten Nachfüllung
 * @param inFlight Zahl der laufenden, noch nicht freigegebenen Aufrufe
 * @param warnedAt Zeitpunkt der letzten {@code WARN}-Zeile für diese Person; {@code null}, solange
 *     keine geschrieben wurde
 */
public record PersonBudget(
    Duration credit, Instant refilledAt, int inFlight, @Nullable Instant warnedAt) {

  /**
   * Dauer, innerhalb derer zu einer Person höchstens eine {@code WARN}-Zeile entsteht. Eine Zeile
   * je Abweisung wäre bei anhaltender Überschreitung selbst ein Flutungsvektor.
   */
  private static final Duration WARN_WINDOW = Duration.ofMinutes(1);

  /**
   * Grenzwerte der Bremse. Sie kommen von außen herein — dieses Paket kennt keine Konfiguration.
   *
   * @param costPerCall Nachfüllintervall: was ein Aufruf vom Guthaben nimmt
   * @param capacity Eimergröße in Aufrufen
   * @param maxConcurrent Deckel der gleichzeitig laufenden Aufrufe
   */
  public record Limits(Duration costPerCall, int capacity, int maxConcurrent) {

    /**
     * Grenzwerte aus „so viele je Minute, davon so viele gleichzeitig". Die Eimergröße ist die
     * Minutenzahl selbst: Eine Person, die eine Minute lang nichts geschickt hat, darf eine volle
     * Minute auf einmal abrufen.
     */
    public static Limits of(int perMinute, int concurrent) {
      return new Limits(Duration.ofMinutes(1).dividedBy(perMinute), perMinute, concurrent);
    }

    /** Das Guthaben eines vollen Eimers. */
    Duration fullCredit() {
      return costPerCall.multipliedBy(capacity);
    }
  }

  /** Das Kontingent einer Person, die bisher nichts geschickt hat: voller Eimer, nichts läuft. */
  public static PersonBudget fresh(Instant now, Limits limits) {
    return new PersonBudget(limits.fullCredit(), now, 0, null);
  }

  /**
   * Das Kontingent zu {@code now}, nachgefüllt um die seit der letzten Nachfüllung verstrichene
   * Zeit, höchstens bis zum vollen Eimer. Läuft die Uhr rückwärts, bleibt alles, wie es ist — sonst
   * zählte dieselbe Zeitspanne nach der Korrektur ein zweites Mal.
   */
  public PersonBudget refilled(Instant now, Limits limits) {
    if (!now.isAfter(refilledAt)) {
      return this;
    }
    Duration topped = credit.plus(Duration.between(refilledAt, now));
    return new PersonBudget(shorter(topped, limits.fullCredit()), now, inFlight, warnedAt);
  }

  /**
   * Wie lange ein Aufruf zu {@code now} noch warten muss; {@link Duration#ZERO}, wenn er
   * durchgelassen wird.
   *
   * <p>Fehlt Guthaben, ist es die Zeit, bis ein Aufruf nachgefüllt ist. Ist der Deckel erreicht,
   * lässt sich nicht vorhersagen, wann ein laufender Aufruf endet; der Hinweis ist dann ein
   * Nachfüllintervall. Greifen beide, gilt der längere.
   */
  public Duration waitTime(Instant now, Limits limits) {
    PersonBudget current = refilled(now, limits);
    Duration forCredit = longer(limits.costPerCall().minus(current.credit), Duration.ZERO);
    Duration forConcurrency =
        current.inFlight >= limits.maxConcurrent() ? limits.costPerCall() : Duration.ZERO;
    return longer(forCredit, forConcurrency);
  }

  /**
   * Lässt einen Aufruf durch: nimmt ihn vom Guthaben und zählt ihn als laufend. Setzt voraus, dass
   * {@link #waitTime} zu {@code now} null ist — ein abgewiesener Aufruf ändert das Kontingent
   * überhaupt nicht (E17), sonst verlängerte jeder Wiederholversuch die eigene Wartezeit.
   */
  public PersonBudget admit(Instant now, Limits limits) {
    PersonBudget current = refilled(now, limits);
    return new PersonBudget(
        current.credit.minus(limits.costPerCall()),
        current.refilledAt,
        current.inFlight + 1,
        current.warnedAt);
  }

  /**
   * Die kürzere der beiden Dauern. Über Nanosekunden statt über {@code compareTo}, weil bei
   * Gleichstand beide Antworten richtig sind — ein Vergleich hinterließe dort eine Grenze, die
   * nichts entscheidet.
   */
  private static Duration shorter(Duration a, Duration b) {
    return Duration.ofNanos(Math.min(a.toNanos(), b.toNanos()));
  }

  /** Die längere der beiden Dauern; zur Form siehe {@link #shorter}. */
  private static Duration longer(Duration a, Duration b) {
    return Duration.ofNanos(Math.max(a.toNanos(), b.toNanos()));
  }

  /** Gibt einen laufenden Aufruf frei. Das Guthaben bleibt verbraucht; nie unter null. */
  public PersonBudget release() {
    return new PersonBudget(credit, refilledAt, Math.max(inFlight - 1, 0), warnedAt);
  }

  /** Ob zu {@code now} eine {@code WARN}-Zeile fällig ist: keine im laufenden Fenster. */
  public boolean shouldWarn(Instant now) {
    return warnedAt == null || !now.isBefore(warnedAt.plus(WARN_WINDOW));
  }

  /** Vermerkt die {@code WARN}-Zeile zu {@code now}. */
  public PersonBudget warned(Instant now) {
    return new PersonBudget(credit, refilledAt, inFlight, now);
  }

  /**
   * Ob der Eintrag zu {@code now} nichts mehr aussagt: nichts läuft, und der Eimer wäre voll. Ein
   * solcher Eintrag ist von einer Person, die nie etwas geschickt hat, nicht zu unterscheiden und
   * darf verdrängt werden. Ein laufender Aufruf dagegen trüge seinen Zähler mit hinaus.
   */
  public boolean isIdle(Instant now, Limits limits) {
    return inFlight == 0 && refilled(now, limits).credit.compareTo(limits.fullCredit()) >= 0;
  }
}
