package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.CardActivityOrigin;

/**
 * Richtung der Freigabe-Labels des claude-workflow-kits, vom Server erzwungen (Issue #1421).
 *
 * <p>{@code kit:night} und {@code kit:nightrun} geben Arbeit frei und setzt nur ein Mensch; der
 * Runner darf sie abnehmen, sobald sie verbraucht sind. {@code kit:klaeren} und {@code
 * kit:geschuetzt} setzt die Maschine; das Abnehmen ist die Freigabe und steht nur dem Menschen zu.
 * Ohne diese Regel könnte sich eine Session am Kit vorbei selbst freigeben.
 *
 * <p>Gesperrt wird allein die Herkunft {@link CardActivityOrigin#TOKEN} — gebundene wie ungebundene
 * Token. Sie ist serververifiziert, anders als der Header {@code X-Agent-Model}, der nur eine
 * Selbstauskunft ist. Der Server kann Mensch und Agent am Token nicht unterscheiden, darum setzt
 * auch ein Mensch per CLI {@code kit:night} nicht mehr. Session und unbekannte Herkunft (interne
 * Aufrufe ohne Sicherheitskontext) dürfen alles.
 *
 * <p>Der Namensvergleich folgt der Label-Auflösung in {@link LabelService}: getrimmt und
 * case-sensitiv. Wiche er ab, entstünde eine Lücke.
 */
public final class FreigabeLabels {

  /** Setzt nur ein Mensch; ein Token darf sie abnehmen. */
  private static final Set<String> NUR_MENSCH_SETZT = Set.of("kit:night", "kit:nightrun");

  /** Nimmt nur ein Mensch ab; ein Token darf sie setzen. */
  private static final Set<String> NUR_MENSCH_NIMMT_AB = Set.of("kit:klaeren", "kit:geschuetzt");

  /** Richtung einer Änderung an der Label-Zuordnung einer Karte. */
  public enum Richtung {
    SETZEN,
    ABNEHMEN
  }

  private FreigabeLabels() {}

  /**
   * Prüft eine einzelne Änderung der Zuordnung.
   *
   * @throws FreigabeLabelException wenn ein Token das Label gegen seine Richtung ändern will
   */
  public static void pruefe(@Nullable CardActivityOrigin herkunft, String name, Richtung richtung) {
    if (herkunft != CardActivityOrigin.TOKEN) {
      return;
    }
    String trimmed = name.trim();
    if (richtung == Richtung.SETZEN && NUR_MENSCH_SETZT.contains(trimmed)) {
      throw new FreigabeLabelException("Label " + trimmed + " setzt nur ein Mensch im Board");
    }
    if (richtung == Richtung.ABNEHMEN && NUR_MENSCH_NIMMT_AB.contains(trimmed)) {
      throw new FreigabeLabelException("Label " + trimmed + " nimmt nur ein Mensch im Board ab");
    }
  }

  /**
   * Prüft das Ersetzen einer ganzen Zuordnung: nur, was neu hinzukommt oder wegfällt. Was in beiden
   * Mengen steht, bleibt unverändert und ist kein Verstoß.
   *
   * @param namen Label-Namen je ID des Boards; eine unbekannte ID ist kein Freigabe-Label
   */
  public static void pruefeWechsel(
      @Nullable CardActivityOrigin herkunft,
      Map<Long, String> namen,
      Collection<Long> vorher,
      Collection<Long> nachher) {
    Set<Long> neu = new HashSet<>(nachher);
    neu.removeAll(vorher);
    Set<Long> weg = new HashSet<>(vorher);
    weg.removeAll(nachher);
    neu.forEach(id -> pruefe(herkunft, namen.getOrDefault(id, ""), Richtung.SETZEN));
    weg.forEach(id -> pruefe(herkunft, namen.getOrDefault(id, ""), Richtung.ABNEHMEN));
  }

  /**
   * Prüft eine Änderung an der Label-Definition: Ein Token darf ein Freigabe-Label weder löschen
   * noch umbenennen, und kein anderes Label auf einen geschützten Namen umbenennen. Löschen nähme
   * es von allen Karten ab, Umbenennen umginge die Sperre. Anlegen bleibt erlaubt, weil das Kit
   * seine Labels selbst anlegt.
   *
   * @param neuerName {@code null} beim Löschen
   */
  public static void pruefeDefinition(
      @Nullable CardActivityOrigin herkunft, String alterName, @Nullable String neuerName) {
    if (herkunft != CardActivityOrigin.TOKEN) {
      return;
    }
    String alt = alterName.trim();
    String neu = neuerName == null ? null : neuerName.trim();
    if (alt.equals(neu)) {
      return;
    }
    if (geschuetzt(alt)) {
      throw definitionGesperrt(alt);
    }
    if (neu != null && geschuetzt(neu)) {
      throw definitionGesperrt(neu);
    }
  }

  private static boolean geschuetzt(String name) {
    return NUR_MENSCH_SETZT.contains(name) || NUR_MENSCH_NIMMT_AB.contains(name);
  }

  private static FreigabeLabelException definitionGesperrt(String name) {
    return new FreigabeLabelException(
        "Label "
            + name
            + " ist ein Freigabe-Label; seine Definition ändert nur ein Mensch im Board");
  }
}
