package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.domain.VorschauEndgrund;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.ratelimit.MutableClock;

/** Starten, Prüfen, Beenden und Protokoll der „Ansicht als XY“ (Issue #1917, Plan #1914). */
// PMD.TooManyMethods: je Startregel und je Endfall aus E9 eine eigene Testmethode, damit ein
// Fehlschlag den Fall nennt, den das Akzeptanzkriterium aufzählt.
@SuppressWarnings("PMD.TooManyMethods")
class AdminVorschauServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
  private static final Instant ABLAUF = NOW.plus(Duration.ofMinutes(30));
  private static final long ADMIN = 1L;
  private static final long ZIEL = 2L;
  private static final long GENERATION = 4L;
  private static final String KENNUNG = "kennung-a";
  private static final String GEHEIMNIS = "geheimnis-a";

  private AppUserRepository users;
  private SessionGenerations generations;
  private AdminVorschauRepository vorschauen;
  private MutableClock clock;
  private AdminVorschauService service;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    generations = mock(SessionGenerations.class);
    vorschauen = mock(AdminVorschauRepository.class);
    clock = new MutableClock(NOW);
    service = new AdminVorschauService(users, generations, vorschauen, clock);
    when(vorschauen.save(any(AdminVorschau.class)))
        .thenAnswer(inv -> mitId(inv.getArgument(0, AdminVorschau.class)));
    when(users.findById(ADMIN)).thenReturn(Optional.of(adminKonto()));
    when(users.findById(ZIEL)).thenReturn(Optional.of(zielKonto(Instant.EPOCH, null)));
    when(generations.current(ADMIN)).thenReturn(OptionalLong.of(GENERATION));
  }

  private static AdminVorschau mitId(AdminVorschau v) {
    return new AdminVorschau(
        99L,
        v.kennung(),
        v.geheimnisHash(),
        v.adminId(),
        v.adminName(),
        v.zielId(),
        v.sitzungsGeneration(),
        v.gestartetAm(),
        v.laeuftAbAm(),
        v.beendetAm(),
        v.endgrund());
  }

  private static AppUser adminKonto() {
    return new AppUser(ADMIN, "admin@example.org", "Ada Admin", true, PlatformRole.ADMIN);
  }

  private static AppUser zielKonto(Instant approvedAt, Instant disabledAt) {
    return new AppUser(
        ZIEL, "xy@example.org", "Xaver", true, PlatformRole.USER, approvedAt, null, disabledAt);
  }

  private static AdminVorschau offen() {
    return new AdminVorschau(
        5L,
        KENNUNG,
        SecureTokens.sha256Hex(GEHEIMNIS),
        ADMIN,
        "Ada Admin",
        ZIEL,
        GENERATION,
        NOW,
        ABLAUF,
        null,
        null);
  }

  private void liegtVor(AdminVorschau vorschau) {
    when(vorschauen.findByKennung(KENNUNG)).thenReturn(Optional.of(vorschau));
    when(vorschauen.beende(anyLong(), any(), any())).thenReturn(true);
  }

  private AdminVorschau gespeichert() {
    ArgumentCaptor<AdminVorschau> captor = ArgumentCaptor.forClass(AdminVorschau.class);
    verify(vorschauen).save(captor.capture());
    return captor.getValue();
  }

  // ---------------------------------------------------------------- Starten (E10, E11)

  @Test
  void startLegtEineOffeneVorschauMitAblaufNachDreissigMinutenAn() {
    VorschauStart start = service.starte(ADMIN, ZIEL, false);

    AdminVorschau zeile = gespeichert();
    assertThat(zeile.id()).isNull();
    assertThat(zeile.adminId()).isEqualTo(ADMIN);
    assertThat(zeile.adminName()).isEqualTo("Ada Admin");
    assertThat(zeile.zielId()).isEqualTo(ZIEL);
    assertThat(zeile.sitzungsGeneration()).isEqualTo(GENERATION);
    assertThat(zeile.gestartetAm()).isEqualTo(NOW);
    assertThat(zeile.laeuftAbAm()).isEqualTo(ABLAUF);
    assertThat(zeile.beendetAm()).isNull();
    assertThat(zeile.endgrund()).isNull();
    assertThat(start.vorschau().id()).isEqualTo(99L);
    assertThat(start.vorschau().kennung()).isEqualTo(zeile.kennung());
  }

  @Test
  void gespeichertWirdNurDerHashDesGeheimnisses() {
    VorschauStart start = service.starte(ADMIN, ZIEL, false);

    AdminVorschau zeile = gespeichert();
    assertThat(start.geheimnis()).hasSizeGreaterThanOrEqualTo(43);
    assertThat(zeile.geheimnisHash())
        .isEqualTo(SecureTokens.sha256Hex(start.geheimnis()))
        .isNotEqualTo(start.geheimnis());
    assertThat(zeile.toString()).doesNotContain(start.geheimnis());
    assertThat(zeile.kennung()).isNotBlank().isNotEqualTo(start.geheimnis());
    assertThat(start.toString()).doesNotContain(start.geheimnis()).contains(zeile.kennung());
  }

  @Test
  void jederStartBekommtEigeneKennungUndEigenesGeheimnis() {
    VorschauStart erster = service.starte(ADMIN, ZIEL, false);
    VorschauStart zweiter = service.starte(ADMIN, ZIEL, false);

    assertThat(zweiter.vorschau().kennung()).isNotEqualTo(erster.vorschau().kennung());
    assertThat(zweiter.geheimnis()).isNotEqualTo(erster.geheimnis());
  }

  @Test
  void startDurchNichtAdminWirdAbgewiesen() {
    when(users.findById(ADMIN))
        .thenReturn(Optional.of(new AppUser(ADMIN, "u@example.org", "U", true, PlatformRole.USER)));

    assertThatThrownBy(() -> service.starte(ADMIN, ZIEL, false))
        .isInstanceOf(AdminAccessDeniedException.class);
    verify(vorschauen, never()).save(any());
  }

  @Test
  void startDurchGesperrtenAdminWirdAbgewiesen() {
    when(users.findById(ADMIN)).thenReturn(Optional.of(adminKonto().withDisabledAt(NOW)));

    assertThatThrownBy(() -> service.starte(ADMIN, ZIEL, false))
        .isInstanceOf(AdminAccessDeniedException.class);
    verify(vorschauen, never()).save(any());
  }

  @Test
  void startDurchUnbekanntesKontoWirdAbgewiesen() {
    when(users.findById(ADMIN)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.starte(ADMIN, ZIEL, false))
        .isInstanceOf(AdminAccessDeniedException.class);
  }

  @Test
  void startOhneSitzungsGenerationWirdAbgewiesen() {
    when(generations.current(ADMIN)).thenReturn(OptionalLong.empty());

    assertThatThrownBy(() -> service.starte(ADMIN, ZIEL, false))
        .isInstanceOf(AdminAccessDeniedException.class);
    verify(vorschauen, never()).save(any());
  }

  @Test
  void startAlsSichSelbstWirdAbgewiesen() {
    assertThatThrownBy(() -> service.starte(ADMIN, ADMIN, false))
        .isInstanceOf(VorschauZielUnzulaessigException.class);
    verify(vorschauen, never()).save(any());
  }

  @Test
  void startAlsAnderenPlattformAdminWirdAbgewiesen() {
    when(users.findById(ZIEL))
        .thenReturn(
            Optional.of(new AppUser(ZIEL, "b@example.org", "Bea", true, PlatformRole.ADMIN)));

    assertThatThrownBy(() -> service.starte(ADMIN, ZIEL, false))
        .isInstanceOf(VorschauZielUnzulaessigException.class);
    verify(vorschauen, never()).save(any());
  }

  @Test
  void startAlsUnbekanntesKontoWirdAbgewiesen() {
    when(users.findById(ZIEL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.starte(ADMIN, ZIEL, false))
        .isInstanceOf(UserNotFoundException.class);
    verify(vorschauen, never()).save(any());
  }

  @Test
  void startAlsGesperrtesKontoIstErlaubt() {
    when(users.findById(ZIEL)).thenReturn(Optional.of(zielKonto(Instant.EPOCH, NOW)));

    assertThat(service.starte(ADMIN, ZIEL, false).vorschau().zielId()).isEqualTo(ZIEL);
  }

  @Test
  void startAlsWartendesKontoIstErlaubt() {
    when(users.findById(ZIEL)).thenReturn(Optional.of(zielKonto(null, null)));

    assertThat(service.starte(ADMIN, ZIEL, false).vorschau().zielId()).isEqualTo(ZIEL);
  }

  @Test
  void startMitVorschauCookieWirdAbgewiesen() {
    assertThatThrownBy(() -> service.starte(ADMIN, ZIEL, true))
        .isInstanceOf(VorschauLaeuftBereitsException.class);
    verify(vorschauen, never()).save(any());
  }

  @Test
  void startLaesstOffeneVorschauenDesselbenAdminsUnberuehrt() {
    service.starte(ADMIN, ZIEL, false);

    verify(vorschauen, never()).beende(anyLong(), any(), any());
    verify(vorschauen, never()).beendeAbgelaufene(anyLong(), any());
  }

  // ---------------------------------------------------------------- Prüfen (E3, E9)

  @Test
  void passendeOffeneVorschauIstGueltig() {
    liegtVor(offen());

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Gueltig(offen()));
    verify(vorschauen, never()).beende(anyLong(), any(), any());
  }

  @Test
  void unbekannteKennungIstUnbekannt() {
    when(vorschauen.findByKennung(KENNUNG)).thenReturn(Optional.empty());

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Unbekannt());
  }

  @Test
  void falschesGeheimnisIstUnbekanntUndBeendetNichts() {
    liegtVor(offen());

    assertThat(service.pruefe(KENNUNG, "falsch", ADMIN))
        .isEqualTo(new VorschauPruefung.Unbekannt());
    verify(vorschauen, never()).beende(anyLong(), any(), any());
  }

  @Test
  void fremderAdminIstUnbekanntUndBeendetNichts() {
    liegtVor(offen());

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, 3L)).isEqualTo(new VorschauPruefung.Unbekannt());
    verify(vorschauen, never()).beende(anyLong(), any(), any());
  }

  @Test
  void zeileOhneAdminIstUnbekannt() {
    AdminVorschau o = offen();
    liegtVor(
        new AdminVorschau(
            o.id(),
            KENNUNG,
            o.geheimnisHash(),
            null,
            o.adminName(),
            ZIEL,
            GENERATION,
            NOW,
            ABLAUF,
            null,
            null));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Unbekannt());
  }

  @Test
  void schonBeendeteVorschauMeldetIhrenEndgrund() {
    liegtVor(offen().beendet(NOW.plusSeconds(60), VorschauEndgrund.BEENDET));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.BEENDET));
    verify(vorschauen, never()).beende(anyLong(), any(), any());
  }

  @Test
  void ablaufBeendetMitDemAblaufzeitpunkt() {
    liegtVor(offen());
    clock.advance(Duration.between(NOW, ABLAUF.plusSeconds(120)));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.ABLAUF));
    verify(vorschauen).beende(5L, ABLAUF, VorschauEndgrund.ABLAUF);
  }

  @Test
  void genauAmAblaufzeitpunktIstDieVorschauAbgelaufen() {
    liegtVor(offen());
    clock.advance(Duration.between(NOW, ABLAUF));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.ABLAUF));
  }

  @Test
  void eineSekundeVorDemAblaufIstDieVorschauGueltig() {
    liegtVor(offen());
    clock.advance(Duration.between(NOW, ABLAUF.minusSeconds(1)));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isInstanceOf(VorschauPruefung.Gueltig.class);
  }

  @Test
  void entzogeneAdminRolleBeendetDieVorschau() {
    liegtVor(offen());
    when(users.findById(ADMIN))
        .thenReturn(Optional.of(adminKonto().withPlatformRole(PlatformRole.USER)));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.ROLLE_ENTZOGEN));
    verify(vorschauen).beende(5L, NOW, VorschauEndgrund.ROLLE_ENTZOGEN);
  }

  @Test
  void gesperrterAdminBeendetDieVorschau() {
    liegtVor(offen());
    when(users.findById(ADMIN)).thenReturn(Optional.of(adminKonto().withDisabledAt(NOW)));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.ADMIN_GESPERRT));
    verify(vorschauen).beende(5L, NOW, VorschauEndgrund.ADMIN_GESPERRT);
  }

  @Test
  void hoehereSitzungsGenerationBeendetDieVorschau() {
    liegtVor(offen());
    when(generations.current(ADMIN)).thenReturn(OptionalLong.of(GENERATION + 1));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.SITZUNG_UNGUELTIG));
    verify(vorschauen).beende(5L, NOW, VorschauEndgrund.SITZUNG_UNGUELTIG);
  }

  @Test
  void fehlendeSitzungsGenerationBeendetDieVorschau() {
    liegtVor(offen());
    when(generations.current(ADMIN)).thenReturn(OptionalLong.empty());

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.SITZUNG_UNGUELTIG));
  }

  @Test
  void fehlendesAdminKontoBeendetDieVorschauAlsUngueltigeSitzung() {
    liegtVor(offen());
    when(users.findById(ADMIN)).thenReturn(Optional.empty());

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.SITZUNG_UNGUELTIG));
  }

  @Test
  void zielInzwischenPlattformAdminBeendetDieVorschau() {
    liegtVor(offen());
    when(users.findById(ZIEL))
        .thenReturn(
            Optional.of(zielKonto(Instant.EPOCH, null).withPlatformRole(PlatformRole.ADMIN)));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.ZIEL_IST_ADMIN));
    verify(vorschauen).beende(5L, NOW, VorschauEndgrund.ZIEL_IST_ADMIN);
  }

  @Test
  void fehlendesZielBeendetDieVorschau() {
    liegtVor(offen());
    when(users.findById(ZIEL)).thenReturn(Optional.empty());

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.ZIEL_FEHLT));
    verify(vorschauen).beende(5L, NOW, VorschauEndgrund.ZIEL_FEHLT);
  }

  @Test
  void gesperrtesZielBleibtGueltig() {
    liegtVor(offen());
    when(users.findById(ZIEL)).thenReturn(Optional.of(zielKonto(null, NOW)));

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isInstanceOf(VorschauPruefung.Gueltig.class);
  }

  @Test
  void verlorenesRennenUmsEndeMeldetDenGeschriebenenEndgrund() {
    when(vorschauen.findByKennung(KENNUNG))
        .thenReturn(Optional.of(offen()))
        .thenReturn(Optional.of(offen().beendet(NOW, VorschauEndgrund.BEENDET)));
    when(vorschauen.beende(anyLong(), any(), any())).thenReturn(false);
    when(users.findById(ZIEL)).thenReturn(Optional.empty());

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.BEENDET));
  }

  @Test
  void verschwundeneZeileNachVerlorenemRennenMeldetDenFestgestelltenEndgrund() {
    when(vorschauen.findByKennung(KENNUNG))
        .thenReturn(Optional.of(offen()))
        .thenReturn(Optional.empty());
    when(vorschauen.beende(anyLong(), any(), any())).thenReturn(false);
    when(users.findById(ZIEL)).thenReturn(Optional.empty());

    assertThat(service.pruefe(KENNUNG, GEHEIMNIS, ADMIN))
        .isEqualTo(new VorschauPruefung.Beendet(VorschauEndgrund.ZIEL_FEHLT));
  }

  // ---------------------------------------------------------------- Beenden (E9)

  @Test
  void beendenSchreibtBeendet() {
    liegtVor(offen());

    service.beende(KENNUNG, GEHEIMNIS, ADMIN);

    verify(vorschauen).beende(5L, NOW, VorschauEndgrund.BEENDET);
  }

  @Test
  void abmeldenSchreibtAbmeldung() {
    liegtVor(offen());

    service.beendeBeimAbmelden(KENNUNG, GEHEIMNIS, ADMIN);

    verify(vorschauen).beende(5L, NOW, VorschauEndgrund.ABMELDUNG);
  }

  @Test
  void beendenMitFalschemGeheimnisBeendetNichts() {
    liegtVor(offen());

    service.beende(KENNUNG, "falsch", ADMIN);
    service.beendeBeimAbmelden(KENNUNG, GEHEIMNIS, 3L);

    verify(vorschauen, never()).beende(anyLong(), any(), any());
  }

  @Test
  void beendenEinerUnbekanntenKennungBeendetNichts() {
    when(vorschauen.findByKennung(KENNUNG)).thenReturn(Optional.empty());

    service.beende(KENNUNG, GEHEIMNIS, ADMIN);

    verify(vorschauen, never()).beende(anyLong(), any(), any());
  }

  @Test
  void beendenEinerSchonBeendetenVorschauSchreibtNichtsNeu() {
    liegtVor(offen().beendet(NOW, VorschauEndgrund.ABLAUF));

    service.beende(KENNUNG, GEHEIMNIS, ADMIN);

    verify(vorschauen, never()).beende(anyLong(), any(), any());
  }

  // ---------------------------------------------------------------- Protokoll (E12)

  @Test
  void protokollSchreibtAbgelaufeneZeilenNachBevorEsLiest() {
    List<AdminVorschau> zeilen = List.of(offen().beendet(ABLAUF, VorschauEndgrund.ABLAUF));
    when(vorschauen.findByZiel(ZIEL)).thenReturn(zeilen);
    clock.advance(Duration.between(NOW, ABLAUF.plusSeconds(5)));

    assertThat(service.protokoll(ZIEL)).isEqualTo(zeilen);

    InOrder reihenfolge = inOrder(vorschauen);
    reihenfolge.verify(vorschauen).beendeAbgelaufene(ZIEL, ABLAUF.plusSeconds(5));
    reihenfolge.verify(vorschauen).findByZiel(ZIEL);
  }
}
