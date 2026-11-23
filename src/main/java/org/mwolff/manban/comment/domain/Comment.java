package org.mwolff.manban.comment.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;
import org.mwolff.manban.common.KitMeldungen;

/**
 * Kommentar an einer Karte.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param cardId zugehörige Karte
 * @param authorUserId Autor-Benutzer; {@code null} bei rein PAT-erzeugten Kommentaren
 * @param authorName Anzeigename des Autors
 * @param body Kommentartext
 * @param createdAt Erstellzeitpunkt
 * @param updatedAt letzte Änderung
 * @param laufStart Laufkennung des letzten Schreibers aus dem Header {@code X-Night-Run} (Issue
 *     #1428, Plan #1423 E3); {@code null}, wenn er sich nicht ausgewiesen hat
 * @param origin Herkunft beim Anlegen, {@code SESSION} oder {@code TOKEN} (Issue #1620); {@code
 *     null} = unbekannt
 * @param tokenName Name des Projekt-Zugangs bei Herkunft {@code TOKEN}; sonst {@code null}
 * @param agent Modell-Selbstauskunft des Werkzeugs; {@code null}, wenn keine angegeben wurde
 * @param vonHandGeaendertAm letzte Änderung von Hand an einer Meldung des Kits (Issue #2109, Plan
 *     #2108 E4); {@code null}, wenn der Text vom Kit stammt oder nie eine Meldung war
 * @param vonHandKopfzeile erste nichtleere Zeile der Fassung des Kits vor der Änderung von Hand;
 *     {@code null} ohne Änderung von Hand oder wenn jene Fassung keine solche Zeile trug
 * @param externKennung Kennung des Kommentars im fremden System, aus dem der Spiegel ihn übernommen
 *     hat (Issue #2162, Plan #2152 A1); {@code null} bei jedem eigenen Kommentar
 * @param zugangArt Art des Zugangs beim Anlegen, {@code STANDARD} oder {@code APP} (Issue #2208,
 *     Plan #2206 E6); {@code null} bei der Sitzung, bei Altzeilen und gespiegelten Kommentaren
 * @param vonKi ob der Kommentar ein Beitrag der KI ist: angelegt über einen Zugang der Art {@code
 *     STANDARD}, dessen Inhaber dabei echter OWNER oder ADMIN war (Plan #2206 E6). Steht beim
 *     Anlegen fest und bleibt bei jeder Änderung.
 */
public record Comment(
    @Nullable Long id,
    Long cardId,
    @Nullable Long authorUserId,
    String authorName,
    String body,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant laufStart,
    @Nullable String origin,
    @Nullable String tokenName,
    @Nullable String agent,
    @Nullable Instant vonHandGeaendertAm,
    @Nullable String vonHandKopfzeile,
    @Nullable String externKennung,
    @Nullable String zugangArt,
    boolean vonKi)
    implements Identifiable {

  private static final String HERKUNFT_TOKEN = "TOKEN";
  private static final String HERKUNFT_SESSION = "SESSION";

  /** Ein Kommentar ohne Zugangsart, nicht von der KI — die bisherige Parameterliste (#2208). */
  public Comment(
      @Nullable Long id,
      Long cardId,
      @Nullable Long authorUserId,
      String authorName,
      String body,
      Instant createdAt,
      Instant updatedAt,
      @Nullable Instant laufStart,
      @Nullable String origin,
      @Nullable String tokenName,
      @Nullable String agent,
      @Nullable Instant vonHandGeaendertAm,
      @Nullable String vonHandKopfzeile,
      @Nullable String externKennung) {
    this(
        id,
        cardId,
        authorUserId,
        authorName,
        body,
        createdAt,
        updatedAt,
        laufStart,
        origin,
        tokenName,
        agent,
        vonHandGeaendertAm,
        vonHandKopfzeile,
        externKennung,
        null,
        false);
  }

  /** Ein eigener Kommentar, nicht gespiegelt — die bisherige Parameterliste (Issue #2162). */
  public Comment(
      @Nullable Long id,
      Long cardId,
      @Nullable Long authorUserId,
      String authorName,
      String body,
      Instant createdAt,
      Instant updatedAt,
      @Nullable Instant laufStart,
      @Nullable String origin,
      @Nullable String tokenName,
      @Nullable String agent,
      @Nullable Instant vonHandGeaendertAm,
      @Nullable String vonHandKopfzeile) {
    this(
        id,
        cardId,
        authorUserId,
        authorName,
        body,
        createdAt,
        updatedAt,
        laufStart,
        origin,
        tokenName,
        agent,
        vonHandGeaendertAm,
        vonHandKopfzeile,
        null);
  }

  /** Ein Kommentar ohne Änderung von Hand — die bisherige Parameterliste (Plan #2108 E4). */
  public Comment(
      @Nullable Long id,
      Long cardId,
      @Nullable Long authorUserId,
      String authorName,
      String body,
      Instant createdAt,
      Instant updatedAt,
      @Nullable Instant laufStart,
      @Nullable String origin,
      @Nullable String tokenName,
      @Nullable String agent) {
    this(
        id,
        cardId,
        authorUserId,
        authorName,
        body,
        createdAt,
        updatedAt,
        laufStart,
        origin,
        tokenName,
        agent,
        null,
        null);
  }

  /**
   * Ein Kommentar aus einem fremden System, wie der Spiegel ihn zum ersten Mal übernimmt (Issue
   * #2162, Plan #2152 A11, A19): ohne Benutzer von kanban-kit, mit den Zeitpunkten des fremden
   * Systems und Herkunft {@code TOKEN}, wenn das Kit-Konto ihn schrieb, sonst {@code SESSION}.
   * Wurde eine Meldung des Kits dort schon nach ihrer Anlage geändert, ist sie von Hand geändert;
   * die Fassung des Kits kennt der Spiegel dann nicht, die Kopfzeile bleibt leer.
   *
   * @param autor Benutzername im fremden System
   * @param vomKitKonto ob der Autor das hinterlegte Kit-Konto ist
   */
  public static Comment gespiegelt(
      long cardId,
      String externKennung,
      String autor,
      String text,
      Instant angelegtAm,
      Instant geaendertAm,
      boolean vomKitKonto) {
    boolean vonHand = vomKitKonto && geaendertAm.isAfter(angelegtAm);
    return new Comment(
        null,
        cardId,
        null,
        autor,
        text,
        angelegtAm,
        geaendertAm,
        null,
        vomKitKonto ? HERKUNFT_TOKEN : HERKUNFT_SESSION,
        null,
        null,
        vonHand ? geaendertAm : null,
        null,
        externKennung);
  }

  /**
   * Der Text, wie das fremde System ihn jetzt führt (Issue #2162, Plan #2152 A19). Liegt die
   * Änderungszeit einer Meldung des Kits nach ihrer Anlage, ist sie von Hand geändert: Die Marke
   * trägt die Änderungszeit, die Kopfzeile bleibt die der Fassung des Kits.
   *
   * @param geaendertAm Änderungszeitpunkt im fremden System
   */
  public Comment withSpiegelText(String newBody, Instant geaendertAm) {
    boolean vonHand = HERKUNFT_TOKEN.equals(origin) && geaendertAm.isAfter(createdAt);
    return new Comment(
        id,
        cardId,
        authorUserId,
        authorName,
        newBody,
        createdAt,
        geaendertAm,
        laufStart,
        origin,
        tokenName,
        agent,
        vonHand ? geaendertAm : vonHandGeaendertAm,
        vonHand ? kopfzeileBeimKennzeichnen() : vonHandKopfzeile,
        externKennung,
        zugangArt,
        vonKi);
  }

  /**
   * Der Kommentar, nachdem das fremde System ihn gelöscht hat (Issue #2162, Plan #2152 A19): Eine
   * Meldung des Kits bleibt stehen und gilt ab {@code zeitpunkt} als von Hand geändert; trägt sie
   * die Marke schon, bleibt sie, wie sie ist. Jeder andere Kommentar bleibt unverändert.
   *
   * @param zeitpunkt wann der Spiegel das Fehlen festgestellt hat
   */
  public Comment alsImFremdenSystemGeloescht(Instant zeitpunkt) {
    if (!HERKUNFT_TOKEN.equals(origin) || vonHandGeaendertAm != null) {
      return this;
    }
    return new Comment(
        id,
        cardId,
        authorUserId,
        authorName,
        body,
        createdAt,
        updatedAt,
        laufStart,
        origin,
        tokenName,
        agent,
        zeitpunkt,
        kopfzeileBeimKennzeichnen(),
        externKennung,
        zugangArt,
        vonKi);
  }

  /**
   * Ist der Kommentar schon gekennzeichnet, bleibt seine Kopfzeile; sonst die des jetzigen Texts.
   */
  private @Nullable String kopfzeileBeimKennzeichnen() {
    return vonHandGeaendertAm != null ? vonHandKopfzeile : erstZeile(body);
  }

  /**
   * Ob der Kopf des Texts eine Antwort ist (Issue #2208, Plan #2206 E7). Kopf ist die erste
   * nichtleere Zeile; erkannt wird wie beim Auswerten über {@link KitMeldungen.Zeile#ANTWORT}, ohne
   * Rücksicht auf führenden Leerraum und Schreibung. Es zählt jede Zeile, die so beginnt, auch mit
   * Text dahinter: Eine Sperre auf dieser Prüfung ist damit weiter als jede Erkennung einer
   * Antwort.
   */
  public static boolean kopfIstAntwort(String text) {
    return KitMeldungen.Zeile.ANTWORT.rest(erstZeile(text)).isPresent();
  }

  private static @Nullable String erstZeile(String text) {
    return text.lines().filter(zeile -> !zeile.isBlank()).findFirst().orElse(null);
  }

  /**
   * Neuer Text vom Schreiber mit der Laufkennung {@code newLaufStart}; sie ersetzt die alte. Die
   * Herkunft bleibt die des Anlegens.
   *
   * <p>Ändert nicht ein Token den Text einer mit Herkunft {@code TOKEN} angelegten Meldung, gilt
   * das als Änderung von Hand (Plan #2108 E4). Die Kopfzeile bleibt dabei die der Fassung des Kits,
   * auch bei jeder weiteren Änderung von Hand. Ersetzt ein Token den Text, stammt er wieder ganz
   * vom Kit und die Marke fällt weg.
   *
   * @param schreiberHerkunft Herkunft der Änderung, {@code null} = unbekannt und damit kein Token
   * @param jetzt Zeitpunkt der Änderung
   */
  public Comment withBody(
      String newBody,
      @Nullable Instant newLaufStart,
      @Nullable String schreiberHerkunft,
      Instant jetzt) {
    boolean vonHand = HERKUNFT_TOKEN.equals(origin) && !HERKUNFT_TOKEN.equals(schreiberHerkunft);
    return new Comment(
        id,
        cardId,
        authorUserId,
        authorName,
        newBody,
        createdAt,
        updatedAt,
        newLaufStart,
        origin,
        tokenName,
        agent,
        vonHand ? jetzt : null,
        vonHand ? kopfzeileDerKitFassung() : null,
        externKennung,
        zugangArt,
        vonKi);
  }

  private @Nullable String kopfzeileDerKitFassung() {
    if (vonHandKopfzeile != null) {
      return vonHandKopfzeile;
    }
    return erstZeile(body);
  }
}
