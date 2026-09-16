package org.mwolff.manban.common.automation;

/**
 * Die wirksame Lage einer abschaltbaren Automatik für diesen Lauf (Issue #907, fachlich #841).
 *
 * @param bezeichnung sprechender Name, unter dem der Betreiber die Automatik in der Anleitung
 *     wiederfindet; zugleich das Sortierkriterium der Protokollzeilen
 * @param eingeschaltet ob die Automatik in diesem Lauf tatsächlich arbeitet. Der Wert stammt aus
 *     der <b>Existenz der bedingten Job-Bean</b>, nicht aus dem gebundenen Property-Wert: Der
 *     Spring-Binder nimmt {@code yes}, {@code on} und {@code 1} als {@code true},
 *     {@code @ConditionalOnProperty(havingValue = "true")} dagegen nicht. Wer den Property-Wert
 *     meldete, schriebe bei {@code MANBAN_CLEANUP_ENABLED=yes} „eingeschaltet" ins Protokoll,
 *     während der Job fehlt — und AK 4 verlangt die wirksame Lage, nicht die konfigurierte Absicht.
 * @param schalterVariable Name der <b>Umgebungsvariablen</b> ({@code MANBAN_CLEANUP_ENABLED}),
 *     nicht der Property. Die Anleitung, an der sich der Betreiber ausrichtet, führt ausschließlich
 *     Umgebungsvariablen; ein Property-Name schickte ihn an eine Stelle, die er nicht bedient.
 * @param detail die tragenden Werte als bereits formulierter Text, etwa {@code "Frist 30 Tage
 *     (Konfiguration)"}. Darf leer sein; dann entfällt der Teil in der Protokollzeile.
 */
public record AutomationStatus(
    String bezeichnung, boolean eingeschaltet, String schalterVariable, String detail) {}
