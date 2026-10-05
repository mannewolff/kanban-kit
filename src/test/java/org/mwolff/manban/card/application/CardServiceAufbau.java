package org.mwolff.manban.card.application;

import java.time.Clock;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Baut die Prüflinge {@link CardService}, {@link CardIngestService} und {@link EpicService} für die
 * Unit-Tests aus ihren Ports (Issue #1389, #1392, #1393).
 *
 * <p>Die modulinternen Bausteine {@link KartenAbhaengigkeiten}, {@link KartenGrundlage} und {@link
 * KartenSicht} entstehen hier echt aus denselben Port-Mocks, nicht als Mocks (Plan #1387, E6): So
 * treffen Abdeckung und Mutationsprüfung die herausgelösten Helfer weiter über die Tests des
 * Dienstes, und ein Test, der einen Port stubbt oder prüft, sieht jeden Zugriff — gleich, welcher
 * Baustein ihn macht. Die Testklassen behalten damit ihre bisherige Konstruktion aus den Ports;
 * {@code ProjectService} entfiel mit der Suche (Issue #1391).
 */
final class CardServiceAufbau {

  private CardServiceAufbau() {}

  static CardService ausPorts(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock) {
    Bausteine b =
        bausteine(
            cards,
            dependencies,
            boardService,
            permissions,
            transitions,
            zuordnung,
            activity,
            actor,
            events,
            clock);
    return new CardService(
        cards,
        b.abhaengigkeiten(),
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        b.grundlage(),
        b.sicht(),
        events,
        clock);
  }

  static CardIngestService ingestAusPorts(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock) {
    Bausteine b =
        bausteine(
            cards,
            dependencies,
            boardService,
            permissions,
            transitions,
            zuordnung,
            activity,
            actor,
            events,
            clock);
    return new CardIngestService(
        cards, b.abhaengigkeiten(), boardService, permissions, b.grundlage(), b.sicht(), clock);
  }

  static EpicService epicAusPorts(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock) {
    Bausteine b =
        bausteine(
            cards,
            dependencies,
            boardService,
            permissions,
            transitions,
            zuordnung,
            activity,
            actor,
            events,
            clock);
    return new EpicService(
        cards,
        b.abhaengigkeiten(),
        boardService,
        permissions,
        zuordnung,
        b.grundlage(),
        b.sicht(),
        clock);
  }

  private static Bausteine bausteine(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
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
    return new Bausteine(abhaengigkeiten, grundlage, sicht);
  }

  /** Die modulinternen Bausteine, die alle Prüflinge aus denselben Ports bekommen. */
  private record Bausteine(
      KartenAbhaengigkeiten abhaengigkeiten, KartenGrundlage grundlage, KartenSicht sicht) {}
}
