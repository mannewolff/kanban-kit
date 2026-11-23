package org.mwolff.manban.card.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;

/**
 * Was ein Aufrufer ohne das Recht {@link Permission#REPAIR_READ} von den Karten nicht sieht (Issue
 * #2211, Plan #2206 E2, E18): Karten mit einem Label der Sache {@link Sache#REPARATUR} — die
 * Störungen beim Anbieter. Ein Mitglied mit der Rolle Kunde erfährt auf keinem Kartenweg von ihnen;
 * alle übrigen Rollen und der Plattform-Admin sehen alles.
 *
 * <p>Die eine Stelle, an der die Dienste eine Karte für einen Aufrufer auflösen, lesend wie
 * schreibend: {@link #requireSichtbar} antwortet für eine verborgene Karte mit derselben {@link
 * CardNotFoundException} wie für eine fremde (404, kein Existenz-Leak), Listen lassen sie mit
 * {@link #sichtbare} aus, und {@link #ohneVerborgeneVerweise} streicht Verweise sichtbarer Karten
 * auf sie. Ausgeschrieben je Dienst wären es rund 20 Abschriften (E18).
 *
 * <p>Verglichen wird über die projektweite Nummer ({@code uq_card_number (project_id, number)}):
 * Verweise einer Karte tragen Nummern. Eine Abfrage je Projekt liefert die IDs aller Karten mit
 * Reparatur-Label, eine zweite ihre Nummern — die nur, wenn es solche Karten gibt. Für einen
 * Aufrufer mit dem Recht fragt die Klasse die Labels gar nicht erst ab.
 *
 * <p>Vertrag des Moduls {@code card} (in {@code CARD_APPLICATION_IST_AUF_FASSADE_BEGRENZT}): Fremde
 * Module wie {@code nightrun} und die Composition-Root fragen hier, ob eine Karte verborgen ist.
 *
 * <p>Ohne {@code @Transactional}: Jede Methode läuft in der Transaktion des aufrufenden Dienstes.
 */
@Service
public final class Anbietersicht {

  /** Die Namen der Sache Reparatur, deutsch und englisch, wie das Register sie führt. */
  private static final List<String> REPARATUR = KitNamen.STANDARD.namen(Sache.REPARATUR);

  private final CardRepository cards;
  private final KartenZuordnung zuordnung;
  private final PermissionChecker permissions;

  public Anbietersicht(
      CardRepository cards, KartenZuordnung zuordnung, PermissionChecker permissions) {
    this.cards = cards;
    this.zuordnung = zuordnung;
    this.permissions = permissions;
  }

  /**
   * Ob der Aufrufer im Projekt Störungen sieht — das Recht {@link Permission#REPAIR_READ}; der
   * Plattform-Admin hat es immer.
   */
  public boolean siehtStoerungen(long userId, long projectId) {
    return permissions.hasPermission(userId, projectId, Permission.REPAIR_READ);
  }

  /**
   * Die Nummern der Karten des Projekts, die der Aufrufer nicht sieht; leer, wenn er Störungen
   * sieht.
   */
  public Set<Integer> verborgeneNummern(long userId, long projectId) {
    if (siehtStoerungen(userId, projectId)) {
      return Set.of();
    }
    List<Long> ids = reparaturkarten(projectId);
    if (ids.isEmpty()) {
      return Set.of();
    }
    return cards.findByIds(ids).stream()
        .map(Card::number)
        .filter(Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Die IDs der Karten des Projekts, die der Aufrufer nicht sieht; leer, wenn er Störungen sieht —
   * für Module, die Einträge je Karten-ID führen, etwa Kommentare (Issue #2214).
   */
  public Set<Long> verborgeneKartenIds(long userId, long projectId) {
    if (siehtStoerungen(userId, projectId)) {
      return Set.of();
    }
    return Set.copyOf(reparaturkarten(projectId));
  }

  /** Ob die Karte für den Aufrufer verborgen ist: ohne das Recht und mit einem Reparatur-Label. */
  public boolean istVerborgen(long userId, long projectId, Card karte) {
    return nummerVerborgen(karte.number(), verborgeneNummern(userId, projectId));
  }

  /**
   * Verlangt, dass die Karte für den Aufrufer sichtbar ist — die Auflösung für Module, die nur eine
   * Karten-ID kennen. Eine unbekannte und eine verborgene Karte enden gleich.
   *
   * @throws CardNotFoundException wenn die Karte fehlt oder dem Aufrufer verborgen ist
   */
  public void requireSichtbar(long userId, long cardId) {
    pruefeSichtbar(userId, cards.findById(cardId).orElseThrow(CardNotFoundException::new));
  }

  /**
   * Wie {@link #requireSichtbar(long, long)} für eine schon geladene Karte.
   *
   * @throws CardNotFoundException wenn die Karte dem Aufrufer verborgen ist
   */
  public void pruefeSichtbar(long userId, Card karte) {
    if (istVerborgen(userId, karte.projectId(), karte)) {
      throw new CardNotFoundException();
    }
  }

  /** Die Karten der Liste, die der Aufrufer sieht, in ihrer Reihenfolge. */
  public List<Card> sichtbare(long userId, long projectId, List<Card> karten) {
    return sichtbare(userId, projectId, karten, Card::number);
  }

  /**
   * Die Einträge der Liste, die der Aufrufer sieht, in ihrer Reihenfolge — für Listen eigener
   * Sichten, die die Nummer ihrer Karte tragen.
   */
  public <T> List<T> sichtbare(
      long userId, long projectId, List<T> eintraege, Function<T, @Nullable Integer> nummer) {
    if (eintraege.isEmpty()) {
      return eintraege;
    }
    return ohneVerborgene(eintraege, verborgeneNummern(userId, projectId), nummer);
  }

  /** Die Karten der Liste ohne die mit einer der verborgenen Nummern, in ihrer Reihenfolge. */
  public static List<Card> ohneVerborgene(List<Card> karten, Set<Integer> verborgen) {
    return ohneVerborgene(karten, verborgen, Card::number);
  }

  private static <T> List<T> ohneVerborgene(
      List<T> eintraege, Set<Integer> verborgen, Function<T, @Nullable Integer> nummer) {
    if (verborgen.isEmpty()) {
      return eintraege;
    }
    return eintraege.stream().filter(e -> !nummerVerborgen(nummer.apply(e), verborgen)).toList();
  }

  /**
   * Die Nummern je Karte — etwa ihre Abhängigkeiten — ohne die verborgenen, Reihenfolge und
   * Schlüssel unverändert.
   */
  public static Map<Long, List<Integer>> ohneVerborgeneNummern(
      Map<Long, List<Integer>> jeKarte, Set<Integer> verborgen) {
    if (verborgen.isEmpty()) {
      return jeKarte;
    }
    Map<Long, List<Integer>> ergebnis = new HashMap<>();
    jeKarte.forEach(
        (karte, nummern) ->
            ergebnis.put(karte, nummern.stream().filter(n -> !verborgen.contains(n)).toList()));
    return ergebnis;
  }

  /**
   * Die Sicht ohne Verweise auf verborgene Karten: Abhängigkeiten, Herkunft ({@code derivedFrom})
   * und die Glieder der Herkunftskette, die eine verborgene Nummer tragen, entfallen.
   */
  public CardView ohneVerborgeneVerweise(CardView sicht, Set<Integer> verborgen) {
    if (verborgen.isEmpty()) {
      return sicht;
    }
    Integer herkunft = sicht.derivedFrom();
    List<HerkunftsGlied> kette = sicht.herkunft();
    return new CardView(
        sicht.id(),
        sicht.boardId(),
        sicht.columnId(),
        sicht.number(),
        sicht.title(),
        sicht.description(),
        sicht.excerpt(),
        sicht.positionInColumn(),
        sicht.archived(),
        sicht.movedToDoneAt(),
        sicht.dependencies().stream().filter(n -> !verborgen.contains(n)).toList(),
        sicht.type(),
        sicht.parentId(),
        sicht.shortcode(),
        sicht.assignees(),
        sicht.dueDate(),
        sicht.labels(),
        nummerVerborgen(herkunft, verborgen) ? null : herkunft,
        sicht.status(),
        sicht.canSetStatus(),
        kette == null ? null : kette.stream().filter(g -> !verborgen.contains(g.number())).toList(),
        sicht.externUrl());
  }

  /**
   * Ob die Karte eine Reparaturkarte ist, gleich für wen — für Wege ohne Aufrufer wie den Strom der
   * Board-Ereignisse, der jedem Abonnenten dasselbe schickt. Eine unbekannte Karte ist keine.
   */
  public boolean istReparaturkarte(long cardId) {
    return cards
        .findById(cardId)
        .map(k -> reparaturkarten(k.projectId()).contains(cardId))
        .orElse(false);
  }

  /** Die IDs der Karten des Projekts mit einem Label der Sache Reparatur, unter jedem Namen. */
  private List<Long> reparaturkarten(long projectId) {
    return REPARATUR.stream()
        .flatMap(n -> zuordnung.kartenMitLabel(projectId, n).stream())
        .toList();
  }

  private static boolean nummerVerborgen(@Nullable Integer nummer, Set<Integer> verborgen) {
    return nummer != null && verborgen.contains(nummer);
  }
}
