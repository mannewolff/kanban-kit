package org.mwolff.manban.codeanalyse.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Die Zählregel einer gemeldeten Messung (Issue #2277, Plan #2272 E8, Fachplan #2271 AK 16): Eine
 * zählende Messung braucht {@code before} und {@code final}, eine nicht zählende einen {@code
 * reason}; jede Zahl eines gemeldeten Zustands ist gesetzt und nicht negativ.
 *
 * <p>Eine Klassenregel statt Feldregeln, damit {@code fieldErrors} jeden Verstoß unter seinem
 * Feldnamen aus V1 nennt — auch {@code final}, das in Java kein Name sein kann.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ZaehlregelValidator.class)
@interface Zaehlregel {
  String message() default "";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
