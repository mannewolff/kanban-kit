package org.mwolff.manban.card.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;

/**
 * Die Sicht einer Karte, wie die Karten-Themen sie nach außen geben (Issue #1389, Plan #1387 E2):
 * die {@link CardView} samt Herkunfts-Nummer, Status als Text und dem Recht, ihn zu setzen.
 *
 * <p>Die Helfer-Gruppe „Sicht bauen“ aus {@link KartenGrundlage} herausgetrennt, damit keiner der
 * beiden Bausteine eine Kopplungsausnahme braucht. Modulintern wie {@link KartenZuordnung}: Die
 * Klasse steht nicht auf der Fassaden-Whitelist der ArchUnit-Regel {@code
 * CARD_APPLICATION_IST_AUF_FASSADE_BEGRENZT}.
 *
 * <p>Ohne {@code @Transactional}: Jede Methode läuft in der Transaktion des aufrufenden
 * Use-Case-Verfahrens.
 */
@Service
public final class KartenSicht {

  /**
   * Länge des Listen-Auszugs in Codepoints (Issue #771). Reicht für die einzeilige, ohnehin
   * abgeschnittene Vorschau der Listenansicht — mehr Text käme nie auf den Bildschirm.
   */
  private static final int AUSZUG_CODEPOINTS = 200;

  private final CardRepository cards;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final KartenZuordnung zuordnung;
  private final PermissionChecker permissions;

  public KartenSicht(
      CardRepository cards,
      KartenAbhaengigkeiten abhaengigkeiten,
      KartenZuordnung zuordnung,
      PermissionChecker permissions) {
    this.cards = cards;
    this.abhaengigkeiten = abhaengigkeiten;
    this.zuordnung = zuordnung;
    this.permissions = permissions;
  }

  /**
   * Herkunfts-Nummern zu einer Kartenliste — <strong>ein</strong> Sammelzugriff, unabhaengig von
   * der Zahl verschiedener Vorfahren. Je Karte einzeln nachzuschlagen ergaebe ein N+1 auf einer
   * Liste, die ein ganzes Board umfasst.
   */
  public Map<Long, Integer> herkunftsnummern(List<Card> karten) {
    Set<Long> ids =
        karten.stream()
            .map(Card::derivedFromCardId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<Long, Integer> nummern = new HashMap<>();
    for (Card vorfahr : cards.findByIds(ids)) {
      nummern.put(vorfahr.requireId(), vorfahr.number());
    }
    return nummern;
  }

  /**
   * Die Sicht der Kartenliste eines Boards — vier Sammelzugriffe statt vier Abfragen <em>je
   * Karte</em> (Issue #768); aus {@code CardService.listByBoard} hierher gezogen (Issue #1397).
   *
   * <p>Bewusst nicht über {@link #view(Card, boolean)}: Der baut eine einzelne Karte und lädt
   * Abhängigkeiten, Zuständige, Labels und Herkunft je Aufruf einzeln nach. Auf einer ganzen
   * Board-Liste ergibt das ein N+1 mit vier Abfragen pro Karte; hier sind es vier für die gesamte
   * Liste.
   *
   * <p>Die Beschreibung kommt hier <b>nicht</b> mit (Issue #771): {@code description} ist immer
   * {@code null}, gesetzt ist stattdessen {@code excerpt} — die ersten {@value #AUSZUG_CODEPOINTS}
   * Codepoints. Den Volltext holt der Einzelabruf.
   */
  public List<CardView> listenSicht(long userId, long projectId, List<Card> karten) {
    Set<Long> ids = karten.stream().map(Card::requireId).collect(Collectors.toSet());
    // Karten ohne Eintrag fehlen in den Maps (Vertrag der drei findByCardIds) — die Sicht setzt
    // dort eine leere Liste, nie null.
    Map<Long, List<Integer>> abhaengigkeitenJeKarte = abhaengigkeiten.abhaengigkeitenJeKarte(ids);
    Map<Long, List<Long>> zustaendige = zuordnung.zustaendigeJeKarte(ids);
    Map<Long, List<Long>> labelIds = zuordnung.labelsJeKarte(ids);
    Map<Long, Integer> nummern = herkunftsnummern(karten);
    // Alle Karten liegen auf diesem Board: eine CARD_MOVE-Prüfung für die ganze Liste (E10).
    boolean darfStatusSetzen = darfStatusSetzen(userId, projectId);
    return karten.stream()
        .map(
            c ->
                new CardView(
                    c.requireId(),
                    c.boardId(),
                    c.columnId(),
                    c.number(),
                    c.title(),
                    // Die Board-Liste zeigt die Beschreibung nirgends ganz: Kacheln gar nicht, die
                    // Listenansicht nur einzeilig abgeschnitten. Der Volltext kommt über den
                    // Einzelabruf (Issue #771).
                    null,
                    auszug(c.description()),
                    c.positionInColumn(),
                    c.archived(),
                    c.movedToDoneAt(),
                    abhaengigkeitenJeKarte.getOrDefault(c.requireId(), List.of()),
                    c.type(),
                    c.parentId(),
                    c.shortcode(),
                    zustaendige.getOrDefault(c.requireId(), List.of()),
                    c.dueDate(),
                    labelIds.getOrDefault(c.requireId(), List.of()),
                    c.derivedFromCardId() == null ? null : nummern.get(c.derivedFromCardId()),
                    statusName(c),
                    c.status() != null && darfStatusSetzen))
        .toList();
  }

  /**
   * Vorschautext einer Karte: die ersten {@value #AUSZUG_CODEPOINTS} Codepoints der <b>rohen</b>,
   * ungestrippten Beschreibung. Roh, weil das Strippen der Markdown-Syntax im Frontend sitzt und
   * dort auch für die Sortierung gebraucht wird.
   *
   * <p>Geschnitten wird über {@link String#offsetByCodePoints(int, int)} und nicht über den
   * char-Index: Ein Emoji belegt zwei {@code char}, und ein Schnitt mitten hinein hinterließe ein
   * halbes Surrogatpaar — im Browser ein Ersatzzeichen.
   *
   * @return {@code null}, wenn keine Beschreibung gesetzt ist
   */
  private static @Nullable String auszug(@Nullable String beschreibung) {
    if (beschreibung == null) {
      return null;
    }
    int codepoints =
        Math.min(beschreibung.codePointCount(0, beschreibung.length()), AUSZUG_CODEPOINTS);
    return beschreibung.substring(0, beschreibung.offsetByCodePoints(0, codepoints));
  }

  /**
   * Herkunfts-Nummer einer einzelnen Karte. Liefert {@code null}, wenn keine Herkunft gesetzt ist
   * oder der Vorfahr nicht mehr existiert — die Sicht haelt den Zustand aus, statt zu scheitern.
   */
  public @Nullable Integer herkunftsnummer(Card c) {
    Long id = c.derivedFromCardId();
    return id == null ? null : cards.findById(id).map(Card::number).orElse(null);
  }

  /**
   * Die Sicht einer einzelnen Karte für diesen Betrachter — die {@link
   * Permission#CARD_MOVE}-Prüfung für {@code canSetStatus} läuft hier einmal (Plan #1294, E10).
   */
  public CardView view(long userId, Card c) {
    return view(c, darfStatusSetzen(userId, c.projectId()));
  }

  /**
   * Die Sicht einer Karte. {@code canSetStatus} ist das Ergebnis der einen {@link
   * Permission#CARD_MOVE}-Prüfung je Anfrage (E10) und greift nur, wo die Karte einen eigenen
   * Status trägt — ohne ihn gibt es nichts zu setzen.
   *
   * <p>Bewusst für eine einzelne Karte: Abhängigkeiten, Zuständige, Labels und Herkunft werden je
   * Aufruf einzeln nachgeladen. Listen bauen ihre Sicht mit Sammelzugriffen selbst (Issue #768).
   */
  public CardView view(Card c, boolean canSetStatus) {
    return new CardView(
        c.requireId(),
        c.boardId(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        // Einzelkarte: der Volltext steht schon in description, ein Auszug daneben wäre redundant.
        null,
        c.positionInColumn(),
        c.archived(),
        c.movedToDoneAt(),
        abhaengigkeiten.abhaengigkeitenVon(c.requireId()),
        c.type(),
        c.parentId(),
        c.shortcode(),
        zuordnung.zustaendigeVon(c.requireId()),
        c.dueDate(),
        zuordnung.labelsVon(c.requireId()),
        herkunftsnummer(c),
        statusName(c),
        c.status() != null && canSetStatus);
  }

  /**
   * Der Status als Text über die Modulgrenze (E24): der Konstantenname, zugleich der gespeicherte
   * Wert; {@code null} ohne eigenen Status.
   */
  public static @Nullable String statusName(Card c) {
    CardStatus status = c.status();
    return status == null ? null : status.name();
  }

  /**
   * Ob der Betrachter im Projekt einer Karte den Status setzen darf — dasselbe Recht wie für das
   * Verschieben (E9). Geprüft wird das Projekt der Karte, nicht das der aufrufenden Ansicht: Eine
   * über {@code #N} gefundene Karte kann auf einem fremden Board mit anderen Rechten liegen (E10).
   * Die Projekt-ID der Karte stimmt mit der ihres Boards überein (siehe {@link
   * KartenGrundlage#requireCardOp}).
   */
  public boolean darfStatusSetzen(long userId, long projectId) {
    return permissions.hasPermission(userId, projectId, Permission.CARD_MOVE);
  }
}
