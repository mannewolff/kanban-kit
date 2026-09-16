package org.mwolff.manban.common.automation;

import java.util.List;

/**
 * Meldet die abschaltbaren Automatiken eines Moduls (Issue #907, fachlich #841).
 *
 * <p>Jedes Modul, das Automatiken über einen Schalter stilllegen lässt, meldet hier seine eigenen.
 * <b>Der Beitrag selbst ist unbedingt, die gemeldeten Zustände sind es nicht</b>: Eine
 * abgeschaltete Automatik meldet sich als abgeschaltet und verschwindet nicht aus der Liste. Sonst
 * wäre im Protokoll „fehlt" nicht von „läuft nicht" zu unterscheiden — und genau diese
 * Unterscheidung verlangt AK 4.
 */
@FunctionalInterface
public interface AutomationStatusContributor {

  /** Die Automatiken dieses Moduls, jede mit ihrer wirksamen Lage. */
  List<AutomationStatus> statuses();
}
