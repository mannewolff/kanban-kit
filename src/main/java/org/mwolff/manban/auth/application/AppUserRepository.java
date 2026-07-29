package org.mwolff.manban.auth.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/**
 * Ausgehender Port für die Persistenz von Benutzern. Die Anwendungs-/Domänenschicht spricht nur
 * gegen dieses Interface; die konkrete Umsetzung liegt in der Infrastruktur.
 */
public interface AppUserRepository {

  AppUser save(AppUser user);

  Optional<AppUser> findById(Long id);

  /** Alle Benutzer (für die Admin-Nutzerverwaltung). */
  List<AppUser> findAll();

  Optional<AppUser> findByEmail(String email);

  boolean existsByEmail(String email);

  /**
   * Alle Benutzer mit der angegebenen Plattform-Rolle (z. B. alle Admins für Benachrichtigungen).
   */
  List<AppUser> findByPlatformRole(PlatformRole platformRole);

  /**
   * Sperrt alle Plattform-Admins bis zum Ende der laufenden Transaktion und liefert ihre IDs.
   *
   * <p><strong>Warum das Sperren zum Lesen gehört (Issue #498):</strong> Der Aussperr-Schutz „der
   * letzte Admin darf nicht degradiert werden" ist eine Bedingung über eine <em>Zeilenmenge</em>,
   * geprüft vor dem Schreiben. Ohne Sperre sähen zwei gleichzeitige Degradierungen der letzten
   * beiden Admins beide noch zwei Admins, bestünden beide die Prüfung — und danach gäbe es keinen
   * mehr. Ein bedingtes Update wie bei den Einmal-Tokens ({@link SingleUseTokenRepository#consume},
   * Issue #497) hilft hier nicht: Beide Transaktionen schreiben <em>verschiedene</em> Zeilen und
   * kämen sich über Zeilensperren nie in die Quere.
   *
   * <p>Gesperrt werden deshalb genau die Träger der Invariante — die Admin-Zeilen selbst — in
   * aufsteigender ID-Reihenfolge (stabile Sperrreihenfolge, damit zwei Aufrufer nicht verklemmen).
   * Der zweite Aufrufer wertet die Bedingung {@code platform_role = 'ADMIN'} nach dem Commit des
   * ersten auf der neuen Zeilenversion aus und sieht den inzwischen degradierten Admin nicht mehr.
   * Eine <em>Beförderung</em> zum Admin bleibt ungesperrt: Sie vergrößert die Menge und kann die
   * Invariante nicht verletzen.
   *
   * <p>Die zurückgegebene Liste ist damit die einzige verlässliche Quelle für „wer ist gerade
   * Admin?" innerhalb der Transaktion — sie ersetzt sowohl das frühere {@code findAll()}-Zählen in
   * der JVM als auch die Rollenabfrage am einzelnen Benutzer.
   *
   * @return IDs aller Benutzer mit Plattform-Rolle ADMIN, aufsteigend
   */
  List<Long> lockPlatformAdminIds();
}
