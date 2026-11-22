package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.application.RolePermissionRepository;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;

/**
 * Was ein Kunde ohne {@link Permission#REPAIR_READ} nicht sieht (Issue #2211, Plan #2206 E2).
 *
 * <p>Die Rechte kommen aus einem echten {@link PermissionChecker} über die Rollen-Matrix: Kunde
 * ohne das Recht, Owner mit ihm, der Plattform-Admin über seinen Bypass — so prüft der Test die
 * Kette Rolle → Recht → Sicht und nicht nur einen gestubbten Wahrheitswert.
 */
class AnbietersichtTest {

  private static final long PROJEKT = 1L;
  private static final long BOARD = 10L;
  private static final long KUNDE = 7L;
  private static final long OWNER = 8L;
  private static final long ADMIN = 9L;
  private static final int REPARATUR_NR = 1501;
  private static final int NORMAL_NR = 1500;
  private static final long REP_ID = 99L;
  private static final Instant T = Instant.parse("2026-11-22T10:00:00Z");

  private CardRepository cards;
  private CardLabelRepository cardLabels;
  private Anbietersicht sicht;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    ProjectMembershipRepository memberships = mock(ProjectMembershipRepository.class);
    RolePermissionRepository matrix = mock(RolePermissionRepository.class);
    PlatformAdminChecker admins = mock(PlatformAdminChecker.class);
    when(admins.isPlatformAdmin(ADMIN)).thenReturn(true);
    when(memberships.findByProjectIdAndUserId(PROJEKT, KUNDE))
        .thenReturn(Optional.of(mitglied(KUNDE, ProjectRole.CUSTOMER)));
    when(memberships.findByProjectIdAndUserId(PROJEKT, OWNER))
        .thenReturn(Optional.of(mitglied(OWNER, ProjectRole.OWNER)));
    when(matrix.isGranted(ProjectRole.OWNER, Permission.REPAIR_READ)).thenReturn(true);
    PermissionChecker permissions =
        new PermissionChecker(
            memberships, matrix, admins, mock(ProjectRepository.class), Clock.systemUTC());
    KartenZuordnung zuordnung =
        new KartenZuordnung(
            mock(CardAssigneeRepository.class),
            mock(LabelRepository.class),
            cardLabels,
            permissions,
            mock(CardLabelEventRepository.class),
            ActorContext.ActorStamp::unknown,
            Clock.systemUTC(),
            mock(ZuweisungsBenachrichtigung.class));
    sicht = new Anbietersicht(cards, zuordnung, permissions);
    when(cardLabels.kartenMitLabel(PROJEKT, "kit:repair")).thenReturn(List.of(REP_ID));
    when(cards.findByIds(List.of(REP_ID))).thenReturn(List.of(karte(REP_ID, REPARATUR_NR)));
  }

  private static ProjectMembership mitglied(long userId, ProjectRole rolle) {
    return new ProjectMembership(1L, PROJEKT, userId, rolle, T);
  }

  private static Card karte(long id, @Nullable Integer nummer) {
    return new Card(
        id,
        BOARD,
        20L,
        nummer,
        "Titel",
        null,
        0,
        false,
        null,
        1L,
        T,
        T,
        CardType.CARD,
        null,
        null,
        null,
        PROJEKT,
        null,
        null,
        null,
        null);
  }

  @Test
  void ohneRecht_istEineKarteMitKitRepairVerborgen() {
    assertThat(sicht.istVerborgen(KUNDE, PROJEKT, karte(1L, REPARATUR_NR))).isTrue();
    assertThat(sicht.istVerborgen(KUNDE, PROJEKT, karte(2L, NORMAL_NR))).isFalse();
    assertThat(sicht.siehtStoerungen(KUNDE, PROJEKT)).isFalse();
  }

  @Test
  void mitRecht_istNichtsVerborgen_undDieLabelsWerdenNichtAbgefragt() {
    assertThat(sicht.istVerborgen(OWNER, PROJEKT, karte(1L, REPARATUR_NR))).isFalse();
    assertThat(sicht.verborgeneNummern(OWNER, PROJEKT)).isEmpty();
    verify(cardLabels, never()).kartenMitLabel(anyLong(), anyString());
  }

  @Test
  void derPlattformAdmin_siehtAlles() {
    assertThat(sicht.istVerborgen(ADMIN, PROJEKT, karte(1L, REPARATUR_NR))).isFalse();
    assertThat(sicht.siehtStoerungen(ADMIN, PROJEKT)).isTrue();
  }

  @Test
  void eineKarteOhneNummer_istNieVerborgen() {
    assertThat(sicht.istVerborgen(KUNDE, PROJEKT, karte(1L, null))).isFalse();
  }

  @Test
  void requireSichtbar_endetFuerDieVerborgeneKarteWieFuerEineUnbekannte() {
    when(cards.findById(1L)).thenReturn(Optional.of(karte(1L, REPARATUR_NR)));
    when(cards.findById(2L)).thenReturn(Optional.of(karte(2L, NORMAL_NR)));

    assertThatThrownBy(() -> sicht.requireSichtbar(KUNDE, 1L))
        .isInstanceOf(CardNotFoundException.class);
    assertThatThrownBy(() -> sicht.requireSichtbar(KUNDE, 3L))
        .isInstanceOf(CardNotFoundException.class);
    assertThatCode(() -> sicht.requireSichtbar(KUNDE, 2L)).doesNotThrowAnyException();
    assertThatCode(() -> sicht.requireSichtbar(OWNER, 1L)).doesNotThrowAnyException();
  }

  @Test
  void sichtbare_laesstVerborgeneAus_undBehaeltDieReihenfolge() {
    Card a = karte(1L, NORMAL_NR);
    Card r = karte(2L, REPARATUR_NR);
    Card b = karte(3L, 1502);

    assertThat(sicht.sichtbare(KUNDE, PROJEKT, List.of(b, r, a))).containsExactly(b, a);
    assertThat(sicht.sichtbare(OWNER, PROJEKT, List.of(b, r, a))).containsExactly(b, r, a);
    assertThat(sicht.sichtbare(KUNDE, PROJEKT, List.<Card>of())).isEmpty();
  }

  @Test
  void sichtbare_fuerEigeneSichten_ueberDieNummer() {
    assertThat(sicht.sichtbare(KUNDE, PROJEKT, List.of(NORMAL_NR, REPARATUR_NR), n -> n))
        .containsExactly(NORMAL_NR);
  }

  @Test
  void ohneVerborgeneNummern_streichtNurDieVerborgenen() {
    Map<Long, List<Integer>> jeKarte = Map.of(1L, List.of(REPARATUR_NR, NORMAL_NR), 2L, List.of());

    assertThat(Anbietersicht.ohneVerborgeneNummern(jeKarte, Set.of(REPARATUR_NR)))
        .containsExactlyInAnyOrderEntriesOf(Map.of(1L, List.of(NORMAL_NR), 2L, List.of()));
    assertThat(Anbietersicht.ohneVerborgeneNummern(jeKarte, Set.of())).isSameAs(jeKarte);
  }

  @Test
  void ohneVerborgeneVerweise_streichtAbhaengigkeitHerkunftUndKettenglied() {
    CardView vorher = sichtMitVerweisen(REPARATUR_NR);

    CardView nachher = sicht.ohneVerborgeneVerweise(vorher, Set.of(REPARATUR_NR));

    assertThat(nachher.dependencies()).containsExactly(NORMAL_NR);
    assertThat(nachher.derivedFrom()).isNull();
    assertThat(nachher.herkunft()).extracting(HerkunftsGlied::number).containsExactly(NORMAL_NR);
    assertThat(nachher)
        .usingRecursiveComparison()
        .ignoringFields("dependencies", "derivedFrom", "herkunft")
        .isEqualTo(vorher);
  }

  @Test
  void ohneVerborgeneVerweise_laesstSichtbareVerweiseStehen() {
    CardView vorher = sichtMitVerweisen(NORMAL_NR);

    assertThat(sicht.ohneVerborgeneVerweise(vorher, Set.of(REPARATUR_NR)).derivedFrom())
        .isEqualTo(NORMAL_NR);
    assertThat(sicht.ohneVerborgeneVerweise(vorher, Set.of())).isSameAs(vorher);
  }

  @Test
  void ohneVerborgeneVerweise_ohneKetteUndHerkunft() {
    CardView vorher =
        new CardView(
            5L,
            BOARD,
            20L,
            1600,
            "T",
            null,
            null,
            0,
            false,
            null,
            List.of(),
            CardType.CARD,
            null,
            null,
            List.of(),
            null,
            List.of(),
            null,
            null,
            false,
            null);

    CardView nachher = sicht.ohneVerborgeneVerweise(vorher, Set.of(REPARATUR_NR));

    assertThat(nachher.herkunft()).isNull();
    assertThat(nachher.derivedFrom()).isNull();
  }

  @Test
  void istReparaturkarte_gleichFuerWen() {
    when(cards.findById(REP_ID)).thenReturn(Optional.of(karte(REP_ID, REPARATUR_NR)));
    when(cards.findById(2L)).thenReturn(Optional.of(karte(2L, NORMAL_NR)));

    assertThat(sicht.istReparaturkarte(REP_ID)).isTrue();
    assertThat(sicht.istReparaturkarte(2L)).isFalse();
    assertThat(sicht.istReparaturkarte(3L)).isFalse();
  }

  @Test
  void ohneReparaturkarten_werdenKeineKartenNachgeladen() {
    when(cardLabels.kartenMitLabel(PROJEKT, "kit:repair")).thenReturn(List.of());

    assertThat(sicht.verborgeneNummern(KUNDE, PROJEKT)).isEmpty();
    verify(cards, never()).findByIds(anyCollection());
  }

  @Test
  void eineReparaturkarteOhneNummer_verbirgtKeineNummer() {
    when(cards.findByIds(List.of(REP_ID))).thenReturn(List.of(karte(REP_ID, null)));

    assertThat(sicht.verborgeneNummern(KUNDE, PROJEKT)).isEmpty();
  }

  private static CardView sichtMitVerweisen(int herkunft) {
    return new CardView(
        5L,
        BOARD,
        20L,
        1600,
        "Titel",
        "Text",
        null,
        2,
        false,
        null,
        List.of(REPARATUR_NR, NORMAL_NR),
        CardType.CARD,
        null,
        null,
        List.of(3L),
        null,
        List.of(4L),
        herkunft,
        "READY",
        true,
        List.of(
            new HerkunftsGlied(REPARATUR_NR, "Reparatur", false, null),
            new HerkunftsGlied(NORMAL_NR, "Plan", false, null)),
        "https://example.org/7");
  }
}
