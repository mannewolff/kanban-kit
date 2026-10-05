package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

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
 * wird wie bisher über die Fenster abgegrenzt.
 */
// PMD.CouplingBetweenObjects: Die Kopplung zählt die Eingabe- und Ergebnistypen der Ermittlung —
// vier Eingabe-Records, acht Ergebnistypen des Fortschritts — und die java.util-Sammlungen, mit
// denen sie verknüpft werden. Eine Aufteilung verteilte die eine Zuordnungsregel (E2 bis E6) auf
// mehrere Klassen, die dieselben Typen weiter kennen müssten, ohne dass ein Schritt einfacher
// würde.
@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class FortschrittErmittlung {

  /** Label einer Kette, die auf eine Frage an den Menschen wartet (Kit-Vorgabe). */
  public static final String LABEL_WARTET = "lauf:wartet";

  /** Label der Variante B: Die Kette setzt die Pakete selbst um (Kit-Vorgabe, E12). */
  public static final String LABEL_DURCHZIEHEN = "kit:durchziehen";

  /** Label einer offenen Frage an den Menschen (Kit-Vorgabe, E9). */
  public static final String LABEL_KLAEREN = "kit:klaeren";

  /** Label der Freigabe zur Übernahme durch den nächsten Runner (Kit-Vorgabe, Plan #1447 E15). */
  public static final String LABEL_NIGHT = "kit:night";

  private static final String HERKUNFT_TOKEN = "TOKEN";
  private static final String TYP_ANGELEGT = "CREATED";
  private static final Set<String> TYP_BEWEGT = Set.of("MOVED", "STATUS_CHANGED");

  /** Das Präfix eines Plandokuments — wie {@code DOKUMENT_PRAEFIX} in {@code Arbeitspaket}. */
  private static final Pattern PLAN_PRAEFIX =
      Pattern.compile("^\\s*\\[plan\\]", Pattern.CASE_INSENSITIVE);

  /** Das Präfix einer fachlichen Anforderung. */
  private static final Pattern FACHLICH_PRAEFIX =
      Pattern.compile("^\\s*\\[fachlich\\]", Pattern.CASE_INSENSITIVE);

  /**
   * Die Zeile des Prüfvermerks — mit Wert gilt der Plan als geprüft ({@code PLAN_REVIEW_ZEILE}).
   */
  private static final String PLAN_REVIEW = "Plan-Review:";

  /**
   * Ein Stufeneintrag des Laufstands mit Zeitstempel: {@code <stufe> begonnen|fertig für #<ziel> um
   * <ISO>} — die Form von {@code stufeBeginnt}/{@code stufeEndet} in {@code night.mjs}.
   */
  private static final Pattern STUFEN_EINTRAG =
      Pattern.compile(
          "(?:^|\\W)(plan|review|pakete|abdeckung|umsetzung|vorbereitung) (begonnen|fertig) für"
              + " #(\\d{1,9})"
              + " um (\\S+)",
          Pattern.CANON_EQ);

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

  private static final Comparator<Karte> NACH_NUMMER = Comparator.comparingInt(Karte::number);

  private final Map<Long, Karte> jeId = new HashMap<>();
  private final Map<Integer, Karte> jeNummer = new HashMap<>();

  /** Die Lauf-Aktivitäten (E2) jeder Karte des Laufs, auch der unbekannten. */
  private final Map<Long, List<Aktivitaet>> laufAktivitaeten = new HashMap<>();

  private final Map<Long, List<StufenEintrag>> eintraegeJeKarte = new HashMap<>();
  private final List<Karte> laufKarten = new ArrayList<>();
  private final List<Karte> unbekannteKarten = new ArrayList<>();

  /**
   * Die Kennung, die eine Spur dieses Laufs tragen muss: die eigene, wenn der Lauf ausgewiesen ist,
   * sonst keine (A6).
   */
  private final @Nullable Instant spurKennung;

  private FortschrittErmittlung(
      NightRun lauf,
      String tokenName,
      Zeitfenster fenster,
      List<Zeitfenster> fremdeFenster,
      List<Aktivitaet> aktivitaeten,
      List<Karte> karten,
      List<Laufstand> laufstaende) {
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
    laufstaende.stream()
        .filter(l -> Objects.equals(kennung(l.laufStart()), spurKennung))
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
    boolean kette = lauf.mode() == NightRunMode.CHAIN;
    if (!kette && lauf.mode() != NightRunMode.IMPLEMENTATION) {
      return NightRunProgress.leer();
    }
    String tokenName = lauf.tokenName();
    if (tokenName == null) {
      return NightRunProgress.zuordnungUnbekannt();
    }
    return new FortschrittErmittlung(
            lauf, tokenName, fenster, fremdeFenster, aktivitaeten, karten, laufstaende)
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
   * Laufkennung, und die Freigabe {@code kit:night} ist verbraucht.
   *
   * @param laufstand ihr Laufstand-Kommentar; {@code null}, solange keiner steht
   */
  public static boolean uebernommen(Karte karte, @Nullable Laufstand laufstand) {
    return laufstand != null
        && laufstand.laufStart() != null
        && !karte.labels().contains(LABEL_NIGHT);
  }

  /**
   * Ob der Plan einer fachlichen Anforderung schon {@code Plan-Review:} trägt (Issue #1452, Plan
   * #1447 E14): Unter den aus ihr entstandenen Karten steht ein Plan mit dieser Zeile. An jeder
   * anderen Karte {@code false}.
   *
   * @param abgeleitete die Karten, deren Herkunft die Karte ist
   */
  public static boolean planReviewVorhanden(Karte karte, List<Karte> abgeleitete) {
    return FACHLICH_PRAEFIX.matcher(karte.title()).find()
        && abgeleitete.stream().anyMatch(k -> istPlan(k) && traegtPlanReview(k));
  }

  private NightRunProgress ergebnis(boolean kette) {
    Map<Integer, CardRef> unbekannt = new TreeMap<>();
    unbekannteKarten.forEach(k -> unbekannt.put(k.number(), ref(k)));
    Map<Integer, CardRef> fragen = new TreeMap<>();
    laufKarten.stream()
        .filter(k -> k.labels().contains(LABEL_KLAEREN))
        .forEach(k -> fragen.put(k.number(), ref(k)));
    Map<Integer, ChainProgress> ketten = new TreeMap<>();
    if (kette) {
      for (Karte t : tragendeKarten()) {
        if (t.labels().contains(LABEL_WARTET) || t.labels().contains(LABEL_KLAEREN)) {
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
    List<ProgressStage> weg = t.labels().contains(LABEL_DURCHZIEHEN) ? WEG_B : WEG_A;
    List<PackageProgress> pakete = plan == null ? List.of() : paketeZu(plan);
    Stufenlage lage = new Stufenlage(anforderung, plan, pakete, eintraege);
    Set<ProgressStage> erreicht = EnumSet.noneOf(ProgressStage.class);
    weg.stream().filter(lage::erreicht).forEach(erreicht::add);
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

  /** Eine Laufkennung, auf Millisekunden gekürzt (A10) — so speichert sie das Board. */
  private static @Nullable Instant kennung(@Nullable Instant laufStart) {
    return laufStart == null ? null : laufStart.truncatedTo(ChronoUnit.MILLIS);
  }

  /** Ob der Plan eine Zeile {@code Plan-Review:} mit nicht leerem Wert trägt. */
  private static boolean traegtPlanReview(Karte plan) {
    String text = plan.description();
    return text != null
        && text.lines()
            .map(String::stripLeading)
            .anyMatch(
                z -> z.startsWith(PLAN_REVIEW) && !z.substring(PLAN_REVIEW.length()).isBlank());
  }

  static boolean istPlan(Karte k) {
    return PLAN_PRAEFIX.matcher(k.title()).find();
  }

  private static CardRef ref(Karte k) {
    return new CardRef(k.number(), k.title(), k.boardId());
  }

  /** Die Stufeneinträge eines Laufstands mit lesbarem Zeitstempel; der Rest fällt weg. */
  static List<StufenEintrag> eintraege(String body) {
    List<StufenEintrag> ergebnis = new ArrayList<>();
    body.lines()
        .forEach(
            zeile -> {
              Matcher m = STUFEN_EINTRAG.matcher(zeile);
              if (m.find()) {
                zeitpunkt(m.group(4))
                    .ifPresent(
                        zeit ->
                            ergebnis.add(
                                new StufenEintrag(
                                    ProgressStage.valueOf(m.group(1).toUpperCase(Locale.ROOT)),
                                    "fertig".equals(m.group(2)),
                                    Integer.parseInt(m.group(3)),
                                    zeit)));
              }
            });
    return ergebnis;
  }

  private static Optional<Instant> zeitpunkt(String iso) {
    try {
      return Optional.of(Instant.parse(iso));
    } catch (DateTimeParseException _) {
      return Optional.empty();
    }
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
        // „abdeckung fertig" erreicht die Pakete als spätere Stufe; eigene Signale haben beide
        // nicht. Die Vorbereitung steht nie im Weg der Laufseite (Issue #1451).
        case PAKETE, ABDECKUNG, VORBEREITUNG -> false;
        case UMSETZUNG ->
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
