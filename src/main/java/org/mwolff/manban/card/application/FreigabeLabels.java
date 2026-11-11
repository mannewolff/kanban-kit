package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.common.KitNamen.Schutz;

/**
 * Richtung der Freigabe-Labels des claude-workflow-kits, vom Server erzwungen (Issue #1421, #1586).
 *
 * <p>Die Labels der Sachen {@code NIGHT} und {@code NIGHTRUN} geben Arbeit frei und setzt nur ein
 * Mensch; der Runner darf sie abnehmen, sobald sie verbraucht sind. Die Labels von {@code KLAEREN},
 * {@code GESCHUETZT} und {@code WARTET_EREIGNIS} setzt die Maschine; das Abnehmen ist die Freigabe
 * und steht nur dem Menschen zu. Die Labels von {@code MANUELL} setzt und nimmt allein der Mensch
 * ab (Issue #2142): Mit ihnen nimmt er eine Karte vom Taktgeber des Kits aus. Ohne diese Regel
 * könnte sich eine Session am Kit vorbei selbst freigeben. Welche Sache ein Name trägt und welchen
 * {@link Schutz} sie hat, sagt allein {@link KitNamen}: Deutscher und englischer Name einer Sache
 * sind gleich geschützt.
 *
 * <p>Ein Name im Namensraum des Kits, den das Register nicht kennt, ist für ein Token weder setzbar
 * noch per Umbenennen erzeugbar (Plan #1582, E7). Sonst legte ein Token fest, welche Namen
 * geschützt sind. Anlegen und Abnehmen eines solchen Namens bleiben erlaubt (E8).
 *
 * <p>Gesperrt wird allein die Herkunft {@link CardActivityOrigin#TOKEN} — gebundene wie ungebundene
 * Token. Sie ist serververifiziert, anders als der Header {@code X-Agent-Model}, der nur eine
 * Selbstauskunft ist. Der Server kann Mensch und Agent am Token nicht unterscheiden, darum setzt
 * auch ein Mensch per CLI {@code kit:night} nicht mehr. Session und unbekannte Herkunft (interne
 * Aufrufe ohne Sicherheitskontext) dürfen alles.
 *
 * <p>Die einzige Ausnahme von dieser Sperre ist {@link #pruefeEinzeln}: Der Zugang der Desktop-App
 * darf an einer einzelnen Karte {@code NIGHT} setzen und {@code KLAEREN} abnehmen (Plan #2100, E2).
 * Seine Herkunft bleibt {@code TOKEN}; darum bleiben {@link #pruefeWechsel} und {@link
 * #pruefeDefinition} für ihn ohne Änderung gesperrt, also Ersetzen, Massenaktion, Anlegen, Löschen
 * und Umbenennen.
 *
 * <p>Der Namensvergleich folgt der Label-Auflösung in {@link LabelService}: getrimmt und
 * case-sensitiv. Wiche er ab, entstünde eine Lücke. Nur der Namensraum vergleicht ohne Rücksicht
 * auf Groß- und Kleinschreibung, damit eine abweichende Schreibung nicht als fremder Name
 * durchgeht.
 *
 * <p>Jede Prüfung gibt es mit dem Register als Parameter (E13); ohne ihn gilt {@link
 * KitNamen#STANDARD}.
 */
public final class FreigabeLabels {

  /** Richtung einer Änderung an der Label-Zuordnung einer Karte. */
  public enum Richtung {
    SETZEN,
    ABNEHMEN
  }

  private FreigabeLabels() {}

  /**
   * Prüft eine einzelne Änderung der Zuordnung gegen {@link KitNamen#STANDARD}.
   *
   * @throws FreigabeLabelException wenn ein Token das Label gegen seine Richtung ändern will
   */
  public static void pruefe(@Nullable CardActivityOrigin herkunft, String name, Richtung richtung) {
    pruefe(KitNamen.STANDARD, herkunft, name, richtung);
  }

  /**
   * Prüft eine einzelne Änderung der Zuordnung gegen das übergebene Register.
   *
   * @throws FreigabeLabelException wenn ein Token das Label gegen seine Richtung ändern oder einen
   *     unbekannten Namen im Namensraum des Kits setzen will
   */
  public static void pruefe(
      KitNamen register, @Nullable CardActivityOrigin herkunft, String name, Richtung richtung) {
    if (herkunft != CardActivityOrigin.TOKEN) {
      return;
    }
    String trimmed = name.trim();
    Optional<Sache> sache = register.sache(trimmed);
    Schutz schutz = sache.map(Sache::schutz).orElse(Schutz.KEINER);
    if (richtung == Richtung.SETZEN && nurMenschSetzt(schutz)) {
      throw FreigabeLabelException.nurMenschSetzt(trimmed);
    }
    if (richtung == Richtung.ABNEHMEN && nurMenschNimmtAb(schutz)) {
      throw FreigabeLabelException.nurMenschNimmtAb(trimmed);
    }
    if (richtung == Richtung.SETZEN && unbekanntImNamensraum(sache, trimmed)) {
      throw FreigabeLabelException.unbekanntSetzen(trimmed);
    }
  }

  /** Setzen ist dem Menschen vorbehalten: allein oder mit dem Abnehmen zusammen (Issue #2142). */
  private static boolean nurMenschSetzt(Schutz schutz) {
    return schutz == Schutz.NUR_MENSCH_SETZT || schutz == Schutz.NUR_MENSCH;
  }

  /** Abnehmen ist dem Menschen vorbehalten: allein oder mit dem Setzen zusammen (Issue #2142). */
  private static boolean nurMenschNimmtAb(Schutz schutz) {
    return schutz == Schutz.NUR_MENSCH_NIMMT_AB || schutz == Schutz.NUR_MENSCH;
  }

  /**
   * Prüft das Setzen oder Abnehmen genau eines Labels an genau einer Karte gegen {@link
   * KitNamen#STANDARD}.
   *
   * @see #pruefeEinzeln(KitNamen, ActorStamp, String, Richtung)
   */
  public static void pruefeEinzeln(ActorStamp akteur, String name, Richtung richtung) {
    pruefeEinzeln(KitNamen.STANDARD, akteur, name, richtung);
  }

  /**
   * Prüft das Setzen oder Abnehmen genau eines Labels an genau einer Karte. Für den Zugang der App
   * lässt sie allein {@code NIGHT} beim Setzen und {@code KLAEREN} beim Abnehmen durch, alles
   * andere entscheidet {@link #pruefe(KitNamen, CardActivityOrigin, String, Richtung)}.
   *
   * @throws FreigabeLabelException wie {@link #pruefe(KitNamen, CardActivityOrigin, String,
   *     Richtung)}, außer in den beiden Fällen des Zugangs der App
   */
  public static void pruefeEinzeln(
      KitNamen register, ActorStamp akteur, String name, Richtung richtung) {
    if (akteur.appZugang() && freigabeDerApp(register.sache(name.trim()), richtung)) {
      return;
    }
    pruefe(register, akteur.origin(), name, richtung);
  }

  private static boolean freigabeDerApp(Optional<Sache> sache, Richtung richtung) {
    Sache erlaubt = richtung == Richtung.SETZEN ? Sache.NIGHT : Sache.KLAEREN;
    return sache.filter(erlaubt::equals).isPresent();
  }

  /**
   * Prüft das Ersetzen einer ganzen Zuordnung gegen {@link KitNamen#STANDARD}.
   *
   * @see #pruefeWechsel(KitNamen, CardActivityOrigin, Map, Collection, Collection)
   */
  public static void pruefeWechsel(
      @Nullable CardActivityOrigin herkunft,
      Map<Long, String> namen,
      Collection<Long> vorher,
      Collection<Long> nachher) {
    pruefeWechsel(KitNamen.STANDARD, herkunft, namen, vorher, nachher);
  }

  /**
   * Prüft das Ersetzen einer ganzen Zuordnung: nur, was neu hinzukommt oder wegfällt. Was in beiden
   * Mengen steht, bleibt unverändert und ist kein Verstoß.
   *
   * @param namen Label-Namen je ID des Boards; eine unbekannte ID ist kein Freigabe-Label
   */
  public static void pruefeWechsel(
      KitNamen register,
      @Nullable CardActivityOrigin herkunft,
      Map<Long, String> namen,
      Collection<Long> vorher,
      Collection<Long> nachher) {
    Set<Long> neu = new HashSet<>(nachher);
    neu.removeAll(vorher);
    Set<Long> weg = new HashSet<>(vorher);
    weg.removeAll(nachher);
    neu.forEach(id -> pruefe(register, herkunft, namen.getOrDefault(id, ""), Richtung.SETZEN));
    weg.forEach(id -> pruefe(register, herkunft, namen.getOrDefault(id, ""), Richtung.ABNEHMEN));
  }

  /**
   * Prüft eine Änderung an der Label-Definition gegen {@link KitNamen#STANDARD}.
   *
   * @see #pruefeDefinition(KitNamen, CardActivityOrigin, String, String)
   */
  public static void pruefeDefinition(
      @Nullable CardActivityOrigin herkunft, String alterName, @Nullable String neuerName) {
    pruefeDefinition(KitNamen.STANDARD, herkunft, alterName, neuerName);
  }

  /**
   * Prüft eine Änderung an der Label-Definition: Ein Token darf ein Freigabe-Label weder löschen
   * noch umbenennen, und kein anderes Label auf einen geschützten Namen oder einen unbekannten
   * Namen im Namensraum des Kits umbenennen. Löschen nähme es von allen Karten ab, Umbenennen
   * umginge die Sperre. Anlegen bleibt erlaubt, weil das Kit seine Labels selbst anlegt.
   *
   * @param neuerName {@code null} beim Löschen
   */
  public static void pruefeDefinition(
      KitNamen register,
      @Nullable CardActivityOrigin herkunft,
      String alterName,
      @Nullable String neuerName) {
    if (herkunft != CardActivityOrigin.TOKEN) {
      return;
    }
    String alt = alterName.trim();
    Optional<String> neu = Optional.ofNullable(neuerName).map(String::trim);
    if (neu.filter(alt::equals).isPresent()) {
      return;
    }
    if (geschuetzt(register.sache(alt))) {
      throw FreigabeLabelException.definitionGesperrt(alt);
    }
    // Beim Löschen fehlt der neue Name, es bleibt nichts zu prüfen.
    neu.ifPresent(name -> pruefeNeuenNamen(register, name));
  }

  /** Prüft den Zielnamen einer Umbenennung: weder geschützt noch unbekannt im Namensraum. */
  private static void pruefeNeuenNamen(KitNamen register, String neu) {
    Optional<Sache> sache = register.sache(neu);
    if (geschuetzt(sache)) {
      throw FreigabeLabelException.definitionGesperrt(neu);
    }
    if (unbekanntImNamensraum(sache, neu)) {
      throw FreigabeLabelException.unbekanntUmbenennen(neu);
    }
  }

  private static boolean geschuetzt(Optional<Sache> sache) {
    return sache.map(Sache::schutz).orElse(Schutz.KEINER) != Schutz.KEINER;
  }

  private static boolean unbekanntImNamensraum(Optional<Sache> sache, String name) {
    return sache.isEmpty() && KitNamen.imNamensraum(name);
  }
}
