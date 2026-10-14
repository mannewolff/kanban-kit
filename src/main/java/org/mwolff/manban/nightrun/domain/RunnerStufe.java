package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Eine Stufe, die ein Lauf gerade fährt — so, wie der Runner sie mit seinem Laufbericht meldet
 * (Issue #1725, Plan #1724 E4, Vertrag V1).
 *
 * <p>{@code stage} und {@code step} sind Zeichenketten und keine Enums (Plan #1724 E5): Ein neueres
 * Kit mit einem weiteren Code soll seinen Laufbericht nicht verlieren, die Anzeige zeigt einen
 * unbekannten Code wörtlich.
 *
 * @param cardNumber die Karte, an der die Stufe arbeitet
 * @param stage die Stufe, heute {@code PLAN}, {@code REVIEW}, {@code PAKETE}, {@code ABDECKUNG},
 *     {@code UMSETZUNG} oder {@code VORBEREITUNG}
 * @param startedAt Beginn der Stufe
 * @param deadline Ende der Frist, die das Kit für die Stufe anwendet; {@code null} heißt „keine
 *     Frist vorgesehen“ (Plan #1724 E6)
 * @param step der Teilschritt, heute {@code SESSION}, {@code WAITING_FOR_IMPLEMENTATION} oder
 *     {@code RELEASE_CHECKS}
 * @param holdsImplementation ob die Stufe die Umsetzung hält
 */
public record RunnerStufe(
    int cardNumber,
    String stage,
    Instant startedAt,
    @Nullable Instant deadline,
    String step,
    boolean holdsImplementation) {}
