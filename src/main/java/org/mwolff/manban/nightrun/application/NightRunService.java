package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Cases der Nachtlauf-Auswertung (Issue #722).
 *
 * <p>Drei Regeln tragen das Modul: <b>Wer darf</b> — jeder Use-Case, lesend wie schreibend,
 * verlangt die Projekt-Rolle OWNER; ein Plattform-Admin passiert {@link
 * PermissionChecker#requireOwner} bewusst mit (Plan #718, A6). <b>Wie viele bleiben</b> — je
 * Projekt höchstens {@code max-per-project} Läufe; verdrängt wird nach {@code startedAt}, in
 * derselben Transaktion wie das Einfügen (A10, A14). <b>Was bei einem bekannten Lauf geschieht</b>
 * — er wird als schon vorliegend gemeldet und bleibt unangetastet (A11).
 */
@Service
public class NightRunService {

  private final NightRunRepository runs;
  private final PermissionChecker permissions;
  private final NightRunProperties properties;
  private final Clock clock;

  public NightRunService(
      NightRunRepository runs,
      PermissionChecker permissions,
      NightRunProperties properties,
      Clock clock) {
    this.runs = runs;
    this.permissions = permissions;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Nimmt die im Browser erzeugten Auswertungen entgegen und meldet für jede einzeln, ob sie
   * angelegt wurde oder schon vorlag — in Eingabereihenfolge, ein Ergebnis je Eingabe.
   *
   * <p>Ob ein Lauf schon vorlag, entscheidet allein {@link NightRunRepository#insertIfAbsent}:
   * keine Vorab-Abfrage (die hätte ein Rennen) und keine gefangene Constraint-Verletzung (die risse
   * die Transaktion mit, und jeder weitere Lauf derselben Anfrage scheiterte mit). Zwei Läufe mit
   * gleichem {@code startedAt} in einer Anfrage lösen sich damit von selbst auf: Der erste wird
   * angelegt, der zweite meldet „lag schon vor".
   *
   * <p>Verdrängt wird am Ende <b>einmal</b> für die ganze Anfrage — {@code deleteOlderThanNewest}
   * kürzt auf {@code keep}, gleich wie viele Läufe hinzukamen. Ein Lauf, der älter ist als alle
   * aufbewahrten, wird angelegt und im selben Commit sofort wieder verdrängt; gemeldet wird
   * trotzdem „angelegt" (A14, hinzunehmende Folge).
   */
  @Transactional
  public List<NightRunResult> submit(long userId, long projectId, List<NewNightRun> submissions) {
    permissions.requireOwner(userId, projectId);
    if (submissions.isEmpty()) {
      return List.of();
    }
    Instant now = clock.instant();
    List<NightRunResult> results = new ArrayList<>(submissions.size());
    for (NewNightRun submission : submissions) {
      boolean created =
          runs.insertIfAbsent(run(projectId, submission, now), items(submission)).isPresent();
      results.add(new NightRunResult(submission.startedAt(), created));
    }
    runs.deleteOlderThanNewest(projectId, properties.maxPerProject());
    return List.copyOf(results);
  }

  /** Die aufbewahrten Läufe des Projekts, neueste zuerst, jeder mit seinen Arbeitspaketen. */
  @Transactional(readOnly = true)
  public List<NightRunView> list(long userId, long projectId) {
    permissions.requireOwner(userId, projectId);
    List<NightRun> gefunden = runs.findByProjectOrderByStartedAtDesc(projectId);
    List<NightRunItem> pakete =
        runs.findItemsByRunIds(gefunden.stream().map(NightRun::requireId).toList());
    return gefunden.stream().map(run -> view(run, pakete)).toList();
  }

  /**
   * Je Fehlerklasse die Zahl der aufbewahrten Läufe, in denen sie mindestens einmal vorkam. Ein
   * Lauf zählt je Klasse höchstens einmal; ein verdrängter Lauf zählt nicht mehr.
   */
  @Transactional(readOnly = true)
  public Map<NightRunErrorClass, Long> countRunsByErrorClass(long userId, long projectId) {
    permissions.requireOwner(userId, projectId);
    return runs.countRunsByErrorClass(projectId);
  }

  private static NightRun run(long projectId, NewNightRun submission, Instant now) {
    return new NightRun(
        null,
        projectId,
        submission.startedAt(),
        submission.mode(),
        submission.durationMs(),
        submission.processedCount(),
        submission.skippedCount(),
        submission.unparsedCount(),
        submission.unparsedSample(),
        now);
  }

  private static List<NightRunItem> items(NewNightRun submission) {
    return submission.items().stream()
        .map(
            item ->
                new NightRunItem(
                    null,
                    null,
                    item.cardNumber(),
                    item.title(),
                    item.state(),
                    item.errorClass(),
                    item.durationMs(),
                    item.commitHash(),
                    item.excerpt()))
        .toList();
  }

  /**
   * Der Lauf mit den Arbeitspaketen, die ihm gehören. Die Zuordnung läuft über einen Filter statt
   * über eine Gruppierung, weil der Fremdschlüssel eines Arbeitspakets erst mit dem Einfügen
   * gesetzt wird und damit {@code @Nullable} ist — ein Gruppierungsschlüssel darf das nicht sein.
   */
  private static NightRunView view(NightRun run, List<NightRunItem> alleItems) {
    Long runId = run.requireId();
    List<NightRunItemView> items =
        alleItems.stream()
            .filter(item -> Objects.equals(item.nightRunId(), runId))
            .map(NightRunService::itemView)
            .toList();
    return new NightRunView(
        runId,
        run.startedAt(),
        run.mode(),
        run.durationMs(),
        run.processedCount(),
        run.skippedCount(),
        run.unparsedCount(),
        run.unparsedSample(),
        run.createdAt(),
        items);
  }

  private static NightRunItemView itemView(NightRunItem item) {
    return new NightRunItemView(
        item.requireId(),
        item.cardNumber(),
        item.title(),
        item.state(),
        item.errorClass(),
        item.durationMs(),
        item.commitHash(),
        item.excerpt());
  }

  /**
   * Ein einzuliefernder Lauf ohne technische Felder — ID und Einfügezeitpunkt vergibt der Service.
   */
  public record NewNightRun(
      Instant startedAt,
      NightRunMode mode,
      long durationMs,
      int processedCount,
      int skippedCount,
      int unparsedCount,
      @Nullable String unparsedSample,
      List<NewNightRunItem> items) {}

  /** Ein einzulieferndes Arbeitspaket ohne technische Felder. */
  public record NewNightRunItem(
      int cardNumber,
      String title,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable String commitHash,
      @Nullable String excerpt) {}

  /**
   * Ergebnis der Einlieferung eines Laufs.
   *
   * @param startedAt fachlicher Schlüssel des Laufs — er ordnet das Ergebnis der Eingabe zu
   * @param created {@code true}, wenn der Lauf angelegt wurde; {@code false}, wenn er schon vorlag
   */
  public record NightRunResult(Instant startedAt, boolean created) {}

  /** Darstellung eines aufbewahrten Laufs samt seiner Arbeitspakete. */
  public record NightRunView(
      Long id,
      Instant startedAt,
      NightRunMode mode,
      long durationMs,
      int processedCount,
      int skippedCount,
      int unparsedCount,
      @Nullable String unparsedSample,
      Instant createdAt,
      List<NightRunItemView> items) {}

  /** Darstellung eines Arbeitspakets. */
  public record NightRunItemView(
      Long id,
      int cardNumber,
      String title,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable String commitHash,
      @Nullable String excerpt) {}
}
