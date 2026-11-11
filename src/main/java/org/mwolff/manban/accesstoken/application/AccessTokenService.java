package org.mwolff.manban.accesstoken.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.accesstoken.domain.AccessTokenArt;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltung persönlicher API-Zugriffstokens: anlegen (Klartext einmalig), auflisten, widerrufen
 * und Auflösung eingehender {@code X-Kanban-Token}-Header.
 *
 * <p>Ein Token kann optional an ein Projekt + Board gebunden werden (#44): damit adressiert die
 * Kanban-Compat-API (#45) genau dieses Board, ohne dass der Client eine Board-ID mitschickt.
 */
// PMD.CouplingBetweenObjects: Mit Issue #2103 kommen Projekt-Fassade, UserLookup und die Zeile der
// Verwaltung dazu. Plan #2100 hält den Zugang der App bewusst in diesem Dienst, weil Auflösung und
// Widerruf dieselben Spalten und denselben Port benutzen; ein zweiter Dienst teilte die Regeln.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
public class AccessTokenService {

  /** Fester Name des Zugangs der App in {@code name} und {@code displayName} (Plan #2100, E12). */
  static final String APP_ZUGANG_NAME = "Desktop-App";

  private final AccessTokenRepository tokens;
  private final TokenCryptoPort crypto;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final ProjectService projects;
  private final UserLookup users;
  private final Clock clock;
  private final LastUsedStampThrottle stampThrottle;
  private final ObjectProvider<AccessTokenService> self;

  public AccessTokenService(
      AccessTokenRepository tokens,
      TokenCryptoPort crypto,
      BoardService boardService,
      PermissionChecker permissions,
      ProjectService projects,
      UserLookup users,
      Clock clock,
      LastUsedStampThrottle stampThrottle,
      ObjectProvider<AccessTokenService> self) {
    this.tokens = tokens;
    this.crypto = crypto;
    this.boardService = boardService;
    this.permissions = permissions;
    this.projects = projects;
    this.users = users;
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
      throw InvalidTokenBindingException.unvollstaendig();
    }
    requireBoardImProjekt(boardId, projectId);
    if (!permissions.hasPermission(userId, projectId, Permission.TICKET_CREATE)) {
      throw new ProjectAccessDeniedException();
    }
  }

  private void requireBoardImProjekt(long boardId, long projectId) {
    // Bewusst die Optional-Variante der Board-Fassade: ein unbekanntes Board ist hier keine
    // fehlende Ressource (404), sondern eine unschluessige Bindungsangabe (400).
    long boardProjectId =
        boardService
            .findProjectId(boardId)
            .orElseThrow(() -> InvalidTokenBindingException.boardUnbekannt(boardId));
    if (projectId != boardProjectId) {
      throw InvalidTokenBindingException.boardGehoertNichtZuProjekt(boardId, projectId);
    }
  }

  @Transactional(readOnly = true)
  public List<AccessTokenView> list(long userId) {
    return tokens.findByUserId(userId).stream()
        .filter(t -> t.art() != AccessTokenArt.APP)
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
   * Legt den Zugang der App für das Board an (Plan #2100, E5, E6, E12); Inhaber ist der Aufrufer,
   * der Klartext steht GENAU EINMAL in der Antwort.
   *
   * <p>Bewusst ohne umschließende Transaktion: Verliert der Aufruf das Rennen um den eindeutigen
   * Index, ist seine Transaktion in PostgreSQL unbrauchbar. Der Inhaber für die Meldung lässt sich
   * erst nach ihrem Rollback nachschlagen.
   *
   * @throws ProjectAccessDeniedException Aufrufer ist Mitglied, aber weder Admin noch Owner, oder
   *     sein Konto ist gesperrt (403)
   * @throws org.mwolff.manban.project.application.ProjectNotFoundException Aufrufer hat keine echte
   *     Mitgliedschaft, auch als Plattform-Admin (404)
   * @throws InvalidTokenBindingException Board unbekannt oder in einem anderen Projekt (400)
   * @throws AppZugangBestehtException am Board besteht schon ein wirksamer Zugang (409)
   */
  public CreatedAccessToken appZugangAnlegen(long userId, long projectId, long boardId) {
    try {
      return self.getObject().appZugangAnlegenInTransaktion(userId, projectId, boardId);
    } catch (DataIntegrityViolationException verletzung) {
      AccessToken bestehend = tokens.findWirksamenAppZugang(boardId).orElseThrow(() -> verletzung);
      AppZugangBestehtException konflikt = bestehtSchon(bestehend);
      konflikt.initCause(verletzung);
      throw konflikt;
    }
  }

  /**
   * Die Transaktion von {@link #appZugangAnlegen}; öffentlich nur für den Spring-Proxy. Die
   * Rechteprüfung läuft unter der Ausgabesperre des Aufrufers (Issue #2122).
   */
  @Transactional
  public CreatedAccessToken appZugangAnlegenInTransaktion(
      long userId, long projectId, long boardId) {
    requireAusgabeBerechtigt(userId, projectId);
    requireBoardImProjekt(boardId, projectId);
    tokens
        .findWirksamenAppZugang(boardId)
        .ifPresent(
            bestehend -> {
              throw bestehtSchon(bestehend);
            });
    return speichereAppZugang(userId, projectId, boardId);
  }

  /**
   * Ersetzt den Zugang der App durch einen neuen; der Klartext steht GENAU EINMAL in der Antwort.
   * Widerruf der alten und Anlage der neuen Zeile bilden eine Transaktion (Plan #2100, E7).
   *
   * <p>Der bedingte Widerruf serialisiert gleichzeitiges Erneuern und Widerrufen: Wer zweite an der
   * Zeilensperre ankommt, findet den Zugang widerrufen und legt nichts an. Gegen Rollenverlust und
   * Kontosperre serialisiert die Ausgabesperre des Aufrufers; unter ihr wird auch die Rolle des
   * Inhabers erneut geprüft (Issue #2122).
   *
   * @throws AccessTokenNotFoundException Zugang unbekannt oder schon widerrufen (404)
   * @throws ProjectAccessDeniedException Aufrufer ist Mitglied des Projekts, aber nicht Inhaber,
   *     nicht mehr Admin oder Owner, oder sein Konto ist gesperrt (403)
   * @throws org.mwolff.manban.project.application.ProjectNotFoundException Aufrufer hat keine echte
   *     Mitgliedschaft (404, kein Existenz-Leak)
   */
  @Transactional
  public CreatedAccessToken appZugangErneuern(long userId, long tokenId) {
    tokens.sperreAppZugangAusgabe(userId);
    AccessToken alt = requireAppZugang(tokenId);
    long projectId = gebunden(alt.projectId());
    permissions.requireEchterOwnerOderAdmin(userId, projectId);
    if (alt.userId() != userId) {
      throw new ProjectAccessDeniedException();
    }
    requireAktivesKonto(userId);
    if (!tokens.markRevokedIfActive(tokenId)) {
      throw new AccessTokenNotFoundException();
    }
    return speichereAppZugang(userId, projectId, gebunden(alt.boardId()));
  }

  /**
   * Widerruft den Zugang der App endgültig: der Inhaber oder ein echter Admin oder Owner des
   * Projekts. Danach kann für das Board sofort ein neuer entstehen.
   *
   * @throws AccessTokenNotFoundException Zugang unbekannt (404)
   * @throws ProjectAccessDeniedException Aufrufer ist Mitglied, aber weder Inhaber noch Admin oder
   *     Owner (403)
   * @throws org.mwolff.manban.project.application.ProjectNotFoundException Aufrufer ist weder
   *     Inhaber noch Mitglied (404)
   */
  @Transactional
  public void appZugangWiderrufen(long userId, long tokenId) {
    AccessToken zugang = requireAppZugang(tokenId);
    if (zugang.userId() != userId) {
      permissions.requireEchterOwnerOderAdmin(userId, gebunden(zugang.projectId()));
    }
    tokens.markRevoked(tokenId);
  }

  /**
   * Je Board aller Projekte, in denen der Aufrufer echter Admin oder Owner ist, eine Zeile (Plan
   * #2100, E8); ohne solche Projekte ist die Liste leer.
   */
  @Transactional(readOnly = true)
  public List<AppZugangZeile> appZugaengeAuflisten(long userId) {
    List<ProjektBoard> boards =
        projects.listAccessible(userId).stream()
            .filter(p -> permissions.istEchterOwnerOderAdmin(userId, p.id()))
            .flatMap(
                p ->
                    boardService.listBoards(userId, p.id()).stream()
                        .map(b -> new ProjektBoard(p, b)))
            .toList();
    Map<Long, AccessToken> zugaenge =
        tokens.findWirksameAppZugaenge(boards.stream().map(pb -> pb.board().id()).toList()).stream()
            .collect(Collectors.toMap(z -> gebunden(z.boardId()), Function.identity()));
    return boards.stream().map(pb -> zeile(userId, pb, zugaenge.get(pb.board().id()))).toList();
  }

  private AppZugangZeile zeile(long userId, ProjektBoard pb, @Nullable AccessToken zugang) {
    if (zugang == null) {
      return new AppZugangZeile(
          null,
          pb.projekt().id(),
          pb.projekt().name(),
          pb.board().id(),
          pb.board().name(),
          null,
          false,
          null,
          null);
    }
    return new AppZugangZeile(
        zugang.requireId(),
        pb.projekt().id(),
        pb.projekt().name(),
        pb.board().id(),
        pb.board().name(),
        inhaberName(zugang).orElse(null),
        zugang.userId() == userId,
        zugang.createdAt(),
        zugang.lastUsedAt());
  }

  /**
   * Nimmt die Ausgabesperre des Aufrufers und prüft erst danach Rolle und Konto (Issue #2122). Jede
   * Abfrage danach sieht damit einen Rollenverlust oder eine Kontosperre, die vor der Sperre
   * committet hat; eine spätere wartet auf das Ende dieser Transaktion.
   */
  private void requireAusgabeBerechtigt(long userId, long projectId) {
    tokens.sperreAppZugangAusgabe(userId);
    permissions.requireEchterOwnerOderAdmin(userId, projectId);
    requireAktivesKonto(userId);
  }

  /**
   * Ein gesperrtes Konto bekommt keinen Zugang (403). Der Filter der Sitzung weist es sonst schon
   * ab; hier geht es um die Anfrage, die vor der Sperre durch den Filter kam (Issue #2122).
   */
  private void requireAktivesKonto(long userId) {
    if (!users.istAktiv(userId)) {
      throw new ProjectAccessDeniedException();
    }
  }

  /** Ein Zugang der App; ein gewöhnliches Token gibt es unter diesem Weg nicht (404). */
  private AccessToken requireAppZugang(long tokenId) {
    return tokens
        .findById(tokenId)
        .filter(t -> t.art() == AccessTokenArt.APP)
        .orElseThrow(AccessTokenNotFoundException::new);
  }

  private CreatedAccessToken speichereAppZugang(long userId, long projectId, long boardId) {
    GeneratedToken generated = crypto.generate();
    AccessToken saved =
        tokens.save(
            new AccessToken(
                null,
                userId,
                projectId,
                boardId,
                APP_ZUGANG_NAME,
                generated.hash(),
                APP_ZUGANG_NAME,
                clock.instant(),
                null,
                false,
                AccessTokenArt.APP));
    return new CreatedAccessToken(saved.requireId(), saved.name(), generated.plaintext());
  }

  private AppZugangBestehtException bestehtSchon(AccessToken bestehend) {
    return new AppZugangBestehtException(inhaberName(bestehend).orElse("#" + bestehend.userId()));
  }

  private Optional<String> inhaberName(AccessToken zugang) {
    return users.findById(zugang.userId()).map(UserSummary::displayName);
  }

  /** Ein Zugang der App ist immer an Projekt und Board gebunden (V70, {@code art = 'APP'}). */
  private static long gebunden(@Nullable Long wert) {
    if (wert == null) {
      throw new IllegalStateException("Zugang der App ohne Bindung");
    }
    return wert;
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
   *
   * <p>Ein Zugang der App wirkt nur, solange sein Inhaber Admin oder Owner des Projekts ist; sonst
   * ist die Auflösung leer und der Zugang widerrufen (Issue #2104).
   */
  public Optional<KanbanPrincipal> resolveBinding(String plaintext) {
    return tokens
        .findByTokenHash(crypto.hash(plaintext))
        .filter(t -> !t.revoked())
        .filter(this::inhaberTraegtDenZugang)
        .map(
            t -> {
              stampLastUsedIfDue(t.requireId());
              return new KanbanPrincipal(
                  t.userId(),
                  t.requireId(),
                  t.projectId(),
                  t.boardId(),
                  t.displayName(),
                  t.art() == AccessTokenArt.APP);
            });
  }

  /**
   * Ob der Inhaber eines Zugangs der App noch Admin oder Owner ist (Plan #2100, E4); bei Verlust
   * wird der Zugang endgültig widerrufen. Fängt einen Schreibweg auf, der das Ereignis vergisst.
   * Gewöhnliche Token kommen ohne jede Abfrage durch.
   *
   * <p>Prüfung und Widerruf holen ihre Verbindung nacheinander, weil die Auflösung ohne Transaktion
   * läuft (siehe {@link #resolveBinding}).
   */
  private boolean inhaberTraegtDenZugang(AccessToken token) {
    if (token.art() != AccessTokenArt.APP
        || permissions.istEchterOwnerOderAdmin(token.userId(), gebunden(token.projectId()))) {
      return true;
    }
    tokens.markRevokedInOwnTransaction(token.requireId());
    return false;
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
  @Schema(description = "Ein neu angelegtes Token samt Klartext.")
  public record CreatedAccessToken(
      @Schema(description = "Interne ID des Tokens.", example = "12") Long id,
      @Schema(description = "Name.", example = "Laptop") String name,
      @Schema(
              description =
                  "Klartext für den Header X-Kanban-Token. Steht nur in dieser Antwort; der"
                      + " Leitstand speichert ihn nicht.")
          String plaintext) {}

  /**
   * Listen-/Detaildarstellung ohne Hash und ohne Klartext; inkl. optionaler Bindung. {@code
   * lastUsedAt} ist minutengenau — siehe {@link #resolveBinding} (Issue #997).
   */
  @Schema(description = "Ein Token, ohne Klartext.")
  public record AccessTokenView(
      @Schema(description = "Interne ID des Tokens.", example = "12") Long id,
      @Schema(description = "Name.", example = "Laptop") String name,
      @Schema(
              description = "Projekt des gebundenen Boards; null bei einem ungebundenen Token.",
              example = "1")
          @Nullable Long projectId,
      @Schema(description = "Gebundenes Board; null bei einem ungebundenen Token.", example = "3")
          @Nullable Long boardId,
      @Schema(description = "Zeitpunkt der Anlage.") Instant createdAt,
      @Schema(
              description =
                  "Letzte Nutzung, minutengenau; null, solange das Token nie benutzt wurde.")
          @Nullable Instant lastUsedAt,
      @Schema(description = "Ob das Token widerrufen ist.", example = "false") boolean revoked) {}

  /** Ein Board der Liste mit seinem Projekt; nur innerhalb von {@link #appZugaengeAuflisten}. */
  private record ProjektBoard(AccessibleProject projekt, BoardView board) {}

  /**
   * Eine Zeile der Verwaltung: ein Board und, falls vorhanden, sein wirksamer Zugang der App. Ohne
   * Hash und ohne Klartext; {@code lastUsedAt} ist minutengenau.
   */
  @Schema(description = "Ein Board mit seinem Zugang der App, ohne Klartext.")
  public record AppZugangZeile(
      @Schema(description = "ID des Zugangs; null, solange das Board keinen hat.", example = "12")
          @Nullable Long tokenId,
      @Schema(description = "Interne ID des Projekts.", example = "1") Long projectId,
      @Schema(description = "Name des Projekts.", example = "Alpha") String projectName,
      @Schema(description = "Interne ID des Boards.", example = "3") Long boardId,
      @Schema(description = "Name des Boards.", example = "Entwicklung") String boardName,
      @Schema(description = "Anzeigename des Inhabers; null ohne Zugang.", example = "Erika Muster")
          @Nullable String inhaberName,
      @Schema(description = "Ob der Aufrufer Inhaber ist und erneuern darf.", example = "true")
          boolean eigener,
      @Schema(description = "Zeitpunkt der Anlage; null ohne Zugang.") @Nullable Instant createdAt,
      @Schema(description = "Letzte Nutzung, minutengenau; null, wenn nie benutzt.")
          @Nullable Instant lastUsedAt) {}
}
