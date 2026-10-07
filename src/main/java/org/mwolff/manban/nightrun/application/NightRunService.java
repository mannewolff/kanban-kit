package org.mwolff.manban.nightrun.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.nightrun.application.NightRunRepository.UpsertResult;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung;
import org.mwolff.manban.nightrun.domain.NachtFreigabe;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunAbortKind;
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
import org.mwolff.manban.nightrun.domain.ReleasePreparation;
import org.mwolff.manban.nightrun.domain.ReleasePreparationResult;
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
// auf beide. Dieselbe Begruendung wie am NightRunRepositoryAdapter. Issue #1454 bringt mit der
// Uebersicht „Heute Nacht" CardRunQueryService und NachtFreigabe dazu — ein weiterer Lesepfad
// derselben Runner-Seite mit derselben Rechtepruefung wie list. Issue #1456 bringt mit der
// Morgenmeldung ReleasePreparation und ReleasePreparationResult dazu — zwei Felder mehr desselben
// Laufs, die derselbe Use-Case schreibt.
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class NightRunService {

  private final NightRunRepository runs;
  private final PermissionChecker permissions;
  private final InteractiveUsageSinceWriter erfassungsbeginn;
  private final NightRunProperties properties;
  private final Clock clock;
  private final CardRunQueryService cards;

  public NightRunService(
      NightRunRepository runs,
      PermissionChecker permissions,
      InteractiveUsageSinceWriter erfassungsbeginn,
      NightRunProperties properties,
      Clock clock,
      CardRunQueryService cards) {
    this.runs = runs;
    this.permissions = permissions;
    this.erfassungsbeginn = erfassungsbeginn;
    this.properties = properties;
    this.clock = clock;
    this.cards = cards;
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
            abbruch,
            // Die Art gilt unter derselben Bedingung wie der Grund (Plan #1498 E4): Eine Art ohne
            // uebernommenen Abbruch ist keine Auskunft, und eine Regel an zwei Stellen liefe
            // auseinander.
            abbruch == null ? null : meldung.abortKind(),
            // Die Morgenmeldung ersetzt wie jedes andere Feld (Issue #1456, Plan #1447 E12): Eine
            // Meldung ohne sie raeumt eine frueher gemeldete ab. Den Eingang setzt die Uhr des
            // Servers, denn die Meldung selbst traegt keinen Zeitpunkt der Vorbereitung.
            vorbereitung(meldung.releasePreparation(), now));

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
    // Ein Abruf fuer alle Morgenmeldungen der Liste, nicht einer je Nummer (Issue #1457) — und nur
    // im Projekt der Laeufe: Eine Nummer eines fremden Projekts bleibt so ohne Titel.
    Map<Integer, String> titel = cards.titlesByCardNumber(projectId, gemeldeteNummern(gefunden));
    return gefunden.stream().map(run -> view(run, pakete, titel)).toList();
  }

  /** Alle Kartennummern, die die Morgenmeldungen der Läufe nennen — enthaltene wie rote. */
  private static Set<Integer> gemeldeteNummern(List<NightRun> laeufe) {
    return laeufe.stream()
        .map(NightRun::releasePreparation)
        .filter(Objects::nonNull)
        .flatMap(v -> Stream.concat(v.cardNumbers().stream(), v.redCards().stream()))
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Die Morgenmeldung für die Laufliste — oder {@code null} ohne Meldung (Issue #1457). Eine Nummer
   * ohne Karte im Projekt bleibt mit {@code title = null} stehen: Die Zahl der Pakete soll stimmen.
   */
  private static @Nullable ReleasePreparationView vorbereitungView(
      @Nullable ReleasePreparation vorbereitung, Map<Integer, String> titel) {
    if (vorbereitung == null) {
      return null;
    }
    return new ReleasePreparationView(
        vorbereitung.result(),
        vorbereitung.commitHash(),
        vorbereitung.version(),
        vorbereitung.redCheck(),
        vorbereitung.pending(),
        vorbereitung.receivedAt(),
        mitTitel(vorbereitung.cardNumbers(), titel),
        mitTitel(vorbereitung.redCards(), titel));
  }

  private static List<CardTitleView> mitTitel(List<Integer> nummern, Map<Integer, String> titel) {
    return nummern.stream().map(n -> new CardTitleView(n, titel.get(n))).toList();
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

  /**
   * Was heute Nacht ansteht (Issue #1454, Plan #1447 E4): die fachlichen Anforderungen und Pläne
   * aller aktiven Boards des Projekts, die {@code kit:night} tragen — freigegeben und von keinem
   * Runner übernommen, denn die Übernahme nimmt {@code kit:night} ab (Kit E1). Je Karte der kleine
   * Stufenstand aus {@link NachtFreigabe}, sortiert nach Kartennummer.
   *
   * <p>Lesen darf, wer die Läufe liest ({@link PermissionChecker#requireNightRunAccess}): Die
   * Sichtregel gilt hier im Backend, nicht im Client.
   */
  @Transactional(readOnly = true)
  public List<NachtFreigabe> heuteNacht(long userId, long projectId) {
    permissions.requireNightRunAccess(userId, projectId);
    return cards
        .freigegebeneKarten(
            projectId, FortschrittErmittlung.LABEL_NIGHT, NachtFreigabe::istStartkarte)
        .stream()
        .map(k -> NachtFreigabe.aus(k.number(), k.title(), k.boardName(), k.labels()))
        .sorted(Comparator.comparingInt(NachtFreigabe::number))
        .toList();
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
        null,
        // Die Abschlussart, fest null aus demselben Grund (Issue #1500): Ohne Abbruchgrund ist sie
        // keine Auskunft.
        null,
        // Fest null aus demselben Grund (Issue #1456): Die Morgenmeldung kommt allein vom Runner.
        null);
  }

  /**
   * Die gemeldete Morgenmeldung mit dem Eingang nach der Uhr des Servers — oder {@code null}, wenn
   * keine gemeldet wurde (Issue #1456, Plan #1447 E12).
   */
  private static @Nullable ReleasePreparation vorbereitung(
      @Nullable NewReleasePreparation gemeldet, Instant eingang) {
    if (gemeldet == null) {
      return null;
    }
    return new ReleasePreparation(
        gemeldet.result(),
        gemeldet.commitHash(),
        gemeldet.version(),
        gemeldet.redCheck(),
        gemeldet.cardNumbers(),
        gemeldet.redCards(),
        gemeldet.pending(),
        eingang);
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
  private NightRunView view(
      NightRun run, List<NightRunItem> alleItems, Map<Integer, String> titel) {
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
        vorbereitungView(run.releasePreparation(), titel),
        NightRunOutcome.of(
            run.complete(),
            // Die Sicht eines Projekts kennt die Kennzeichnung von Hand nicht (Issue #1197): Sie
            // wird auf dem Plattform-Leitstand gesetzt und dort gelesen; der Lauf-Datensatz dieser
            // Sicht fuehrt die Spalte nicht. Ein gekennzeichneter Lauf steht hier also weiter als
            // verstummt — dieselbe Auskunft wie vor der Kennzeichnung, keine falsche.
            null,
            run.noWorkReason(),
            // Seit Issue #1143 wirkt der Abbruchgrund auf den Ausgang: Ein Lauf, der abbrach, ist
            // nie gelungen. Dieselben Argumente wie in DisruptionService.view — eine Rechnung,
            // zwei Auswertungswege (AK 8 der fachlichen Quelle #1074).
            run.abortReason(),
            // Die Abschlussart (Issue #1500): Nur ein selbst gemeldeter Abbruch ohne angefasstes
            // Paket ist „nicht angelaufen"; ohne Art bleibt jeder Abbruch eine Störung (Plan #1498
            // A4).
            run.abortKind(),
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
   * @param abortKind wie der abgebrochene Lauf zu seinem Abschluss kam (Issue #1500); {@code null}
   *     heißt „nicht gemeldet". Der Upload-Weg führt sie nicht und übergibt hier fest {@code null}.
   *     Übernommen wird sie nur zusammen mit dem Abbruchgrund (Plan #1498 E4).
   * @param releasePreparation die gemeldete Morgenmeldung (Issue #1456); {@code null} heißt „keine
   *     gemeldet". Der Upload-Weg führt sie nicht und übergibt hier fest {@code null}.
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
      @Nullable NightRunAbortKind abortKind,
      @Nullable NewReleasePreparation releasePreparation,
      List<NewNightRunItem> items) {}

  /**
   * Eine gemeldete Morgenmeldung ohne den Eingangszeitpunkt — den setzt der Dienst (Issue #1456).
   * {@code releaseFiles} der Meldung fehlt hier mit Absicht: Das Board nimmt es an, aber speichert
   * es nicht (Plan #1447 E12).
   *
   * @param cardNumbers die enthaltenen Arbeitspakete — leer statt {@code null}
   * @param redCards die von der fehlgeschlagenen Prüfung betroffenen Karten — leer statt {@code
   *     null}
   * @param pending die noch offenen Prüfungen — leer statt {@code null}
   */
  public record NewReleasePreparation(
      ReleasePreparationResult result,
      @Nullable String commitHash,
      @Nullable String version,
      @Nullable String redCheck,
      List<Integer> cardNumbers,
      List<Integer> redCards,
      List<String> pending) {

    /** Die Listen werden beim Anlegen kopiert und unveränderlich gemacht. */
    public NewReleasePreparation {
      cardNumbers = List.copyOf(cardNumbers);
      redCards = List.copyOf(redCards);
      pending = List.copyOf(pending);
    }
  }

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
   * @param releasePreparation die Morgenmeldung des Laufs (Issue #1457); {@code null} heißt „keine
   *     gemeldet"
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
      @Schema(
              description =
                  "Morgenmeldung: ob und wie der Lauf eine Veröffentlichung vorbereitet hat; null,"
                      + " wenn der Runner keine gemeldet hat.")
          @Nullable ReleasePreparationView releasePreparation,
      NightRunOutcome outcome,
      List<NightRunItemView> items) {}

  /**
   * Die Morgenmeldung eines Laufs in der Laufliste (Issue #1457, Plan #1447 E12), mit aufgelösten
   * Kartentiteln.
   */
  @Schema(description = "Die Morgenmeldung eines Laufs: der vorbereitete Stand und seine Pakete.")
  public record ReleasePreparationView(
      @Schema(
              description =
                  "Ausgang: GREEN grün, von Hand veröffentlichbar; GREEN_PENDING grün, eine"
                      + " Prüfung offen (siehe pending); RED eine Prüfung fehlgeschlagen (siehe"
                      + " redCheck und redCards); NOT_PREPARED nichts vorbereitet.",
              example = "GREEN")
          ReleasePreparationResult result,
      @Schema(
              description =
                  "Kennung des vorbereiteten Stands, mit der er sich außerhalb des Boards"
                      + " wiederfinden lässt.",
              example = "b2ae30f6")
          @Nullable String commitHash,
      @Schema(description = "Beschriftung des Stands, etwa die Versionsnummer.", example = "1.4.0")
          @Nullable String version,
      @Schema(description = "Die fehlgeschlagene Prüfung bei RED.", example = "mvn verify")
          @Nullable String redCheck,
      @Schema(description = "Die noch offenen Prüfungen bei GREEN_PENDING; sonst leer.")
          List<String> pending,
      @Schema(
              description =
                  "Eingang der Meldung nach der Uhr des Servers („gemeldet um“); die Meldung"
                      + " selbst trägt keinen Zeitpunkt der Vorbereitung.",
              example = "2026-10-06T05:12:00Z")
          Instant receivedAt,
      @Schema(
              description =
                  "Die enthaltenen Arbeitspakete in gemeldeter Reihenfolge, auch aus anderen"
                      + " Ketten des Projekts.")
          List<CardTitleView> cards,
      @Schema(description = "Die von der fehlgeschlagenen Prüfung betroffenen Karten.")
          List<CardTitleView> redCards) {}

  /** Eine gemeldete Kartennummer mit ihrem Titel im Projekt des Laufs (Issue #1457). */
  @Schema(description = "Eine gemeldete Karte mit Nummer und Titel.")
  public record CardTitleView(
      @Schema(description = "Projektweite Nummer der Karte.", example = "1449") int number,
      @Schema(
              description = "Titel der Karte; null, wenn es die Nummer im Projekt nicht gibt.",
              example = "Export als CSV")
          @Nullable String title) {}

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
