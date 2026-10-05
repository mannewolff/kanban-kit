package org.mwolff.manban.card.application;

import java.time.Clock;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Baut den Prüfling {@link CardService} für die Unit-Tests aus seinen Ports (Issue #1389).
 *
 * <p>Die modulinternen Bausteine {@link KartenAbhaengigkeiten}, {@link KartenGrundlage} und {@link
 * KartenSicht} entstehen hier echt aus denselben Port-Mocks, nicht als Mocks (Plan #1387, E6): So
 * treffen Abdeckung und Mutationsprüfung die herausgelösten Helfer weiter über die Tests des
 * Dienstes, und ein Test, der einen Port stubbt oder prüft, sieht jeden Zugriff — gleich, welcher
 * Baustein ihn macht. Die Testklassen behalten damit ihre bisherige Konstruktion aus elf Ports.
 */
final class CardServiceAufbau {

  private CardServiceAufbau() {}

  static CardService ausPorts(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
      ProjectService projects,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock) {
    KartenAbhaengigkeiten abhaengigkeiten = new KartenAbhaengigkeiten(dependencies, cards);
    KartenGrundlage grundlage =
        new KartenGrundlage(
            cards,
            abhaengigkeiten,
            boardService,
            permissions,
            transitions,
            zuordnung,
            activity,
            actor,
            events,
            clock);
    KartenSicht sicht = new KartenSicht(cards, abhaengigkeiten, zuordnung, permissions);
    return new CardService(
        cards,
        abhaengigkeiten,
        boardService,
        permissions,
        projects,
        transitions,
        zuordnung,
        activity,
        grundlage,
        sicht,
        events,
        clock);
  }
}
