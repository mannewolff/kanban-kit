package org.mwolff.manban.push.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Ein Push-Abo steht für ein Gerät (Issue #1997, Plan #1989 E13, E14): der Endpunkt des
 * Push-Dienstes mit den Schlüsseln des Browsers, das Konto, dem das Gerät gerade gehört, und die
 * Boards, die auf diesem Gerät abgeschaltet sind.
 *
 * <p><b>Lebensdauer (E14).</b> Das Abo gilt bis {@code giltBis}, dem Ende der Sitzung, mit der es
 * zuletzt angelegt oder aufgefrischt wurde. Ein abgelaufenes Abo ist beendet: Frischt man es auf,
 * beginnt es neu — mit neuem {@code angelegtAt}, damit nichts nachgemeldet wird, was schon vorher
 * bestand (E12), und ohne die alten Board-Schalter.
 *
 * <p><b>Board-Schalter (E13).</b> Eingeschaltet heißt: für alle Boards an, bis man eines
 * abschaltet. Gespeichert werden darum nur die abgeschalteten.
 *
 * @param id Kennung; {@code null} vor dem ersten Speichern
 * @param userId Konto, dem das Abo gehört
 * @param endpoint Adresse des Push-Dienstes für dieses Gerät
 * @param p256dh öffentlicher Schlüssel des Browsers, base64url
 * @param auth Authentifizierungsgeheimnis des Browsers, base64url
 * @param angelegtAt Beginn des Abos; was davor bestand, wird nicht nachgemeldet
 * @param giltBis Ende der Sitzung, mit der das Abo zuletzt aufgefrischt wurde
 * @param boardsAus die auf diesem Gerät abgeschalteten Boards
 */
public record PushAbo(
    @Nullable Long id,
    long userId,
    String endpoint,
    String p256dh,
    String auth,
    Instant angelegtAt,
    Instant giltBis,
    Set<Long> boardsAus) {

  public PushAbo {
    boardsAus = Set.copyOf(boardsAus);
  }

  /** Ein neues Abo, für alle Boards eingeschaltet. */
  public static PushAbo neu(
      long userId, String endpoint, String p256dh, String auth, Instant jetzt, Instant giltBis) {
    return new PushAbo(null, userId, endpoint, p256dh, auth, jetzt, giltBis, Set.of());
  }

  /** Kennung des gespeicherten Abos. */
  public long requireId() {
    return Objects.requireNonNull(id, "PushAbo ohne id");
  }

  /** Wahr, sobald die Sitzung des Abos zu Ende ist. */
  public boolean istAbgelaufen(Instant jetzt) {
    return !giltBis.isAfter(jetzt);
  }

  /** Wahr, wenn das Board auf diesem Gerät nicht abgeschaltet ist. */
  public boolean istAn(long boardId) {
    return !boardsAus.contains(boardId);
  }

  /**
   * Das Abo mit neuen Schlüsseln und neuem Sitzungsende. Ein noch laufendes Abo behält Beginn und
   * Schalter; ein abgelaufenes beginnt neu.
   */
  public PushAbo aufgefrischt(String neuP256dh, String neuAuth, Instant jetzt, Instant neuGiltBis) {
    if (istAbgelaufen(jetzt)) {
      return neuBegonnen(userId, neuP256dh, neuAuth, jetzt, neuGiltBis);
    }
    return new PushAbo(id, userId, endpoint, neuP256dh, neuAuth, angelegtAt, neuGiltBis, boardsAus);
  }

  /**
   * Das Abo beginnt auf demselben Endpunkt neu, für das genannte Konto: neuer Beginn, alle Boards
   * an. Für ein abgelaufenes Abo, dessen Gerät sich wieder anmeldet — auch mit einem anderen Konto.
   */
  public PushAbo neuBegonnen(
      long neuUserId, String neuP256dh, String neuAuth, Instant jetzt, Instant neuGiltBis) {
    return new PushAbo(id, neuUserId, endpoint, neuP256dh, neuAuth, jetzt, neuGiltBis, Set.of());
  }

  /** Das Abo mit dem Board ein- oder abgeschaltet. */
  public PushAbo geschaltet(long boardId, boolean an) {
    Set<Long> aus = new HashSet<>(boardsAus);
    if (an) {
      aus.remove(boardId);
    } else {
      aus.add(boardId);
    }
    return new PushAbo(id, userId, endpoint, p256dh, auth, angelegtAt, giltBis, aus);
  }
}
