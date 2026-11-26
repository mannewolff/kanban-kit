package org.mwolff.manban.codeanalyse.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.codeanalyse.domain.Messung;
import org.mwolff.manban.codeanalyse.domain.Zustand;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.domain.Permission;

/**
 * Verhaltenstests des Melde-Dienstes der Codeanalyse (Issue #2275, Plan #2272 E3, E5, E8) gegen
 * einen Doppelgänger des Ports. Dass der Port selbst hält, was er zusagt, belegt {@code
 * JdbcMessungStoreIT}.
 */
class MessungServiceTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;
  private static final String TOKEN = "kit-token";
  private static final Instant JETZT = Instant.parse("2026-11-26T12:00:00Z");
  private static final Instant VEROEFFENTLICHT = Instant.parse("2026-11-26T11:00:00Z");
  private static final Zustand VORHER = new Zustand(3, 1, 5, 400);
  private static final Zustand ENDE = new Zustand(1, 0, 2, 420);

  private FakeStore store;
  private PermissionChecker permissions;
  private MessungService service;

  @BeforeEach
  void setUp() {
    store = new FakeStore();
    permissions = mock(PermissionChecker.class);
    service = new MessungService(store, permissions, Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static GemeldeteMessung zaehlend(Instant publishedAt, @Nullable String grund) {
    return new GemeldeteMessung(
        "abc123", publishedAt, "base000", "2.4.0", true, grund, 4, VORHER, ENDE);
  }

  private static GemeldeteMessung zaehlend() {
    return zaehlend(VEROEFFENTLICHT, null);
  }

  private static GemeldeteMessung nichtZaehlend(String grund) {
    return new GemeldeteMessung(
        "abc123", VEROEFFENTLICHT, "base000", "2.4.0", false, grund, 2, VORHER, null);
  }

  @Test
  void ersteMeldungWirdGespeichertUndZurueckgegeben() {
    Messung antwort = service.melden(USER, PROJECT, TOKEN, zaehlend());

    Messung erwartet =
        new Messung("abc123", VEROEFFENTLICHT, "base000", "2.4.0", true, null, 4, VORHER, ENDE);
    assertThat(antwort).isEqualTo(erwartet);
    assertThat(store.gespeichert).containsExactly(new Gespeichert(PROJECT, TOKEN, erwartet, JETZT));
    verify(permissions).require(USER, PROJECT, Permission.TICKET_CREATE);
  }

  @Test
  void ohneTicketCreateWirdNichtsGespeichertUndDieAusnahmeGehtDurch() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(USER, PROJECT, Permission.TICKET_CREATE);

    assertThatThrownBy(() -> service.melden(USER, PROJECT, TOKEN, zaehlend()))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void nichtmitgliedBekommtDieAusnahmeDesCheckersUndNichtsWirdGespeichert() {
    doThrow(new ProjectNotFoundException())
        .when(permissions)
        .require(USER, PROJECT, Permission.TICKET_CREATE);

    assertThatThrownBy(() -> service.melden(USER, PROJECT, TOKEN, zaehlend()))
        .isInstanceOf(ProjectNotFoundException.class);
    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void genauFuenfMinutenNachDerServerzeitWirdAngenommen() {
    Messung antwort = service.melden(USER, PROJECT, TOKEN, zaehlend(JETZT.plusSeconds(300), null));

    assertThat(antwort.publishedAt()).isEqualTo(JETZT.plusSeconds(300));
    assertThat(store.gespeichert).hasSize(1);
  }

  @Test
  void eineSekundeUeberFuenfMinutenWirdAbgewiesenUndNichtGespeichert() {
    GemeldeteMessung zukunft = zaehlend(JETZT.plusSeconds(301), null);

    assertThatThrownBy(() -> service.melden(USER, PROJECT, TOKEN, zukunft))
        .isInstanceOf(MessungInZukunftException.class)
        .extracting(e -> ((MessungInZukunftException) e).schluessel())
        .isEqualTo("codeanalyse.in-zukunft");
    assertThat(store.gespeichert).isEmpty();
  }

  @Test
  void zweiteMeldungMitAnderemInhaltGibtDieErsteZurueckUndAendertNichts() {
    Messung erste = service.melden(USER, PROJECT, TOKEN, zaehlend());
    GemeldeteMessung andere =
        new GemeldeteMessung(
            "abc123",
            VEROEFFENTLICHT.minusSeconds(60),
            "base999",
            "2.5.0",
            false,
            "incomplete",
            9,
            null,
            null);

    Messung zweite = service.melden(USER, PROJECT, "anderes-token", andere);

    assertThat(zweite).isEqualTo(erste);
    assertThat(store.gespeichert).hasSize(1);
    assertThat(store.gespeichert.getFirst().messung()).isEqualTo(erste);
  }

  @Test
  void derGrundEinerZaehlendenMessungWirdNichtGespeichert() {
    Messung antwort = service.melden(USER, PROJECT, TOKEN, zaehlend(VEROEFFENTLICHT, "incomplete"));

    assertThat(antwort.zaehlt()).isTrue();
    assertThat(antwort.grund()).isNull();
    assertThat(store.gespeichert.getFirst().messung().grund()).isNull();
  }

  @Test
  void derGrundEinerNichtZaehlendenMessungWirdGespeichert() {
    Messung antwort = service.melden(USER, PROJECT, TOKEN, nichtZaehlend("missing-final"));

    assertThat(antwort.grund()).isEqualTo("missing-final");
    assertThat(antwort.endzustand()).isNull();
    assertThat(store.gespeichert.getFirst().messung().grund()).isEqualTo("missing-final");
  }

  @Test
  void auchEinUnbekannterGrundWirdGespeichert() {
    Messung antwort = service.melden(USER, PROJECT, TOKEN, nichtZaehlend("neuer-grund-2"));

    assertThat(antwort.grund()).isEqualTo("neuer-grund-2");
    assertThat(store.gespeichert.getFirst().messung().grund()).isEqualTo("neuer-grund-2");
  }

  @Test
  void ausnahmenNennenIhreSchluessel() {
    assertThat(new TokenNotBoundForMessungException().schluessel())
        .isEqualTo("codeanalyse.token-ohne-board");
    assertThat(new IdempotencyKeyMismatchException().schluessel())
        .isEqualTo("codeanalyse.kopf-weicht-ab");
  }

  private record Gespeichert(long projectId, String tokenName, Messung messung, Instant eingang) {}

  /** Doppelgänger des Ports: hält die erste Messung je Projekt und Stand. */
  private static final class FakeStore implements MessungStore {

    private final List<Gespeichert> gespeichert = new ArrayList<>();

    @Override
    public Messung speichereErste(
        long projectId, String tokenName, Messung messung, Instant eingang) {
      Optional<Messung> vorhanden =
          gespeichert.stream()
              .filter(
                  g ->
                      g.projectId() == projectId
                          && g.messung().publishedCommit().equals(messung.publishedCommit()))
              .map(Gespeichert::messung)
              .findFirst();
      if (vorhanden.isPresent()) {
        return vorhanden.get();
      }
      gespeichert.add(new Gespeichert(projectId, tokenName, messung, eingang));
      return messung;
    }

    @Override
    public List<Messung> ladeAb(long projectId, Instant ab) {
      throw new UnsupportedOperationException("nicht Teil des Dienstes");
    }

    @Override
    public Optional<Instant> erstesPublishedAt(long projectId) {
      throw new UnsupportedOperationException("nicht Teil des Dienstes");
    }

    @Override
    public boolean hatMessungen(long projectId) {
      throw new UnsupportedOperationException("nicht Teil des Dienstes");
    }
  }
}
