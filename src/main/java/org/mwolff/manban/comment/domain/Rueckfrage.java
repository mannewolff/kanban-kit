package org.mwolff.manban.comment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen;

/**
 * Eine Rückfrage der KI an einer Karte (Issue #2210, Plan #2206 E8, E9, E11; Vertrag V1): ein
 * Kommentar mit dem Kopf {@code ## Rückfrage} bzw. {@code ## Question}, optional den Zeilen {@code
 * Vorschau:} und {@code Commit:} über dem Block, und einem Block {@code json kit-vorlage} mit den
 * Einträgen.
 *
 * @param kommentarId Kennung des Kommentars; auf sie verweist die Antwort
 * @param angelegtAm wann der Kommentar angelegt wurde
 * @param sprache Sprache des Kopfs; die Antwort folgt ihr
 * @param vorschau Adresse der Vorschau einer Freigabe-Rückfrage; sonst {@code null}
 * @param commit Stand, den eine Freigabe-Rückfrage zur Freigabe stellt; sonst {@code null}
 * @param eintraege die Fragen und Wege, je Nummer einer
 */
public record Rueckfrage(
    long kommentarId,
    Instant angelegtAm,
    Kopfsprache sprache,
    @Nullable String vorschau,
    @Nullable String commit,
    List<Eintrag> eintraege) {

  private static final String KENNUNG = "kit-vorlage";

  public Rueckfrage {
    eintraege = List.copyOf(eintraege);
  }

  /** Art eines Eintrags. */
  public enum Art {
    FRAGE,
    WEG
  }

  /**
   * Eine angebotene Möglichkeit; ihre Nummer zählt ab 1 in der Reihenfolge der Liste.
   *
   * @param text Text der Möglichkeit
   * @param hinweis Hinweis dazu; {@code null} ohne
   * @param empfohlen ob die KI sie empfiehlt
   */
  public record Option(String text, @Nullable String hinweis, boolean empfohlen) {}

  /**
   * Ein Eintrag der Rückfrage. Fehlende Texte sind leer.
   *
   * @param nr Nummer, eindeutig in der Rückfrage
   * @param art Frage oder Weg
   * @param frage die Frage
   * @param empfehlung die Empfehlung der KI
   * @param grund ihr Grund
   * @param einarbeitung was die KI mit der Empfehlung einarbeitet
   * @param optionen die angebotenen Möglichkeiten; leer, wenn keine angeboten sind
   */
  public record Eintrag(
      int nr,
      Art art,
      String frage,
      String empfehlung,
      String grund,
      String einarbeitung,
      List<Option> optionen) {

    public Eintrag {
      optionen = List.copyOf(optionen);
    }

    /** Die Nummer der ersten empfohlenen Möglichkeit; {@code null}, wenn keine empfohlen ist. */
    public @Nullable Integer empfohleneWahl() {
      for (int i = 0; i < optionen.size(); i++) {
        if (optionen.get(i).empfohlen()) {
          return i + 1;
        }
      }
      return null;
    }

    /** Ob dieser Eintrag eine Möglichkeit mit dieser Nummer anbietet. */
    public boolean bietet(int wahl) {
      return wahl >= 1 && wahl <= optionen.size();
    }
  }

  /** Ob die Rückfrage einen Stand zur Freigabe stellt (Zeile {@code Commit:}, E11). */
  public boolean istFreigabe() {
    return commit != null;
  }

  /** Der Eintrag mit dieser Nummer. */
  public Optional<Eintrag> eintrag(int nr) {
    return eintraege.stream().filter(e -> e.nr() == nr).findFirst();
  }

  /**
   * Liest die Rückfrage aus dem Text eines Kommentars. Leer, wenn die erste nichtleere Zeile nicht
   * der Kopf ist, der Block fehlt, kein gültiges JSON-Feld trägt oder keinen lesbaren Eintrag hat.
   * Einträge ohne ganzzahlige Nummer, mit unbekannter Art oder mit schon vergebener Nummer fallen
   * weg.
   */
  public static Optional<Rueckfrage> lies(long kommentarId, Instant angelegtAm, String text) {
    List<String> zeilen = text.lines().toList();
    int kopf = 0;
    while (kopf < zeilen.size() && zeilen.get(kopf).isBlank()) {
      kopf++;
    }
    if (kopf == zeilen.size()) {
      return Optional.empty();
    }
    Optional<Kopfsprache> sprache =
        Kopfsprache.vonKopf(KitMeldungen.Zeile.RUECKFRAGE, zeilen.get(kopf));
    if (sprache.isEmpty()) {
      return Optional.empty();
    }
    Optional<KitBlock.Fund> fund = KitBlock.finde(zeilen, kopf + 1, KENNUNG);
    if (fund.isEmpty()) {
      return Optional.empty();
    }
    List<String> davor = zeilen.subList(kopf + 1, fund.get().beginn());
    List<Eintrag> eintraege =
        KitBlock.alsJson(fund.get().inhalt()).map(Rueckfrage::leseEintraege).orElse(List.of());
    if (eintraege.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Rueckfrage(
            kommentarId,
            angelegtAm,
            sprache.get(),
            wert(davor, KitMeldungen.Zeile.VORSCHAU),
            wert(davor, KitMeldungen.Zeile.COMMIT),
            eintraege));
  }

  private static @Nullable String wert(List<String> zeilen, KitMeldungen.Zeile zeile) {
    return zeilen.stream()
        .map(z -> zeile.rest(z).map(String::strip).orElse(""))
        .filter(w -> !w.isEmpty())
        .findFirst()
        .orElse(null);
  }

  private static List<Eintrag> leseEintraege(JsonNode block) {
    List<Eintrag> eintraege = new ArrayList<>();
    if (!block.isArray()) {
      return eintraege;
    }
    Set<Integer> vergeben = new HashSet<>();
    for (JsonNode knoten : block) {
      leseEintrag(knoten).filter(e -> vergeben.add(e.nr())).ifPresent(eintraege::add);
    }
    return eintraege;
  }

  private static Optional<Eintrag> leseEintrag(JsonNode knoten) {
    JsonNode nr = knoten.path("nr");
    Optional<Art> art = art(knoten.path("art"));
    if (!nr.isInt() || art.isEmpty()) {
      return Optional.empty();
    }
    List<Option> optionen = new ArrayList<>();
    for (JsonNode option : knoten.path("optionen")) {
      optionen.add(
          new Option(
              text(option, "text"),
              option.path("hinweis").isTextual() ? option.path("hinweis").asText() : null,
              option.path("empfohlen").isBoolean() && option.path("empfohlen").booleanValue()));
    }
    return Optional.of(
        new Eintrag(
            nr.intValue(),
            art.get(),
            text(knoten, "frage"),
            text(knoten, "empfehlung"),
            text(knoten, "grund"),
            text(knoten, "einarbeitung"),
            optionen));
  }

  private static Optional<Art> art(JsonNode art) {
    if (art.isMissingNode()) {
      return Optional.of(Art.FRAGE);
    }
    return switch (art.asText().toLowerCase(Locale.ROOT)) {
      case "frage" -> Optional.of(Art.FRAGE);
      case "weg" -> Optional.of(Art.WEG);
      default -> Optional.empty();
    };
  }

  private static String text(JsonNode knoten, String feld) {
    JsonNode wert = knoten.path(feld);
    return wert.isTextual() ? wert.asText() : "";
  }
}
