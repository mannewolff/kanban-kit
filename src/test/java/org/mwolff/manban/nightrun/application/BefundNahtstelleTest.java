package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.nightrun.application.DisruptionRepository.DisruptionCandidate;
import org.mwolff.manban.nightrun.application.NightRunService.NightRunView;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.project.application.InteractiveUsageSinceWriter;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Kriterium 10 der fachlichen Quelle #1086: Über den Ausgang eines Laufs gibt es <b>eine</b>
 * Wahrheit — die Nachtlauf-Auswertung des Projekts und der Plattform-Leitstand sagen dasselbe.
 *
 * <p>Eigene Klasse, weil die Zusage keinem der beiden Dienste allein gehört: Sie entsteht erst
 * daraus, dass beide denselben {@link NightRunOutcome} mit denselben Eingaben bilden. In einem der
 * beiden Test-Klassen stünde sie über einer Hälfte und läse sich wie deren Eigenschaft.
 *
 * <p>Der <b>verstummte</b> Lauf ist der Prüfstein: Bei ihm entsteht der Ausgang nicht aus einem
 * gespeicherten Feld, sondern aus Lebenszeichen, Uhr und Stillefrist — drei Größen, die jeder
 * Dienst selbst heranträgt und deshalb auch auseinandertragen könnte.
 *
 * <p>Seit Issue #1143 steht der <b>Abbruchgrund</b> daneben: Er ist die vierte Größe, die jeder
 * Dienst aus seiner eigenen Quelle holt — die Projekt-Ansicht aus dem {@link NightRun}, der
 * Leitstand aus dem {@link DisruptionCandidate}. Vergäße ihn einer der beiden, gölte derselbe
 * abgebrochene Lauf an der einen Stelle als gelungen und an der anderen als gescheitert.
 */
class BefundNahtstelleTest {

  private static final Instant JETZT = Instant.parse("2026-09-20T08:00:00Z");
  private static final Instant GESTARTET = JETZT.minus(Duration.ofHours(5));
  private static final Instant LETZTE_MELDUNG = JETZT.minus(Duration.ofHours(4));
  private static final long PROJEKT = 9L;
  private static final long LAUF = 5L;
  private static final long ADMIN = 1L;

  /** Ein Abbruchgrund, wie der Runner ihn meldet (Issue #1142). */
  private static final String ABBRUCH = "Dirty-Guard: uncommittete Reste in src/main/java/Foo.java";

  private final Clock uhr = Clock.fixed(JETZT, ZoneOffset.UTC);
  private final NightRunProperties properties =
      new NightRunProperties(null, null, null, null, null);
  private final NightRunRepository runs = mock(NightRunRepository.class);

  /**
   * Der Lauf, wie die Projekt-Ansicht ihn liest.
   *
   * @param complete ob der Lauf sich als abgeschlossen gemeldet hat — {@code false} für den
   *     verstummten, {@code true} für den abgebrochenen
   * @param abortReason der gemeldete Abbruchgrund; {@code null} für den verstummten Lauf
   */
  private NightRunOutcome befundDerProjektAnsicht(boolean complete, @Nullable String abortReason) {
    when(runs.findByProjectAndKindOrderByStartedAtDesc(PROJEKT, NightRunKind.NIGHT))
        .thenReturn(
            List.of(
                new NightRun(
                    LAUF,
                    PROJEKT,
                    GESTARTET,
                    NightRunMode.IMPLEMENTATION,
                    NightRunKind.NIGHT,
                    1L,
                    1,
                    0,
                    0,
                    null,
                    GESTARTET,
                    NightRunOrigin.TOKEN,
                    null,
                    complete,
                    LETZTE_MELDUNG,
                    null,
                    null,
                    null,
                    abortReason)));
    when(runs.findItemsByRunIds(any())).thenReturn(List.of());
    NightRunService service =
        new NightRunService(
            runs,
            mock(PermissionChecker.class),
            mock(InteractiveUsageSinceWriter.class),
            properties,
            uhr);
    return service.list(ADMIN, PROJEKT).stream()
        .map(NightRunView::outcome)
        .findFirst()
        .orElseThrow();
  }

  /** Derselbe Lauf, wie der Plattform-Leitstand ihn liest. */
  private NightRunOutcome befundDesLeitstands(boolean complete, @Nullable String abortReason) {
    DisruptionRepository disruptions = mock(DisruptionRepository.class);
    when(disruptions.candidatesOfNight(any(), any(), any(), any()))
        .thenReturn(
            List.of(
                new DisruptionCandidate(
                    LAUF,
                    PROJEKT,
                    "Projekt",
                    NightRunMode.IMPLEMENTATION,
                    GESTARTET,
                    LETZTE_MELDUNG,
                    complete,
                    null,
                    abortReason)));
    PlatformAdminChecker admins = mock(PlatformAdminChecker.class);
    when(admins.isPlatformAdmin(ADMIN)).thenReturn(true);
    DisruptionService service = new DisruptionService(disruptions, runs, admins, properties, uhr);
    return service.leitstand(ADMIN, ZoneId.of("UTC")).durchgefuehrte().getFirst().outcome();
  }

  @Test
  void derVerstummteLaufHatInBeidenAnsichtenDenselbenAusgang() {
    NightRunOutcome ausProjektsicht = befundDerProjektAnsicht(false, null);

    assertThat(befundDesLeitstands(false, null)).isEqualTo(ausProjektsicht);
    assertThat(ausProjektsicht.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  /** AK 8 der fachlichen Quelle #1074: Auch der Abbruchgrund geht durch beide Wege gleich. */
  @Test
  void derAbgebrocheneLaufHatInBeidenAnsichtenDenselbenAusgang() {
    NightRunOutcome ausProjektsicht = befundDerProjektAnsicht(true, ABBRUCH);

    assertThat(befundDesLeitstands(true, ABBRUCH)).isEqualTo(ausProjektsicht);
    assertThat(ausProjektsicht.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(ausProjektsicht.abortReason()).isEqualTo(ABBRUCH);
  }
}
