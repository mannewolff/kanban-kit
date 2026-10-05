package org.mwolff.manban.common.web.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Vertragsangaben eines Aufrufs für die API-Beschreibung (Issue #1402, Plan #1400): Stabilität, das
 * verlangte Recht und gegebenenfalls die Abkündigung. Fehlt die Annotation an einer
 * Controller-Methode, gilt der Aufruf als {@link Stabilitaet#AENDERBAR} ohne genanntes Recht.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiVertrag {

  /** Ob sich eine fremde Anbindung auf den Aufruf stützen kann. */
  Stabilitaet stabilitaet() default Stabilitaet.AENDERBAR;

  /**
   * Das Recht, das der Aufruf verlangt: der Name einer Projekt-Berechtigung oder {@code
   * PLATTFORM_ADMIN}. Leer, wenn der Aufruf kein eigenes Recht verlangt.
   */
  String recht() default "";

  /**
   * Version {@code <major>.<minor>}, seit der der Aufruf abgekündigt ist; leer, solange er es nicht
   * ist. Ein abgekündigter Aufruf ändert sich oder entfällt frühestens mit der folgenden
   * Minor-Version.
   */
  String abgekuendigtSeit() default "";
}
