package org.mwolff.manban.card.application;

import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltet die projektweite Startnummer ({@code project.next_card_number}). Liegt im card-Modul,
 * weil die Nummerierung eine Karten-Belange ist und card bereits vom project-Modul abhängen darf
 * (die Umkehrung project→card wäre ein Modul-Zyklus). Das Lesen liefert den <em>effektiven</em>
 * nächsten Wert (Floor aus höchster Nummer + Startnummer); das Setzen ist Owner-/Edit-gated und
 * lehnt eine Nummer ab, die bereits vergeben ist.
 */
@Service
public class ProjectStartNumberService {

  private final CardRepository cards;
  private final ProjectRepository projects;
  private final PermissionChecker permissions;

  public ProjectStartNumberService(
      CardRepository cards, ProjectRepository projects, PermissionChecker permissions) {
    this.cards = cards;
    this.projects = projects;
    this.permissions = permissions;
  }

  /** Effektive nächste Kartennummer des Projekts (für die Vorbelegung im Editiermodus). */
  @Transactional(readOnly = true)
  public int effectiveNextCardNumber(long userId, long projectId) {
    permissions.requireMembership(userId, projectId);
    return cards.nextCardNumber(projectId);
  }

  /**
   * Setzt die Startnummer (Owner-/Edit-Recht). Der Wert muss über der höchsten bereits vergebenen
   * Nummer liegen; sonst {@link InvalidCardNumberException} (400). Gibt die neue effektive nächste
   * Nummer zurück.
   */
  @Transactional
  public int setNextCardNumber(long userId, long projectId, int value) {
    permissions.require(userId, projectId, Permission.PROJECT_EDIT);
    int highest = cards.highestNumberInProject(projectId);
    if (value <= highest) {
      throw new InvalidCardNumberException(
          "Nächste Nummer muss größer als die höchste vergebene Nummer " + highest + " sein");
    }
    projects.setNextCardNumber(projectId, value);
    return cards.nextCardNumber(projectId);
  }
}
