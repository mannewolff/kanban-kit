package org.mwolff.manban.nightrun.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
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
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.FestgehalteneVoraussage;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.VorhabenKosten;
import org.mwolff.manban.nightrun.domain.KostenVoraussage;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Eingaben;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Ergebnis;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.GrundlageVorhaben;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Paket;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Paketstufe;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Preise;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Rechnung;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Voraussage;
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
 * <p><b>Grundlage (E3):</b> die übrigen Vorhaben des Projekts, deren Abschluss in den zwölf {@link
 * Wochenfenster}n festgehalten wurde und die vollständig aufbewahrt sind — die früheste Kostenkarte
 * nicht vor der Grenze, kein Item und kein abgeschlossener Vorgang ohne Kostenangabe. Maßgeblich
 * ist der festgehaltene Abschluss aus {@link VorhabenKostenRepository} (Issue #2004), nicht das
 * späteste Done der Pakete. Die Vorgänge werden ab dem frühesten Beginn der Grundlage gelesen,
 * mindestens ab dem ältesten Wochenfenster.
 *
 * <p><b>Festhalten:</b> {@link #festhalten} schreibt die Übergänge je Vorhaben, aufgerufen vom
 * geplanten Job je Projekt in einer eigenen Transaktion.
 *
 * <p><b>Preise (E6):</b> aus allen abgeschlossenen Nachtlauf-Vorgängen der zwölf Wochenfenster mit
 * gemeldetem Modell, je Stufe beim Modell des jüngsten Vorgangs. Ein Paketpreis ist die Summe aller
 * Items eines Pakets in Done, das in den zwölf Wochenfenstern nach Done kam, vollständig aufbewahrt
 * ist und dessen letzter Umsetzungsvorgang mit dem zuletzt umsetzenden Modell lief.
 */
// PMD.CouplingBetweenObjects: Der Dienst trägt Kartenlage, Vorgänge, Kosten je Karte und Grenze für
// die reine Rechnung zusammen; die Typen dieser Quellen und der Eingaben sind sein Zweck.
// PMD.ExcessiveImports, PMD.GodClass: dieselbe Ursache, anders gezählt — mit Issue #2004 kommen
// die festgehaltenen Zeilen dazu. Festhalten und Voraussage teilen sich Kontext und Grundlage; eine
// Aufteilung verteilte das Zusammentragen auf zwei Klassen, die dieselben Quellen kennen müssten.
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.ExcessiveImports", "PMD.GodClass"})
@Service
public class VorhabenKostenService {

  private final CardRunQueryService cards;
  private final NightRunRepository runs;
  private final NightRunUsageRepository usage;
  private final VorhabenKostenRepository festgehalten;
  private final Aufbewahrungsgrenze grenze;

  public VorhabenKostenService(
      CardRunQueryService cards,
      NightRunRepository runs,
      NightRunUsageRepository usage,
      VorhabenKostenRepository festgehalten,
      NightRunProperties properties) {
    this.cards = cards;
    this.runs = runs;
    this.usage = usage;
    this.festgehalten = festgehalten;
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
    Kontext kontext = kontext(projectId, jetzt);
    Optional<VorhabenStandView> gefunden =
        kontext.staende().stream().filter(s -> s.vorhaben().id() == epicId).findFirst();
    if (gefunden.isEmpty()
        || (gefunden.get().dokumente().isEmpty() && gefunden.get().pakete().isEmpty())) {
      return Optional.empty();
    }
    VorhabenStandView ziel = gefunden.get();
    return Optional.of(rechne(kontext, ziel, rechnung(ziel)));
  }

  /** Die Projekte mit mindestens einem Vorhaben, deren Kosten der Job festhält (Issue #2004). */
  public List<Long> projekteMitVorhaben() {
    return cards.projekteMitVorhaben();
  }

  /**
   * Hält die Übergänge aller Vorhaben des Projekts fest (Issue #2004, Plan #1990 E8 bis E10, E12;
   * fachlich #1954 AK 8). Geschrieben wird nur eine Zeile, die sich ändert.
   *
   * <ul>
   *   <li>Erstes Paket ohne Voraussage aus der Anforderung: sie wird festgehalten — „keine
   *       Voraussage“, wenn nie ein ungeschnittenes Dokument da war oder die Rechnung zu wenig
   *       Daten hat.
   *   <li>Paket da und nichts mehr ungeschnitten offen, ohne Voraussage aus den Paketen: sie wird
   *       festgehalten. Beide Übergänge im selben Lauf folgen in dieser Reihenfolge (E12).
   *   <li>Abgeschlossen (E10): Zeitpunkt, tatsächliche Kosten bis dahin, „unvollständig“ nach E3
   *       und die Kostenkarten. Ist der Abschluss schon festgehalten, werden die Kosten über die
   *       festgehaltenen Kostenkarten nachgerechnet, und der größere Wert bleibt (E9).
   *   <li>Wieder offen: Der Abschluss entfällt, die Voraussagen bleiben.
   * </ul>
   *
   * <p><b>Einführung (E8):</b> Beim ersten Lauf für das Projekt ergeben Zeitpunkte, die schon
   * vorbei sind, „keine Voraussage“, und ein schon abgeschlossenes Vorhaben gilt beim spätesten
   * Done seiner Pakete als abgeschlossen. Danach wird die Einführung vermerkt.
   *
   * @param projectId das Projekt
   * @param jetzt der Zeitpunkt des Laufs
   */
  @Transactional
  public void festhalten(long projectId, Instant jetzt) {
    Kontext kontext = kontext(projectId, jetzt);
    boolean einfuehrung = festgehalten.eingefuehrtAm(projectId).isEmpty();
    for (VorhabenStandView stand : kontext.staende()) {
      VorhabenKosten alt = kontext.zeilen().get(stand.vorhaben().id());
      VorhabenKosten neu = naechsteZeile(kontext, stand, alt, jetzt, einfuehrung);
      if (neu != null && !neu.equals(alt)) {
        festgehalten.speichere(neu);
      }
    }
    if (einfuehrung) {
      festgehalten.einfuehren(projectId, jetzt);
    }
  }

  private @Nullable VorhabenKosten naechsteZeile(
      Kontext kontext,
      VorhabenStandView stand,
      @Nullable VorhabenKosten alt,
      Instant jetzt,
      boolean einfuehrung) {
    if (stand.pakete().isEmpty() && alt == null) {
      return null;
    }
    VorhabenKosten zeile =
        voraussagen(
            kontext,
            stand,
            alt == null ? VorhabenKosten.leer(stand.vorhaben().id(), kontext.projectId()) : alt,
            jetzt,
            einfuehrung);
    return abschluss(
        kontext, stand, zeile, einfuehrung ? spaetestesDone(stand).orElse(jetzt) : jetzt);
  }

  /** Erst die Voraussage aus der Anforderung, dann die aus den Paketen (E12). */
  private VorhabenKosten voraussagen(
      Kontext kontext,
      VorhabenStandView stand,
      VorhabenKosten zeile,
      Instant jetzt,
      boolean einfuehrung) {
    if (stand.pakete().isEmpty()) {
      return zeile;
    }
    VorhabenKosten ergebnis = zeile;
    if (ergebnis.anforderung() == null) {
      ergebnis =
          ergebnis.mitAnforderung(
              einfuehrung || !stand.hatteUngeschnittenesDokument()
                  ? FestgehalteneVoraussage.keine(jetzt)
                  : voraussageFesthalten(kontext, stand, Rechnung.ANFORDERUNG, jetzt));
    }
    if (!ungeschnittenOffen(stand) && ergebnis.pakete() == null) {
      ergebnis =
          ergebnis.mitPaketen(
              einfuehrung
                  ? FestgehalteneVoraussage.keine(jetzt)
                  : voraussageFesthalten(kontext, stand, Rechnung.PAKETE, jetzt));
    }
    return ergebnis;
  }

  private FestgehalteneVoraussage voraussageFesthalten(
      Kontext kontext, VorhabenStandView stand, Rechnung rechnung, Instant jetzt) {
    return rechne(kontext, stand, rechnung) instanceof Voraussage voraussage
        ? FestgehalteneVoraussage.beziffert(voraussage, jetzt)
        : FestgehalteneVoraussage.keine(jetzt);
  }

  private VorhabenKosten abschluss(
      Kontext kontext, VorhabenStandView stand, VorhabenKosten zeile, Instant zeitpunkt) {
    Instant abgeschlossen = zeile.abgeschlossenAt();
    if (!stand.abgeschlossen()) {
      return abgeschlossen == null ? zeile : zeile.wiederOffen();
    }
    if (abgeschlossen == null) {
      List<ItemKostenJeKarte> bisDahin =
          usage.itemKostenJeKarte(kontext.projectId(), stand.kostenkarten(), zeitpunkt);
      boolean unvollstaendig =
          !vollstaendig(stand.fruehesteKostenkarteAngelegt(), kontext.grenze())
              || bisDahin.stream().anyMatch(k -> !ohneLuecke(k));
      return zeile.mitAbschluss(zeitpunkt, summe(bisDahin), unvollstaendig, stand.kostenkarten());
    }
    BigDecimal nachgerechnet =
        summe(usage.itemKostenJeKarte(kontext.projectId(), zeile.kostenKarten(), abgeschlossen));
    BigDecimal bisher = zeile.tatsaechlichUsd();
    return bisher == null || nachgerechnet.compareTo(bisher) > 0
        ? zeile.mitAbschluss(
            abgeschlossen, nachgerechnet, zeile.tatsaechlichUnvollstaendig(), zeile.kostenKarten())
        : zeile;
  }

  /** Das späteste Done der Pakete; leer, wenn eines keinen Zeitpunkt trägt. */
  private static Optional<Instant> spaetestesDone(VorhabenStandView stand) {
    Instant spaetestes = null;
    for (PaketStandView paket : stand.pakete()) {
      Instant done = paket.movedToDoneAt();
      if (done == null) {
        return Optional.empty();
      }
      spaetestes = spaetestes == null || done.isAfter(spaetestes) ? done : spaetestes;
    }
    return Optional.ofNullable(spaetestes);
  }

  private static BigDecimal summe(List<ItemKostenJeKarte> zeilen) {
    return zeilen.stream()
        .map(VorhabenKostenService::betrag)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** Was ein Lauf des Projekts einmal liest. */
  private Kontext kontext(long projectId, Instant jetzt) {
    List<VorhabenStandView> staende = cards.vorhabenStaende(projectId);
    Map<Long, VorhabenKosten> zeilen = new HashMap<>();
    festgehalten.findByProjectId(projectId).forEach(z -> zeilen.put(z.epicId(), z));
    Optional<Instant> grenzeNacht =
        usage.retentionBoundary(projectId).stream()
            .filter(r -> r.kind() == NightRunKind.NIGHT)
            .findFirst()
            .flatMap(grenze::jeGattung);
    return new Kontext(
        projectId,
        staende,
        zeilen,
        Wochenfenster.beginn(0, jetzt),
        grenzeNacht,
        kostenJeKarte(projectId, staende));
  }

  /**
   * Die Rechnung für ein Vorhaben. Grundlage sind die übrigen Vorhaben mit festgehaltenem Abschluss
   * in den zwölf Wochenfenstern, die vollständig aufbewahrt sind (E3).
   */
  private Ergebnis rechne(Kontext kontext, VorhabenStandView ziel, Rechnung rechnung) {
    Map<Integer, ItemKostenJeKarte> kosten = kontext.kosten();
    List<VorhabenStandView> kandidaten =
        kontext.staende().stream()
            .filter(s -> s.vorhaben().id() != ziel.vorhaben().id())
            .filter(s -> abgeschlossenSeit(kontext.zeilen().get(s.vorhaben().id()), kontext))
            .filter(s -> vollstaendig(s.fruehesteKostenkarteAngelegt(), kontext.grenze()))
            .filter(s -> s.kostenkarten().stream().allMatch(n -> ohneLuecke(kosten.get(n))))
            .toList();
    Instant von =
        Stream.concat(Stream.of(ziel), kandidaten.stream())
            .map(VorhabenStandView::fruehesteKostenkarteAngelegt)
            .filter(Objects::nonNull)
            .reduce(kontext.fenster(), (a, b) -> a.isBefore(b) ? a : b);
    List<Vorgang> nacht =
        runs.findAttemptsSince(kontext.projectId(), von).stream()
            .filter(v -> v.kind() == NightRunKind.NIGHT)
            .toList();
    List<Vorgang> gezaehlt = nacht.stream().filter(Vorgang::laufAbgeschlossen).toList();

    Eingaben eingaben =
        new Eingaben(
            rechnung,
            vorhaben(ziel, nacht, kosten, kontext.grenze()),
            kandidaten.stream().map(s -> grundlage(s, gezaehlt)).flatMap(Optional::stream).toList(),
            preise(kontext.staende(), gezaehlt, kosten, kontext.fenster(), kontext.grenze()));
    return KostenVoraussage.rechne(eingaben);
  }

  /** Ob der festgehaltene Abschluss in den zwölf Wochenfenstern liegt. */
  private static boolean abgeschlossenSeit(@Nullable VorhabenKosten zeile, Kontext kontext) {
    Instant abgeschlossen = zeile == null ? null : zeile.abgeschlossenAt();
    return abgeschlossen != null && !abgeschlossen.isBefore(kontext.fenster());
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

  /**
   * Was ein Lauf des Projekts einmal liest.
   *
   * @param projectId das Projekt
   * @param staende der Stand jedes Vorhabens
   * @param zeilen die festgehaltenen Zeilen je Vorhaben-ID
   * @param fenster Beginn des ältesten der zwölf Wochenfenster
   * @param grenze die Aufbewahrungsgrenze der Gattung NIGHT
   * @param kosten die Kosten je Karte über alle aufbewahrten Items
   */
  private record Kontext(
      long projectId,
      List<VorhabenStandView> staende,
      Map<Long, VorhabenKosten> zeilen,
      Instant fenster,
      Optional<Instant> grenze,
      Map<Integer, ItemKostenJeKarte> kosten) {}
}
