package org.mwolff.manban.nightrun.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.KettenStand;

/**
 * Der Kettenstand einer Karte für die Stufenleiste (Issue #1452, Plan #1447 E14, E15).
 *
 * @param stand Ziel, Grenze und Stationszustände aus dem Laufstand der Karte
 * @param uebernommen ob ein Runner die Karte übernommen hat (E15)
 * @param planReviewVorhanden ob der Plan einer fachlichen Anforderung schon {@code Plan-Review:}
 *     trägt (E14); an jeder anderen Karte {@code false}
 * @param lauf Kennung (Start) des zugrunde gelegten Laufs: die des Laufstands, sonst die des
 *     jüngsten Anlaufs der Karte; {@code null} ohne beide
 */
public record KettenstandDerKarte(
    KettenStand stand, boolean uebernommen, boolean planReviewVorhanden, @Nullable Instant lauf) {}
