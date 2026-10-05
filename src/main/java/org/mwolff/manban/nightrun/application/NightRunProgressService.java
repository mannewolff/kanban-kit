package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.LaufKarteView;
import org.mwolff.manban.card.application.CardService.TokenActivityView;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.LaufstandView;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Aktivitaet;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Karte;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Laufstand;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Zeitfenster;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.nightrun.domain.NightRunProgress;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der Fortschritt eines Laufs aus seinen Spuren am Board (Issue #1375, Plan #1372).
 *
 * <p>Der Dienst prüft das Recht, lädt den Lauf, bestimmt sein Zeitfenster und holt die Spuren über
 * die Fassaden von {@code card} und {@code comment}; was sie bedeuten, entscheidet allein {@link
 * FortschrittErmittlung}.
 *
 * <p><b>Wer darf (E10):</b> dieselbe Prüfung wie die Laufliste, {@link
 * PermissionChecker#requireNightRunAccess}. Ausgeliefert werden je Karte nur Nummer, Titel und
 * Board — dieselben Angaben, die die Lauf-Items heute schon tragen; ein neues Recht entsteht nicht.
 */
@Service
public class NightRunProgressService {

  /** Die Herkunft jeder gelieferten Aktivität — die Abfrage liefert nur Token-Aktivitäten. */
  private static final String HERKUNFT_TOKEN = "TOKEN";

  /**
   * Platzhalter für {@code agent}: {@link CardService#tokenActivitiesInWindow} liefert nur
   * Aktivitäten mit gesetztem {@code agent} und lässt den Wert selbst weg. Die Ermittlung prüft
   * allein, dass er gesetzt ist; welches Modell es war, spielt für den Fortschritt keine Rolle.
   */
  private static final String AGENT_GESETZT = "gesetzt";

  /** Die Laufarten mit Fortschritt (E8) — alle anderen fragen nichts ab. */
  private static final Set<NightRunMode> MIT_FORTSCHRITT =
      Set.of(NightRunMode.CHAIN, NightRunMode.IMPLEMENTATION);

  private final NightRunRepository runs;
  private final PermissionChecker permissions;
  private final CardService cards;
  private final CommentService comments;
  private final NightRunProperties properties;
  private final Clock clock;

  public NightRunProgressService(
      NightRunRepository runs,
      PermissionChecker permissions,
      CardService cards,
      CommentService comments,
      NightRunProperties properties,
      Clock clock) {
    this.runs = runs;
    this.permissions = permissions;
    this.cards = cards;
    this.comments = comments;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Der Fortschritt des Laufs {@code runId} im Projekt.
   *
   * @throws NightRunNotFoundException wenn der Lauf nicht zu diesem Projekt gehört
   */
  @Transactional(readOnly = true)
  public NightRunProgress progress(long userId, long projectId, long runId) {
    permissions.requireNightRunAccess(userId, projectId);
    NightRun lauf =
        runs.findByIdAndProjectId(runId, projectId).orElseThrow(NightRunNotFoundException::new);
    Instant jetzt = clock.instant();
    Zeitfenster fenster = fenster(lauf, jetzt);
    String tokenName = lauf.tokenName();
    if (!MIT_FORTSCHRITT.contains(lauf.mode()) || tokenName == null) {
      // Leer (E8) oder ganz unbekannt (E3) — das entscheidet die Ermittlung, ohne Spuren.
      return FortschrittErmittlung.ermittle(
          lauf, fenster, List.of(), List.of(), List.of(), List.of());
    }
    List<Zeitfenster> fremdeFenster =
        runs.findOverlapping(projectId, tokenName, fenster.von(), fenster.bis()).stream()
            .filter(r -> !Objects.equals(r.id(), lauf.id()))
            .map(r -> fenster(r, jetzt))
            .toList();
    List<TokenActivityView> aktivitaeten =
        cards.tokenActivitiesInWindow(projectId, tokenName, fenster.von(), fenster.bis());
    List<LaufstandView> laufstaende = comments.laufstaendeImProjekt(projectId);
    return FortschrittErmittlung.ermittle(
        lauf,
        fenster,
        fremdeFenster,
        aktivitaeten.stream()
            .map(
                a ->
                    new Aktivitaet(
                        a.cardId(),
                        a.type(),
                        a.createdAt(),
                        HERKUNFT_TOKEN,
                        tokenName,
                        AGENT_GESETZT))
            .toList(),
        karten(aktivitaeten, laufstaende),
        laufstaende.stream().map(l -> new Laufstand(l.cardId(), l.body())).toList());
  }

  /**
   * Das Zeitfenster eines Laufs (E2): Es beginnt beim Start und endet bei Start plus Dauer, wenn
   * der Lauf gemeldet ist; bei seinem letzten Lebenszeichen, wenn er verstummt ist; sonst jetzt.
   */
  private Zeitfenster fenster(NightRun lauf, Instant jetzt) {
    Instant von = lauf.startedAt();
    if (lauf.complete()) {
      return new Zeitfenster(von, von.plusMillis(lauf.durationMs()));
    }
    Instant updatedAt = lauf.updatedAt();
    if (NightRunOutcome.verstummt(von, updatedAt, jetzt, properties.stilleFrist())) {
      return new Zeitfenster(von, updatedAt == null ? von : updatedAt);
    }
    return new Zeitfenster(von, jetzt);
  }

  /**
   * Die Karten der Aktivitäten und der Laufstände samt ihrer Herkunft — Stufe für Stufe, bis keine
   * neue Karte dazukommt: Der Lauf legt Pakete zum Plan an, den Plan zur Anforderung, und die
   * Anforderung selbst hat er oft nie angefasst.
   */
  private List<Karte> karten(
      List<TokenActivityView> aktivitaeten, List<LaufstandView> laufstaende) {
    Map<Long, LaufKarteView> bekannt = new LinkedHashMap<>();
    Set<Long> offen = new LinkedHashSet<>();
    aktivitaeten.forEach(a -> offen.add(a.cardId()));
    laufstaende.forEach(l -> offen.add(l.cardId()));
    while (!offen.isEmpty()) {
      List<LaufKarteView> geladen = cards.cardsByIds(List.copyOf(offen));
      offen.clear();
      geladen.forEach(k -> bekannt.put(k.id(), k));
      geladen.stream()
          .map(LaufKarteView::derivedFromCardId)
          .filter(id -> id != null && !bekannt.containsKey(id))
          .forEach(offen::add);
    }
    return bekannt.values().stream()
        .map(
            k ->
                new Karte(
                    k.id(),
                    k.number(),
                    k.title(),
                    k.boardId(),
                    k.status(),
                    k.labels(),
                    k.derivedFromCardId(),
                    k.arbeitspaket(),
                    k.description()))
        .toList();
  }
}
