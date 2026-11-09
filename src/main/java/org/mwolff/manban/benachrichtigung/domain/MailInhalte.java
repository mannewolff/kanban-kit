package org.mwolff.manban.benachrichtigung.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;

/**
 * Betreff und Text der Benachrichtigungen (Issue #2080, Plan #2070 E9, E11, E13, E16).
 *
 * <p><b>Nur Text, eine Zeile je Fremdwert.</b> Projektname und Titel kommen von Menschen; ein
 * Zeilenumbruch darin würde im Betreff eine Kopfzeile und im Text eine eigene Zeile vortäuschen.
 *
 * <p><b>Was nicht hineingehört.</b> Label-Namen, Dateipfade und Protokollauszüge gehen die Person
 * nichts an; darum liest die Übersicht den Ausgang des Laufs und nie seinen Abbruchgrund.
 */
public final class MailInhalte {

  private static final String PRAEFIX = "mail.benachrichtigung.";
  private static final String ZEILE = "\n";
  private static final Pattern ZEILENUMBRUCH = Pattern.compile("\\R+");

  /** Diese Arten sind nach einer Ruhezeit vorbei; die übrigen warten weiter auf die Person. */
  private static final Set<AnlassArt> VERGANGEN =
      EnumSet.of(AnlassArt.STOERUNG, AnlassArt.UEBERSICHT);

  private MailInhalte() {}

  /** Liefert die Texte in der Sprache, die der Aufrufer beim Versand bestimmt hat. */
  @FunctionalInterface
  public interface Textquelle {
    /** Der Text zum Schlüssel. */
    String text(String schluessel, Object... argumente);
  }

  /**
   * Was je Installation feststeht.
   *
   * @param produkt der Produktname für den Betreff
   * @param baseUrl die Adresse der Installation
   */
  public record Instanz(String produkt, String baseUrl) {}

  /**
   * Eine Karte oder ein Paket, wie die Mail sie nennt.
   *
   * @param nummer die projektweite Nummer
   * @param titel der Titel
   */
  public record Karte(int nummer, String titel) {}

  /**
   * Der Inhalt der Übersicht nach einem Lauf.
   *
   * @param ausgang der Befund des Laufs
   * @param verstummt ob der Lauf seine Abmeldung schuldig blieb; der Befund nennt das nur „FAILED“
   * @param kostenUsd der gemeldete Betrag; {@code null} heißt nicht gemeldet und nie null
   * @param wartend die Pakete, die auf einen Menschen warten
   * @param stoerung ob der Lauf eine Störung trägt
   */
  public record Uebersicht(
      Verdict ausgang,
      boolean verstummt,
      @Nullable BigDecimal kostenUsd,
      List<Karte> wartend,
      boolean stoerung) {

    boolean ohneHandlungsbedarf() {
      return wartend.isEmpty() && !stoerung;
    }
  }

  /**
   * Eine Zustellung mit allem, was ihr Abschnitt braucht.
   *
   * @param art worüber die Mail informiert
   * @param projectId das Projekt für den Link
   * @param projektName der Name des Projekts
   * @param ausLauf ob der Anlass aus einem Lauf stammt; sonst aus einem Kartenlabel
   * @param karte die zugeordnete Karte; {@code null} ohne Karte, bei einem Kartenlabel nie
   * @param uebersicht der Inhalt bei der Art {@link AnlassArt#UEBERSICHT}, sonst {@code null}
   */
  public record Zustellung(
      AnlassArt art,
      long projectId,
      String projektName,
      boolean ausLauf,
      @Nullable Karte karte,
      @Nullable Uebersicht uebersicht) {}

  /**
   * Eine fertige Mail.
   *
   * @param betreff der Betreff, einzeilig
   * @param text der Text
   */
  public record Mail(String betreff, String text) {}

  /** Die Mail zu einer sofort zugestellten Benachrichtigung. */
  public static Mail einzeln(Textquelle texte, Instanz instanz, Zustellung zustellung) {
    String projekt = einzeilig(zustellung.projektName());
    String art = texte.text(schluessel("art.", zustellung.art()));
    Karte karte = zustellung.karte();
    String betreff =
        karte == null
            ? texte.text(PRAEFIX + "betreff", instanz.produkt(), projekt, art)
            : texte.text(
                PRAEFIX + "betreff-karte",
                instanz.produkt(),
                projekt,
                art,
                String.valueOf(karte.nummer()));
    return new Mail(betreff, abschnitt(texte, instanz, zustellung, false) + ZEILE);
  }

  /** Die Sammelmail nach einer Ruhezeit, je Zustellung ein Abschnitt. */
  public static Mail sammlung(Textquelle texte, Instanz instanz, List<Zustellung> zustellungen) {
    String abschnitte =
        zustellungen.stream()
            .map(zustellung -> abschnitt(texte, instanz, zustellung, true))
            .collect(Collectors.joining(ZEILE + ZEILE));
    return new Mail(
        texte.text(PRAEFIX + "betreff-sammlung", instanz.produkt()),
        texte.text(PRAEFIX + "sammlung-kopf") + ZEILE + ZEILE + abschnitte + ZEILE);
  }

  private static String abschnitt(
      Textquelle texte, Instanz instanz, Zustellung zustellung, boolean sammlung) {
    List<String> zeilen = new ArrayList<>();
    zeilen.add(texte.text(PRAEFIX + "projekt", einzeilig(zustellung.projektName())));
    zeilen.add(texte.text(PRAEFIX + "anlass", anlass(texte, zustellung.art(), sammlung)));
    Karte karte = zustellung.karte();
    if (karte != null) {
      zeilen.add(
          texte.text(PRAEFIX + "karte", String.valueOf(karte.nummer()), einzeilig(karte.titel())));
    }
    Uebersicht uebersicht = zustellung.uebersicht();
    if (uebersicht != null) {
      zeilen.addAll(uebersicht(texte, uebersicht));
    }
    zeilen.add(texte.text(PRAEFIX + "schritt", schritt(texte, zustellung)));
    zeilen.add(texte.text(PRAEFIX + "link", link(instanz, zustellung)));
    return String.join(ZEILE, zeilen);
  }

  private static String anlass(Textquelle texte, AnlassArt art, boolean sammlung) {
    String schluessel = schluessel("anlass.", art);
    return texte.text(sammlung && VERGANGEN.contains(art) ? schluessel + ".vergangen" : schluessel);
  }

  private static List<String> uebersicht(Textquelle texte, Uebersicht uebersicht) {
    List<String> zeilen = new ArrayList<>();
    String ausgang =
        uebersicht.verstummt() ? "verstummt" : uebersicht.ausgang().name().toLowerCase(Locale.ROOT);
    zeilen.add(texte.text(PRAEFIX + "ausgang", texte.text(PRAEFIX + "ausgang." + ausgang)));
    if (uebersicht.wartend().isEmpty()) {
      zeilen.add(texte.text(PRAEFIX + "wartend-keine"));
    } else {
      zeilen.add(texte.text(PRAEFIX + "wartend"));
      uebersicht.wartend().stream()
          .map(
              paket ->
                  texte.text(
                      PRAEFIX + "wartend-eintrag",
                      String.valueOf(paket.nummer()),
                      einzeilig(paket.titel())))
          .forEach(zeilen::add);
    }
    BigDecimal kosten = uebersicht.kostenUsd();
    zeilen.add(
        kosten == null
            ? texte.text(PRAEFIX + "kosten-fehlen")
            : texte.text(PRAEFIX + "kosten", kosten));
    return zeilen;
  }

  private static String schritt(Textquelle texte, Zustellung zustellung) {
    Uebersicht uebersicht = zustellung.uebersicht();
    return uebersicht != null && uebersicht.ohneHandlungsbedarf()
        ? texte.text(PRAEFIX + "kein-handlungsbedarf")
        : texte.text(schluessel("schritt.", zustellung.art()));
  }

  private static String link(Instanz instanz, Zustellung zustellung) {
    String projekt = instanz.baseUrl() + "/projects/" + zustellung.projectId();
    return zustellung.ausLauf()
        ? projekt + "/nachtlauf"
        : projekt + "/karten/" + Objects.requireNonNull(zustellung.karte()).nummer();
  }

  private static String schluessel(String gruppe, AnlassArt art) {
    return PRAEFIX + gruppe + art.name().toLowerCase(Locale.ROOT);
  }

  private static String einzeilig(String wert) {
    return ZEILENUMBRUCH.matcher(wert).replaceAll(" ");
  }
}
