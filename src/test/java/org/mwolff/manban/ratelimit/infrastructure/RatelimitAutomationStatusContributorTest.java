package org.mwolff.manban.ratelimit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.automation.AutomationStatus;
import org.springframework.beans.factory.ObjectProvider;

/** Der Beitrag des ratelimit-Moduls zum Startprotokoll (Issue #1000). */
class RatelimitAutomationStatusContributorTest {

  // Mockito kann generische Typen nicht ohne ungeprüfte Umwandlung mocken.
  @SuppressWarnings("unchecked")
  private static ObjectProvider<RejectionRetentionJob> liefert(RejectionRetentionJob job) {
    ObjectProvider<RejectionRetentionJob> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(job);
    return provider;
  }

  @Test
  void mitJob_istDieAufraeumungEingeschaltet() {
    assertThat(
            new RatelimitAutomationStatusContributor(liefert(mock(RejectionRetentionJob.class)))
                .statuses())
        .containsExactly(
            new AutomationStatus(
                "Aufräumung der Überlast-Abweisungen",
                true,
                "MANBAN_CLEANUP_ENABLED",
                "Aufbewahrung 90 Tage"));
  }

  @Test
  void ohneJob_erscheintSieTrotzdem_alsAbgeschaltet() {
    assertThat(new RatelimitAutomationStatusContributor(liefert(null)).statuses())
        .singleElement()
        .extracting(AutomationStatus::eingeschaltet)
        .isEqualTo(false);
  }
}
