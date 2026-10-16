package org.mwolff.manban.auth.application;

import java.util.OptionalLong;

/**
 * Ausgehender Port für die kontogebundene Sitzungs-Generation. Sie ist der Hebel, mit dem sich
 * bestehende Anmeldesitzungen eines Kontos vorzeitig beenden lassen: Ein Session-Token trägt die
 * Generation seiner Ausstellung, und ein Token mit einer älteren Generation gilt nicht mehr (Plan
 * #883).
 *
 * <p><strong>Warum die Generation nicht am Aggregat {@code AppUser} hängt (Plan #883, E5):</strong>
 * Fachlich gehört sie zur Sitzungsprüfung und nicht zu den Stammdaten des Benutzers. Technisch
 * schützt die Trennung den Wert: Wäre die Spalte an der Entity gemappt, schriebe ein {@code
 * AppUserRepository.save(...)} mit einem <em>vorher</em> geladenen Benutzer den dort gesehenen
 * alten Stand zurück — die per SQL erhöhte Generation fiele zurück, und Sitzungen, die hätten enden
 * müssen, liefen weiter.
 */
// PMD.ImplicitFunctionalInterface: bewusst KEIN @FunctionalInterface — der Port ist ein Lesepfad
// mit JPA-Adapter, kein Lambda-Ziel.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface SessionGenerations {

  /**
   * Die aktuelle Generation des Kontos.
   *
   * @param userId technische ID des Kontos
   * @return die Generation, oder leer, wenn es das Konto nicht (mehr) gibt
   */
  OptionalLong current(long userId);
}
