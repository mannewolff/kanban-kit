package org.mwolff.manban.auth.application;

import java.time.Instant;
import java.util.Optional;

/**
 * Gemeinsamer Port-Anteil der einmalig verwendbaren Auth-Tokens (Passwort-Reset,
 * E-Mail-Verifikation). Beide folgen derselben Regel: Ein Token darf genau einmal eingelöst werden,
 * und zwar nur solange es weder abgelaufen noch bereits verbraucht ist.
 *
 * <p><strong>Warum der Verbrauch ein eigener Port-Aufruf ist (Issue #497):</strong> Die Prüfung
 * „unbenutzt und nicht abgelaufen?" und das Setzen der Verbrauchsmarke dürfen nicht als Lesen,
 * Prüfen, Schreiben nacheinander laufen. Unter der Standard-Isolation (READ COMMITTED) sähen zwei
 * gleichzeitige Transaktionen beide ein unbenutztes Token, bestünden beide die Prüfung und
 * schrieben beide — beim Passwort-Reset würden so zwei verschiedene neue Passwörter akzeptiert. Der
 * Verbrauch gehört deshalb als eine Operation in die Datenbank; {@link #consume} ist der Vertrag
 * dafür.
 */
// PMD.ImplicitFunctionalInterface: bewusst KEIN @FunctionalInterface — das Interface ist die
// gemeinsame Basis der beiden Token-Ports (Vererbung), kein Lambda-Ziel; als funktional markiert
// waere es eine Einladung, den Verbrauch ad hoc zu implementieren statt in der Datenbank.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface SingleUseTokenRepository {

  /**
   * Verbraucht das Token mit dem angegebenen Hash <strong>atomar</strong> und meldet, wer das
   * Rennen gewonnen hat.
   *
   * <p>Genau ein Aufruf kann für ein Token erfolgreich sein. Alle weiteren — sowie Aufrufe mit
   * unbekanntem oder abgelaufenem Hash — liefern {@link Optional#empty()}. Die Fälle sind bewusst
   * ununterscheidbar: Ein Aufrufer darf aus der Antwort nicht ableiten können, ob ein Token
   * existiert.
   *
   * @param tokenHash SHA-256-Hash des Klartext-Tokens
   * @param now Bezugszeitpunkt für die Ablaufprüfung und die Verbrauchsmarke
   * @return ID des zugehörigen Benutzers, wenn dieser Aufruf das Token verbraucht hat; sonst leer
   */
  Optional<Long> consume(String tokenHash, Instant now);
}
