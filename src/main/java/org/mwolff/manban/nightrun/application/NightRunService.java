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
import org.mwolff.manban.nightrun.domain.NightRunBudget;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunItemStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.InteractiveUsageSinceWriter;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Cases der Nachtlauf-Auswertung (Issue #722).
 *
 * <p>Drei Regeln tragen das Modul: <b>Wer darf</b> — jeder Use-Case, lesend wie schreibend,
 * verlangt die Projekt-Rolle OWNER. Die <b>schreibenden</b> Wege ({@link #submit}, {@link #ingest})
 * lassen einen Plattform-Admin dabei mit durch ({@link PermissionChecker#requireOwner}, Plan #718,
 * A6); die <b>lesenden</b> seit Issue #1079 nur noch, wenn das Projekt am Plattform-Leitstand
 * teilnimmt ({@link PermissionChecker#requireNightRunAccess}) — die Teilnahme ist die Einwilligung
 * des Projekts in die Einsicht durch den Betreiber. <b>Wie viele bleiben</b> — je Projekt höchstens
 * {@code max-per-project} Läufe; verdrängt wird nach {@code startedAt}, in derselben Transaktion
 * wie das Einfügen (A10, A14); die verwaisten Arbeitspakete verdrängter Läufe haben eine eigene
 * Grenze {@code max-items-per-project} (Issue #966), und beide Grenzen gelten je Gattung getrennt
 * (Issue #1011). <b>Was bei einem bekannten Lauf geschieht</b> — er wird als schon vorliegend
 * gemeldet und bleibt unangetastet (A11).
 */
@Service
// Die Kopplung folgt dem Domaenenmodell: Der Dienst baut ein vollstaendiges NightRun samt seinen
// Arbeitspaketen und kennt deshalb jeden Typ, den die beiden Records fuehren. Mit Issue #1012 kommt
// InteractiveUsageSinceWriter dazu, und der Zaehler steht bei 21 (Schwelle 20). Eine Aufteilung
// loeste das nur nominell: submit, ingest und list teilen sich run(), items() und den Ringpuffer;
// sie zu trennen verteilte einen zusammenhaengenden Use-Case auf zwei Klassen und dieselben Typen
// auf beide. Dieselbe Begruendung wie am NightRunRepositoryAdapter.
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class NightRunService {

  private final NightRunRepository runs;
  private final PermissionChecker permissions;
  private final InteractiveUsageSinceWriter erfassungsbeginn;
  private final NightRunProperties properties;
  private final Clock clock;

  public NightRunService(
      NightRunRepository runs,
      PermissionChecker permissions,
      InteractiveUsageSinceWriter erfassungsbeginn,
      NightRunProperties properties,
      Clock clock) {
    this.runs = runs;
    this.permissions = permissions;
    this.erfassungsbeginn = erfassungsbeginn;
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
    // Erste Datenbankaktion dieses Wegs (Issue #1090): Sie serialisiert die Einlieferungen des
    // Projekts, damit zwei gleichzeitige Laeufe einander beim Nachziehen des Ringpuffers
    // mitzaehlen. Begruendung und Sperrreihenfolge stehen am Port.
    runs.lockProject(projectId);
    Instant now = clock.instant();
    List<NightRunResult> results = new ArrayList<>(submissions.size());
    for (NewNightRun submission : submissions) {
      // Ein verdrängter Lauf, der wiederkommt, bringt seinen vollständigen Stand mit (#965).
      runs.deleteOrphanItemsOfRun(projectId, submission.startedAt());
      boolean created =
          runs.insertIfAbsent(
                  run(projectId, submission, now), items(projectId, submission, NightRunKind.NIGHT))
              .isPresent();
      results.add(new NightRunResult(submission.startedAt(), created));
    }
    // Der Upload-Weg legt ausschliesslich Nachtlaeufe an (siehe run()), also zieht er deren
    // Ringpuffer — nicht den der interaktiven Sitzungen (Issue #1011).
    ringpufferNachziehen(projectId, NightRunKind.NIGHT);
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
   *
   * <p><b>Die Gattung kommt mit der Meldung</b> (Issue #1012): Derselbe Endpunkt trägt den
   * Nachtlauf und die interaktive Sitzung. Bei einer Sitzung wird zusätzlich der Erfassungsbeginn
   * des Projekts gesetzt — der Port setzt ihn nur, falls er noch leer ist, und der Wert ist der
   * Startzeitpunkt der Sitzung. Aus der ältesten vorhandenen Sitzung ließe sich die Grenze nicht
   * ableiten: Die wandert mit dem Ringpuffer nach vorn, und der Unterschied zwischen „nie erfasst"
   * und „erfasst, dann verdrängt" ginge verloren.
   */
  @Transactional
  public NightRunResult ingest(
      long userId, long projectId, String tokenName, NightRunKind kind, NewNightRun meldung) {
    permissions.requireOwner(userId, projectId);
    // Erste Datenbankaktion dieses Wegs (Issue #1090), aus demselben Grund wie in submit und vor
    // der Laufzeile aus upsert — die Sperrreihenfolge ist damit in beiden Wegen dieselbe.
    runs.lockProject(projectId);
    Instant now = clock.instant();
    // Einmal bestimmt, zweimal gebraucht: Der Abbruchgrund entscheidet mit, ob der Grund ohne
    // Arbeit ueberhaupt gesetzt wird (Plan #1139, E6).
    @Nullable String abbruch = abbruchGrund(kind, meldung.complete(), meldung.abortReason());
    NightRun gemeldet =
        new NightRun(
            null,
            projectId,
            meldung.startedAt(),
            meldung.mode(),
            kind,
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
            meldung.usage(),
            grundOhneArbeit(
                kind,
                meldung.complete(),
                meldung.processedCount(),
                meldung.noWorkReason(),
                abbruch),
            // Die Vorgaben kommen mit der Meldung (Issue #1113). Fehlen sie, steht am Lauf
            // „nicht angegeben" — der Dienst ergaenzt sie nicht aus Voreinstellungen, die er
            // gar nicht kennt (Plan #1110 E4).
            meldung.budget(),
            abbruch);

    // Wie beim Upload-Weg: verwaiste Pakete eines verdrängten Laufs zuerst weg (#965).
    runs.deleteOrphanItemsOfRun(projectId, meldung.startedAt());
    UpsertResult ergebnis = runs.upsert(gemeldet, items(projectId, meldung, kind));
    if (kind == NightRunKind.INTERACTIVE) {
      erfassungsbeginn.setInteractiveUsageSinceIfAbsent(projectId, meldung.startedAt());
    }
    // Der Ringpuffer gilt unverändert auch für maschinell eingelieferte Läufe — gezogen wird der
    // der Gattung, die gerade eingeliefert wurde (Issue #1011).
    ringpufferNachziehen(projectId, gemeldet.kind());
    return new NightRunResult(meldung.startedAt(), ergebnis.created());
  }

  /**
   * Zieht den Ringpuffer einer Gattung nach: erst die Läufe, dann die verwaisten Arbeitspakete —
   * erst danach steht fest, welche Pakete verwaist sind. Beide Grenzen kommen aus {@link
   * NightRunProperties} und gelten je Gattung getrennt (Plan #1007, E14): Die häufigeren
   * interaktiven Sitzungen verdrängen sonst binnen Tagen die Nachtlauf-Auswertung.
   */
  private void ringpufferNachziehen(long projectId, NightRunKind kind) {
    runs.deleteOlderThanNewest(projectId, kind, properties.maxRunsFor(kind));
    runs.deleteOrphanItemsOlderThanNewest(projectId, kind, properties.maxOrphanItemsFor(kind));
  }

  /**
   * Die aufbewahrten <b>Nachtläufe</b> des Projekts, neueste zuerst, jeder mit seinen
   * Arbeitspaketen.
   *
   * <p>Die Gattung {@code NIGHT} steht hier fest (Issue #1012, Nicht-Ziel): Diese Liste speist die
   * Nachtlauf-Seite und die Platte „Letzter Lauf". Sie liefert dieselben Ergebnisse wie vor der
   * Einlieferung von Sitzungen, auch wenn im selben Projekt Sitzungen liegen — die bekommen ihre
   * eigene Ansicht.
   */
  @Transactional(readOnly = true)
  public List<NightRunView> list(long userId, long projectId) {
    permissions.requireNightRunAccess(userId, projectId);
    List<NightRun> gefunden =
        runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT);
    List<NightRunItem> pakete =
        runs.findItemsByRunIds(gefunden.stream().map(NightRun::requireId).toList());
    return gefunden.stream().map(run -> view(run, pakete)).toList();
  }

  /**
   * Je Fehlerklasse die Zahl der aufbewahrten <b>Nachtläufe</b>, in denen sie mindestens einmal
   * vorkam. Ein Lauf zählt je Klasse höchstens einmal; ein verdrängter Lauf zählt nicht mehr.
   *
   * <p>Die Gattung {@code NIGHT} steht aus demselben Grund fest wie bei {@link #list} (Issue
   * #1012): Die Platte „Abbruchgründe" gehört zur Nachtlauf-Seite.
   */
  @Transactional(readOnly = true)
  public Map<NightRunErrorClass, Long> countRunsByErrorClass(long userId, long projectId) {
    permissions.requireNightRunAccess(userId, projectId);
    return runs.countRunsByErrorClass(projectId, NightRunKind.NIGHT);
  }

  /**
   * Die Anläufe einer Karte über Läufe hinweg, jüngster zuerst — auch die verdrängter Läufe (Issue
   * #967). Lesen darf, wer auch die Laufliste sieht: {@code requireOwner}, wie in jedem
   * Nachtlauf-Use-Case (Plan #718, A6).
   *
   * <p>Anders als die Laufliste und die Abbruchgründe legt dieser Abruf <b>keine</b> Gattung fest
   * (Issue #1015): Er reicht beide durch, jede mit ihrer eigenen am Anlauf. Die Karte ist der eine
   * Ort, an dem Nachtlauf und interaktive Sitzung zusammengehören.
   */
  @Transactional(readOnly = true)
  public List<NightRunItem> anlaeufeDerKarte(long userId, long projectId, int cardNumber) {
    permissions.requireNightRunAccess(userId, projectId);
    return runs.findByCard(projectId, cardNumber);
  }

  private static NightRun run(long projectId, NewNightRun submission, Instant now) {
    return new NightRun(
        null,
        projectId,
        submission.startedAt(),
        submission.mode(),
        NightRunKind.NIGHT,
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
        submission.usage(),
        // Der Upload-Weg fuehrt kein Grund-Feld (Plan #1067, E4): Ein hochgeladenes Protokoll
        // kommt aus der Datei, nicht aus dem Runner. Ein Lauf ohne Arbeit landet damit im
        // Rueckfalltext -- angezeigt wird er trotzdem, nur ohne die Begruendung des Runners.
        grundOhneArbeit(
            NightRunKind.NIGHT, submission.complete(), submission.processedCount(), null, null),
        // Durchgereicht wie jedes andere Feld; der Upload-Weg uebergibt hier fest „nicht
        // angegeben", weil die Ergebnisdatei den Browser nicht verlaesst (Plan #1110 E14).
        submission.budget(),
        // Fest null aus demselben Grund wie der Grund ohne Arbeit (Plan #1139, E7): Ein
        // hochgeladenes Protokoll kommt aus der Datei, nicht aus dem Runner, und traegt dessen
        // Abbruchmeldung nicht.
        null);
  }

  /**
   * Der Grund, warum ein Lauf nichts abgearbeitet hat — oder {@code null}, wenn die Frage sich
   * nicht stellt (Issue #1068, Plan #1067).
   *
   * <p>Drei Faelle liefern {@code null}, und jeder ist ein Normalfall statt eines Befundes: Eine
   * interaktive Sitzung arbeitet keine Arbeitspakete ab, ihre 0 sagt nichts (E6). Ein nicht
   * abgeschlossen gemeldeter Lauf ist noch unterwegs. Und ein Lauf mit bearbeiteten Paketen hat
   * gearbeitet.
   *
   * <p>Gemessen wird an {@code processedCount} — der vom Runner <b>gemeldeten</b> Zahl (E5),
   * derselben, die die Metazeile als „N bearbeitet" zeigt. Ausdruecklich keine zweite Rechnung
   * ueber die Arbeitspakete: Zwei Zaehlweisen fuer dieselbe Aussage liefen auseinander, und die
   * Anzeige zeigte dann eine 0 neben einem gruenen Melder.
   *
   * <p>Ein gemeldeter, aber leerer Grund gilt wie ein fehlender. AK 2 verlangt einen Text, nicht
   * ein gesetztes Feld — ein leerer Grund erschiene in der Anzeige als Luecke.
   *
   * <p>Der Rueckfalltext steht seit Issue #1121 in {@link NightRunOutcome} und ist dort seit Issue
   * #1185 ein reiner <b>Anzeigetext</b>: Jeder Grund — gemeldet oder zurueckgefallen — ergibt den
   * Ausgang „nichts zu tun". Gesetzt wird der Text weiterhin nur hier, und das Verhalten dieser
   * Methode hat #1185 nicht angetastet: Ein Lauf ohne Arbeit steht nie ohne Text da.
   *
   * <p><b>Ein gesetzter Abbruchgrund verdraengt den Rueckfalltext</b> (Issue #1142, Plan #1139 E6):
   * Ein Lauf, der abgebrochen ist, hat nichts abgearbeitet — aber der Grund dafuer ist bekannt und
   * steht in {@code abort_reason}. Stuende daneben „Nichts abgearbeitet — Grund unbekannt", sagte
   * derselbe Lauf an zwei Stellen Widerspruechliches.
   */
  private static @Nullable String grundOhneArbeit(
      NightRunKind kind,
      boolean complete,
      int processedCount,
      @Nullable String gemeldet,
      @Nullable String abbruchGrund) {
    if (kind != NightRunKind.NIGHT || !complete || processedCount > 0 || abbruchGrund != null) {
      return null;
    }
    return gemeldet == null || gemeldet.isBlank() ? NightRunOutcome.GRUND_UNBEKANNT : gemeldet;
  }

  /**
   * Der gemeldete Grund eines harten Abbruchs — oder {@code null}, wenn die Frage sich nicht stellt
   * (Issue #1142, Plan #1139 E12).
   *
   * <p>Uebernommen wird er nur an einem <b>Nachtlauf</b>, der <b>abgeschlossen</b> gemeldet wurde.
   * Beides aus demselben Grund wie bei {@link #grundOhneArbeit}: Eine interaktive Sitzung bricht
   * keine Kette ab, und ein noch nicht abgeschlossener Lauf ist unterwegs — sein Abbruch stuende
   * fest, bevor er feststeht.
   *
   * <p>Ein gemeldeter, aber leerer Grund gilt wie ein fehlender: Ein leeres Feld ist keine Auskunft
   * ueber den Abbruch, und es gibt hier — anders als beim Grund ohne Arbeit — keinen Rueckfalltext,
   * der dafuer einspringen koennte. Der Ausgang des Laufs entsteht im Folgepaket.
   */
  private static @Nullable String abbruchGrund(
      NightRunKind kind, boolean complete, @Nullable String gemeldet) {
    if (kind != NightRunKind.NIGHT || !complete || gemeldet == null || gemeldet.isBlank()) {
      return null;
    }
    return gemeldet;
  }

  /**
   * Die Arbeitspakete tragen Projekt, Startzeitpunkt, Lauf-Art und Gattung ihres Laufs (Issue #964,
   * um die Gattung erweitert in #1010). Der Adapter schreibt diese vier aus dem Lauf selbst; hier
   * stehen sie, weil ein Paket ohne sie kein vollständiges Domänenobjekt ist.
   */
  private static List<NightRunItem> items(
      long projectId, NewNightRun submission, NightRunKind kind) {
    return submission.items().stream()
        .map(
            item ->
                new NightRunItem(
                    null,
                    null,
                    projectId,
                    submission.startedAt(),
                    submission.mode(),
                    kind,
                    item.cardNumber(),
                    item.title(),
                    item.state(),
                    item.errorClass(),
                    item.durationMs(),
                    item.commitHash(),
                    item.excerpt(),
                    item.usage(),
                    // Die Stufen kommen mit der Meldung (Issue #1113); der Upload-Weg uebergibt
                    // hier fest die leere Liste — „dieser Vorgang hatte keine".
                    item.stages()))
        .toList();
  }

  /**
   * Der Lauf mit den Arbeitspaketen, die ihm gehören. Die Zuordnung läuft über einen Filter statt
   * über eine Gruppierung, weil der Fremdschlüssel eines Arbeitspakets erst mit dem Einfügen
   * gesetzt wird und damit {@code @Nullable} ist — ein Gruppierungsschlüssel darf das nicht sein.
   *
   * <p>Instanzmethode statt {@code static} seit Issue #1091: Der Befund braucht die Stillefrist aus
   * {@link NightRunProperties} und den Jetzt-Zeitpunkt aus der {@link Clock}.
   */
  private NightRunView view(NightRun run, List<NightRunItem> alleItems) {
    Long runId = run.requireId();
    // Einmal filtern, zweimal gebraucht: Die Sicht zeigt die Pakete, der Befund wertet sie aus
    // (Issue #1078). Die Reihenfolge bleibt die der Abfrage — sie entscheidet zusammen mit der
    // Laufart bei gleichrangigen Paketen, welches maßgeblich ist (Issue #1123).
    List<NightRunItem> eigeneItems =
        alleItems.stream().filter(item -> Objects.equals(item.nightRunId(), runId)).toList();
    List<NightRunItemView> items = eigeneItems.stream().map(NightRunService::itemView).toList();
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
        run.noWorkReason(),
        run.budget(),
        run.abortReason(),
        NightRunOutcome.of(
            run.complete(),
            run.noWorkReason(),
            // Seit Issue #1143 wirkt der Abbruchgrund auf den Ausgang: Ein Lauf, der abbrach, ist
            // nie gelungen. Dieselben Argumente wie in DisruptionService.view — eine Rechnung,
            // zwei Auswertungswege (AK 8 der fachlichen Quelle #1074).
            run.abortReason(),
            run.mode(),
            eigeneItems,
            run.startedAt(),
            run.updatedAt(),
            clock.instant(),
            properties.stilleFrist()),
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
        item.usage(),
        item.stages());
  }

  /**
   * Ein einzuliefernder Lauf ohne technische Felder — ID und Einfügezeitpunkt vergibt der Service.
   *
   * @param budget die gemeldeten Vorgaben des Laufs (Issue #1113); {@code null} heißt „nicht
   *     angegeben". Der Upload-Weg führt sie nicht und übergibt hier fest {@code null} (Plan #1110
   *     E14).
   * @param abortReason der gemeldete Grund eines harten Abbruchs (Issue #1142); {@code null} heißt
   *     „nicht abgebrochen". Der Upload-Weg führt ihn nicht und übergibt hier fest {@code null}
   *     (Plan #1139 E7). Ob der Wert am Lauf landet, entscheidet {@link #abbruchGrund} — gemeldet
   *     heißt nicht gesetzt.
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
      @Nullable String noWorkReason,
      @Nullable NightRunBudget budget,
      @Nullable String abortReason,
      List<NewNightRunItem> items) {}

  /**
   * Ein einzulieferndes Arbeitspaket ohne technische Felder.
   *
   * @param stages die gemeldeten Stufen der Kette (Issue #1113) — leer statt {@code null}, denn
   *     „dieser Vorgang hatte keine Stufen" ist eine Aussage
   */
  public record NewNightRunItem(
      int cardNumber,
      String title,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable String commitHash,
      @Nullable String excerpt,
      @Nullable NightRunUsage usage,
      List<NightRunItemStage> stages) {}

  /**
   * Ergebnis der Einlieferung eines Laufs.
   *
   * @param startedAt fachlicher Schlüssel des Laufs — er ordnet das Ergebnis der Eingabe zu
   * @param created {@code true}, wenn der Lauf angelegt wurde; {@code false}, wenn er schon vorlag
   */
  public record NightRunResult(Instant startedAt, boolean created) {}

  /**
   * Darstellung eines aufbewahrten Laufs samt seiner Arbeitspakete.
   *
   * @param budget die Vorgaben, unter denen der Lauf angetreten ist (Issue #1113); {@code null}
   *     heißt „nicht angegeben"
   * @param abortReason der Grund, warum der Lauf hart abgebrochen ist (Issue #1142); {@code null}
   *     heißt „nicht abgebrochen"
   */
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
      @Nullable String noWorkReason,
      @Nullable NightRunBudget budget,
      @Nullable String abortReason,
      NightRunOutcome outcome,
      List<NightRunItemView> items) {}

  /**
   * Darstellung eines Arbeitspakets.
   *
   * @param stages die Stufen der Kette, die dieser Vorgang durchlaufen hat (Issue #1113) — leer
   *     statt {@code null}
   */
  public record NightRunItemView(
      Long id,
      int cardNumber,
      String title,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable Long durationMs,
      @Nullable String commitHash,
      @Nullable String excerpt,
      @Nullable NightRunUsage usage,
      List<NightRunItemStage> stages) {}
}
