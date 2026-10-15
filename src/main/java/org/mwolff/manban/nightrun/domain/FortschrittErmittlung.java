package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Art;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.nightrun.domain.LaufstandWortformen.Stufenzeile;

/**
 * Ermittelt den Fortschritt eines Laufs aus seinen Spuren am Board (Issue #1374, Plan #1372) —
 * rein, ohne I/O. Die Eingaben befüllt der Service aus den Abfragen von Issue #1373.
 *
 * <p>Gelesen werden dieselben Spuren, die der Runner als Erfolgsregel benutzt ({@code
 * ERGEBNIS_REGELN} in {@code night.mjs}, E4): die vom Lauf angelegten Karten, die Zeile {@code
 * Plan-Review:} im Plan und die Stufenzeilen im Laufstand der tragenden Karte. So passt die Anzeige
 * zum Board.
 *
 * <p><b>Zuordnung (E2):</b> Eine Karte gehört zum Lauf, wenn sie im Fenster mindestens eine
 * Aktivität mit Herkunft {@code TOKEN}, dem Token-Namen des Laufs und gesetztem {@code agent} hat.
 * Liegt eine dieser Aktivitäten zugleich im Fenster eines anderen Nachtlaufs, ist die Karte
 * „unbekannt" (E3): gelistet, nicht gezählt.
 *
 * <p><b>Laufkennung (Issue #1429, Plan #1423 A1, A6, A7):</b> Kennung eines Laufs ist sein Start,
 * auf Millisekunden gekürzt. Trägt mindestens eine übergebene Aktivität oder ein Laufstand diese
 * Kennung, ist der Lauf ausgewiesen: Er nimmt nur Spuren mit seiner Kennung, und die fremden
 * Fenster spielen keine Rolle mehr. Ein nicht ausgewiesener Lauf nimmt nur Spuren ohne Kennung und
 * wird wie bisher über die Fenster abgegrenzt. Einen Laufstand ohne Kennung nimmt ein ausgewiesener
 * Lauf, wenn die Lauf-ID im Text seinen Start nennt (Issue #1764): Der Runner schreibt den
 * Laufstand selbst, ohne Header.
 *
 * <p><b>Namen des Kits (Issue #1590, Plan #1582 A3, A5, A6, E2, E13):</b> Labels zählen als Sachen
 * aus {@link KitNamen} — jeder Name einer Sache wirkt gleich, ein gemischtes Board zählt zusammen.
 * Titel-Präfixe kommen über {@link KitNamen#artAusTitel}, die Wortformen des Laufstands aus {@link
 * LaufstandWortformen}. Das Register ist ein Parameter mit der Vorgabe {@link KitNamen#STANDARD}.
 */
// PMD.CouplingBetweenObjects: Die Kopplung zählt die Eingabe- und Ergebnistypen der Ermittlung —
// vier Eingabe-Records, acht Ergebnistypen des Fortschritts — und die java.util-Sammlungen, mit
// denen sie verknüpft werden. Eine Aufteilung verteilte die eine Zuordnungsregel (E2 bis E6) auf
// mehrere Klassen, die dieselben Typen weiter kennen müssten, ohne dass ein Schritt einfacher
// würde. PMD.GodClass: Seit Issue #1590 liest die Ermittlung Labels als Sachen des Registers und
// Titel-Präfixe als Arten (E13) — PMD zählt jede dieser Konstanten als Zugriff auf fremde Daten.
// Die Regel bleibt dieselbe eine Zuordnung; ausgelagert sind schon die Wortformen
// (LaufstandWortformen), das Lesen des Zeitstempels (Stufenzeile) und die Labelprüfung (Karte).
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.GodClass"})
public final class FortschrittErmittlung {

  /**
   * Deutscher Name der Sache {@link Sache#LAUF_WARTET} — eine Kette, die auf eine Frage an den
   * Menschen wartet. Die Ermittlung selbst prüft alle Namen der Sache.
   */
  public static final String LABEL_WARTET = KitNamen.STANDARD.namen(Sache.LAUF_WARTET).getFirst();

  /**
   * Deutscher Name der Sache {@link Sache#DURCHZIEHEN} — Variante B, die Kette setzt die Pakete
   * selbst um (E12). Die Ermittlung selbst prüft alle Namen der Sache.
   */
  public static final String LABEL_DURCHZIEHEN =
      KitNamen.STANDARD.namen(Sache.DURCHZIEHEN).getFirst();

  /**
   * Deutscher Name der Sache {@link Sache#KLAEREN} — eine offene Frage an den Menschen (E9). Die
   * Ermittlung selbst prüft alle Namen der Sache.
   */
  public static final String LABEL_KLAEREN = KitNamen.STANDARD.namen(Sache.KLAEREN).getFirst();

  private static final String HERKUNFT_TOKEN = "TOKEN";
  private static final String TYP_ANGELEGT = "CREATED";
  private static final Set<String> TYP_BEWEGT = Set.of("MOVED", "STATUS_CHANGED");

  /** Die Stufen, deren Laufstand-Zeile eine Karte zur tragenden Karte macht (E5). */
  private static final Set<ProgressStage> TRAGENDE_STUFEN =
      EnumSet.of(
          ProgressStage.PLAN, ProgressStage.REVIEW, ProgressStage.PAKETE, ProgressStage.ABDECKUNG);

  private static final List<ProgressStage> WEG_A =
      List.of(
          ProgressStage.PLAN, ProgressStage.REVIEW, ProgressStage.PAKETE, ProgressStage.ABDECKUNG);

  private static final List<ProgressStage> WEG_B =
      List.of(
          ProgressStage.PLAN,
          ProgressStage.REVIEW,
          ProgressStage.PAKETE,
          ProgressStage.ABDECKUNG,
          ProgressStage.UMSETZUNG);

  /** Die Ziele (als markierte Station), deren Kette bis in die Umsetzung führt (Issue #1529). */
  private static final Set<ProgressStage> ZIELE_MIT_UMSETZUNG =
      EnumSet.of(ProgressStage.UMSETZUNG, ProgressStage.VORBEREITUNG);

  private static final Comparator<Karte> NACH_NUMMER = Comparator.comparingInt(Karte::number);

  private final Map<Long, Karte> jeId = new HashMap<>();
  private final Map<Integer, Karte> jeNummer = new HashMap<>();

  /** Die Lauf-Aktivitäten (E2) jeder Karte des Laufs, auch der unbekannten. */
  private final Map<Long, List<Aktivitaet>> laufAktivitaeten = new HashMap<>();

  private final Map<Long, List<StufenEintrag>> eintraegeJeKarte = new HashMap<>();

  /**
   * Das Ziel jeder Karte aus der Zeile {@code Ziel:} ihres Laufstands — ohne Kennungsfilter: Das
   * Ziel steht fest, sobald die Kette beginnt, und gehört der Karte (Issue #1529).
   */
  private final Map<Long, ProgressStage> zielJeKarte = new HashMap<>();

  private final List<Karte> laufKarten = new ArrayList<>();
  private final List<Karte> unbekannteKarten = new ArrayList<>();

  /**
   * Die Kennung, die eine Spur dieses Laufs tragen muss: die eigene, wenn der Lauf ausgewiesen ist,
   * sonst keine (A6).
   */
  private final @Nullable Instant spurKennung;

  /** Das Register, aus dem die Labels als Sachen gelesen werden (E13). */
  private final KitNamen namen;

  private FortschrittErmittlung(
      KitNamen namen,
      NightRun lauf,
      String tokenName,
      Zeitfenster fenster,
      List<Zeitfenster> fremdeFenster,
      List<Aktivitaet> aktivitaeten,
      List<Karte> karten,
      List<Laufstand> laufstaende) {
    this.namen = namen;
    Instant eigeneKennung = lauf.startedAt().truncatedTo(ChronoUnit.MILLIS);
    boolean ausgewiesen =
        aktivitaeten.stream().anyMatch(a -> eigeneKennung.equals(kennung(a.laufStart())))
            || laufstaende.stream().anyMatch(l -> eigeneKennung.equals(kennung(l.laufStart())));
    spurKennung = ausgewiesen ? eigeneKennung : null;
    karten.forEach(
        k -> {
          jeId.put(k.id(), k);
          jeNummer.put(k.number(), k);
        });
    aktivitaeten.stream()
        .filter(a -> gehoertZumLauf(a, tokenName, fenster))
        .forEach(a -> laufAktivitaeten.computeIfAbsent(a.cardId(), id -> new ArrayList<>()).add(a));
    laufAktivitaeten.forEach(
        (id, akt) -> {
          Karte k = jeId.get(id);
          if (k != null) {
            // Nur Spuren ohne Kennung sind mehrdeutig (A9) — und die nimmt allein ein nicht
            // ausgewiesener Lauf.
            boolean ueberlappt =
                !ausgewiesen
                    && akt.stream()
                        .anyMatch(
                            a -> fremdeFenster.stream().anyMatch(f -> f.enthaelt(a.createdAt())));
            (ueberlappt ? unbekannteKarten : laufKarten).add(k);
          }
        });
    laufKarten.sort(NACH_NUMMER);
    laufstaende.forEach(
        l -> KettenAbleitung.ziel(l.body()).ifPresent(z -> zielJeKarte.putIfAbsent(l.cardId(), z)));
    laufstaende.stream()
        .filter(this::gehoertZumLauf)
        .forEach(
            l ->
                eintraegeJeKarte
                    .computeIfAbsent(l.cardId(), id -> new ArrayList<>())
                    .addAll(
                        eintraege(l.body()).stream()
                            .filter(
                                e ->
                                    fenster.enthaelt(e.zeit())
                                        && e.stufe() != ProgressStage.VORBEREITUNG)
                            .toList()));
  }

  /**
   * Der Fortschritt des Laufs.
   *
   * @param lauf der Lauf
   * @param fenster sein Zeitfenster (E2) — der Service bestimmt das Ende
   * @param fremdeFenster die Fenster der anderen Nachtläufe desselben Projekts mit demselben
   *     Token-Namen, die das Fenster überlappen (E3) — ohne den Lauf selbst
   * @param aktivitaeten Kartenaktivitäten um das Fenster — auch die anderer Läufe; ob der Lauf
   *     ausgewiesen ist, entscheidet sich an ihnen und an den Laufständen
   * @param karten die Karten der Aktivitäten und der Laufstände samt Anforderungen und Plänen
   * @param laufstaende die Laufstand-Kommentare des Projekts
   */
  public static NightRunProgress ermittle(
      NightRun lauf,
      Zeitfenster fenster,
      List<Zeitfenster> fremdeFenster,
      List<Aktivitaet> aktivitaeten,
      List<Karte> karten,
      List<Laufstand> laufstaende) {
    return ermittle(
        lauf, fenster, fremdeFenster, aktivitaeten, karten, laufstaende, KitNamen.STANDARD);
  }

  /**
   * Der Fortschritt des Laufs mit einem eigenen Register (E13) — sonst wie {@link
   * #ermittle(NightRun, Zeitfenster, List, List, List, List)}.
   *
   * @param namen das Register, aus dem die Labels als Sachen gelesen werden
   */
  public static NightRunProgress ermittle(
      NightRun lauf,
      Zeitfenster fenster,
      List<Zeitfenster> fremdeFenster,
      List<Aktivitaet> aktivitaeten,
      List<Karte> karten,
      List<Laufstand> laufstaende,
      KitNamen namen) {
    boolean kette = lauf.mode() == NightRunMode.CHAIN;
    if (!kette && lauf.mode() != NightRunMode.IMPLEMENTATION) {
      return NightRunProgress.leer();
    }
    String tokenName = lauf.tokenName();
    if (tokenName == null) {
      return NightRunProgress.zuordnungUnbekannt();
    }
    return new FortschrittErmittlung(
            namen, lauf, tokenName, fenster, fremdeFenster, aktivitaeten, karten, laufstaende)
        .ergebnis(kette);
  }

  /**
   * Der Stand der Kette einer Karte aus ihrem Laufstand (Issue #1451, Plan #1447 E5, E10, E13) —
   * für die Stufenleiste. Der Zustand der aktuellen Station kommt aus dem {@code lauf:*}-Label der
   * Karte, alles andere aus dem Laufstand.
   *
   * @param karte die Startkarte der Kette, eine Anforderung oder ein Plan
   * @param laufstand ihr Laufstand-Kommentar; {@code null}, solange keiner steht
   */
  public static KettenStand kettenStand(Karte karte, @Nullable Laufstand laufstand) {
    return KettenAbleitung.stand(karte, laufstand == null ? "" : laufstand.body());
  }

  /**
   * Ob ein Runner die Karte übernommen hat (Issue #1452, Plan #1447 E15): Ihr Laufstand trägt eine
   * Laufkennung, und die Freigabe {@code kit:night} ist verbraucht — unter keinem Namen der Sache
   * {@link Sache#NIGHT} mehr gesetzt.
   *
   * @param laufstand ihr Laufstand-Kommentar; {@code null}, solange keiner steht
   */
  public static boolean uebernommen(Karte karte, @Nullable Laufstand laufstand) {
    return uebernommen(karte, laufstand, KitNamen.STANDARD);
  }

  /**
   * Wie {@link #uebernommen(Karte, Laufstand)} mit einem eigenen Register (E13).
   *
   * @param namen das Register, aus dem die Freigabe als Sache gelesen wird
   */
  public static boolean uebernommen(Karte karte, @Nullable Laufstand laufstand, KitNamen namen) {
    return laufstand != null && laufstand.laufStart() != null && !karte.traegt(Sache.NIGHT, namen);
  }

  /**
   * Ob der Plan einer fachlichen Anforderung schon {@code Plan-Review:} trägt (Issue #1452, Plan
   * #1447 E14): Unter den aus ihr entstandenen Karten steht ein Plan mit dieser Zeile, in einer der
   * Formen aus {@link LaufstandWortformen#PLAN_REVIEW}. An jeder anderen Karte {@code false}.
   *
   * @param abgeleitete die Karten, deren Herkunft die Karte ist
   */
  public static boolean planReviewVorhanden(Karte karte, List<Karte> abgeleitete) {
    return KitNamen.artAusTitel(karte.title()).filter(Art.FACHLICH::equals).isPresent()
        && abgeleitete.stream().anyMatch(k -> istPlan(k) && traegtPlanReview(k));
  }

  private NightRunProgress ergebnis(boolean kette) {
    Map<Integer, CardRef> unbekannt = new TreeMap<>();
    unbekannteKarten.forEach(k -> unbekannt.put(k.number(), ref(k)));
    Map<Integer, CardRef> fragen = new TreeMap<>();
    laufKarten.stream()
        .filter(k -> k.traegt(Sache.KLAEREN, namen))
        .forEach(k -> fragen.put(k.number(), ref(k)));
    Map<Integer, ChainProgress> ketten = new TreeMap<>();
    if (kette) {
      for (Karte t : tragendeKarten()) {
        if (t.traegt(Sache.LAUF_WARTET, namen) || t.traegt(Sache.KLAEREN, namen)) {
          fragen.put(t.number(), ref(t));
        }
        kette(t)
            .ifPresentOrElse(
                c -> ketten.putIfAbsent(c.anforderung().number(), c),
                () -> unbekannt.put(t.number(), ref(t)));
      }
    }
    List<PackageProgress> pakete =
        laufKarten.stream().filter(Karte::arbeitspaket).map(this::paket).toList();
    return new NightRunProgress(
        ProgressAssignment.OK,
        List.copyOf(ketten.values()),
        pakete,
        List.copyOf(unbekannt.values()),
        List.copyOf(fragen.values()),
        !unbekannteKarten.isEmpty());
  }

  // --- Tragende Karte (E5)
  // -------------------------------------------------------------------------

  /**
   * Die tragenden Karten, nach Nummer: die Herkunft jedes vom Lauf angelegten Plans — ist sie nicht
   * geliefert, der Plan selbst — und jede Karte mit einer Stufenzeile im Fenster. Nie ein
   * Arbeitspaket: Der Runner führt Laufstand und {@code lauf:*}-Labels auch an Paketen.
   */
  private List<Karte> tragendeKarten() {
    Map<Integer, Karte> tragende = new TreeMap<>();
    for (Karte plan : angelegtePlaene()) {
      Long herkunft = plan.derivedFromCardId();
      if (herkunft != null) {
        Karte k = jeId.getOrDefault(herkunft, plan);
        tragende.put(k.number(), k);
      }
    }
    eintraegeJeKarte.forEach(
        (id, eintraege) -> {
          Karte k = jeId.get(id);
          if (k != null && eintraege.stream().anyMatch(e -> TRAGENDE_STUFEN.contains(e.stufe()))) {
            tragende.put(k.number(), k);
          }
        });
    return tragende.values().stream().filter(k -> !k.arbeitspaket()).toList();
  }

  /**
   * Die Kette an der tragenden Karte {@code t}; leer, wenn sie sich keiner Anforderung zuordnen
   * lässt (E3). Ist {@code t} selbst ein Plan, setzt die Kette an einem vorhandenen Plan an, und
   * die Anforderung ist seine Herkunft.
   */
  private Optional<ChainProgress> kette(Karte t) {
    List<StufenEintrag> eintraege = eintraegeJeKarte.getOrDefault(t.id(), List.of());
    if (istPlan(t)) {
      return Optional.ofNullable(t.derivedFromCardId())
          .map(jeId::get)
          .map(f -> ketteAus(f, t, eintraege, t));
    }
    return Optional.of(ketteAus(t, planZu(t, eintraege), eintraege, t));
  }

  /**
   * Der Plan einer Kette, die an der Anforderung ansetzt: der jüngste vom Lauf angelegte Plan mit
   * dieser Herkunft, sonst der Plan, den der jüngste Stufeneintrag nennt — die Einträge ab der
   * Prüfung nennen den Plan, die der Planstufe die Anforderung selbst.
   */
  private @Nullable Karte planZu(Karte anforderung, List<StufenEintrag> eintraege) {
    Optional<Karte> angelegt =
        angelegtePlaene().stream()
            .filter(p -> Objects.equals(p.derivedFromCardId(), anforderung.id()))
            .max(NACH_NUMMER);
    if (angelegt.isPresent()) {
      return angelegt.get();
    }
    return eintraege.stream()
        .sorted(Comparator.comparing(StufenEintrag::zeit).reversed())
        .map(e -> jeNummer.get(e.ziel()))
        .filter(k -> k != null && istPlan(k))
        .findFirst()
        .orElse(null);
  }

  private ChainProgress ketteAus(
      Karte anforderung, @Nullable Karte plan, List<StufenEintrag> eintraege, Karte t) {
    // Das Ziel aus dem Laufstand entscheidet; ohne Ziel-Zeile gilt wie vor Issue #1529 das Label.
    ProgressStage ziel = zielJeKarte.get(t.id());
    boolean bisUmsetzung =
        ziel == null ? t.traegt(Sache.DURCHZIEHEN, namen) : ZIELE_MIT_UMSETZUNG.contains(ziel);
    List<ProgressStage> weg = bisUmsetzung ? WEG_B : WEG_A;
    List<PackageProgress> pakete = plan == null ? List.of() : paketeZu(plan);
    Stufenlage lage = new Stufenlage(anforderung, plan, pakete, eintraege);
    // Folgerichtig (Issue #1783): Ist eine Stufe erreicht, sind es alle früheren des Wegs auch —
    // ein Paket in Umsetzung belegt, dass Prüfung und Schnitt vorbei sind.
    int letzte = -1;
    for (int i = 0; i < weg.size(); i++) {
      if (lage.erreicht(weg.get(i))) {
        letzte = i;
      }
    }
    Set<ProgressStage> erreicht = EnumSet.noneOf(ProgressStage.class);
    erreicht.addAll(weg.subList(0, letzte + 1));
    // Die erste offene Stufe ist die aus „zuletzt begonnen:", sofern sie im Fenster liegt und noch
    // nicht fertig ist: Eine begonnene Stufe macht alle früheren erreicht.
    ProgressStage aktuell =
        weg.stream().filter(s -> !erreicht.contains(s)).findFirst().orElse(null);
    List<StageProgress> stufen =
        weg.stream().map(s -> new StageProgress(s, zustand(s, erreicht, aktuell))).toList();
    return new ChainProgress(
        ref(anforderung),
        plan == null ? null : ref(plan),
        pakete,
        stufen,
        aktuell,
        erreicht.containsAll(weg));
  }

  private static StageState zustand(
      ProgressStage s, Set<ProgressStage> erreicht, @Nullable ProgressStage aktuell) {
    if (erreicht.contains(s)) {
      return StageState.ERREICHT;
    }
    return s == aktuell ? StageState.LAEUFT : StageState.OFFEN;
  }

  /** Die Pakete, die der Lauf zum Plan angelegt hat (E4), nach Nummer. */
  private List<PackageProgress> paketeZu(Karte plan) {
    return laufKarten.stream()
        .filter(k -> Objects.equals(k.derivedFromCardId(), plan.id()) && angelegt(k))
        .map(this::paket)
        .toList();
  }

  /** Die vom Lauf angelegten Pläne — ohne die unbekannten. */
  private List<Karte> angelegtePlaene() {
    return laufKarten.stream().filter(k -> istPlan(k) && angelegt(k)).toList();
  }

  private boolean angelegt(Karte k) {
    return laufAktivitaeten.getOrDefault(k.id(), List.of()).stream()
        .anyMatch(a -> TYP_ANGELEGT.equals(a.type()));
  }

  // --- Paketzustand (E6)
  // ---------------------------------------------------------------------------

  private PackageProgress paket(Karte k) {
    return new PackageProgress(ref(k), paketZustand(k));
  }

  /**
   * Der Zustand, den das Paket durch die jüngste Bewegung dieses Laufs erreicht hat (A8); ohne
   * festgehaltenen Status danach gilt der heutige der Karte.
   */
  private PackageState paketZustand(Karte k) {
    Optional<Aktivitaet> letzte =
        laufAktivitaeten.getOrDefault(k.id(), List.of()).stream()
            .filter(a -> TYP_BEWEGT.contains(a.type()))
            .max(Comparator.comparing(Aktivitaet::createdAt));
    if (letzte.isEmpty()) {
      return PackageState.ANGELEGT;
    }
    String statusAfter = letzte.get().statusAfter();
    String status = statusAfter == null ? k.status() : statusAfter;
    if (status == null) {
      return PackageState.ANGELEGT;
    }
    return switch (status) {
      case "READY" -> PackageState.GEZOGEN;
      case "IN_PROGRESS" -> PackageState.IN_UMSETZUNG;
      case "IN_REVIEW", "DONE" -> PackageState.FERTIG;
      case "BACKLOG" -> PackageState.ZURUECKGESTELLT;
      default -> PackageState.ANGELEGT;
    };
  }

  // --- Hilfen
  // --------------------------------------------------------------------------------------

  /**
   * Ob die Aktivität zum Lauf gehört: die Regel aus E2, dazu die passende Kennung (A6). Die
   * Fensterprüfung gilt auch für einen ausgewiesenen Lauf.
   */
  private boolean gehoertZumLauf(Aktivitaet a, String tokenName, Zeitfenster fenster) {
    return HERKUNFT_TOKEN.equals(a.origin())
        && tokenName.equals(a.tokenName())
        && a.agent() != null
        && fenster.enthaelt(a.createdAt())
        && Objects.equals(kennung(a.laufStart()), spurKennung);
  }

  /**
   * Ob der Laufstand zum Lauf gehört: über seine Kennung (A6) oder — ohne Kennung im ausgewiesenen
   * Lauf — über die Lauf-ID im Text (Issue #1764). Der Runner schreibt den Laufstand mit eigenen
   * Board-Aufrufen ohne Header {@code X-Night-Run}; nur seine Sessions tragen die Kennung. Die
   * Lauf-ID nennt denselben Laufstart, auf Sekunden gekürzt.
   */
  private boolean gehoertZumLauf(Laufstand l) {
    Instant k = kennung(l.laufStart());
    if (k != null || spurKennung == null) {
      return Objects.equals(k, spurKennung);
    }
    Instant sekunde = spurKennung.truncatedTo(ChronoUnit.SECONDS);
    return LaufstandWortformen.laufIdStart(l.body()).filter(sekunde::equals).isPresent();
  }

  /** Eine Laufkennung, auf Millisekunden gekürzt (A10) — so speichert sie das Board. */
  private static @Nullable Instant kennung(@Nullable Instant laufStart) {
    return laufStart == null ? null : laufStart.truncatedTo(ChronoUnit.MILLIS);
  }

  /** Ob der Plan einen Prüfvermerk mit nicht leerem Wert trägt, in einer seiner Formen. */
  private static boolean traegtPlanReview(Karte plan) {
    String text = plan.description();
    return text != null
        && text.lines()
            .map(String::stripLeading)
            .anyMatch(
                z ->
                    LaufstandWortformen.wert(LaufstandWortformen.PLAN_REVIEW, z)
                        .filter(w -> !w.isBlank())
                        .isPresent());
  }

  /** Ob die Karte ein Plandokument ist — Titel-Präfix {@code [Plan]} über {@link KitNamen}. */
  static boolean istPlan(Karte k) {
    return KitNamen.artAusTitel(k.title()).filter(Art.PLAN::equals).isPresent();
  }

  private static CardRef ref(Karte k) {
    return new CardRef(k.number(), k.title(), k.boardId());
  }

  /**
   * Die Stufeneinträge eines Laufstands in beiden Formen ({@link LaufstandWortformen}) mit lesbarem
   * Zeitstempel; der Rest fällt weg.
   */
  static List<StufenEintrag> eintraege(String body) {
    return body.lines()
        .map(LaufstandWortformen::stufenzeile)
        .flatMap(Optional::stream)
        .map(Stufenzeile::eintrag)
        .flatMap(Optional::stream)
        .toList();
  }

  // --- Stufen (E4)
  // ---------------------------------------------------------------------------------

  /**
   * Was eine Kette über ihre Stufen weiß. Eine Stufe ist erreicht, wenn ihr eigenes Signal da ist,
   * eine {@code fertig}-Zeile für sie im Fenster steht oder eine spätere Stufe begonnen hat — der
   * Laufstand hält nur die letzte begonnene und die letzte abgeschlossene Stufe. Gezählt werden nur
   * Einträge mit dem Ziel dieser Kette.
   */
  private record Stufenlage(
      Karte anforderung,
      @Nullable Karte plan,
      List<PackageProgress> pakete,
      List<StufenEintrag> eintraege) {

    boolean erreicht(ProgressStage stufe) {
      return signal(stufe)
          || eintraege.stream()
              .filter(this::passt)
              .anyMatch(e -> e.stufe().compareTo(stufe) > 0 || (e.stufe() == stufe && e.fertig()));
    }

    private boolean signal(ProgressStage stufe) {
      return switch (stufe) {
        // Ein Plan der Kette steht am Board: vom Lauf angelegt, die tragende Karte selbst oder
        // vom Laufstand einer späteren Stufe genannt.
        case PLAN -> plan != null;
        case REVIEW -> plan != null && traegtPlanReview(plan);
        // Eigene Signale neben dem Laufstand (Issue #1764): Die Pakete sind erreicht, sobald die
        // Kette Pakete hat; die Abdeckung, sobald eines über „angelegt" hinaus ist.
        case PAKETE -> !pakete.isEmpty();
        case ABDECKUNG -> pakete.stream().anyMatch(p -> p.zustand() != PackageState.ANGELEGT);
        // Die Vorbereitung steht nie im Weg der Laufseite (Issue #1451): erreicht(...) wird nur für
        // die Stufen aus WEG_A und WEG_B gerufen. Ein eigener Zweig für sie wäre toter Code
        // (Issue #1796); sie teilt sich darum den Zweig der Umsetzung.
        case UMSETZUNG, VORBEREITUNG ->
            !pakete.isEmpty() && pakete.stream().allMatch(p -> p.zustand() == PackageState.FERTIG);
      };
    }

    /**
     * Ob der Eintrag zu dieser Kette gehört: Die Planstufe nennt die Anforderung als Ziel, jede
     * spätere den Plan.
     */
    private boolean passt(StufenEintrag e) {
      if (e.stufe() == ProgressStage.PLAN) {
        return e.ziel() == anforderung.number();
      }
      return plan != null && e.ziel() == plan.number();
    }
  }

  /** Ein Stufeneintrag des Laufstands mit seinem Zeitstempel. */
  record StufenEintrag(ProgressStage stufe, boolean fertig, int ziel, Instant zeit) {}

  // --- Eingaben
  // ------------------------------------------------------------------------------------

  /**
   * Das Zeitfenster eines Laufs, beide Grenzen eingeschlossen (E2).
   *
   * @param von Beginn, einschließlich
   * @param bis Ende, einschließlich
   */
  public record Zeitfenster(Instant von, Instant bis) {

    /** Ob der Zeitpunkt im Fenster liegt. */
    public boolean enthaelt(Instant zeit) {
      return !zeit.isBefore(von) && !zeit.isAfter(bis);
    }
  }

  /**
   * Eine Kartenaktivität.
   *
   * @param cardId Karte, an der die Aktivität stattfand
   * @param type Konstantenname von {@code CardActivityType}, etwa {@code CREATED} oder {@code
   *     MOVED}
   * @param createdAt Zeitpunkt
   * @param origin Konstantenname der Herkunft, {@code TOKEN} oder {@code SESSION}
   * @param tokenName Name des Tokens; {@code null} ohne Token
   * @param agent Modell der Session, die das Token im Nachtbetrieb führt; {@code null} sonst
   * @param laufStart Laufkennung des Laufs, der die Aktivität auslöste (Issue #1426); {@code null}
   *     ohne Ausweis
   * @param statusAfter Konstantenname von {@code CardStatus} nach einer Bewegung (Issue #1427);
   *     {@code null} sonst und bei Alt-Einträgen
   */
  public record Aktivitaet(
      long cardId,
      String type,
      Instant createdAt,
      String origin,
      @Nullable String tokenName,
      @Nullable String agent,
      @Nullable Instant laufStart,
      @Nullable String statusAfter) {

    /** Eine Aktivität ohne Laufkennung und ohne Status danach — wie vor Issue #1426. */
    public Aktivitaet(
        long cardId,
        String type,
        Instant createdAt,
        String origin,
        @Nullable String tokenName,
        @Nullable String agent) {
      this(cardId, type, createdAt, origin, tokenName, agent, null, null);
    }
  }

  /**
   * Eine Karte, wie die Ermittlung sie braucht.
   *
   * @param status Konstantenname von {@code CardStatus}; {@code null} bei Vorhaben und
   *     Dokumentarten
   * @param labels Labelnamen der Karte
   * @param derivedFromCardId ID der Karte, aus der diese entstanden ist
   * @param arbeitspaket ob die Karte ein Arbeitspaket ist — die Regel {@code
   *     Arbeitspaket.istArbeitspaket} liegt in {@code card.domain} und ist von hier nicht
   *     erreichbar
   * @param description Markdown-Beschreibung
   */
  public record Karte(
      long id,
      int number,
      String title,
      long boardId,
      @Nullable String status,
      List<String> labels,
      @Nullable Long derivedFromCardId,
      boolean arbeitspaket,
      @Nullable String description) {

    public Karte {
      labels = List.copyOf(labels);
    }

    /** Ob die Karte die Sache unter einem ihrer Namen im Register trägt (A3, E13). */
    boolean traegt(Sache sache, KitNamen namen) {
      return namen.namen(sache).stream().anyMatch(labels::contains);
    }
  }

  /**
   * Ein Laufstand-Kommentar.
   *
   * @param cardId Karte, an der er steht
   * @param body Kommentartext
   * @param laufStart Laufkennung des letzten Schreibers (Issue #1428); {@code null} ohne Ausweis
   */
  public record Laufstand(long cardId, String body, @Nullable Instant laufStart) {

    /** Ein Laufstand ohne Laufkennung — wie vor Issue #1428. */
    public Laufstand(long cardId, String body) {
      this(cardId, body, null);
    }
  }
}
