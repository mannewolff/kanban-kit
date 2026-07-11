package org.mwolff.manban.accesstoken.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltung persönlicher API-Zugriffstokens: anlegen (Klartext einmalig),
 * auflisten, widerrufen und Auflösung eingehender {@code X-Kanban-Token}-Header.
 *
 * <p>Ein Token kann optional an ein Projekt + Board gebunden werden (#44): damit
 * adressiert die Kanban-Compat-API (#45) genau dieses Board, ohne dass der Client
 * eine Board-ID mitschickt.
 */
@Service
public class AccessTokenService {

    private final AccessTokenRepository tokens;
    private final TokenCryptoPort crypto;
    private final BoardRepository boards;
    private final PermissionChecker permissions;
    private final Clock clock;

    public AccessTokenService(AccessTokenRepository tokens, TokenCryptoPort crypto,
                              BoardRepository boards, PermissionChecker permissions, Clock clock) {
        this.tokens = tokens;
        this.crypto = crypto;
        this.boards = boards;
        this.permissions = permissions;
        this.clock = clock;
    }

    /**
     * Legt ein Token an und gibt den Klartext GENAU EINMAL zurück. Sind {@code projectId}
     * und {@code boardId} gesetzt, wird das Token an dieses Board gebunden (beide Werte
     * müssen zusammen gesetzt sein und der Nutzer auf dem Board arbeiten dürfen).
     *
     * @throws InvalidTokenBindingException  Bindung unschlüssig: nur eines gesetzt, Board
     *                                       unbekannt oder Board gehört nicht zum Projekt (400)
     * @throws ProjectAccessDeniedException  Nutzer darf auf dem gebundenen Board nicht arbeiten (403)
     */
    @Transactional
    public CreatedAccessToken create(long userId, String name, Long projectId, Long boardId) {
        validateBinding(userId, projectId, boardId);
        GeneratedToken generated = crypto.generate();
        AccessToken saved = tokens.save(new AccessToken(
                null, userId, projectId, boardId, name, generated.hash(), name, clock.instant(), null, false));
        return new CreatedAccessToken(saved.id(), saved.name(), generated.plaintext());
    }

    /** Prüft die optionale Projekt-/Board-Bindung eines neu anzulegenden Tokens. */
    private void validateBinding(long userId, Long projectId, Long boardId) {
        if (projectId == null && boardId == null) {
            return; // ungebundenes Token
        }
        if (projectId == null || boardId == null) {
            throw new InvalidTokenBindingException(
                    "projectId und boardId müssen zusammen gesetzt sein (oder beide leer)");
        }
        Board board = boards.findById(boardId)
                .orElseThrow(() -> new InvalidTokenBindingException("Board " + boardId + " unbekannt"));
        if (!projectId.equals(board.projectId())) {
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
                .map(t -> new AccessTokenView(
                        t.id(), t.name(), t.projectId(), t.boardId(), t.createdAt(), t.lastUsedAt(), t.revoked()))
                .toList();
    }

    @Transactional
    public void revoke(long userId, long tokenId) {
        AccessToken token = tokens.findById(tokenId)
                .filter(t -> t.userId() == userId)
                .orElseThrow(AccessTokenNotFoundException::new);
        if (!token.revoked()) {
            tokens.save(token.asRevoked());
        }
    }

    /**
     * Löst einen eingehenden Klartext-Header zum vollständigen Principal auf (inkl. optionaler
     * Board-Bindung); leer bei unbekannt/widerrufen. Aktualisiert {@code lastUsedAt}.
     */
    @Transactional
    public Optional<KanbanPrincipal> resolveBinding(String plaintext) {
        return tokens.findByTokenHash(crypto.hash(plaintext))
                .filter(t -> !t.revoked())
                .map(t -> {
                    tokens.save(t.withLastUsedAt(clock.instant()));
                    return new KanbanPrincipal(t.userId(), t.id(), t.projectId(), t.boardId());
                });
    }

    /** Löst einen eingehenden Klartext-Header zur User-ID auf; leer bei unbekannt/widerrufen. */
    @Transactional
    public OptionalLong resolve(String plaintext) {
        return resolveBinding(plaintext)
                .map(p -> OptionalLong.of(p.userId()))
                .orElseGet(OptionalLong::empty);
    }

    /** Ergebnis der Erstellung — enthält den einmalig sichtbaren Klartext. */
    public record CreatedAccessToken(Long id, String name, String plaintext) {
    }

    /** Listen-/Detaildarstellung ohne Hash und ohne Klartext; inkl. optionaler Bindung. */
    public record AccessTokenView(Long id, String name, Long projectId, Long boardId,
                                  Instant createdAt, Instant lastUsedAt, boolean revoked) {
    }
}
