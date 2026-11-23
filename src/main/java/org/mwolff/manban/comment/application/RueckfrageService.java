package org.mwolff.manban.comment.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.comment.application.CommentService.CommentView;
import org.mwolff.manban.comment.domain.Antwort;
import org.mwolff.manban.comment.domain.Rueckfrage;
import org.mwolff.manban.comment.domain.Rueckfrage.Art;
import org.mwolff.manban.comment.domain.Rueckfrage.Eintrag;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.EingegangeneAntwort;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.FrageStand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.FrageZustand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.RueckfrageZustand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.Stand;
import org.mwolff.manban.comment.domain.Vorschau;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rückfragen der KI lesen und beantworten (Issue #2210, Plan #2206 E8, E9, E11). Ausgewertet wird
 * allein hier, damit Oberfläche, App und „wartet auf dich“ denselben Stand sehen.
 *
 * <p>Lesen verlangt die Mitgliedschaft, Antworten {@code COMMENT_CREATE}. Die Antwort geht als
 * Kommentar über {@link CommentService#create(long, long, String)} an die Karte und durchläuft
 * damit dessen Prüfungen, auch die Sperre für Antworten über einen gewöhnlichen Zugang (E7). Ihr
 * Kopf folgt der Sprache der Rückfrage (E9). Freigeben an einer überholten Rückfrage endet mit 409
 * (E11), ebenso Freigeben eines anderen als des angezeigten Stands (Issue #2253).
 */
@Service
public class RueckfrageService {

  private final CommentService comments;
  private final CardService cards;
  private final PermissionChecker permissions;
  private final JuengsteVorschau vorschauen;

  public RueckfrageService(
      CommentService comments,
      CardService cards,
      PermissionChecker permissions,
      JuengsteVorschau vorschauen) {
    this.comments = comments;
    this.cards = cards;
    this.permissions = permissions;
    this.vorschauen = vorschauen;
  }

  /** Die Rückfragen der Karte mit ihrem Stand, in der Reihenfolge der Kommentare. */
  @Transactional(readOnly = true)
  public List<RueckfrageView> liste(long userId, long cardId) {
    List<CommentView> kommentare = comments.list(userId, cardId);
    return staende(kommentare, vorschauen.juengste(cards.requireProjectId(cardId)));
  }

  /**
   * Die Rückfragen mehrerer Karten eines Projekts mit ihrem Stand, in einem Zugriff auf die
   * Kommentare (Issue #2216, Plan #2206 E14) — für „wartet auf dich“. Karten ohne Rückfrage fehlen.
   * Rechte und Sicht wie {@link CommentService#listJeKarte}.
   */
  @Transactional(readOnly = true)
  public Map<Long, List<RueckfrageView>> staendeJeKarte(
      long userId, long projectId, Collection<Long> cardIds) {
    if (cardIds.isEmpty()) {
      return Map.of();
    }
    Optional<Vorschau> juengste = vorschauen.juengste(projectId);
    Map<Long, List<RueckfrageView>> jeKarte = new LinkedHashMap<>();
    comments
        .listJeKarte(userId, projectId, cardIds)
        .forEach(
            (karte, kommentare) -> {
              List<RueckfrageView> staende = staende(kommentare, juengste);
              if (!staende.isEmpty()) {
                jeKarte.put(karte, staende);
              }
            });
    return jeKarte;
  }

  private static List<RueckfrageView> staende(
      List<CommentView> kommentare, Optional<Vorschau> juengste) {
    List<EingegangeneAntwort> antworten = eingegangen(kommentare);
    return rueckfragen(kommentare).stream()
        .map(r -> view(RueckfragenAuswertung.auswerten(r, antworten, juengste)))
        .toList();
  }

  /**
   * Beantwortet die Rückfrage und liefert ihren Stand danach.
   *
   * @throws org.mwolff.manban.project.application.FremdesBoardException wenn das Board des Projekts
   *     in einem fremden System liegt
   * @throws CommentNotFoundException wenn an der Karte keine Rückfrage mit dieser Kennung steht
   * @throws RueckfrageUeberholtException bei {@code freigeben} an einer überholten Rückfrage
   * @throws FreigabeStandAbweichendException bei {@code freigeben}, wenn der angezeigte Stand nicht
   *     der der Rückfrage ist
   * @throws UngueltigeAntwortException wenn die Antwort nicht zur Rückfrage passt
   */
  @Transactional
  public RueckfrageView antworten(
      long userId, long cardId, long kommentarId, AntwortAuftrag auftrag) {
    long projectId = cards.requireProjectId(cardId);
    permissions.require(userId, projectId, Permission.COMMENT_CREATE);
    // Bei fremdem Board antwortet der Kunde dort, nicht hier (Issue #2164, Plan #2206 E15) — vor
    // der Suche nach der Rückfrage, damit die Sperre nicht hinter einem 404 verschwindet.
    comments.pruefeSchreibbar(projectId);
    List<CommentView> kommentare = new ArrayList<>(comments.list(userId, cardId));
    Rueckfrage rueckfrage =
        rueckfragen(kommentare).stream()
            .filter(r -> r.kommentarId() == kommentarId)
            .findFirst()
            .orElseThrow(CommentNotFoundException::new);
    Optional<Vorschau> juengste = vorschauen.juengste(projectId);
    Antwort antwort = AntwortPruefung.antwort(rueckfrage, auftrag, juengste);
    kommentare.add(comments.create(userId, cardId, AntwortPruefung.kommentar(rueckfrage, antwort)));
    return view(RueckfragenAuswertung.auswerten(rueckfrage, eingegangen(kommentare), juengste));
  }

  private static List<Rueckfrage> rueckfragen(List<CommentView> kommentare) {
    return kommentare.stream()
        .flatMap(k -> Rueckfrage.lies(k.id(), k.createdAt(), k.body()).stream())
        .toList();
  }

  private static List<EingegangeneAntwort> eingegangen(List<CommentView> kommentare) {
    return kommentare.stream()
        .flatMap(
            k ->
                Antwort.lies(k.body()).stream()
                    .map(a -> new EingegangeneAntwort(a, k.createdAt(), k.vonKi(), k.zugangArt())))
        .toList();
  }

  private static RueckfrageView view(Stand stand) {
    Rueckfrage r = stand.rueckfrage();
    List<EintragView> eintraege = new ArrayList<>();
    for (int i = 0; i < r.eintraege().size(); i++) {
      Eintrag e = r.eintraege().get(i);
      FrageStand s = stand.fragen().get(i);
      eintraege.add(
          new EintragView(
              e.nr(),
              e.art(),
              e.frage(),
              e.empfehlung(),
              e.grund(),
              e.einarbeitung(),
              e.optionen().stream()
                  .map(o -> new OptionView(o.text(), o.hinweis(), o.empfohlen()))
                  .toList(),
              s.zustand(),
              s.wahl(),
              s.text(),
              s.empfehlung()));
    }
    return new RueckfrageView(
        r.kommentarId(), r.angelegtAm(), stand.zustand(), r.vorschau(), r.commit(), eintraege);
  }

  /**
   * Was der Kunde antwortet.
   *
   * @param ja alle Empfehlungen übernehmen; {@code antworten} nennt dann nur Abweichungen
   * @param antworten die Einzelantworten; {@code null} wie leer
   * @param weg Nummer des gewählten Wegs; {@code null} ohne
   * @param freigeben ob der Stand der Rückfrage freigegeben wird
   * @param commit der Stand, den der Mensch angezeigt bekam und mit {@code freigeben} bestätigt;
   *     weicht er vom Stand der Rückfrage ab, endet die Freigabe mit 409 (Issue #2253)
   */
  public record AntwortAuftrag(
      boolean ja,
      @Nullable List<EinzelAuftrag> antworten,
      @Nullable Integer weg,
      boolean freigeben,
      @Nullable String commit) {

    List<EinzelAuftrag> antwortenOderLeer() {
      return antworten == null ? List.of() : antworten;
    }
  }

  /**
   * Die Antwort auf eine Frage: genau eines von {@code wahl}, {@code text} und {@code
   * zurueckstellen}.
   *
   * @param nr Nummer des Eintrags
   * @param wahl Nummer der gewählten Möglichkeit, ab 1
   * @param text eigene Antwort
   * @param zurueckstellen Frage zurückstellen („Später“)
   */
  public record EinzelAuftrag(
      int nr, @Nullable Integer wahl, @Nullable String text, @Nullable Boolean zurueckstellen) {}

  /** Eine Rückfrage mit ihrem Stand. */
  @Schema(description = "Eine Rückfrage der KI an einer Karte mit ihrem Stand.")
  public record RueckfrageView(
      @Schema(description = "Interne ID des Kommentars der Rückfrage.", example = "455")
          long kommentarId,
      @Schema(description = "Zeitpunkt der Anlage der Rückfrage.") Instant angelegtAm,
      @Schema(
              description =
                  "Stand: OFFEN, BEANTWORTET oder UEBERHOLT (eine neuere Vorschau ist gemeldet,"
                      + " die Freigabe gilt nicht mehr).",
              example = "OFFEN")
          RueckfrageZustand zustand,
      @Schema(
              description = "Adresse der Vorschau bei einer Freigabe-Rückfrage; sonst null.",
              example = "https://vorschau.example.org/42")
          @Nullable String vorschau,
      @Schema(
              description = "Stand, den die Rückfrage zur Freigabe stellt; sonst null.",
              example = "3f2a9c1")
          @Nullable String commit,
      @Schema(description = "Die Fragen und Wege mit ihrem Stand.") List<EintragView> eintraege) {}

  /** Ein Eintrag einer Rückfrage mit seinem Stand. */
  @Schema(description = "Eine Frage oder ein Weg einer Rückfrage mit ihrem Stand.")
  public record EintragView(
      @Schema(description = "Nummer des Eintrags.", example = "1") int nr,
      @Schema(description = "FRAGE oder WEG.", example = "FRAGE") Art art,
      @Schema(description = "Die Frage.", example = "Welche Farbe soll der Knopf haben?")
          String frage,
      @Schema(description = "Die Empfehlung der KI.", example = "Blau") String empfehlung,
      @Schema(description = "Grund der Empfehlung.", example = "Passt zum Logo.") String grund,
      @Schema(description = "Was die KI mit der Empfehlung einarbeitet.", example = "Knopf blau")
          String einarbeitung,
      @Schema(description = "Die angebotenen Möglichkeiten; Nummer ab 1 in dieser Reihenfolge.")
          List<OptionView> optionen,
      @Schema(description = "OFFEN, BEANTWORTET oder ZURUECKGESTELLT.", example = "OFFEN")
          FrageZustand zustand,
      @Schema(description = "Nummer der gewählten Möglichkeit; null ohne.", example = "2")
          @Nullable Integer wahl,
      @Schema(description = "Eigene Antwort; null ohne.", example = "Lieber grün.")
          @Nullable String text,
      @Schema(description = "Ob die Empfehlung übernommen wurde.", example = "false")
          boolean empfehlungUebernommen) {}

  /** Eine angebotene Möglichkeit. */
  @Schema(description = "Eine angebotene Möglichkeit einer Frage.")
  public record OptionView(
      @Schema(description = "Text der Möglichkeit.", example = "Blau") String text,
      @Schema(description = "Hinweis dazu; null ohne.", example = "ruhig") @Nullable String hinweis,
      @Schema(description = "Ob die KI sie empfiehlt.", example = "true") boolean empfohlen) {}
}
