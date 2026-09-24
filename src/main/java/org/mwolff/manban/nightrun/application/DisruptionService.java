package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;
import org.mwolff.manban.nightrun.domain.NightRunPeriod;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Cases des Plattform-Leitstands: seine drei Listen über alle teilnehmenden Projekte und das
 * Quittieren einer Störung (Issue #1080, Plan #1072 E29; um die Listen der laufenden Nacht
 * erweitert in Issue #1095).
 *
 * <p><b>Eigener Dienst neben {@link NightRunService}</b>, obwohl beide dieselben Tabellen lesen.
 * Der Grund ist die Rechteregel: Jeder Use-Case von {@code NightRunService} verlangt eine
 * <em>Projekt</em>-Rolle, und der Klassenkommentar dort trägt genau diese Zusage. Die beiden
 * Use-Cases hier gehen über alle Projekte und prüfen die <em>Plattform</em>-Rolle — sie wären dort
 * die Ausnahme, die man beim Lesen übersieht. {@link NightRunUsageService} ist der Präzedenzfall
 * für einen zweiten Dienst im selben Modul.
 */
@Service
public class DisruptionService {

  private final DisruptionRepository repository;
  private final NightRunRepository runs;
  private final CardService cards;
  private final PlatformAdminChecker platformAdminChecker;
  private final NightRunProperties properties;
  private final Clock clock;

  public DisruptionService(
      DisruptionRepository repository,
      NightRunRepository runs,
      CardService cards,
      PlatformAdminChecker platformAdminChecker,
      NightRunProperties properties,
      Clock clock) {
    this.repository = repository;
    this.runs = runs;
    this.cards = cards;
    this.platformAdminChecker = platformAdminChecker;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Die drei Listen des Plattform-Leitstands in <b>einem</b> Abruf (Issue #1095, Plan #1088 E5).
   *
   * <p><b>Warum einer und nicht drei:</b> Die Seite frischt sich alle 30 Sekunden auf (Kriterium
   * 19). Drei Abfragen wären drei Rundreisen gegen einen Stand, der sich dazwischen ändert — ein
   * Lauf, der zwischen der ersten und der zweiten Antwort endet, erschiene doppelt oder gar nicht.
   * Aus demselben Grund steht hier <b>eine</b> Rechteprüfung für alle drei Listen (Kriterium 15).
   *
   * <p>Die Datenbank liefert die Kandidaten, die Domäne entscheidet: Über die Aufteilung in
   * laufende und durchgeführte Läufe befindet allein der {@link NightRunOutcome}, nicht {@code
   * complete}. Ein verstummter Lauf trägt {@code complete = false} und steht trotzdem unter den
   * durchgeführten — das leistet die Stillefrist aus Issue #1091.
   *
   * <p><b>Die Nachtgrenze gilt nur den durchgeführten Läufen</b> (Issue #1109): „Aktive Läufe" ist
   * jeder Kandidat mit {@link Verdict#RUNNING}, gleich wann er begann — #1086 AK 1 kennt für diesen
   * Bereich keine Grenze, sie zieht AK 9 ausdrücklich nur für die beendeten. Ein Kettenlauf, der um
   * 10:27 beginnt und über Mittag geht, bleibt so sichtbar, statt für seine ganze Restlaufzeit zu
   * verschwinden.
   *
   * <p><b>Warum die Grenze für die beendeten ausdrücklich hier steht</b> und nicht aus der Abfrage
   * fällt: Deren zweiter Zweig liefert auch Läufe früherer Nächte. Lässt die Stillefrist einen
   * davon zwischen Abfrage und Auswertung verstummen, stünde er ohne die Grenze unter den beendeten
   * Läufen der <em>neuen</em> Nacht — gegen AK 9.
   *
   * <p><b>Beendet heißt: dieser und der vorige Zyklus</b> (Issue #1135, erweitert Kriterium 9 aus
   * #1086). Die Abfrage reicht deshalb vom Beginn des vorigen Zyklus bis zum Ende des laufenden;
   * die beiden Listen trennt die Grenze um 12:00. Wer um 13:00 nachsieht, findet die Läufe der
   * vergangenen Nacht unter „Voriger Zyklus" statt einer leeren Liste. Ältere Läufe fallen weg.
   *
   * <p><b>Die Zone kommt vom Leser</b> (Plan #1088 E6): Im Container läuft die JVM regelmäßig in
   * UTC, und „12:00 zonenlokal" wäre dann 14:00 in Berlin — die Nachtgrenze läge um Stunden
   * verschoben gegen die, die die Nachtlauf-Auswertung zieht.
   *
   * @param zone Zone, in der die Grenzen der laufenden Nacht gezogen werden
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist (403)
   */
  @Transactional(readOnly = true)
  public LeitstandView leitstand(long userId, ZoneId zone) {
    requirePlatformAdmin(userId);
    Instant jetzt = clock.instant();
    NightRunPeriod nacht = NightRunPeriod.laufendeNacht(jetzt, zone);
    NightRunPeriod vorige = nacht.previous();
    List<DisruptionRepository.DisruptionCandidate> kandidaten =
        repository.candidatesOfNight(vorige.from(), nacht.to(), jetzt, properties.stilleFrist());
    List<DisruptionRepository.DisruptionCandidate> offene = repository.openCandidates();
    Map<Long, List<NightRunItem>> jeLauf = pakete(kandidaten, offene);
    List<DisruptionView> zeilen = views(kandidaten, jeLauf);
    List<DisruptionView> beendete =
        zeilen.stream().filter(v -> v.outcome().verdict() != Verdict.RUNNING).toList();
    List<DisruptionView> laufende =
        zeilen.stream().filter(v -> v.outcome().verdict() == Verdict.RUNNING).toList();
    return new LeitstandView(
        laufende,
        beendete.stream().filter(v -> nacht.contains(v.startedAt())).toList(),
        beendete.stream().filter(v -> vorige.contains(v.startedAt())).toList(),
        views(offene, jeLauf).stream().filter(v -> v.outcome().isDisruption()).toList(),
        gemeldetePakete(laufende, jeLauf));
  }

  /**
   * Die gemeldeten Pakete der <b>arbeitenden</b> Läufe, in deren Reihenfolge (Issue #1170).
   *
   * <p>Kein zweiter Abruf: {@link #pakete} hat die Zeilen beider Abfragen schon geholt; hier werden
   * nur die der laufenden Läufe herausgegriffen. Ein laufender Lauf ohne Pakete steht mit leerer
   * Liste dabei — „noch nichts gemeldet" ist eine Auskunft, sein Fehlen wäre keine.
   */
  private List<LaufPaketeView> gemeldetePakete(
      List<DisruptionView> laufende, Map<Long, List<NightRunItem>> jeLauf) {
    List<NightRunItem> items =
        laufende.stream()
            .flatMap(v -> jeLauf.getOrDefault(v.nightRunId(), List.<NightRunItem>of()).stream())
            .toList();
    Map<Long, Set<Integer>> vorhanden = vorhandeneKarten(items);
    return laufende.stream()
        .map(
            v ->
                new LaufPaketeView(
                    v.nightRunId(),
                    jeLauf.getOrDefault(v.nightRunId(), List.<NightRunItem>of()).stream()
                        .map(
                            i ->
                                new PaketView(
                                    i.cardNumber(),
                                    i.title(),
                                    i.state(),
                                    i.errorClass(),
                                    vorhanden
                                        .getOrDefault(i.projectId(), Set.of())
                                        .contains(i.cardNumber())))
                        .toList()))
        .toList();
  }

  /**
   * Die vorhandenen Kartennummern je Projekt — <b>ein</b> Abruf je Projekt, nicht einer je Paket.
   *
   * <p>Über alle laufenden Läufe wären das sonst dutzende Abfragen bei jedem Auffrischen der Seite.
   */
  private Map<Long, Set<Integer>> vorhandeneKarten(List<NightRunItem> items) {
    return items.stream()
        .collect(
            Collectors.groupingBy(
                NightRunItem::projectId,
                Collectors.mapping(NightRunItem::cardNumber, Collectors.toSet())))
        .entrySet()
        .stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, e -> cards.existingCardNumbers(e.getKey(), e.getValue())));
  }

  /**
   * Die Pakete beider Abfragen in <b>einem</b> Zug, je Lauf gebündelt.
   *
   * <p>Ein Lauf kann in beiden Listen stehen — eine Störung der laufenden Nacht steht unter den
   * durchgeführten <em>und</em> unter den Störungen. Zwei Abfragen liefen deshalb zweimal über
   * dieselben Zeilen; die Vereinigung hält die Reihenfolge der Nacht-Abfrage vorn, damit der Aufruf
   * vorhersagbar bleibt.
   */
  private Map<Long, List<NightRunItem>> pakete(
      List<DisruptionRepository.DisruptionCandidate> derNacht,
      List<DisruptionRepository.DisruptionCandidate> offene) {
    Set<Long> laufIds =
        Stream.concat(derNacht.stream(), offene.stream())
            .map(DisruptionRepository.DisruptionCandidate::nightRunId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (laufIds.isEmpty()) {
      return Map.of();
    }
    return runs.findItemsByRunIds(List.copyOf(laufIds)).stream()
        .collect(Collectors.groupingBy(NightRunItem::nightRunId));
  }

  private List<DisruptionView> views(
      List<DisruptionRepository.DisruptionCandidate> kandidaten,
      Map<Long, List<NightRunItem>> jeLauf) {
    return kandidaten.stream()
        .map(k -> view(k, jeLauf.getOrDefault(k.nightRunId(), List.of())))
        .toList();
  }

  /**
   * Quittiert die Störung eines Laufs — „ich habe es gesehen" (AK 8).
   *
   * <p><b>Idempotent:</b> Ein zweiter Aufruf ist kein Fehler. Zwei Admins können dieselbe Zeile
   * gleichzeitig wegräumen, und der zweite soll dabei nichts Rotes sehen.
   *
   * <p>Der Lauf selbst bleibt unberührt (AK 10) — die Quittung liegt in einer eigenen Tabelle und
   * sagt nichts über den Lauf, nur über seine Sichtung.
   *
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist (403)
   * @throws DisruptionNotFoundException wenn der Lauf unbekannt ist — auch, weil ihn der Ringpuffer
   *     verdrängt hat — oder sein Projekt nicht teilnimmt (404)
   */
  @Transactional
  public void acknowledge(long userId, long nightRunId) {
    requirePlatformAdmin(userId);
    if (repository.ackTarget(nightRunId).isEmpty()) {
      throw new DisruptionNotFoundException();
    }
    repository.acknowledge(nightRunId, userId, clock.instant());
  }

  /**
   * Kennzeichnet einen hängenden Lauf von Hand als beendet (Issue #1197).
   *
   * <p><b>Warum es das gibt:</b> Ein Lauf, dessen Prozess weg ist, meldet sich nie ab und stünde
   * für immer unter den laufenden. Warum er sich nicht abmeldet, ist ein eigenes Problem — hier
   * wird nur die ausgebliebene Meldung ersetzt. Den Prozess berührt das nicht, er ist ohnehin weg.
   *
   * <p><b>Nur ein laufender Lauf</b> lässt sich kennzeichnen. Ein verstummter ist heute eine
   * Störung und bleibt es: Für ihn gibt es das Quittieren, und das sagt etwas anderes. Ein
   * abgeschlossener trägt seinen gemeldeten Ausgang.
   *
   * <p><b>Idempotent</b> wie das Quittieren: Ein bereits gekennzeichneter Lauf ist kein Fehler,
   * sondern das Ziel des Aufrufs — zwei Admins können dieselbe Zeile gleichzeitig wegräumen. Der
   * erste Kennzeichnende bleibt vermerkt.
   *
   * <p>Der Ausgang kommt aus {@link NightRunOutcome} und wird hier nicht nachgerechnet (dieselbe
   * Zusage wie in {@link #leitstand}): Was der Leitstand als laufend zeigt, ist genau das, was sich
   * kennzeichnen lässt. Die Pakete werden dafür geholt, obwohl ein unfertiger Lauf sie für seinen
   * Ausgang nicht braucht — mit einer leeren Liste wäre es derselbe Maßstab nur unter einer
   * Annahme, die beim nächsten Feinschliff der Rangfolge stillschweigend bräche.
   *
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist (403)
   * @throws DisruptionNotFoundException wenn der Lauf unbekannt ist oder sein Projekt nicht
   *     teilnimmt (404)
   * @throws NightRunNotRunningException wenn der Lauf weder läuft noch schon gekennzeichnet ist
   *     (409)
   */
  @Transactional
  public void close(long userId, long nightRunId) {
    requirePlatformAdmin(userId);
    DisruptionRepository.DisruptionCandidate kandidat =
        repository.candidate(nightRunId).orElseThrow(DisruptionNotFoundException::new);
    Verdict ausgang =
        view(kandidat, runs.findItemsByRunIds(List.of(nightRunId))).outcome().verdict();
    if (ausgang == Verdict.CLOSED) {
      return;
    }
    if (ausgang != Verdict.RUNNING) {
      throw new NightRunNotRunningException();
    }
    repository.close(nightRunId, userId, clock.instant());
  }

  private void requirePlatformAdmin(long userId) {
    if (!platformAdminChecker.isPlatformAdmin(userId)) {
      throw new AdminAccessDeniedException();
    }
  }

  /**
   * Die Zeile eines Kandidaten — dieselbe für alle drei Listen.
   *
   * <p><b>Auch die Laufart kommt vom Kandidaten</b> (Issue #1123): Bei einer Kette ist unter
   * gleichrangigen Paketen das letzte maßgeblich, und ohne die Laufart zeigte die Störzeile die
   * Ketten-Einheit statt des Pakets, an dem die Kette riss.
   *
   * <p><b>Abschluss und Lebenszeichen kommen vom Kandidaten</b>, nicht als Festwert. Über {@link
   * DisruptionRepository#openCandidates()} ist {@code complete} stets {@code true}, weil die
   * Abfrage darauf filtert; über {@link DisruptionRepository#candidatesOfNight} nicht — dort
   * entscheidet erst die Stillefrist (Issue #1091), ob ein unfertiger Lauf noch läuft oder
   * verstummt ist.
   *
   * <p><b>Auch der Abbruchgrund kommt vom Kandidaten</b> (Issue #1143): Er erzwingt {@code FAILED}
   * und bringt den Lauf damit in die Störungsliste — ohne ihn erschiene ein Lauf, der nach drei
   * grünen Paketen abbrach, als gelungen.
   */
  private DisruptionView view(
      DisruptionRepository.DisruptionCandidate k, List<NightRunItem> items) {
    return new DisruptionView(
        k.nightRunId(),
        k.projectId(),
        k.projectName(),
        k.mode(),
        k.startedAt(),
        NightRunOutcome.of(
            k.complete(),
            // Issue #1197: Die Kennzeichnung eines Admins ersetzt die ausgebliebene Abmeldung —
            // der Lauf steht danach unter den beendeten statt ewig unter den laufenden.
            k.closedAt(),
            k.noWorkReason(),
            // Dieselben Argumente wie in NightRunService.view (Issue #1143): Ein Lauf, der seinen
            // Abbruch meldete, ist in beiden Auswertungswegen gescheitert — nicht hier gelungen
            // und dort gestört (AK 8 der fachlichen Quelle #1074).
            k.abortReason(),
            k.mode(),
            items,
            k.startedAt(),
            k.updatedAt(),
            clock.instant(),
            properties.stilleFrist()));
  }

  /**
   * Eine Störzeile, wie der Plattform-Leitstand sie zeigt.
   *
   * <p>Der <b>Grund</b> steht nicht als Text hier, sondern als {@link NightRunOutcome}: Der Browser
   * bildet ihn aus denselben Tabellen, aus denen die Nachtlauf-Auswertung ihn zeigt. Ein zweiter
   * Satz im Server wäre die zweite Formulierung desselben Sachverhalts, die AK 6 verbietet.
   *
   * <p>Die <b>Art</b> des Laufs ({@code mode}) steht seit Issue #1128 dabei: Der
   * Plattform-Leitstand zeigt sie als Marke an jeder Zeile.
   */
  public record DisruptionView(
      long nightRunId,
      long projectId,
      String projectName,
      NightRunMode mode,
      Instant startedAt,
      NightRunOutcome outcome) {}

  /**
   * Die drei Bereiche des Plattform-Leitstands in ihrer Ordnung (Kriterium 18) — vier Listen, denn
   * die laufenden Läufe tragen ihre gemeldeten Pakete daneben (Issue #1170).
   *
   * <p>Dieselbe Zeilenform für alle vier Lauf-Listen: Ein laufender Lauf, ein durchgeführter und
   * eine Störung tragen dieselben Angaben — Projekt, Startzeitpunkt, Befund —, und woraus der
   * Browser welchen Melder und welches Wort bildet, steht im Befund.
   *
   * @param laufende Läufe, die noch arbeiten — <b>ohne</b> Nachtgrenze, also auch die einer
   *     früheren Nacht, die über Mittag weiterlaufen (Issue #1109); jüngster zuoberst
   * @param durchgefuehrte beendete Läufe des <b>laufenden Zyklus</b>, verstummte eingeschlossen;
   *     jüngster zuoberst
   * @param durchgefuehrteVoriger beendete Läufe des <b>vorigen Zyklus</b> (Issue #1135), in
   *     derselben Form und Ordnung
   * @param stoerungen offene Störungen über <b>alle</b> Nächte (Kriterium 17), jüngste zuoberst
   * @param gemeldetePakete die Pakete der Läufe aus {@code laufende}, in derselben Ordnung — ein
   *     Eintrag je laufender Lauf, auch ohne ein einziges Paket
   */
  public record LeitstandView(
      List<DisruptionView> laufende,
      List<DisruptionView> durchgefuehrte,
      List<DisruptionView> durchgefuehrteVoriger,
      List<DisruptionView> stoerungen,
      List<LaufPaketeView> gemeldetePakete) {}

  /**
   * Die gemeldeten Pakete eines laufenden Laufs.
   *
   * <p><b>Warum eine eigene Liste neben {@link LeitstandView#laufende()}</b> und kein Feld an
   * {@link DisruptionView} (Plan #1167, E1): {@code DisruptionView} ist die eine Zeilenform aller
   * vier {@code DisruptionView}-Listen. Ein nur dort gefülltes Feld stünde in den anderen leer und
   * behauptete „keine Pakete" statt „nicht gefragt" — und für die beendeten Läufe schickte es Daten
   * hinaus, die AK 11 der Quelle #1153 nicht zeigt.
   *
   * @param nightRunId Lauf, dem die Pakete gehören — derselbe Wert wie in seiner Zeile
   * @param pakete in der Reihenfolge, in der der Lauf sie meldete; leer, wenn noch keines vorliegt
   */
  public record LaufPaketeView(long nightRunId, List<PaketView> pakete) {}

  /**
   * Ein gemeldetes Paket, wie die Sektion „Aktueller Status" es zeigt.
   *
   * <p><b>Warum so schmal</b> (Plan #1167, E2): {@code NightRunItemView} durchzureichen brächte
   * Kosten, Dauer, Stufen, Verbrauch und vor allem den Protokollauszug je Paket mit. AK 7 der
   * Quelle #1153 verbietet jede Obergrenze für diese Sektion — der Auszug über alle laufenden Läufe
   * wäre damit die größte Last der Antwort. Was nicht hinausgeht, kann auch nicht versehentlich
   * erscheinen.
   *
   * @param cardNumber projektweite Kartennummer des Pakets
   * @param title Titel zum Zeitpunkt des Laufs — ein Schnappschuss, kein Verweis
   * @param state Ausgang des Pakets, auch {@link NightRunState#GREY} für ein zurückgestelltes
   * @param errorClass Grund für einen nicht-grünen Ausgang; {@code null} bei grün
   * @param cardExists ob es im Projekt noch eine Karte zu dieser Nummer gibt — der Leitstand
   *     verlinkt nur dann dorthin
   */
  public record PaketView(
      int cardNumber,
      String title,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      boolean cardExists) {}
}
