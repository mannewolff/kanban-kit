package org.mwolff.manban.outbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.mwolff.manban.outbox.application.OutboxProperties;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Der Beitrag des outbox-Moduls zum Startprotokoll (Issue #909).
 *
 * <p>Die Werte weichen bewusst von den Vorgaben ab: Mit 5000 ms und 8 Versuchen wäre nicht zu
 * unterscheiden, ob der Text aus den Properties stammt oder fest im Code steht.
 */
class OutboxAutomationStatusContributorTest {

  private static final OutboxProperties PROPERTIES =
      new OutboxProperties(true, 60_000L, 25, 3, Duration.ofSeconds(10), Duration.ofHours(1), 21);

  // Mockito kann generische Typen nicht ohne Warnung mocken; ObjectProvider<T> ist hier
  // unvermeidbar, weil der Contributor genau darueber die Existenz der Bean erfragt.
  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> liefert(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private static List<AutomationStatus> statuses(boolean workerBean, boolean retentionBean) {
    return new OutboxAutomationStatusContributor(
            liefert(workerBean ? mock(OutboxWorker.class) : null),
            liefert(retentionBean ? mock(OutboxRetentionJob.class) : null),
            PROPERTIES)
        .statuses();
  }

  private static AutomationStatus mitBezeichnung(List<AutomationStatus> stati, String bezeichnung) {
    return stati.stream()
        .filter(s -> bezeichnung.equals(s.bezeichnung()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void mitBeidenBeans_sindBeideEingeschaltet() {
    assertThat(statuses(true, true)).hasSize(2).allMatch(AutomationStatus::eingeschaltet);
  }

  @Test
  void ohneBeans_erscheinenBeideTrotzdem_alsAbgeschaltet() {
    List<AutomationStatus> stati = statuses(false, false);

    assertThat(stati).hasSize(2).noneMatch(AutomationStatus::eingeschaltet);
    assertThat(stati)
        .extracting(AutomationStatus::bezeichnung)
        .containsExactlyInAnyOrder("Outbox-Worker", "Outbox-Aufräumung");
  }

  @Test
  void fehlenderWorker_trifftNurDieZustellung() {
    List<AutomationStatus> stati = statuses(false, true);

    assertThat(mitBezeichnung(stati, "Outbox-Worker").eingeschaltet()).isFalse();
    assertThat(mitBezeichnung(stati, "Outbox-Aufräumung").eingeschaltet()).isTrue();
  }

  @Test
  void fehlenderAufraeumJob_trifftNurDieAufraeumung() {
    List<AutomationStatus> stati = statuses(true, false);

    assertThat(mitBezeichnung(stati, "Outbox-Worker").eingeschaltet()).isTrue();
    assertThat(mitBezeichnung(stati, "Outbox-Aufräumung").eingeschaltet()).isFalse();
  }

  @Test
  void derWorkerNenntAbstandUndVersucheAusDenProperties() {
    assertThat(mitBezeichnung(statuses(true, true), "Outbox-Worker").detail())
        .isEqualTo("Abstand 60000 ms, höchstens 3 Versuche");
  }

  @Test
  void dieAufraeumungNenntDieAufbewahrungAusDenProperties() {
    assertThat(mitBezeichnung(statuses(true, true), "Outbox-Aufräumung").detail())
        .isEqualTo("Aufbewahrung 21 Tage");
  }

  /**
   * Die abweichende Kopplung, die dieses Protokoll erstmals sichtbar macht: Die Aufräumung haengt
   * am Aufräum-Schalter, nicht am Outbox-Schalter.
   */
  @Test
  void dieBeidenNennenVerschiedeneSchalter() {
    List<AutomationStatus> stati = statuses(true, true);

    assertThat(mitBezeichnung(stati, "Outbox-Worker").schalterVariable())
        .isEqualTo("MANBAN_OUTBOX_ENABLED");
    assertThat(mitBezeichnung(stati, "Outbox-Aufräumung").schalterVariable())
        .isEqualTo("MANBAN_CLEANUP_ENABLED");
  }
}
