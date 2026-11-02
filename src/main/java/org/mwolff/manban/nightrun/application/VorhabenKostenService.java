package org.mwolff.manban.nightrun.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.Aufgabenstufe;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.PaketStandView;
import org.mwolff.manban.card.application.CardRunQueryService.VorhabenStandView;
import org.mwolff.manban.common.Wochenfenster;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.ItemKostenJeKarte;
import org.mwolff.manban.nightrun.domain.KostenVoraussage;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Eingaben;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Ergebnis;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.GrundlageVorhaben;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Paket;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Paketstufe;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Preise;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Rechnung;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Vorhaben;
import org.mwolff.manban.nightrun.domain.ModellvergleichErmittlung.Vorgang;
import org.mwolff.manban.nightrun.domain.NightRunAttemptStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trägt die Eingaben der Kostenvoraussage zusammen und rechnet sie mit {@link KostenVoraussage}
 * (Issue #2003, Plan #1990 E3, E6, E7; fachlich #1954 AK 4 bis 7 und 12). Muster ist {@link
 * NightRunModelsService}.
 *
 * <p><b>Quellen:</b> die Kartenlage aus {@link CardRunQueryService#vorhabenStaende}, die Vorgänge
 * aus {@link NightRunRepository#findAttemptsSince}, die Kosten je Karte aus {@link
 * NightRunUsageRepository#itemKostenJeKarte} und die Grenze der Aufbewahrung der Gattung NIGHT aus
 * {@link Aufbewahrungsgrenze}. Gezählt wird nur die Gattung NIGHT; interaktive Sitzungen zählen
 * nicht.
 *
 * <p><b>Grundlage (E3):</b> die übrigen Vorhaben des Projekts, die abgeschlossen sind, deren
 * letztes Paket in den zwölf {@link Wochenfenster}n nach Done kam und die vollständig aufbewahrt
 * sind — die früheste Kostenkarte nicht vor der Grenze, kein Item und kein abgeschlossener Vorgang
 * ohne Kostenangabe. Das Festhalten des Abschlusses folgt in einem eigenen Paket; bis dahin erkennt
 * der Dienst ihn aus dem Stand. Die Vorgänge werden darum ab dem frühesten Beginn der Grundlage
 * gelesen, mindestens ab dem ältesten Wochenfenster.
 *
 * <p><b>Preise (E6):</b> aus allen abgeschlossenen Nachtlauf-Vorgängen der zwölf Wochenfenster mit
 * gemeldetem Modell, je Stufe beim Modell des jüngsten Vorgangs. Ein Paketpreis ist die Summe aller
 * Items eines Pakets in Done, das in den zwölf Wochenfenstern nach Done kam, vollständig aufbewahrt
 * ist und dessen letzter Umsetzungsvorgang mit dem zuletzt umsetzenden Modell lief.
 */
// PMD.CouplingBetweenObjects: Der Dienst trägt Kartenlage, Vorgänge, Kosten je Karte und Grenze für
// die reine Rechnung zusammen; die Typen dieser Quellen und der Eingaben sind sein Zweck.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
public class VorhabenKostenService {

  private final CardRunQueryService cards;
  private final NightRunRepository runs;
  private final NightRunUsageRepository usage;
  private final Aufbewahrungsgrenze grenze;

  public VorhabenKostenService(
      CardRunQueryService cards,
      NightRunRepository runs,
      NightRunUsageRepository usage,
      NightRunProperties properties) {
    this.cards = cards;
    this.runs = runs;
    this.usage = usage;
    this.grenze = new Aufbewahrungsgrenze(properties);
  }

  /**
   * Die aktuelle Voraussage eines Vorhabens. Ohne Rechteprüfung — die übernimmt der Aufrufer.
   *
   * @param projectId das Projekt
   * @param epicId die ID der Vorhaben-Karte
   * @param jetzt der Zeitpunkt, bis zu dem die zwölf Wochenfenster reichen
   * @return die Voraussage oder {@link KostenVoraussage.ZuWenigDaten}; leer, wenn das Vorhaben im
   *     Projekt fehlt oder weder Dokument noch Paket hat (AK 3)
   */
  @Transactional(readOnly = true)
  public Optional<Ergebnis> aktuelleVoraussage(long projectId, long epicId, Instant jetzt) {
    List<VorhabenStandView> staende = cards.vorhabenStaende(projectId);
    Optional<VorhabenStandView> gefunden =
        staende.stream().filter(s -> s.vorhaben().id() == epicId).findFirst();
    if (gefunden.isEmpty()
        || (gefunden.get().dokumente().isEmpty() && gefunden.get().pakete().isEmpty())) {
      return Optional.empty();
    }
    VorhabenStandView ziel = gefunden.get();
    Instant fenster = Wochenfenster.beginn(0, jetzt);
    Optional<Instant> grenzeNacht =
        usage.retentionBoundary(projectId).stream()
            .filter(r -> r.kind() == NightRunKind.NIGHT)
            .findFirst()
            .flatMap(grenze::jeGattung);
    Map<Integer, ItemKostenJeKarte> kosten = kostenJeKarte(projectId, staende);

    List<VorhabenStandView> kandidaten =
        staende.stream()
            .filter(s -> s.vorhaben().id() != epicId)
            .filter(s -> s.abgeschlossen() && abgeschlossenSeit(s, fenster))
            .filter(s -> vollstaendig(s.fruehesteKostenkarteAngelegt(), grenzeNacht))
            .filter(s -> s.kostenkarten().stream().allMatch(n -> ohneLuecke(kosten.get(n))))
            .toList();
    Instant von =
        Stream.concat(Stream.of(ziel), kandidaten.stream())
            .map(VorhabenStandView::fruehesteKostenkarteAngelegt)
            .filter(Objects::nonNull)
            .reduce(fenster, (a, b) -> a.isBefore(b) ? a : b);
    List<Vorgang> nacht =
        runs.findAttemptsSince(projectId, von).stream()
            .filter(v -> v.kind() == NightRunKind.NIGHT)
            .toList();
    List<Vorgang> gezaehlt = nacht.stream().filter(Vorgang::laufAbgeschlossen).toList();

    Eingaben eingaben =
        new Eingaben(
            rechnung(ziel),
            vorhaben(ziel, nacht, kosten, grenzeNacht),
            kandidaten.stream().map(s -> grundlage(s, gezaehlt)).flatMap(Optional::stream).toList(),
            preise(staende, gezaehlt, kosten, fenster, grenzeNacht));
    return Optional.of(KostenVoraussage.rechne(eingaben));
  }

  private Map<Integer, ItemKostenJeKarte> kostenJeKarte(
      long projectId, List<VorhabenStandView> staende) {
    Set<Integer> nummern = new TreeSet<>();
    for (VorhabenStandView stand : staende) {
      nummern.addAll(stand.kostenkarten());
      stand.pakete().forEach(p -> nummern.add(p.number()));
    }
    return usage.itemKostenJeKarte(projectId, nummern, null).stream()
        .collect(Collectors.toMap(ItemKostenJeKarte::cardNumber, Function.identity()));
  }

  /** Erste Stufe, solange ein Dokument ungeschnitten offen ist oder noch kein Paket besteht. */
  private static Rechnung rechnung(VorhabenStandView stand) {
    return ungeschnittenOffen(stand) || stand.pakete().isEmpty()
        ? Rechnung.ANFORDERUNG
        : Rechnung.PAKETE;
  }

  private static boolean ungeschnittenOffen(VorhabenStandView stand) {
    return stand.dokumente().stream().anyMatch(d -> d.offen() && !d.geschnitten());
  }

  /** Der Abschluss gilt beim spätesten Done eines Pakets; fehlt eines, ist er unbekannt. */
  private static boolean abgeschlossenSeit(VorhabenStandView stand, Instant fenster) {
    boolean imFenster = false;
    for (PaketStandView paket : stand.pakete()) {
      Instant done = paket.movedToDoneAt();
      if (done == null) {
        return false;
      }
      imFenster |= !done.isBefore(fenster);
    }
    return imFenster;
  }

  private static boolean vollstaendig(@Nullable Instant fruehester, Optional<Instant> grenze) {
    return fruehester == null || grenze.isEmpty() || !fruehester.isBefore(grenze.get());
  }

  /** Keine Messung an der Karte, oder jede Messung trägt Kosten. */
  private static boolean ohneLuecke(@Nullable ItemKostenJeKarte kosten) {
    return kosten == null || kosten.itemsOhneKosten() == 0;
  }

  private static Vorhaben vorhaben(
      VorhabenStandView stand,
      List<Vorgang> nacht,
      Map<Integer, ItemKostenJeKarte> kosten,
      Optional<Instant> grenze) {
    Set<Integer> karten = Set.copyOf(stand.kostenkarten());
    Map<NightRunAttemptStage, BigDecimal> angefallen = new EnumMap<>(NightRunAttemptStage.class);
    nacht.stream()
        .filter(v -> karten.contains(v.attempt().cardNumber()))
        .forEach(v -> angefallen.merge(v.attempt().stage(), kostenVon(v), BigDecimal::add));
    List<Paket> pakete =
        stand.pakete().stream()
            .map(
                p ->
                    new Paket(
                        p.number(),
                        p.done(),
                        paketstufe(p.aufgabenstufe()),
                        betrag(kosten.get(p.number()))))
            .toList();
    BigDecimal bisher =
        stand.kostenkarten().stream()
            .map(n -> betrag(kosten.get(n)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean unvollstaendig =
        !vollstaendig(stand.fruehesteKostenkarteAngelegt(), grenze)
            || !stand.kostenkarten().stream().allMatch(n -> ohneLuecke(kosten.get(n)));
    return new Vorhaben(
        stand.hatAnforderung(),
        ungeschnittenOffen(stand),
        angefallen,
        pakete,
        bisher,
        unvollstaendig);
  }

  /**
   * Vorgänge je Stufe und Paketzahl eines Grundlage-Vorhabens; leer, wenn einer seiner Vorgänge
   * keine Kostenangabe trägt (AK 7).
   */
  private static Optional<GrundlageVorhaben> grundlage(
      VorhabenStandView stand, List<Vorgang> gezaehlt) {
    Set<Integer> karten = Set.copyOf(stand.kostenkarten());
    List<Vorgang> eigene =
        gezaehlt.stream().filter(v -> karten.contains(v.attempt().cardNumber())).toList();
    if (eigene.stream().anyMatch(v -> kostenOderNull(v) == null)) {
      return Optional.empty();
    }
    Map<NightRunAttemptStage, Integer> jeStufe = new EnumMap<>(NightRunAttemptStage.class);
    eigene.forEach(v -> jeStufe.merge(v.attempt().stage(), 1, Integer::sum));
    return Optional.of(new GrundlageVorhaben(jeStufe, stand.pakete().size()));
  }

  private static Preise preise(
      List<VorhabenStandView> staende,
      List<Vorgang> gezaehlt,
      Map<Integer, ItemKostenJeKarte> kosten,
      Instant fenster,
      Optional<Instant> grenze) {
    List<Vorgang> imFenster =
        gezaehlt.stream()
            .filter(v -> v.attempt().model() != null && !v.laufStart().isBefore(fenster))
            .toList();
    Map<NightRunAttemptStage, List<BigDecimal>> jeStufe = new EnumMap<>(NightRunAttemptStage.class);
    for (NightRunAttemptStage stufe : NightRunAttemptStage.values()) {
      List<Vorgang> derStufe =
          imFenster.stream().filter(v -> v.attempt().stage() == stufe).toList();
      juengsterModell(derStufe)
          .ifPresent(
              modell ->
                  jeStufe.put(
                      stufe,
                      derStufe.stream()
                          .filter(v -> modell.equals(v.attempt().model()))
                          .map(VorhabenKostenService::kostenOderNull)
                          .filter(Objects::nonNull)
                          .toList()));
    }

    Optional<String> umsetzendes =
        juengsterModell(
            imFenster.stream()
                .filter(v -> v.attempt().stage() == NightRunAttemptStage.UMSETZUNG)
                .toList());
    Map<Integer, PaketStandView> pakete = new LinkedHashMap<>();
    staende.forEach(s -> s.pakete().forEach(p -> pakete.putIfAbsent(p.number(), p)));
    Map<Paketstufe, List<BigDecimal>> paketJeStufe = new EnumMap<>(Paketstufe.class);
    List<BigDecimal> paketAlle = new ArrayList<>();
    for (PaketStandView paket : pakete.values()) {
      Optional<BigDecimal> preis =
          paketpreis(paket, gezaehlt, kosten, fenster, grenze, umsetzendes);
      preis.ifPresent(
          p -> {
            paketAlle.add(p);
            Paketstufe stufe = paketstufe(paket.aufgabenstufe());
            if (stufe != null) {
              paketJeStufe.computeIfAbsent(stufe, s -> new ArrayList<>()).add(p);
            }
          });
    }
    return new Preise(jeStufe, paketJeStufe, List.copyOf(paketAlle));
  }

  private static Optional<BigDecimal> paketpreis(
      PaketStandView paket,
      List<Vorgang> gezaehlt,
      Map<Integer, ItemKostenJeKarte> kosten,
      Instant fenster,
      Optional<Instant> grenze,
      Optional<String> umsetzendes) {
    Instant done = paket.movedToDoneAt();
    ItemKostenJeKarte summe = kosten.get(paket.number());
    if (!paket.done()
        || done == null
        || done.isBefore(fenster)
        || !vollstaendig(paket.createdAt(), grenze)
        || summe == null
        || summe.itemsOhneKosten() > 0
        || summe.costUsd() == null) {
      return Optional.empty();
    }
    Optional<String> letztes =
        juengsterModell(
            gezaehlt.stream()
                .filter(v -> v.attempt().stage() == NightRunAttemptStage.UMSETZUNG)
                .filter(v -> v.attempt().cardNumber() == paket.number())
                .filter(v -> v.attempt().model() != null)
                .toList());
    return letztes.isPresent() && letztes.equals(umsetzendes)
        ? Optional.of(summe.costUsd())
        : Optional.empty();
  }

  /** Das Modell des jüngsten Vorgangs mit Modell. */
  private static Optional<String> juengsterModell(Collection<Vorgang> vorgaenge) {
    return vorgaenge.stream()
        .filter(v -> v.attempt().model() != null)
        .max(Comparator.comparingInt(Vorgang::position))
        .map(v -> v.attempt().model());
  }

  private static @Nullable BigDecimal kostenOderNull(Vorgang vorgang) {
    NightRunUsage verbrauch = vorgang.attempt().usage();
    return verbrauch == null ? null : verbrauch.costUsd();
  }

  /** Eine fehlende Kostenangabe zieht nichts ab — sie zählt beim Vorhaben als unvollständig. */
  private static BigDecimal kostenVon(Vorgang vorgang) {
    BigDecimal betrag = kostenOderNull(vorgang);
    return betrag == null ? BigDecimal.ZERO : betrag;
  }

  private static BigDecimal betrag(@Nullable ItemKostenJeKarte kosten) {
    return kosten == null || kosten.costUsd() == null ? BigDecimal.ZERO : kosten.costUsd();
  }

  private static @Nullable Paketstufe paketstufe(@Nullable Aufgabenstufe stufe) {
    return stufe == null ? null : Paketstufe.valueOf(stufe.name());
  }
}
