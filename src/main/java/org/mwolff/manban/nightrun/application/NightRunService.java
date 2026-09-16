package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.NightRunRepository.UpsertResult;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
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

  /**
   * Nimmt einen <b>maschinell gemeldeten</b> Lauf entgegen (Issue #946, fachlich #927).
   *
   * <p>Zwei Unterschiede zu {@link #submit}: Hier gilt <b>Zustands-Semantik</b> — die Meldung ist
   * der vollständige Stand des Laufs und ersetzt einen vorhandenen, statt mit „lag schon vor"
   * abzuprallen. Und es kommt <b>eine</b> Meldung statt einer Liste: Eine Kette meldet den Lauf, an
   * dem sie gerade arbeitet.
   *
   * <p>Die Rechteprüfung ist dieselbe. Ein Token darf nicht mehr als sein Besitzer, also verlangt
   * auch dieser Weg {@code requireOwner}.
   *
   * <p><b>Die Verbrauchszahlen werden gemeldet, nicht gerechnet.</b> Die Lauf-Summe darf größer
   * sein als die Summe über die Arbeitspakete — die Differenz ist der Verbrauch, der zu keinem
   * Paket gehört (Vorflug, übergreifendes Review, Aufräumen), und die Auswertung braucht ihn als
   * eigene Zahl. Würde der Server die Lauf-Summe aus den Paketen rechnen, wäre dieser Rest per
   * Konstruktion null und damit unsichtbar, obwohl er existiert.
   */
  @Transactional
  public NightRunResult ingest(long userId, long projectId, String tokenName, NewNightRun meldung) {
    permissions.requireOwner(userId, projectId);
    Instant now = clock.instant();
    NightRun gemeldet =
        new NightRun(
            null,
            projectId,
            meldung.startedAt(),
            meldung.mode(),
            meldung.durationMs(),
            meldung.processedCount(),
            meldung.skippedCount(),
            meldung.unparsedCount(),
            meldung.unparsedSample(),
            // Beim Ersetzen lässt der Adapter created_at unangetastet; der Wert trägt also nur
            // beim ersten Mal, und updated_at sagt, wann zuletzt gemeldet wurde.
            now,
            NightRunOrigin.TOKEN,
            tokenName,
            meldung.complete(),
            now,
            meldung.usage());

    UpsertResult ergebnis = runs.upsert(gemeldet, items(meldung));
    // Der Ringpuffer gilt unverändert auch für maschinell eingelieferte Läufe.
    runs.deleteOlderThanNewest(projectId, properties.maxPerProject());
    return new NightRunResult(meldung.startedAt(), ergebnis.created());
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
        now,
        // Der Upload-Weg ist per Definition die menschliche Herkunft; updatedAt bleibt leer,
        // weil ein hochgeladener Lauf nie fortgeschrieben wird.
        NightRunOrigin.UPLOAD,
        null,
        submission.complete(),
        null,
        submission.usage());
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
                    item.excerpt(),
                    item.usage()))
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
        run.origin(),
        run.tokenName(),
        run.complete(),
        run.updatedAt(),
        run.usage(),
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
        item.excerpt(),
        item.usage());
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
      boolean complete,
      @Nullable NightRunUsage usage,
      List<NewNightRunItem> items) {}

  /** Ein einzulieferndes Arbeitspaket ohne technische Felder. */
  public record NewNightRunItem(
      int cardNumber,
      String title,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable String commitHash,
      @Nullable String excerpt,
      @Nullable NightRunUsage usage) {}

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
      NightRunOrigin origin,
      @Nullable String tokenName,
      boolean complete,
      @Nullable Instant updatedAt,
      @Nullable NightRunUsage usage,
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
      @Nullable String excerpt,
      @Nullable NightRunUsage usage) {}
}
