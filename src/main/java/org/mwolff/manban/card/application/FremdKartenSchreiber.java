package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Arbeitspaket;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.BoardQuelle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der einzige Schreibweg für gespiegelte Karten eines fremden Boards (Issue #2162, Plan #2152 A1,
 * A2, A4, A10, A11): Der Spiegel legt Karten mit der Nummer des fremden Systems an, aktualisiert
 * sie, markiert sie als gelöscht, bucht Spalten- und Statuswechsel mit dem Zeitpunkt des fremden
 * Systems und setzt und entfernt Labels.
 *
 * <p>Jede Methode umgeht die Schreibsperre für fremde Boards ausdrücklich und nur hier — und
 * umgekehrt schreibt sie nur in ein Projekt, dessen Board in einem fremden System liegt. Rechte
 * prüft sie nicht: Es schreibt kein Mensch, sondern der Abgleich mit dem fremden System.
 *
 * <p>Zeitpunkt (A4): Jede schreibende Methode setzt den übergebenen Zeitpunkt als {@link
 * FlussZeitpunkt} der Transaktion, damit Spaltenverlauf und Flusserfassung mit der Zeit des fremden
 * Systems stempeln. Weil die Flusserfassung je Karte und Transaktion höchstens einen Wechsel bucht,
 * läuft jeder Aufruf in einer eigenen Transaktion; der Spiegel ruft je Ereignis einmal.
 *
 * <p>Herkunft (A11): Aktivitäten und Label-Ereignisse tragen {@code TOKEN}, wenn der Benutzer im
 * fremden System das hinterlegte Kit-Konto ist, sonst {@code SESSION}, dazu den Benutzernamen als
 * externen Akteur — nie einen Benutzer von kanban-kit.
 *
 * <p>Vertraulichkeit (Issue #2200): Eine Karte, die im fremden System vertraulich ist, entfernt
 * {@link #vertraulicheEntfernen} endgültig über {@link CardArchiveService}.
 */
// PMD.CouplingBetweenObjects: Der einzige Schreibweg des Spiegels (A1) braucht jeden Schreibweg
// von card, den der Abgleich bedient — seit Issue #2200 auch das endgültige Entfernen
// vertraulicher Karten, das er an CardArchiveService abgibt, statt es ein zweites Mal zu bauen.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
public class FremdKartenSchreiber {

  /** Farbe eines Labels, das erst der Spiegel am Board anlegt. */
  static final String LABEL_FARBE = "#607d8b";

  private static final int POSITION_AM_ENDE = Integer.MAX_VALUE;

  private final CardRepository cards;
  private final BoardService boardService;
  private final CardColumnTransitionRepository transitions;
  private final CardActivityRepository activity;
  private final CardLabelEventRepository labelEvents;
  private final LabelRepository labels;
  private final CardLabelRepository cardLabels;
  private final KartenGrundlage grundlage;
  private final BoardQuelle boardQuelle;
  private final CardArchiveService archiv;

  public FremdKartenSchreiber(
      CardRepository cards,
      BoardService boardService,
      CardColumnTransitionRepository transitions,
      CardActivityRepository activity,
      CardLabelEventRepository labelEvents,
      LabelRepository labels,
      CardLabelRepository cardLabels,
      KartenGrundlage grundlage,
      BoardQuelle boardQuelle,
      CardArchiveService archiv) {
    this.cards = cards;
    this.boardService = boardService;
    this.transitions = transitions;
    this.activity = activity;
    this.labelEvents = labelEvents;
    this.labels = labels;
    this.cardLabels = cardLabels;
    this.grundlage = grundlage;
    this.boardQuelle = boardQuelle;
    this.archiv = archiv;
  }

  /**
   * Legt die Karte mit der Nummer des fremden Systems an (A2), in der Spalte ihres Status.
   *
   * @param boardId das Spiegel-Board des Projekts
   * @param eintritt beim Anbinden übernommene Karte: {@code angebunden_am}; später angelegte: ihr
   *     Anlagezeitpunkt im fremden System (A4)
   * @param akteur wer die Karte im fremden System angelegt hat
   * @return die interne ID der neuen Karte
   * @throws CardNumberConflictException wenn die Nummer im Projekt schon vergeben ist, auch an eine
   *     Karte im Papierkorb
   * @throws IllegalStateException wenn das Projekt kein fremdes Board hat
   */
  @Transactional
  public long anlegen(long boardId, FremdeKarte karte, Instant eintritt, FremderAkteur akteur) {
    long projectId = fremdesProjekt(boardId);
    FlussZeitpunkt.setze(eintritt);
    cards.lockCardNumbers(projectId);
    if (cards.isNumberTaken(projectId, karte.nummer())) {
      throw CardNumberConflictException.nummerVergeben(karte.nummer());
    }
    List<ColumnView> spalten = boardService.listColumns(boardId);
    ColumnView spalte = spalteFuer(spalten, karte.status());
    Card neu =
        new Card(
            null,
            boardId,
            spalte.id(),
            karte.nummer(),
            karte.titel().trim(),
            KartenGrundlage.normalize(karte.text()),
            cards.allocateActivePosition(spalte.id()),
            false,
            null,
            null,
            eintritt,
            eintritt,
            CardType.CARD,
            null,
            null,
            null,
            projectId,
            null,
            null,
            null,
            null,
            null,
            karte.externUrl());
    Card saved =
        cards.save(KartenStatus.inSpalte(neu, KartenStatus.bis(spalten, spalte), eintritt));
    long cardId = saved.requireId();
    transitions.open(cardId, spalte.id(), spalte.name(), eintritt);
    activity.addFremd(
        cardId,
        CardActivityType.CREATED,
        "Karte angelegt",
        eintritt,
        akteur.herkunft(),
        akteur.name(),
        null);
    grundlage.publishChanged(boardId, ActivityType.CREATED, cardId);
    return cardId;
  }

  /**
   * Übernimmt Titel, Text und Adresse aus dem fremden System. Ändert sich mit dem Titel die Art der
   * Karte, folgt ihr Status der Spalte wie bei jeder Umbenennung.
   *
   * @param zeitpunkt Änderungszeitpunkt im fremden System
   * @return {@code false}, wenn sich nichts geändert hat — dann bleibt die Karte unberührt
   * @throws CardNotFoundException wenn es die Karte nicht gibt oder sie im Papierkorb liegt
   */
  @Transactional
  public boolean aktualisieren(
      long boardId,
      int nummer,
      String titel,
      @Nullable String text,
      @Nullable String externUrl,
      Instant zeitpunkt) {
    long projectId = fremdesProjekt(boardId);
    FlussZeitpunkt.setze(zeitpunkt);
    Card vorher = karte(projectId, nummer);
    String neuerTitel = titel.trim();
    String neuerText = KartenGrundlage.normalize(text);
    if (neuerTitel.equals(vorher.title())
        && Objects.equals(neuerText, vorher.description())
        && Objects.equals(externUrl, vorher.externUrl())) {
      return false;
    }
    Card inhalt = vorher.withContent(neuerTitel, neuerText).withExternUrl(externUrl);
    Card nachher = grundlage.folgeWegBeleg(vorher, grundlage.folgeArtwechsel(vorher, inhalt));
    cards.save(nachher);
    grundlage.publishChanged(boardId, ActivityType.UPDATED, vorher.requireId());
    return true;
  }

  /**
   * Bucht einen Status- und Spaltenwechsel mit dem Zeitpunkt des fremden Systems (A4): Die Karte
   * wandert ans Ende der ersten Spalte ihres neuen Status; Status, Done-Zeitpunkt, Spaltenverlauf,
   * Aufenthalt und Verlauf folgen daraus.
   *
   * @param status Konstantenname des Status ({@code BACKLOG} … {@code DONE})
   * @return {@code false}, wenn die Karte schon in der Spalte des Status liegt — dann bleibt keine
   *     Spur
   * @throws CardNotFoundException wenn es die Karte nicht gibt oder sie im Papierkorb liegt
   */
  @Transactional
  public boolean wechseln(
      long boardId, int nummer, String status, Instant zeitpunkt, FremderAkteur akteur) {
    long projectId = fremdesProjekt(boardId);
    FlussZeitpunkt.setze(zeitpunkt);
    Card karte = karte(projectId, nummer);
    List<ColumnView> spalten = boardService.listColumns(boardId);
    ColumnView ziel = spalteFuer(spalten, status);
    if (karte.columnId().equals(ziel.id())) {
      return false;
    }
    long cardId = karte.requireId();
    cards.move(cardId, ziel.id(), POSITION_AM_ENDE);
    transitions.closeOpen(cardId, zeitpunkt);
    transitions.open(cardId, ziel.id(), ziel.name(), zeitpunkt);
    Card bewegt = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    Card gesetzt =
        KartenStatus.inSpalte(
            bewegt.withMovedToDoneAt(null), KartenStatus.bis(spalten, ziel), zeitpunkt);
    cards.save(gesetzt);
    activity.addFremd(
        cardId,
        CardActivityType.MOVED,
        "Verschoben nach " + ziel.name(),
        zeitpunkt,
        akteur.herkunft(),
        akteur.name(),
        gesetzt.status());
    grundlage.publishChanged(boardId, ActivityType.MOVED, cardId);
    return true;
  }

  /**
   * Markiert die Karte als gelöscht (A8): Sie wandert in den Papierkorb, ihre Messungen bleiben.
   * Eine schon gelöschte oder unbekannte Karte bleibt unberührt.
   *
   * @param zeitpunkt wann der Spiegel das Fehlen festgestellt hat
   */
  @Transactional
  public void alsGeloeschtMarkieren(long boardId, int nummer, Instant zeitpunkt) {
    long projectId = fremdesProjekt(boardId);
    Optional<Card> karte = cards.findByProjectIdAndNumber(projectId, nummer);
    if (karte.isEmpty()) {
      return;
    }
    FlussZeitpunkt.setze(zeitpunkt);
    long cardId = karte.get().requireId();
    cards.softDelete(cardId, zeitpunkt);
    grundlage.publishChanged(boardId, ActivityType.DELETED, cardId);
  }

  /**
   * Entfernt die Karten mit diesen Nummern endgültig, weil sie im fremden System vertraulich sind
   * (Issue #2200): Der Zugang des Spiegels darf dort mehr lesen als ein Mitglied des Projekts, und
   * eine Kopie im Papierkorb bliebe samt Beschreibung und Kommentaren für jedes Mitglied lesbar.
   * Erfasst werden Karten auf dem Board, im Archiv und im Papierkorb; eine unbekannte Nummer ist
   * kein Fehler. Anders als beim Löschen (A8) bleiben keine Messungen.
   *
   * @param nummern Nummern der vertraulichen Karten im fremden System
   * @return wie viele Karten entfernt wurden
   */
  @Transactional
  public int vertraulicheEntfernen(long boardId, List<Integer> nummern) {
    if (nummern.isEmpty()) {
      return 0;
    }
    return archiv.vertraulicheEntfernen(fremdesProjekt(boardId), boardId, nummern);
  }

  /**
   * Setzt der Karte ein Label; das Board bekommt es, wenn es den Namen noch nicht trägt. Nur ein
   * tatsächliches Hinzukommen wird als Label-Ereignis festgehalten, mit Herkunft und externem
   * Akteur (A11).
   *
   * @param zeitpunkt wann das Label im fremden System gesetzt wurde
   * @return {@code false}, wenn die Karte das Label schon trug
   */
  @Transactional
  public boolean labelSetzen(
      long boardId, int nummer, String labelName, Instant zeitpunkt, FremderAkteur akteur) {
    long projectId = fremdesProjekt(boardId);
    Card karte = karte(projectId, nummer);
    labels.insertIfAbsent(boardId, labelName, LABEL_FARBE);
    long labelId =
        label(boardId, labelName).orElseThrow(() -> new IllegalStateException(labelName));
    if (!cardLabels.addLabel(karte.requireId(), labelId)) {
      return false;
    }
    labelEvents.addFremd(karte.requireId(), labelName, zeitpunkt, akteur.herkunft(), akteur.name());
    grundlage.publishChanged(boardId, ActivityType.UPDATED, karte.requireId());
    return true;
  }

  /**
   * Nimmt der Karte ein Label ab. Wie überall in kanban-kit wird das Abnehmen nicht als Ereignis
   * festgehalten (Issue #1754); ein Label, das die Karte nicht trägt, ist kein Fehler.
   *
   * @return {@code false}, wenn die Karte das Label nicht trug
   */
  @Transactional
  public boolean labelEntfernen(long boardId, int nummer, String labelName) {
    long projectId = fremdesProjekt(boardId);
    Card karte = karte(projectId, nummer);
    Optional<Long> labelId = label(boardId, labelName);
    if (labelId.isEmpty() || !cardLabels.removeLabel(karte.requireId(), labelId.get())) {
      return false;
    }
    grundlage.publishChanged(boardId, ActivityType.UPDATED, karte.requireId());
    return true;
  }

  /**
   * Setzt die Herkunft der Karte auf die Karte mit der Nummer {@code vorfahrNummer} (Issue #2167,
   * Plan #2152 A12). Das fremde System kennt keine Herkunft; der Spiegel liest sie aus dem Text.
   * Gibt es den Vorfahren im Projekt nicht (noch nicht gespiegelt, im Papierkorb) oder entstünde
   * ein Kreis, bleibt die Karte ohne Herkunft — der nächste Abgleich versucht es wieder. {@code
   * null} nimmt die Herkunft weg: Es gilt der Text im fremden System.
   *
   * @return {@code false}, wenn die Herkunft schon so stand
   * @throws CardNotFoundException wenn es die Karte nicht gibt oder sie im Papierkorb liegt
   */
  @Transactional
  public boolean herkunftSetzen(long boardId, int nummer, @Nullable Integer vorfahrNummer) {
    long projectId = fremdesProjekt(boardId);
    Card karte = karte(projectId, nummer);
    Long vorfahr = vorfahrId(projectId, karte.requireId(), vorfahrNummer);
    if (Objects.equals(vorfahr, karte.derivedFromCardId())) {
      return false;
    }
    cards.save(karte.withDerivedFrom(vorfahr));
    grundlage.publishChanged(boardId, ActivityType.UPDATED, karte.requireId());
    return true;
  }

  private @Nullable Long vorfahrId(long projectId, long cardId, @Nullable Integer vorfahrNummer) {
    try {
      return DerivedFrom.resolve(cards, projectId, vorfahrNummer, cardId);
    } catch (InvalidDerivedFromException _) {
      return null;
    }
  }

  /**
   * Das Projekt des Boards, sofern es ein fremdes Board hat — sonst schreibt der Spiegel nicht.
   *
   * @throws IllegalStateException wenn das Projekt sein Board in kanban-kit führt
   */
  private long fremdesProjekt(long boardId) {
    long projectId = boardService.requireProjectId(boardId);
    if (!boardQuelle.istFremd(projectId)) {
      throw new IllegalStateException(
          "Der Spiegel schreibt nur in ein Projekt mit fremdem Board (Projekt " + projectId + ")");
    }
    return projectId;
  }

  private Card karte(long projectId, int nummer) {
    return cards
        .findByProjectIdAndNumber(projectId, nummer)
        .orElseThrow(CardNotFoundException::new);
  }

  private Optional<Long> label(long boardId, String name) {
    return labels.findByBoardId(boardId).stream()
        .filter(l -> l.name().equals(name))
        .map(Label::requireId)
        .findFirst();
  }

  /**
   * Die erste Spalte des Boards, deren Name den Status ergibt.
   *
   * @throws IllegalStateException wenn das Spiegel-Board keine solche Spalte hat — dann stimmt das
   *     Board nicht mit den fünf Standardspalten überein (A18)
   */
  private static ColumnView spalteFuer(List<ColumnView> spalten, String status) {
    CardStatus gesucht = CardStatus.valueOf(status);
    return spalten.stream()
        .filter(s -> Arbeitspaket.statusVonSpalte(s.name()).filter(gesucht::equals).isPresent())
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Spiegel-Board ohne Spalte für Status " + status));
  }

  /**
   * Eine Karte, wie der Spiegel sie aus dem fremden System übernimmt.
   *
   * @param nummer Nummer im fremden System (bei GitLab die {@code iid}), zugleich die Kartennummer
   * @param titel Titel samt Präfix des Kits
   * @param text Beschreibung; leer oder {@code null} heißt keine
   * @param status Konstantenname des Status ({@code BACKLOG} … {@code DONE}), bestimmt die Spalte
   * @param externUrl Adresse der Karte im fremden System
   */
  public record FremdeKarte(
      int nummer, String titel, @Nullable String text, String status, @Nullable String externUrl) {}

  /**
   * Wer im fremden System gehandelt hat (A11).
   *
   * @param name Benutzername im fremden System; {@code null}, wenn es ihn nicht festhält
   * @param kitKonto ob es das hinterlegte Kit-Konto ist
   */
  public record FremderAkteur(@Nullable String name, boolean kitKonto) {

    /**
     * Der Akteur zum Benutzernamen; Kit-Konto ist er, wenn der Name dem hinterlegten gleicht — ohne
     * Rücksicht auf Groß- und Kleinschreibung, wie Benutzernamen in GitLab.
     *
     * @param kitKonto das hinterlegte Kit-Konto; {@code null}, wenn keines hinterlegt ist
     */
    public static FremderAkteur von(@Nullable String name, @Nullable String kitKonto) {
      return new FremderAkteur(name, name != null && name.equalsIgnoreCase(kitKonto));
    }

    CardActivityOrigin herkunft() {
      return kitKonto ? CardActivityOrigin.TOKEN : CardActivityOrigin.SESSION;
    }
  }
}
