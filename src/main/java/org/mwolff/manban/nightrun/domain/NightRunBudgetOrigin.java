package org.mwolff.manban.nightrun.domain;

/**
 * Woher die Budgets eines Kettenlaufs stammen (Issue #1112, Plan #1110 E3).
 *
 * <p>Die Aufzählung unterscheidet zwei der drei Aussagen, die die Anzeige treffen können muss; die
 * dritte — „nicht angegeben" — ist das fehlende Budget selbst und trägt deshalb keinen eigenen
 * Wert. Ein Wert {@code UNBEKANNT} neben einem {@code null}-fähigen Feld wäre dieselbe Aussage
 * zweimal, und beide Fassungen liefen früher oder später auseinander.
 *
 * <p>Nicht zu verwechseln mit {@link NightRunOrigin}: Das ist die Herkunft des <b>Laufs</b> — auf
 * welchem Weg er ans Board kam —, dies die Herkunft seiner <b>Vorgaben</b>.
 */
public enum NightRunBudgetOrigin {

  /** Die Vorgaben standen in der Konfiguration des Laufs. */
  CONFIGURED,

  /** Mindestens eine Vorgabe fehlte und kam aus den Voreinstellungen des Kits. */
  DEFAULTED
}
