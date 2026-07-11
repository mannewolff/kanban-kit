package org.mwolff.manban.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Markiert einen Typ, ein Konstruktor oder eine Methode als bewusst von der JaCoCo-Coverage
 * ausgenommen (CLAUDE-java.md §5.4, Punkt 3): für punktuelle, begründete Fälle, in denen ein Zweig
 * nachweisbar nicht erreichbar ist und weder sinnvoll testbar noch ohne Verlust an Robustheit
 * entfernbar ist.
 *
 * <p>JaCoCo ignoriert Elemente, die mit einer Annotation annotiert sind, deren einfacher Name
 * {@code "Generated"} enthält (Retention {@code CLASS} oder {@code RUNTIME}). Jede Verwendung muss
 * direkt am Code mit einer Begründung kommentiert sein.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ExcludeFromJacocoGeneratedReport {}
