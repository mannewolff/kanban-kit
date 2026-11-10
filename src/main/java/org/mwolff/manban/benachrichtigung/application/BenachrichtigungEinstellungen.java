package org.mwolff.manban.benachrichtigung.application;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.benachrichtigung.domain.Ruhezeit;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schalter und Ruhezeit der Benachrichtigung per Mail (Issue #2076, Plan #2070 E12).
 *
 * <p><b>Projektschalter.</b> Lesen und ändern darf ihn, wer im Projekt echt Owner oder Admin ist,
 * ohne Plattform-Admin-Bypass.
 *
 * <p><b>Personenschalter.</b> Er gilt nur für die eigene Person, und nur für Projekte mit
 * Mitgliedschaft oder {@link PermissionChecker#hatNachtlaufZugriff}. Ein anderes Projekt gilt wie
 * unbekannt ({@link ProjectNotFoundException}, kein Existenz-Leak).
 *
 * <p>Der Einschaltzeitpunkt ist die Grenze für „was vorher war, wird nicht nachgemeldet“. Erneutes
 * Einschalten verschiebt ihn darum nicht.
 */
@Service
public class BenachrichtigungEinstellungen {

  private final BenachrichtigungEinstellungenRepository einstellungen;
  private final PermissionChecker permissions;
  private final ProjectService projects;
  private final Clock clock;

  public BenachrichtigungEinstellungen(
      BenachrichtigungEinstellungenRepository einstellungen,
      PermissionChecker permissions,
      ProjectService projects,
      Clock clock) {
    this.einstellungen = einstellungen;
    this.permissions = permissions;
    this.projects = projects;
    this.clock = clock;
  }

  /**
   * Ein Projekt, für das die Person ihren Schalter bedienen darf.
   *
   * @param projectId Kennung des Projekts
   * @param projectName Name des Projekts
   * @param projektAn wahr, wenn der Schalter des Projekts eingeschaltet ist
   * @param personAn wahr, wenn der eigene Schalter eingeschaltet ist
   */
  public record PersonenSchalter(
      long projectId, String projectName, boolean projektAn, boolean personAn) {}

  /**
   * Die eigenen Einstellungen.
   *
   * @param projekte die schaltbaren Projekte
   * @param ruhezeit die Ruhezeit, ohne eingestellte Ruhezeit {@code null}
   */
  public record MeineEinstellungen(List<PersonenSchalter> projekte, @Nullable Ruhezeit ruhezeit) {}

  /**
   * Ob der Schalter des Projekts eingeschaltet ist.
   *
   * @throws ProjectNotFoundException wenn der Aufrufer kein echtes Mitglied ist (404)
   * @throws org.mwolff.manban.project.application.ProjectAccessDeniedException wenn die echte Rolle
   *     weder Owner noch Admin ist (403)
   */
  @Transactional(readOnly = true)
  public boolean projektschalter(long userId, long projectId) {
    permissions.requireEchterOwnerOderAdmin(userId, projectId);
    return einstellungen.projektEingeschaltetSeit(projectId).isPresent();
  }

  /**
   * Schaltet die Benachrichtigung des Projekts ein oder aus.
   *
   * @return der neue Zustand
   * @throws ProjectNotFoundException wenn der Aufrufer kein echtes Mitglied ist (404)
   * @throws org.mwolff.manban.project.application.ProjectAccessDeniedException wenn die echte Rolle
   *     weder Owner noch Admin ist (403)
   */
  @Transactional
  public boolean schalteProjekt(long userId, long projectId, boolean an) {
    permissions.requireEchterOwnerOderAdmin(userId, projectId);
    if (!an) {
      einstellungen.schalteProjektAus(projectId);
    } else if (einstellungen.projektEingeschaltetSeit(projectId).isEmpty()) {
      einstellungen.schalteProjektEin(projectId, clock.instant());
    }
    return an;
  }

  /** Die schaltbaren Projekte der Person mit beiden Schaltern, dazu ihre Ruhezeit. */
  @Transactional(readOnly = true)
  public MeineEinstellungen meine(long userId) {
    List<AccessibleProject> schaltbar =
        projects.listAccessible(userId).stream()
            .filter(projekt -> darfSichSchalten(userId, projekt.id()))
            .toList();
    Set<Long> projekteAn =
        einstellungen.eingeschalteteProjekte(
            schaltbar.stream().map(AccessibleProject::id).toList());
    Set<Long> personAn = einstellungen.eingeschalteteProjekteDerPerson(userId);
    return new MeineEinstellungen(
        schaltbar.stream()
            .map(
                projekt ->
                    new PersonenSchalter(
                        projekt.id(),
                        projekt.name(),
                        projekteAn.contains(projekt.id()),
                        personAn.contains(projekt.id())))
            .toList(),
        einstellungen.ruhezeit(userId).orElse(null));
  }

  /**
   * Schaltet die Benachrichtigung der eigenen Person für das Projekt ein oder aus.
   *
   * @throws ProjectNotFoundException wenn die Person im Projekt weder Mitglied ist noch Läufe sehen
   *     darf (404)
   */
  @Transactional
  public void schaltePerson(long userId, long projectId, boolean an) {
    if (!darfSichSchalten(userId, projectId)) {
      throw new ProjectNotFoundException();
    }
    if (!an) {
      einstellungen.schaltePersonAus(userId, projectId);
    } else if (einstellungen.personEingeschaltetSeit(userId, projectId).isEmpty()) {
      einstellungen.schaltePersonEin(userId, projectId, clock.instant());
    }
  }

  /**
   * Legt die eigene Ruhezeit an oder ersetzt sie.
   *
   * @throws UngueltigeRuhezeitException wenn Beginn und Ende gleich sind oder die Zone nicht in der
   *     IANA-Liste steht (400)
   */
  @Transactional
  public void setzeRuhezeit(long userId, LocalTime beginn, LocalTime ende, String zeitzone) {
    if (beginn.equals(ende)) {
      throw UngueltigeRuhezeitException.gleicheZeiten();
    }
    // Nicht ZoneId.of allein: Es nähme auch feste Versätze wie +01:00 an, die keine Zone sind.
    if (!ZoneId.getAvailableZoneIds().contains(zeitzone)) {
      throw UngueltigeRuhezeitException.unbekannteZone();
    }
    einstellungen.speichereRuhezeit(userId, new Ruhezeit(beginn, ende, ZoneId.of(zeitzone)));
  }

  /** Löscht die eigene Ruhezeit; ohne Ruhezeit geschieht nichts. */
  @Transactional
  public void loescheRuhezeit(long userId) {
    einstellungen.loescheRuhezeit(userId);
  }

  private boolean darfSichSchalten(long userId, long projectId) {
    return permissions.isRealProjectMember(userId, projectId)
        || permissions.hatNachtlaufZugriff(userId, projectId);
  }
}
