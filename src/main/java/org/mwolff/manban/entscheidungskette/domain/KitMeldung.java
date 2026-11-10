package org.mwolff.manban.entscheidungskette.domain;

/** Eine gelesene Meldung des Kits; was sie nicht nennt, ist leer (Issue #2113). */
public sealed interface KitMeldung
    permits Pruefrunde,
        Einarbeitung,
        Abschlussbericht,
        CodeReview,
        Uebertragung,
        BuildNachtrag,
        Veroeffentlichung {}
