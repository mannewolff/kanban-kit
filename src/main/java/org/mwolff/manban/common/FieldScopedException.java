package org.mwolff.manban.common;

/**
 * Eine Domänenexception, deren Meldung genau einem Eingabefeld gilt.
 *
 * <p>Der {@code GlobalExceptionHandler} hängt die Meldung solcher Exceptions als {@code
 * fieldErrors}-Extension ans Problemdokument — dieselbe Form, die Bean-Validation erzeugt. Ohne
 * diese Zuordnung bleibt am Draht nur {@code detail}, und ein Client kann zwei Ablehnungen
 * desselben Aufrufs nicht auseinanderhalten.
 *
 * <p>Schnittstelle statt direkter Typprüfung, damit {@code common} nicht auf die Fachmodule zeigen
 * muss: Die Module kennen {@code common}, nicht umgekehrt.
 *
 * <p>Sie liegt bewusst in {@code common} und <strong>nicht</strong> in {@code common.web}: Die
 * ArchUnit-Schichtenregel verbietet jedem {@code ..application..}-Paket den Zugriff auf {@code
 * ..web..} — und das Muster trifft auch {@code common.web}. Eine Exception aus der
 * Anwendungsschicht könnte die Schnittstelle dort also gar nicht implementieren.
 */
// PMD.ImplicitFunctionalInterface: bewusst kein @FunctionalInterface. Die Schnittstelle wird
// ausschliesslich von Exception-Klassen implementiert; ein Lambda dafuer gibt es nicht und soll es
// nicht geben — die Annotation wuerde genau das nahelegen.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface FieldScopedException {

  /** Name des Eingabefelds, dem die Meldung gilt. */
  String field();
}
