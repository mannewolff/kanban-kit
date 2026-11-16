package org.mwolff.manban.card.application;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.project.application.BoardQuelle;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Baut die Prüflinge {@link CardService}, {@link CardIngestService}, {@link EpicService}, {@link
 * CardArchiveService} und {@link CardMoveService} für die Unit-Tests aus ihren Ports (Issue #1389,
 * #1392, #1393, #1394, #1395).
 *
 * <p>Die modulinternen Bausteine {@link KartenAbhaengigkeiten}, {@link KartenGrundlage}, {@link
 * KartenSicht} und {@link VorhabenArchivierung} entstehen hier echt aus denselben Port-Mocks, nicht
 * als Mocks (Plan #1387, E6): So treffen Abdeckung und Mutationsprüfung die herausgelösten Helfer
 * weiter über die Tests des Dienstes, und ein Test, der einen Port stubbt oder prüft, sieht jeden
 * Zugriff — gleich, welcher Baustein ihn macht. Die Testklassen behalten damit ihre bisherige
 * Konstruktion aus den Ports; {@code ProjectService} entfiel mit der Suche (Issue #1391).
 */
// PMD.CouplingBetweenObjects: Die Klasse baut fünf Prüflinge samt Bausteinen aus allen Ports; mit
// Issue #1621 kommt UserLookup für CardService dazu. Eine Aufteilung trennte die gemeinsamen
// Bausteine, die alle Prüflinge aus denselben Mocks bekommen sollen (Plan #1387, E6).
@SuppressWarnings("PMD.CouplingBetweenObjects")
final class CardServiceAufbau {

  /** Vorgabe der Board-Quelle: kein Projekt hat ein fremdes Board (Issue #2161). */
  static final BoardQuelle KEIN_FREMDES_BOARD = projectId -> false;

  private CardServiceAufbau() {}

  /** Wie die Voll-Signatur, mit einem stummen {@link UserLookup}-Mock (Issue #1621). */
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
    return ausPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        mock(UserLookup.class));
  }

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
      Clock clock,
      UserLookup users) {
    return ausPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        users,
        mock(CardBestandRepository.class));
  }

  /** Wie die Signatur davor, mit einem eigenen {@link CardBestandRepository} (Issue #1930). */
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
      Clock clock,
      UserLookup users,
      CardBestandRepository bestand) {
    return ausPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        users,
        bestand,
        KEIN_FREMDES_BOARD);
  }

  /** Wie die Signatur davor, mit eigener {@link BoardQuelle} (Issue #2161). */
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
      Clock clock,
      UserLookup users,
      CardBestandRepository bestand,
      BoardQuelle quelle) {
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
            clock,
            bestand,
            quelle);
    return new CardService(
        cards,
        b.abhaengigkeiten(),
        boardService,
        permissions,
        zuordnung,
        activity,
        b.grundlage(),
        b.sicht(),
        users);
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
    return ingestAusPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        KEIN_FREMDES_BOARD);
  }

  /** Wie die Signatur davor, mit eigener {@link BoardQuelle} (Issue #2161). */
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
      Clock clock,
      BoardQuelle quelle) {
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
            clock,
            mock(CardBestandRepository.class),
            quelle);
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
    return epicAusPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        KEIN_FREMDES_BOARD);
  }

  /** Wie die Signatur davor, mit eigener {@link BoardQuelle} (Issue #2161). */
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
      Clock clock,
      BoardQuelle quelle) {
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
            clock,
            mock(CardBestandRepository.class),
            quelle);
    return new EpicService(
        cards,
        b.abhaengigkeiten(),
        boardService,
        permissions,
        zuordnung,
        b.grundlage(),
        b.sicht(),
        new VorhabenMassenzuordnung(cards, b.grundlage(), permissions),
        clock);
  }

  static CardArchiveService archiveAusPorts(
      CardRepository cards,
      CardBestandRepository bestand,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock) {
    return archiveAusPorts(
        cards,
        bestand,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        KEIN_FREMDES_BOARD);
  }

  /** Wie die Signatur davor, mit eigener {@link BoardQuelle} (Issue #2161). */
  static CardArchiveService archiveAusPorts(
      CardRepository cards,
      CardBestandRepository bestand,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock,
      BoardQuelle quelle) {
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
            clock,
            bestand,
            quelle);
    return new CardArchiveService(
        cards,
        bestand,
        b.abhaengigkeiten(),
        boardService,
        permissions,
        b.grundlage(),
        b.sicht(),
        new VorhabenArchivierung(cards, quelle),
        events,
        clock);
  }

  static CardMoveService moveAusPorts(
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
    return moveAusPorts(
        cards,
        dependencies,
        boardService,
        permissions,
        transitions,
        zuordnung,
        activity,
        actor,
        events,
        clock,
        KEIN_FREMDES_BOARD);
  }

  /** Wie die Signatur davor, mit eigener {@link BoardQuelle} (Issue #2161). */
  static CardMoveService moveAusPorts(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock,
      BoardQuelle quelle) {
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
            clock,
            mock(CardBestandRepository.class),
            quelle);
    return new CardMoveService(
        cards,
        b.abhaengigkeiten(),
        boardService,
        permissions,
        transitions,
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
      Clock clock,
      CardBestandRepository bestand,
      BoardQuelle quelle) {
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
            clock,
            quelle);
    KartenSicht sicht = new KartenSicht(cards, bestand, abhaengigkeiten, zuordnung, permissions);
    return new Bausteine(abhaengigkeiten, grundlage, sicht);
  }

  /** Die modulinternen Bausteine, die alle Prüflinge aus denselben Ports bekommen. */
  private record Bausteine(
      KartenAbhaengigkeiten abhaengigkeiten, KartenGrundlage grundlage, KartenSicht sicht) {}
}
