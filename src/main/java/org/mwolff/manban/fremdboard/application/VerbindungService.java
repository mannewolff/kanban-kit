package org.mwolff.manban.fremdboard.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.card.application.FremdKartenBestand;
import org.mwolff.manban.card.application.FremdKartenBestand.KartenBestand;
import org.mwolff.manban.fremdboard.domain.FremdAbrufException;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.FremdBoardQuelle;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Owner bindet das Board eines Projekts an ein fremdes System, prüft die Verbindung und
 * hinterlegt den Zugang neu (Issue #2166, Plan #2152 A2, A5, A13, A14, A18). Lösen gibt es nicht.
 *
 * <p>Einrichten geht nur ohne jede Zeile in {@code card} des Projekts — aktiv, archiviert, im
 * Papierkorb oder Vorhaben (A2), weil die Kartennummern danach aus dem fremden System kommen. Das
 * Projekt darf höchstens ein aktives Board haben: Gibt es keines, entsteht eines mit den fünf
 * Standardspalten, sonst übernimmt die Verbindung das vorhandene, leere Board als Spiegel-Board
 * (A18). Ein daran gebundener Zugang des Kits ({@code X-Kanban-Token}) zeigt damit ohne weiteres
 * auf das Spiegel-Board; einen anderen gebundenen Zugang kann es nicht geben, weil die Bindung mit
 * ihrem Board verschwindet.
 *
 * <p>Vor dem Speichern prüft ein Probeabruf mit dem frisch verschlüsselten Zugang, ob das fremde
 * Board sich lesen lässt. kanban-kit schreibt nie in das fremde System (A5).
 */
@Service
public class VerbindungService {

  /** Name des Spiegel-Boards, wenn das Projekt keines hat — wie bei der Projektanlage. */
  static final String SPIEGEL_BOARD = "default";

  private final PermissionChecker permissions;
  private final VerbindungRepository verbindungen;
  private final FremdKartenBestand karten;
  private final BoardService boards;
  private final ZugangsSchutz schutz;
  private final AdressPruefung adressen;
  private final List<FremdBoardQuelle> quellen;
  private final Clock clock;

  public VerbindungService(
      PermissionChecker permissions,
      VerbindungRepository verbindungen,
      FremdKartenBestand karten,
      BoardService boards,
      ZugangsSchutz schutz,
      AdressPruefung adressen,
      List<FremdBoardQuelle> quellen,
      Clock clock) {
    this.permissions = permissions;
    this.verbindungen = verbindungen;
    this.karten = karten;
    this.boards = boards;
    this.schutz = schutz;
    this.adressen = adressen;
    this.quellen = List.copyOf(quellen);
    this.clock = clock;
  }

  /**
   * Die Verbindung des Projekts samt der Prüfung, ob ihr Zugang sich entschlüsseln lässt.
   *
   * @throws org.mwolff.manban.project.application.ProjectAccessDeniedException wenn der Aufrufer
   *     nicht Owner ist (403)
   */
  @Transactional(readOnly = true)
  public VerbindungAnsicht lesen(long userId, long projectId) {
    permissions.requireOwner(userId, projectId);
    return verbindungen
        .finde(projectId)
        .map(this::ansicht)
        .orElseGet(() -> VerbindungAnsicht.ohne(schutz.verfuegbar()));
  }

  /**
   * Die knappe Auskunft für jedes Mitglied (Issue #2223, Plan #2221 E1, E3, E6). Die Mitgliedschaft
   * wird vor jedem Lesen der Verbindung geprüft: Ein Nichtmitglied erfährt so nicht einmal, ob das
   * Board in einem fremden System liegt.
   *
   * @throws org.mwolff.manban.project.application.ProjectNotFoundException wenn der Aufrufer weder
   *     Mitglied noch Plattform-Admin ist (404)
   */
  @Transactional(readOnly = true)
  public FremdBoardStand stand(long userId, long projectId) {
    permissions.requireMembership(userId, projectId);
    return verbindungen.stand(projectId).orElseGet(FremdBoardStand::ohne);
  }

  /**
   * Richtet die Verbindung ein oder ändert sie. Beim Ändern bleiben Art, Adresse und Projektpfad
   * fest; ein angegebener Zugang ersetzt den alten, Kit-Konto und Statuslabels werden übernommen.
   *
   * @throws AnbindenAbgelehntException wenn das Projekt Karten oder weitere Boards hat, kein
   *     Schlüssel gesetzt ist oder Adresse oder Pfad einer bestehenden Verbindung wechseln (409)
   * @throws VerbindungUngueltigException wenn der Zugang fehlt, die Adresse nicht erlaubt ist oder
   *     sich das fremde Board nicht lesen lässt (400)
   */
  @Transactional
  public VerbindungAnsicht speichern(long userId, long projectId, VerbindungEingabe eingabe) {
    permissions.requireOwner(userId, projectId);
    Optional<Verbindung> bestehend = verbindungen.finde(projectId);
    Verbindung verbindung =
        bestehend.isPresent()
            ? geaendert(bestehend.get(), eingabe)
            : eingerichtet(userId, projectId, eingabe);
    verbindungen.speichere(verbindung);
    return ansicht(verbindung);
  }

  private Verbindung eingerichtet(long userId, long projectId, VerbindungEingabe eingabe) {
    KartenBestand bestand = karten.bestand(projectId);
    if (!bestand.leer()) {
      throw AnbindenAbgelehntException.hatKarten(
          bestand.aufBoards(), bestand.archiviert(), bestand.papierkorb(), bestand.vorhaben());
    }
    Optional<BoardView> vorhanden = einzigesBoard(userId, projectId);
    String zugang = eingabe.zugang();
    if (zugang == null) {
      throw VerbindungUngueltigException.zugangFehlt();
    }
    String verschluesselt = geprueft(projectId, eingabe, zugang);
    long boardId =
        vorhanden.isPresent()
            ? vorhanden.get().id()
            : boards.createBoard(userId, projectId, SPIEGEL_BOARD).id();
    return new Verbindung(
        projectId,
        eingabe.art(),
        eingabe.basisUrl(),
        eingabe.projektPfad(),
        verschluesselt,
        eingabe.kitKonto(),
        eingabe.statuslabels(),
        boardId,
        clock.instant(),
        null,
        null,
        null);
  }

  /**
   * Das Board, das Spiegel-Board wird; leer, wenn das Projekt keines hat und eines entstehen muss.
   */
  private Optional<BoardView> einzigesBoard(long userId, long projectId) {
    List<BoardView> aktive = boards.listBoards(userId, projectId);
    List<BoardView> archivierte = boards.listArchivedBoards(userId, projectId);
    if (aktive.isEmpty() && archivierte.isEmpty()) {
      return Optional.empty();
    }
    if (aktive.size() != 1 || !archivierte.isEmpty()) {
      throw AnbindenAbgelehntException.weitereBoards(
          Stream.concat(aktive.stream(), archivierte.stream())
              .map(BoardView::name)
              .collect(Collectors.joining(", ")));
    }
    BoardView board = aktive.getFirst();
    List<String> fehlend = karten.fehlendeStatus(board.id());
    if (!fehlend.isEmpty()) {
      throw AnbindenAbgelehntException.spaltenFehlen(board.name(), String.join(", ", fehlend));
    }
    return Optional.of(board);
  }

  private Verbindung geaendert(Verbindung alt, VerbindungEingabe eingabe) {
    if (alt.art() != eingabe.art()
        || !alt.basisUrl().equals(eingabe.basisUrl())
        || !alt.projektPfad().equals(eingabe.projektPfad())) {
      throw AnbindenAbgelehntException.loesenGesperrt();
    }
    String zugang = eingabe.zugang();
    String verschluesselt =
        zugang == null ? alt.zugangVerschluesselt() : geprueft(alt.projectId(), eingabe, zugang);
    return new Verbindung(
        alt.projectId(),
        alt.art(),
        alt.basisUrl(),
        alt.projektPfad(),
        verschluesselt,
        eingabe.kitKonto(),
        eingabe.statuslabels(),
        alt.boardId(),
        alt.angebundenAm(),
        alt.letzterAbgleichAm(),
        alt.letzterVollabgleichAm(),
        alt.letzterFehler());
  }

  /**
   * Prüft Schlüssel und Adresse, verschlüsselt den Zugang und liest damit einmal beim fremden
   * System; liefert den verschlüsselten Zugang.
   */
  private String geprueft(long projectId, VerbindungEingabe eingabe, String zugang) {
    if (!schutz.verfuegbar()) {
      throw AnbindenAbgelehntException.keinSchluessel();
    }
    adressen
        .ablehnung(eingabe.basisUrl())
        .ifPresent(
            grund -> {
              throw VerbindungUngueltigException.adresseAbgelehnt(grund);
            });
    String verschluesselt = schutz.verschluesseln(zugang);
    Verbindung probe =
        new Verbindung(
            projectId,
            eingabe.art(),
            eingabe.basisUrl(),
            eingabe.projektPfad(),
            verschluesselt,
            eingabe.kitKonto(),
            eingabe.statuslabels(),
            0L,
            clock.instant(),
            null,
            null,
            null);
    try {
      // Was sich ab jetzt geändert hat, ist leer — der Abruf prüft nur Adresse, Pfad und Zugang.
      quelle(eingabe.art()).geaenderteKarten(probe, clock.instant());
    } catch (FremdAbrufException e) {
      throw VerbindungUngueltigException.nichtLesbar(String.valueOf(e.getMessage()), e);
    }
    return verschluesselt;
  }

  private FremdBoardQuelle quelle(FremdBoardArt art) {
    return quellen.stream()
        .filter(q -> q.art() == art)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Keine Quelle für " + art));
  }

  private VerbindungAnsicht ansicht(Verbindung verbindung) {
    return new VerbindungAnsicht(
        true,
        schutz.verfuegbar(),
        verbindung.art(),
        verbindung.basisUrl(),
        verbindung.projektPfad(),
        verbindung.kitKonto(),
        verbindung.statuslabels(),
        verbindung.angebundenAm(),
        verbindung.letzterAbgleichAm(),
        verbindung.letzterFehler(),
        schutz.lesbar(verbindung.zugangVerschluesselt()));
  }
}
