package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CleanupProperties;
import org.mwolff.manban.card.application.DoneRetentionSettingService;
import org.mwolff.manban.card.application.DoneRetentionSettingService.RetentionSettings;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Der Beitrag des card-Moduls zum Startprotokoll (Issue #908).
 *
 * <p>Zwei Dinge trägt dieser Test, die ein bloßer „läuft/läuft nicht"-Test nicht trüge: dass eine
 * abgeschaltete Automatik <b>erscheint</b> statt zu fehlen, und dass ein Admin-Override als solcher
 * erkannt wird — auch dann, wenn er zufällig dieselbe Zahl trägt wie die Konfiguration.
 */
class CleanupAutomationStatusContributorTest {

  private static final CleanupProperties PROPERTIES = new CleanupProperties(true, 30, 14, null);

  // Mockito kann generische Typen nicht ohne Warnung mocken; ObjectProvider<T> ist hier
  // unvermeidbar, weil der Contributor genau darueber die Existenz der Bean erfragt.
  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> liefert(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private static List<AutomationStatus> statuses(
      boolean trashBean, boolean doneBean, RetentionSettings frist) {
    DoneRetentionSettingService setting = mock(DoneRetentionSettingService.class);
    when(setting.retentionForStartup()).thenReturn(frist);
    return new CleanupAutomationStatusContributor(
            liefert(trashBean ? mock(TrashRetentionJob.class) : null),
            liefert(doneBean ? mock(DoneRetentionJob.class) : null),
            PROPERTIES,
            setting)
        .statuses();
  }

  private static AutomationStatus mitBezeichnung(List<AutomationStatus> stati, String bezeichnung) {
    return stati.stream()
        .filter(s -> bezeichnung.equals(s.bezeichnung()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void mitBeidenBeans_sindBeideAutomatikenEingeschaltet() {
    List<AutomationStatus> stati = statuses(true, true, new RetentionSettings(30, null));

    assertThat(stati).hasSize(2).allMatch(AutomationStatus::eingeschaltet);
  }

  @Test
  void ohneBeans_erscheinenBeideTrotzdem_alsAbgeschaltet() {
    List<AutomationStatus> stati = statuses(false, false, new RetentionSettings(30, null));

    assertThat(stati).hasSize(2).noneMatch(AutomationStatus::eingeschaltet);
    assertThat(stati)
        .extracting(AutomationStatus::bezeichnung)
        .containsExactlyInAnyOrder("Papierkorb-Löschung", "Done-Archivierung");
  }

  @Test
  void fehlendeTrashBean_trifftNurDiePapierkorbLoeschung() {
    List<AutomationStatus> stati = statuses(false, true, new RetentionSettings(30, null));

    assertThat(mitBezeichnung(stati, "Papierkorb-Löschung").eingeschaltet()).isFalse();
    assertThat(mitBezeichnung(stati, "Done-Archivierung").eingeschaltet()).isTrue();
  }

  @Test
  void fehlendeDoneBean_trifftNurDieDoneArchivierung() {
    List<AutomationStatus> stati = statuses(true, false, new RetentionSettings(30, null));

    assertThat(mitBezeichnung(stati, "Papierkorb-Löschung").eingeschaltet()).isTrue();
    assertThat(mitBezeichnung(stati, "Done-Archivierung").eingeschaltet()).isFalse();
  }

  @Test
  void ohneOverride_nenntDieDoneArchivierungDieKonfiguration() {
    List<AutomationStatus> stati = statuses(true, true, new RetentionSettings(30, null));

    assertThat(mitBezeichnung(stati, "Done-Archivierung").detail())
        .isEqualTo("Frist 30 Tage (Konfiguration)");
  }

  @Test
  void mitOverride_nenntDieDoneArchivierungDenOverrideUndDessenWert() {
    List<AutomationStatus> stati = statuses(true, true, new RetentionSettings(7, 7));

    assertThat(mitBezeichnung(stati, "Done-Archivierung").detail())
        .isEqualTo("Frist 7 Tage (Plattform-Admin-Override)");
  }

  @Test
  void overrideNull_meldetAbgeschaltet_obwohlDieBeanSteht() {
    List<AutomationStatus> stati = statuses(true, true, new RetentionSettings(0, 0));

    assertThat(mitBezeichnung(stati, "Done-Archivierung").eingeschaltet()).isFalse();
  }

  /**
   * Der Grund, warum die Quelle aus {@code override()} kommt und nicht aus einem Vergleich des
   * effektiven Werts mit der Konfiguration: Hier sind beide Zahlen gleich, und ein Vergleich
   * meldete „Konfiguration" für eine Einstellung, die in der Datenbank steht.
   */
  @Test
  void overrideZahlengleichMitDerKonfiguration_giltTrotzdemAlsOverride() {
    List<AutomationStatus> stati = statuses(true, true, new RetentionSettings(30, 30));

    assertThat(mitBezeichnung(stati, "Done-Archivierung").detail())
        .isEqualTo("Frist 30 Tage (Plattform-Admin-Override)");
  }

  @Test
  void diePapierkorbFristStammtAusDenProperties_nichtAusEinerFestenZahl() {
    List<AutomationStatus> stati = statuses(true, true, new RetentionSettings(30, null));

    assertThat(mitBezeichnung(stati, "Papierkorb-Löschung").detail()).isEqualTo("Frist 14 Tage");
  }

  @Test
  void beideNennenDenselbenSchalter() {
    List<AutomationStatus> stati = statuses(true, true, new RetentionSettings(30, null));

    assertThat(stati)
        .extracting(AutomationStatus::schalterVariable)
        .containsOnly("MANBAN_CLEANUP_ENABLED");
  }
}
