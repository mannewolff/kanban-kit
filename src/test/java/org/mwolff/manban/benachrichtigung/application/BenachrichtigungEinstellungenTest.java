package org.mwolff.manban.benachrichtigung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungen.MeineEinstellungen;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungen.PersonenSchalter;
import org.mwolff.manban.benachrichtigung.domain.Ruhezeit;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;

/**
 * Schalter und Ruhezeit (Issue #2076, Plan #2070 E12): Ohne Zeile ist ein Schalter aus, Einschalten
 * setzt den Zeitpunkt genau einmal, die Rechte prüft der Dienst vor jedem Zugriff.
 */
class BenachrichtigungEinstellungenTest {

  private static final Instant JETZT = Instant.parse("2026-11-09T12:00:00Z");
  private static final Instant FRUEHER = Instant.parse("2026-10-01T08:00:00Z");
  private static final long USER = 2L;
  private static final long PROJEKT = 7L;
  private static final LocalTime ZWEIUNDZWANZIG = LocalTime.of(22, 0);
  private static final LocalTime SIEBEN = LocalTime.of(7, 0);

  private BenachrichtigungEinstellungenRepository einstellungen;
  private PermissionChecker permissions;
  private ProjectService projects;
  private BenachrichtigungEinstellungen dienst;

  @BeforeEach
  void setUp() {
    einstellungen = mock(BenachrichtigungEinstellungenRepository.class);
    permissions = mock(PermissionChecker.class);
    projects = mock(ProjectService.class);
    dienst =
        new BenachrichtigungEinstellungen(
            einstellungen, permissions, projects, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  // --- Projektschalter ---------------------------------------------------------------------

  @Test
  void projektschalter_ohneZeile_istAus() {
    when(einstellungen.projektEingeschaltetSeit(PROJEKT)).thenReturn(Optional.empty());

    assertThat(dienst.projektschalter(USER, PROJEKT)).isFalse();
  }

  @Test
  void projektschalter_mitZeile_istAn() {
    when(einstellungen.projektEingeschaltetSeit(PROJEKT)).thenReturn(Optional.of(FRUEHER));

    assertThat(dienst.projektschalter(USER, PROJEKT)).isTrue();
  }

  @Test
  void projektschalter_lesen_prueftDieEchteRolleZuerst() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireEchterOwnerOderAdmin(USER, PROJEKT);

    assertThatThrownBy(() -> dienst.projektschalter(USER, PROJEKT))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(einstellungen, never()).projektEingeschaltetSeit(anyLong());
  }

  @Test
  void schalteProjekt_einschalten_setztDenZeitpunktDerUhr() {
    when(einstellungen.projektEingeschaltetSeit(PROJEKT)).thenReturn(Optional.empty());

    assertThat(dienst.schalteProjekt(USER, PROJEKT, true)).isTrue();

    verify(einstellungen).schalteProjektEin(PROJEKT, JETZT);
  }

  @Test
  void schalteProjekt_erneutesEinschalten_laesstDenZeitpunktUnveraendert() {
    when(einstellungen.projektEingeschaltetSeit(PROJEKT)).thenReturn(Optional.of(FRUEHER));

    assertThat(dienst.schalteProjekt(USER, PROJEKT, true)).isTrue();

    verify(einstellungen, never()).schalteProjektEin(anyLong(), eq(JETZT));
  }

  @Test
  void schalteProjekt_ausschalten_loeschtDieZeile() {
    assertThat(dienst.schalteProjekt(USER, PROJEKT, false)).isFalse();

    verify(einstellungen).schalteProjektAus(PROJEKT);
  }

  @Test
  void schalteProjekt_ohneEchteRolle_schreibtNichts() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireEchterOwnerOderAdmin(USER, PROJEKT);

    assertThatThrownBy(() -> dienst.schalteProjekt(USER, PROJEKT, true))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(einstellungen, never()).schalteProjektEin(anyLong(), eq(JETZT));
    verify(einstellungen, never()).schalteProjektAus(anyLong());
  }

  // --- Personenschalter --------------------------------------------------------------------

  @Test
  void schaltePerson_alsMitglied_setztDenZeitpunktDerUhr() {
    when(permissions.isRealProjectMember(USER, PROJEKT)).thenReturn(true);
    when(einstellungen.personEingeschaltetSeit(USER, PROJEKT)).thenReturn(Optional.empty());

    dienst.schaltePerson(USER, PROJEKT, true);

    verify(einstellungen).schaltePersonEin(USER, PROJEKT, JETZT);
  }

  @Test
  void schaltePerson_mitNachtlaufZugriffOhneMitgliedschaft_istErlaubt() {
    when(permissions.hatNachtlaufZugriff(USER, PROJEKT)).thenReturn(true);
    when(einstellungen.personEingeschaltetSeit(USER, PROJEKT)).thenReturn(Optional.empty());

    dienst.schaltePerson(USER, PROJEKT, true);

    verify(einstellungen).schaltePersonEin(USER, PROJEKT, JETZT);
  }

  @Test
  void schaltePerson_erneutesEinschalten_laesstDenZeitpunktUnveraendert() {
    when(permissions.isRealProjectMember(USER, PROJEKT)).thenReturn(true);
    when(einstellungen.personEingeschaltetSeit(USER, PROJEKT)).thenReturn(Optional.of(FRUEHER));

    dienst.schaltePerson(USER, PROJEKT, true);

    verify(einstellungen, never()).schaltePersonEin(anyLong(), anyLong(), eq(JETZT));
  }

  @Test
  void schaltePerson_ausschalten_loeschtDieZeile() {
    when(permissions.isRealProjectMember(USER, PROJEKT)).thenReturn(true);

    dienst.schaltePerson(USER, PROJEKT, false);

    verify(einstellungen).schaltePersonAus(USER, PROJEKT);
  }

  @Test
  void schaltePerson_ohneMitgliedschaftUndOhneNachtlaufZugriff_giltWieUnbekannt() {
    assertThatThrownBy(() -> dienst.schaltePerson(USER, PROJEKT, true))
        .isInstanceOf(ProjectNotFoundException.class);

    verify(einstellungen, never()).schaltePersonEin(anyLong(), anyLong(), eq(JETZT));
    verify(einstellungen, never()).schaltePersonAus(anyLong(), anyLong());
  }

  // --- Eigene Einstellungen lesen ----------------------------------------------------------

  @Test
  void meine_ohneZeilen_sindBeideSchalterAusUndKeineRuhezeit() {
    when(projects.listAccessible(USER)).thenReturn(List.of(new AccessibleProject(PROJEKT, "A")));
    when(permissions.isRealProjectMember(USER, PROJEKT)).thenReturn(true);
    when(einstellungen.eingeschalteteProjekte(List.of(PROJEKT))).thenReturn(Set.of());
    when(einstellungen.eingeschalteteProjekteDerPerson(USER)).thenReturn(Set.of());
    when(einstellungen.ruhezeit(USER)).thenReturn(Optional.empty());

    MeineEinstellungen meine = dienst.meine(USER);

    assertThat(meine.projekte()).containsExactly(new PersonenSchalter(PROJEKT, "A", false, false));
    assertThat(meine.ruhezeit()).isNull();
  }

  @Test
  void meine_nenntNurProjekteMitMitgliedschaftOderNachtlaufZugriff() {
    when(projects.listAccessible(USER))
        .thenReturn(
            List.of(
                new AccessibleProject(1L, "Mitglied"),
                new AccessibleProject(2L, "Nachtlauf"),
                new AccessibleProject(3L, "Fremd")));
    when(permissions.isRealProjectMember(USER, 1L)).thenReturn(true);
    when(permissions.hatNachtlaufZugriff(USER, 2L)).thenReturn(true);
    when(einstellungen.eingeschalteteProjekte(List.of(1L, 2L))).thenReturn(Set.of(1L));
    when(einstellungen.eingeschalteteProjekteDerPerson(USER)).thenReturn(Set.of(2L, 3L));
    Ruhezeit ruhezeit = new Ruhezeit(ZWEIUNDZWANZIG, SIEBEN, ZoneId.of("Europe/Berlin"));
    when(einstellungen.ruhezeit(USER)).thenReturn(Optional.of(ruhezeit));

    MeineEinstellungen meine = dienst.meine(USER);

    assertThat(meine.projekte())
        .containsExactly(
            new PersonenSchalter(1L, "Mitglied", true, false),
            new PersonenSchalter(2L, "Nachtlauf", false, true));
    assertThat(meine.ruhezeit()).isEqualTo(ruhezeit);
  }

  // --- Ruhezeit ----------------------------------------------------------------------------

  @Test
  void setzeRuhezeit_speichertBeginnEndeUndZone() {
    dienst.setzeRuhezeit(USER, ZWEIUNDZWANZIG, SIEBEN, "Europe/Berlin");

    verify(einstellungen)
        .speichereRuhezeit(USER, new Ruhezeit(ZWEIUNDZWANZIG, SIEBEN, ZoneId.of("Europe/Berlin")));
  }

  @Test
  void setzeRuhezeit_mitGleichemBeginnUndEnde_wirdAbgewiesen() {
    assertThatThrownBy(() -> dienst.setzeRuhezeit(USER, SIEBEN, SIEBEN, "Europe/Berlin"))
        .isInstanceOf(UngueltigeRuhezeitException.class)
        .extracting(e -> ((UngueltigeRuhezeitException) e).schluessel())
        .isEqualTo("benachrichtigung.ruhezeit.gleiche-zeiten");

    verify(einstellungen, never()).speichereRuhezeit(anyLong(), org.mockito.ArgumentMatchers.any());
  }

  /** Nur Namen aus der IANA-Liste: ein fester Versatz ist gültig für Java, aber keine Zone. */
  @ParameterizedTest
  @ValueSource(strings = {"Mond/Basis", "", "+01:00", "UTC+1", "europe/berlin"})
  void setzeRuhezeit_mitUnbekannterZone_wirdAbgewiesen(String zone) {
    assertThatThrownBy(() -> dienst.setzeRuhezeit(USER, ZWEIUNDZWANZIG, SIEBEN, zone))
        .isInstanceOf(UngueltigeRuhezeitException.class)
        .extracting(e -> ((UngueltigeRuhezeitException) e).schluessel())
        .isEqualTo("benachrichtigung.ruhezeit.unbekannte-zone");

    verify(einstellungen, never()).speichereRuhezeit(anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void loescheRuhezeit_loeschtDieEigene() {
    dienst.loescheRuhezeit(USER);

    verify(einstellungen).loescheRuhezeit(USER);
  }
}
