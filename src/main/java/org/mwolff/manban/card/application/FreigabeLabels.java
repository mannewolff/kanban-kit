package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.common.KitNamen.Schutz;

/**
 * Richtung der Freigabe-Labels des claude-workflow-kits, vom Server erzwungen (Issue #1421, #1586).
 *
 * <p>Die Labels der Sachen {@code NIGHT} und {@code NIGHTRUN} geben Arbeit frei und setzt nur ein
 * Mensch; der Runner darf sie abnehmen, sobald sie verbraucht sind. Die Labels von {@code KLAEREN}
 * und {@code GESCHUETZT} setzt die Maschine; das Abnehmen ist die Freigabe und steht nur dem
 * Menschen zu. Ohne diese Regel könnte sich eine Session am Kit vorbei selbst freigeben. Welche
 * Sache ein Name trägt und welchen {@link Schutz} sie hat, sagt allein {@link KitNamen}: Deutscher
 * und englischer Name einer Sache sind gleich geschützt.
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
 * <p>Der Namensvergleich folgt der Label-Auflösung in {@link LabelService}: getrimmt und
 * case-sensitiv. Wiche er ab, entstünde eine Lücke. Nur der Namensraum vergleicht ohne Rücksicht
 * auf Groß- und Kleinschreibung, damit eine abweichende Schreibung nicht als fremder Name
 * durchgeht.
 *
 * <p>Jede Prüfung gibt es mit dem Register als Parameter (E13); ohne ihn gilt {@link
 * KitNamen#STANDARD}.
 */
public final class FreigabeLabels {

  /** Begründung eines unbekannten Namens im Namensraum, zwischen Name und Folge (E7). */
  private static final String NAMENSRAUM_UNBEKANNT =
      " liegt im Namensraum des Kits und ist dort unbekannt; ";

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
    if (richtung == Richtung.SETZEN && schutz == Schutz.NUR_MENSCH_SETZT) {
      throw new FreigabeLabelException("Label " + trimmed + " setzt nur ein Mensch im Board");
    }
    if (richtung == Richtung.ABNEHMEN && schutz == Schutz.NUR_MENSCH_NIMMT_AB) {
      throw new FreigabeLabelException("Label " + trimmed + " nimmt nur ein Mensch im Board ab");
    }
    if (richtung == Richtung.SETZEN && unbekanntImNamensraum(sache, trimmed)) {
      throw new FreigabeLabelException(
          "Label " + trimmed + NAMENSRAUM_UNBEKANNT + "es setzt nur ein Mensch im Board");
    }
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
      throw definitionGesperrt(alt);
    }
    // Beim Löschen fehlt der neue Name, es bleibt nichts zu prüfen.
    neu.ifPresent(name -> pruefeNeuenNamen(register, name));
  }

  /** Prüft den Zielnamen einer Umbenennung: weder geschützt noch unbekannt im Namensraum. */
  private static void pruefeNeuenNamen(KitNamen register, String neu) {
    Optional<Sache> sache = register.sache(neu);
    if (geschuetzt(sache)) {
      throw definitionGesperrt(neu);
    }
    if (unbekanntImNamensraum(sache, neu)) {
      throw new FreigabeLabelException(
          "Label "
              + neu
              + NAMENSRAUM_UNBEKANNT
              + "auf diesen Namen benennt nur ein Mensch im Board um");
    }
  }

  private static boolean geschuetzt(Optional<Sache> sache) {
    return sache.map(Sache::schutz).orElse(Schutz.KEINER) != Schutz.KEINER;
  }

  private static boolean unbekanntImNamensraum(Optional<Sache> sache, String name) {
    return sache.isEmpty() && KitNamen.imNamensraum(name);
  }

  private static FreigabeLabelException definitionGesperrt(String name) {
    return new FreigabeLabelException(
        "Label "
            + name
            + " ist ein Freigabe-Label; seine Definition ändert nur ein Mensch im Board");
  }
}
