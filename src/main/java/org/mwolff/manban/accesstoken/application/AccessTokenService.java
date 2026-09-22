package org.mwolff.manban.accesstoken.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltung persönlicher API-Zugriffstokens: anlegen (Klartext einmalig), auflisten, widerrufen
 * und Auflösung eingehender {@code X-Kanban-Token}-Header.
 *
 * <p>Ein Token kann optional an ein Projekt + Board gebunden werden (#44): damit adressiert die
 * Kanban-Compat-API (#45) genau dieses Board, ohne dass der Client eine Board-ID mitschickt.
 */
@Service
public class AccessTokenService {

  private final AccessTokenRepository tokens;
  private final TokenCryptoPort crypto;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final Clock clock;
  private final LastUsedStampThrottle stampThrottle;
  private final ObjectProvider<AccessTokenService> self;

  public AccessTokenService(
      AccessTokenRepository tokens,
      TokenCryptoPort crypto,
      BoardService boardService,
      PermissionChecker permissions,
      Clock clock,
      LastUsedStampThrottle stampThrottle,
      ObjectProvider<AccessTokenService> self) {
    this.tokens = tokens;
    this.crypto = crypto;
    this.boardService = boardService;
    this.permissions = permissions;
    this.clock = clock;
    this.stampThrottle = stampThrottle;
    this.self = self;
  }

  /**
   * Legt ein Token an und gibt den Klartext GENAU EINMAL zurück. Sind {@code projectId} und {@code
   * boardId} gesetzt, wird das Token an dieses Board gebunden (beide Werte müssen zusammen gesetzt
   * sein und der Nutzer auf dem Board arbeiten dürfen).
   *
   * @throws InvalidTokenBindingException Bindung unschlüssig: nur eines gesetzt, Board unbekannt
   *     oder Board gehört nicht zum Projekt (400)
   * @throws ProjectAccessDeniedException Nutzer darf auf dem gebundenen Board nicht arbeiten (403)
   */
  @Transactional
  public CreatedAccessToken create(
      long userId, String name, @Nullable Long projectId, @Nullable Long boardId) {
    validateBinding(userId, projectId, boardId);
    GeneratedToken generated = crypto.generate();
    AccessToken saved =
        tokens.save(
            new AccessToken(
                null,
                userId,
                projectId,
                boardId,
                name,
                generated.hash(),
                name,
                clock.instant(),
                null,
                false));
    return new CreatedAccessToken(saved.requireId(), saved.name(), generated.plaintext());
  }

  /** Prüft die optionale Projekt-/Board-Bindung eines neu anzulegenden Tokens. */
  private void validateBinding(long userId, @Nullable Long projectId, @Nullable Long boardId) {
    if (projectId == null && boardId == null) {
      return; // ungebundenes Token
    }
    if (projectId == null || boardId == null) {
      throw new InvalidTokenBindingException(
          "projectId und boardId müssen zusammen gesetzt sein (oder beide leer)");
    }
    // Bewusst die Optional-Variante der Board-Fassade: ein unbekanntes Board ist hier keine
    // fehlende Ressource (404), sondern eine unschluessige Bindungsangabe (400).
    Long boardProjectId =
        boardService
            .findProjectId(boardId)
            .orElseThrow(() -> new InvalidTokenBindingException("Board " + boardId + " unbekannt"));
    if (!projectId.equals(boardProjectId)) {
      throw new InvalidTokenBindingException(
          "Board " + boardId + " gehört nicht zu Projekt " + projectId);
    }
    if (!permissions.hasPermission(userId, projectId, Permission.TICKET_CREATE)) {
      throw new ProjectAccessDeniedException();
    }
  }

  @Transactional(readOnly = true)
  public List<AccessTokenView> list(long userId) {
    return tokens.findByUserId(userId).stream()
        .map(
            t ->
                new AccessTokenView(
                    t.requireId(),
                    t.name(),
                    t.projectId(),
                    t.boardId(),
                    t.createdAt(),
                    t.lastUsedAt(),
                    t.revoked()))
        .toList();
  }

  /**
   * Widerruft ein eigenes Token; ein fremdes oder unbekanntes gibt es für den Aufrufer nicht (404).
   *
   * <p>Geschrieben wird allein die Spalte {@code revoked} — Nutzung und Widerruf fassen disjunkte
   * Spalten an, damit keins das andere überholt (Issue #878). Der Aufruf ist idempotent: Der
   * Zielzustand steht fest und hängt nicht am gelesenen Stand.
   *
   * @throws AccessTokenNotFoundException Token unbekannt oder nicht dem Aufrufer gehörend (404)
   */
  @Transactional
  public void revoke(long userId, long tokenId) {
    AccessToken token =
        tokens
            .findById(tokenId)
            .filter(t -> t.userId() == userId)
            .orElseThrow(AccessTokenNotFoundException::new);
    tokens.markRevoked(token.requireId());
  }

  /**
   * Löst einen eingehenden Klartext-Header zum vollständigen Principal auf (inkl. optionaler
   * Board-Bindung); leer bei unbekannt/widerrufen. Stempelt {@code lastUsedAt} <strong>gedrosselt:
   * höchstens einmal je Token und Minute</strong> (Issue #997).
   *
   * <p>Die Auflösung selbst ist damit ein reiner Lesevorgang. Der Stempel lief zuvor in dieser
   * Transaktion mit und hielt bis zu deren Ende einen Zeilen-Lock auf der Token-Zeile — genau der
   * Zeile, die sich die gleichzeitigen Befehle einer Person teilen. „Zuletzt benutzt" ist seither
   * <strong>minutengenau statt aufrufgenau</strong>; wer einen älteren Wert sieht als den letzten
   * Aufruf, sieht keinen Fehler.
   *
   * <p><strong>Bewusst ohne umschließende Transaktion</strong>, auch ohne lesende: Das Nachschlagen
   * gibt seine Verbindung nach der einen Abfrage zurück, erst danach holt der Stempel eine eigene.
   * Hielte eine Lesetransaktion ihre Verbindung, während der Stempel eine zweite anfordert,
   * bräuchte jede stempelnde Auflösung zwei Verbindungen zugleich — treffen so viele fällige
   * Auflösungen gleichzeitig ein, wie der Pool Verbindungen hat, wartet jede auf eine zweite, die
   * keine mehr freigibt, bis der Verbindungs-Timeout den Filter abbricht. Der gelesene Datensatz
   * braucht keine Transaktion: Er hat keine nachzuladenden Beziehungen und wird sofort zum
   * Domänenobjekt.
   *
   * <p>Geschrieben wird allein die Spalte {@code lastUsedAt}, und nur solange das Token nicht
   * widerrufen ist. Ein volles Zurückschreiben des gelesenen Datensatzes nähme einen
   * zwischenzeitlich committeten Widerruf wieder zurück — ein Widerruf ist aber ein Endzustand
   * (Issue #878).
   */
  public Optional<KanbanPrincipal> resolveBinding(String plaintext) {
    return tokens
        .findByTokenHash(crypto.hash(plaintext))
        .filter(t -> !t.revoked())
        .map(
            t -> {
              stampLastUsedIfDue(t.requireId());
              return new KanbanPrincipal(
                  t.userId(), t.requireId(), t.projectId(), t.boardId(), t.displayName());
            });
  }

  /**
   * Stempelt die Nutzung, sofern die Minute seit dem letzten Stempel um ist. Die Entscheidung fällt
   * aus dem prozesslokalen Zwischenspeicher — ohne zusätzlichen Lesezugriff —, der Schreibvorgang
   * läuft in einer eigenen, kurzen Transaktion.
   */
  private void stampLastUsedIfDue(long tokenId) {
    Instant now = clock.instant();
    if (stampThrottle.claimStamp(tokenId, now)) {
      tokens.touchLastUsedAtInOwnTransaction(tokenId, now);
    }
  }

  /**
   * Löst einen eingehenden Klartext-Header zur User-ID auf; leer bei unbekannt/widerrufen. Ruft
   * {@link #resolveBinding} über den injizierten Self-Provider auf (nicht via {@code this}), damit
   * der Aufruf durch den Spring-Transaktions-Proxy läuft (Sonar {@code java:S6809}).
   *
   * <p>Ohne umschließende Transaktion, aus demselben Grund wie {@link #resolveBinding} (Issue
   * #997): Sie hielte eine Verbindung, während der Stempel eine zweite anfordert.
   */
  public OptionalLong resolve(String plaintext) {
    return self.getObject()
        .resolveBinding(plaintext)
        .map(p -> OptionalLong.of(p.userId()))
        .orElseGet(OptionalLong::empty);
  }

  /** Ergebnis der Erstellung — enthält den einmalig sichtbaren Klartext. */
  public record CreatedAccessToken(Long id, String name, String plaintext) {}

  /**
   * Listen-/Detaildarstellung ohne Hash und ohne Klartext; inkl. optionaler Bindung. {@code
   * lastUsedAt} ist minutengenau — siehe {@link #resolveBinding} (Issue #997).
   */
  public record AccessTokenView(
      Long id,
      String name,
      @Nullable Long projectId,
      @Nullable Long boardId,
      Instant createdAt,
      @Nullable Instant lastUsedAt,
      boolean revoked) {}
}
