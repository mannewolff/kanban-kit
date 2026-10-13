package org.mwolff.manban.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Namen-Register des claude-workflow-kits (Issue #1584, Plan #1582 A1–A3, E2, E5, E7, E13).
 *
 * <p>Jede {@link Sache} — eine Bedeutung, die der Leitstand aus einem Label ableitet — trägt ihre
 * Schutzrichtung und einen deutschen sowie optional einen englischen Namen. Deutscher und
 * englischer Name derselben Sache gelten überall als eins. Dazu kommen die Titel-Präfixe je {@link
 * Art} und der Namensraum des Kits.
 *
 * <p>Herkunft: Kit-Register {@code kit/board/namen.mjs}, Kit-Commit 7388418. Der Leitstand
 * übersetzt nicht selbst; Sachen, die das Kit-Register in beiden Sprachen gleich führt, haben hier
 * genau einen Namen (E5).
 *
 * <p>Die Produktion arbeitet mit {@link #STANDARD}. {@link #aus(List)} baut ein Register aus
 * eigenen Einträgen und ist Tests vorbehalten, die einen Zusatznamen einbringen (E13); eine
 * ArchUnit-Regel verbietet den Aufruf aus anderem Produktionscode.
 *
 * <p>Frameworkfrei und ohne Modulabhängigkeit, damit {@code card.application}, {@code card.domain}
 * und {@code nightrun.domain} es importieren dürfen.
 */
public final class KitNamen {

  /** Wer ein Label der Sache setzen oder abnehmen darf. */
  public enum Schutz {
    /** Jeder mit Schreibrecht. */
    KEINER,
    /** Setzen darf nur ein Mensch. */
    NUR_MENSCH_SETZT,
    /** Abnehmen darf nur ein Mensch. */
    NUR_MENSCH_NIMMT_AB
  }

  /** Bedeutung eines Kit-Labels, unabhängig von der Sprache seines Namens. */
  public enum Sache {
    DURCHZIEHEN(Schutz.KEINER),
    GESCHUETZT(Schutz.NUR_MENSCH_NIMMT_AB),
    KLAEREN(Schutz.NUR_MENSCH_NIMMT_AB),
    GEKLAERT(Schutz.KEINER),
    NIGHT(Schutz.NUR_MENSCH_SETZT),
    NIGHTRUN(Schutz.NUR_MENSCH_SETZT),
    PRUEFEN(Schutz.KEINER),
    NIGHTPLAN(Schutz.KEINER),
    NIGHTREVIEW(Schutz.KEINER),
    NIGHTISSUES(Schutz.KEINER),
    WARTET_EREIGNIS(Schutz.NUR_MENSCH_NIMMT_AB),
    LAUF_ABGEBROCHEN(Schutz.KEINER),
    LAUF_LAEUFT(Schutz.KEINER),
    LAUF_WARTET(Schutz.KEINER),
    REVIEW_OFFEN(Schutz.KEINER),
    REVIEW_FERTIG(Schutz.KEINER),
    ZIEL_PLAN(Schutz.KEINER),
    ZIEL_PAKETE(Schutz.KEINER),
    ZIEL_UMSETZUNG(Schutz.KEINER),
    ZIEL_PUSH_VORBEREITET(Schutz.KEINER),
    PLANREVIEW_1(Schutz.KEINER),
    PLANREVIEW_2(Schutz.KEINER);

    private final Schutz schutzrichtung;

    Sache(Schutz schutzrichtung) {
      this.schutzrichtung = schutzrichtung;
    }

    /** Schutzrichtung der Sache, gleich unter jedem ihrer Namen. */
    public Schutz schutz() {
      return schutzrichtung;
    }
  }

  /** Ein Name des Kits für eine Sache. */
  public record Eintrag(Sache sache, String name) {}

  /** Kartenart, die das Kit über ein Titel-Präfix kennzeichnet (E2). */
  public enum Art {
    FACHLICH,
    IDEE,
    MENSCH,
    PLAN,
    TASK
  }

  /** Das Register der Produktion; Reihenfolge je Sache: deutsch, dann englisch. */
  public static final KitNamen STANDARD =
      aus(
          List.of(
              new Eintrag(Sache.DURCHZIEHEN, "kit:durchziehen"),
              new Eintrag(Sache.DURCHZIEHEN, "kit:carry-through"),
              new Eintrag(Sache.GESCHUETZT, "kit:geschuetzt"),
              new Eintrag(Sache.GESCHUETZT, "kit:protected"),
              new Eintrag(Sache.KLAEREN, "kit:klaeren"),
              new Eintrag(Sache.KLAEREN, "kit:clarify"),
              new Eintrag(Sache.GEKLAERT, "kit:geklaert"),
              new Eintrag(Sache.GEKLAERT, "kit:clarified"),
              new Eintrag(Sache.NIGHT, "kit:night"),
              new Eintrag(Sache.NIGHTRUN, "kit:nightrun"),
              new Eintrag(Sache.PRUEFEN, "kit:pruefen"),
              new Eintrag(Sache.PRUEFEN, "kit:check"),
              new Eintrag(Sache.NIGHTPLAN, "kit:nightplan"),
              new Eintrag(Sache.NIGHTREVIEW, "kit:nightreview"),
              new Eintrag(Sache.NIGHTISSUES, "kit:nightissues"),
              new Eintrag(Sache.WARTET_EREIGNIS, "kit:wartet"),
              new Eintrag(Sache.WARTET_EREIGNIS, "kit:on-hold"),
              new Eintrag(Sache.LAUF_ABGEBROCHEN, "lauf:abgebrochen"),
              new Eintrag(Sache.LAUF_ABGEBROCHEN, "run:aborted"),
              new Eintrag(Sache.LAUF_LAEUFT, "lauf:laeuft"),
              new Eintrag(Sache.LAUF_LAEUFT, "run:running"),
              new Eintrag(Sache.LAUF_WARTET, "lauf:wartet"),
              new Eintrag(Sache.LAUF_WARTET, "run:waiting"),
              new Eintrag(Sache.REVIEW_OFFEN, "review:offen"),
              new Eintrag(Sache.REVIEW_FERTIG, "review:fertig"),
              new Eintrag(Sache.REVIEW_FERTIG, "review:done"),
              new Eintrag(Sache.ZIEL_PLAN, "ziel:plan"),
              new Eintrag(Sache.ZIEL_PLAN, "goal:plan"),
              new Eintrag(Sache.ZIEL_PAKETE, "ziel:pakete"),
              new Eintrag(Sache.ZIEL_PAKETE, "goal:packages"),
              new Eintrag(Sache.ZIEL_UMSETZUNG, "ziel:umsetzung"),
              new Eintrag(Sache.ZIEL_UMSETZUNG, "goal:implementation"),
              new Eintrag(Sache.ZIEL_PUSH_VORBEREITET, "ziel:push-vorbereitet"),
              new Eintrag(Sache.ZIEL_PUSH_VORBEREITET, "goal:push-prepared"),
              new Eintrag(Sache.PLANREVIEW_1, "planreview:1"),
              new Eintrag(Sache.PLANREVIEW_2, "planreview:2")));

  /** Präfixe des Namensraums, verglichen ohne Rücksicht auf Groß- und Kleinschreibung (E7). */
  private static final List<String> NAMENSRAUM =
      List.of("kit:", "lauf:", "ziel:", "review:", "planreview:", "run:", "goal:");

  /** Titel-Präfixe je Art, deutsch zuerst. */
  private static final Map<Art, List<String>> TITEL_PRAEFIXE = titelPraefixeAufbauen();

  /** Je Art ein Muster: führender Leerraum, dann eines ihrer Präfixe in beliebiger Schreibung. */
  private static final Map<Art, Pattern> TITEL_MUSTER = titelMusterAufbauen();

  private final List<Eintrag> registerEintraege;
  private final Map<String, Sache> sacheJeName;
  private final Map<Sache, List<String>> namenJeSache;

  private KitNamen(List<Eintrag> eintraege) {
    this.registerEintraege = List.copyOf(eintraege);
    Map<String, Sache> jeName = new HashMap<>();
    Map<Sache, List<String>> jeSache = new EnumMap<>(Sache.class);
    for (Eintrag eintrag : registerEintraege) {
      Sache vorher = jeName.putIfAbsent(eintrag.name(), eintrag.sache());
      if (vorher != null) {
        throw new IllegalArgumentException(
            "Name " + eintrag.name() + " steht für " + vorher + " und " + eintrag.sache());
      }
      jeSache.computeIfAbsent(eintrag.sache(), s -> new ArrayList<>()).add(eintrag.name());
    }
    jeSache.replaceAll((sache, namen) -> List.copyOf(namen));
    this.sacheJeName = Map.copyOf(jeName);
    this.namenJeSache = Collections.unmodifiableMap(jeSache);
  }

  /**
   * Baut ein Register aus eigenen Einträgen — nur für Tests (E13).
   *
   * @throws IllegalArgumentException wenn ein Name für zwei Sachen steht
   */
  public static KitNamen aus(List<Eintrag> eintraege) {
    return new KitNamen(eintraege);
  }

  /** Alle Einträge in Registerreihenfolge. */
  public List<Eintrag> eintraege() {
    return registerEintraege;
  }

  /** Sache zum Namen: nach {@code trim()} und mit exakter Schreibung, sonst leer. */
  public Optional<Sache> sache(@Nullable String name) {
    if (name == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(sacheJeName.get(name.trim()));
  }

  /** Alle Namen der Sache in Registerreihenfolge, der deutsche zuerst; leer ohne Eintrag. */
  public List<String> namen(Sache sache) {
    return namenJeSache.getOrDefault(sache, List.of());
  }

  /** Alle Namen in Registerreihenfolge. */
  public List<String> alleNamen() {
    return registerEintraege.stream().map(Eintrag::name).toList();
  }

  /**
   * Ob der Name im Namensraum des Kits liegt — auch wenn das Register ihn nicht kennt. Verglichen
   * wird nach {@code trim()} und ohne Rücksicht auf Groß- und Kleinschreibung, damit etwa {@code
   * KIT:night} nicht als fremder Name durchgeht (E7).
   */
  public static boolean imNamensraum(@Nullable String name) {
    if (name == null) {
      return false;
    }
    String klein = name.trim().toLowerCase(Locale.ROOT);
    return NAMENSRAUM.stream().anyMatch(klein::startsWith);
  }

  /** Die Präfixe des Namensraums in fester Reihenfolge. */
  public static List<String> namensraumPraefixe() {
    return NAMENSRAUM;
  }

  /**
   * Kartenart aus dem Titel-Präfix: führender Leerraum ist erlaubt, die Schreibung beliebig. Ohne
   * bekanntes Präfix leer.
   */
  public static Optional<Art> artAusTitel(@Nullable String titel) {
    if (titel == null) {
      return Optional.empty();
    }
    return TITEL_MUSTER.entrySet().stream()
        .filter(e -> e.getValue().matcher(titel).lookingAt())
        .map(Map.Entry::getKey)
        .findFirst();
  }

  /** Die Titel-Präfixe je Art in Reihenfolge der Arten, deutsch zuerst. */
  public static Map<Art, List<String>> titelPraefixe() {
    return Collections.unmodifiableMap(TITEL_PRAEFIXE);
  }

  private static Map<Art, List<String>> titelPraefixeAufbauen() {
    Map<Art, List<String>> praefixe = new EnumMap<>(Art.class);
    praefixe.put(Art.FACHLICH, List.of("[Fachlich]", "[Requirement]"));
    praefixe.put(Art.IDEE, List.of("[Idee]", "[Idea]"));
    praefixe.put(Art.MENSCH, List.of("[Mensch]", "[Human]"));
    praefixe.put(Art.PLAN, List.of("[Plan]"));
    praefixe.put(Art.TASK, List.of("[Task]"));
    return praefixe;
  }

  private static Map<Art, Pattern> titelMusterAufbauen() {
    Map<Art, Pattern> muster = new EnumMap<>(Art.class);
    TITEL_PRAEFIXE.forEach(
        (art, praefixe) ->
            muster.put(
                art,
                Pattern.compile(
                    "^\\s*(?:"
                        + String.join("|", praefixe.stream().map(Pattern::quote).toList())
                        + ")",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)));
    return Collections.unmodifiableMap(muster);
  }
}
