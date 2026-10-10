package org.mwolff.manban.card.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der feste Satz an Labels, die das claude-workflow-kit, die Laufsteuerung und die Stufenleiste auf
 * jedem Board erwarten, und sein Anlegen auf allen aktiven Boards der Installation (Issue #1485).
 * Beides verlangt einen Plattform-Admin.
 *
 * <p>Das Anlegen ist idempotent: Ein Label, das ein Board schon trägt, bleibt mit Farbe und
 * Zuordnungen unverändert und zählt als übersprungen — auch, wenn ein gleichzeitiger zweiter Aufruf
 * es gerade angelegt hat ({@link LabelRepository#insertIfAbsent}).
 *
 * <p>Die Namen kommen aus dem Register {@link KitNamen}: Je Sache entsteht jeder ihrer Namen als
 * eigenes Label, das englische Gegenstück direkt hinter dem deutschen (Issue #1585, Plan #1582 A3,
 * E5, E6, E13).
 */
@Service
public class StandardLabelService {

  private static final String KIT = "Kit";
  private static final String LAUF = "Lauf";
  private static final String REVIEW = "Review";
  private static final String STUFENLEISTE = "Stufenleiste";

  private static final String FARBE_KIT = "#6a1b9a";
  private static final String FARBE_LAUF = "#1565c0";
  private static final String FARBE_REVIEW = "#2e7d32";
  private static final String FARBE_STUFENLEISTE = "#ef6c00";

  private final LabelRepository labels;
  private final BoardService boardService;
  private final PlatformAdminChecker platformAdminChecker;

  /**
   * Gruppe und Farbe je Sache — Anzeigedaten, gleich unter jedem Namen der Sache (A3). Eine Sache
   * ohne Eintrag fehlte im Satz; der Test über alle Sachen fängt das.
   */
  private static final Map<Sache, Darstellung> DARSTELLUNG = darstellungAufbauen();

  /** Der Standardsatz in der Reihenfolge, in der die Oberfläche ihn zeigt. */
  private final List<StandardLabel> satz;

  /**
   * Der Konstruktor, den Spring nimmt — mit dem Register der Produktion. Bei zwei Konstruktoren
   * wählte Spring sonst nach Regel statt nach Absicht (Issue #1242).
   */
  @Autowired
  public StandardLabelService(
      LabelRepository labels,
      BoardService boardService,
      PlatformAdminChecker platformAdminChecker) {
    this(labels, boardService, platformAdminChecker, KitNamen.STANDARD);
  }

  /**
   * Mit eigenem Register — für Tests, die einen Zusatznamen einbringen (Plan #1582 E13).
   *
   * @param register liefert je Sache alle Namen; jeder wird ein eigener Eintrag des Satzes
   */
  public StandardLabelService(
      LabelRepository labels,
      BoardService boardService,
      PlatformAdminChecker platformAdminChecker,
      KitNamen register) {
    this.labels = labels;
    this.boardService = boardService;
    this.platformAdminChecker = platformAdminChecker;
    this.satz = satzAus(register);
  }

  /** Der Standardsatz — nur für Plattform-Admins. */
  public List<StandardLabel> standardsatz(long actorUserId) {
    requireAdmin(actorUserId);
    return satz;
  }

  /**
   * Legt jedes fehlende Label des Standardsatzes auf jedem aktiven Board aller Projekte an.
   *
   * @return Zahl der Boards, der neu angelegten und der übersprungenen Labels
   */
  @Transactional
  public Ergebnis aufAlleBoardsAnlegen(long actorUserId) {
    requireAdmin(actorUserId);
    List<BoardSummary> boards = boardService.listAllActiveBoards();
    int angelegt = 0;
    for (BoardSummary board : boards) {
      for (StandardLabel label : satz) {
        if (labels.insertIfAbsent(board.id(), label.name(), label.farbe())) {
          angelegt++;
        }
      }
    }
    return new Ergebnis(boards.size(), angelegt, boards.size() * satz.size() - angelegt);
  }

  /**
   * Je Sache alle Namen des Registers in Registerreihenfolge, das Gegenstück direkt hinter dem
   * deutschen Namen (E6); eine Sache ohne Gegenstück steht genau einmal da (E5).
   */
  private static List<StandardLabel> satzAus(KitNamen register) {
    List<StandardLabel> eintraege = new ArrayList<>();
    // Die EnumMap liefert die Sachen in ihrer Deklarationsreihenfolge.
    DARSTELLUNG.forEach(
        (sache, darstellung) -> {
          for (String name : register.namen(sache)) {
            eintraege.add(new StandardLabel(name, darstellung.gruppe(), darstellung.farbe()));
          }
        });
    return List.copyOf(eintraege);
  }

  private static Map<Sache, Darstellung> darstellungAufbauen() {
    Map<Sache, Darstellung> tabelle = new EnumMap<>(Sache.class);
    zuordnen(
        tabelle,
        new Darstellung(KIT, FARBE_KIT),
        Sache.DURCHZIEHEN,
        Sache.GESCHUETZT,
        Sache.KLAEREN,
        Sache.NIGHT,
        Sache.NIGHTRUN,
        Sache.PRUEFEN,
        Sache.NIGHTPLAN,
        Sache.NIGHTREVIEW,
        Sache.NIGHTISSUES);
    zuordnen(
        tabelle,
        new Darstellung(LAUF, FARBE_LAUF),
        Sache.LAUF_ABGEBROCHEN,
        Sache.LAUF_LAEUFT,
        Sache.LAUF_WARTET);
    zuordnen(
        tabelle, new Darstellung(REVIEW, FARBE_REVIEW), Sache.REVIEW_OFFEN, Sache.REVIEW_FERTIG);
    zuordnen(
        tabelle,
        new Darstellung(STUFENLEISTE, FARBE_STUFENLEISTE),
        Sache.ZIEL_PLAN,
        Sache.ZIEL_PAKETE,
        Sache.ZIEL_UMSETZUNG,
        Sache.ZIEL_PUSH_VORBEREITET,
        Sache.PLANREVIEW_1,
        Sache.PLANREVIEW_2);
    return Collections.unmodifiableMap(tabelle);
  }

  private static void zuordnen(
      Map<Sache, Darstellung> tabelle, Darstellung darstellung, Sache... sachen) {
    for (Sache sache : sachen) {
      tabelle.put(sache, darstellung);
    }
  }

  private void requireAdmin(long actorUserId) {
    if (!platformAdminChecker.isPlatformAdmin(actorUserId)) {
      throw new AdminAccessDeniedException();
    }
  }

  /**
   * Ein Label des Standardsatzes.
   *
   * @param gruppe Kit, Lauf, Review oder Stufenleiste — für die Anzeige nach Gruppen
   * @param farbe CSS-Farbwert, mit dem es neu angelegt wird
   */
  public record StandardLabel(String name, String gruppe, String farbe) {}

  /** Gruppe und Farbe einer Sache. */
  private record Darstellung(String gruppe, String farbe) {}

  /**
   * Ergebnis eines Anlegens.
   *
   * @param boards Zahl der aktiven Boards, auf denen angelegt wurde
   * @param angelegt Zahl der neu angelegten Labels über alle Boards
   * @param uebersprungen Zahl der Labels, die ein Board schon trug
   */
  public record Ergebnis(int boards, int angelegt, int uebersprungen) {}
}
