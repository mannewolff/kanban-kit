package org.mwolff.manban.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Invarianten einer Vorschau „Ansicht als XY“ (Issue #1917, Plan #1914 E2, E12). */
class AdminVorschauTest {

  private static final Instant START = Instant.parse("2026-10-26T10:00:00Z");
  private static final Instant ABLAUF = START.plusSeconds(1800);

  private static AdminVorschau vorschau(
      String kennung,
      String hash,
      String adminName,
      Instant laeuftAbAm,
      @Nullable Instant beendetAm,
      @Nullable VorschauEndgrund endgrund) {
    return new AdminVorschau(
        null, kennung, hash, 1L, adminName, 2L, 3L, START, laeuftAbAm, beendetAm, endgrund);
  }

  private static AdminVorschau offen() {
    return vorschau("k", "h", "Ada", ABLAUF, null, null);
  }

  @Test
  void offeneVorschauIstNichtBeendet() {
    assertThat(offen().beendet()).isFalse();
  }

  @Test
  void beendetSetztZeitpunktUndGrundUndLaesstDenRestStehen() {
    AdminVorschau zu = offen().beendet(ABLAUF, VorschauEndgrund.ABLAUF);

    assertThat(zu.beendet()).isTrue();
    assertThat(zu.beendetAm()).isEqualTo(ABLAUF);
    assertThat(zu.endgrund()).isEqualTo(VorschauEndgrund.ABLAUF);
    assertThat(zu)
        .usingRecursiveComparison()
        .ignoringFields("beendetAm", "endgrund")
        .isEqualTo(offen());
  }

  @Test
  void leereKennungIstUnzulaessig() {
    assertThatThrownBy(() -> vorschau(" ", "h", "Ada", ABLAUF, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void leererHashIstUnzulaessig() {
    assertThatThrownBy(() -> vorschau("k", "", "Ada", ABLAUF, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void leererAdminNameIstUnzulaessig() {
    assertThatThrownBy(() -> vorschau("k", "h", " ", ABLAUF, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ablaufMussNachDemStartLiegen() {
    assertThatThrownBy(() -> vorschau("k", "h", "Ada", START, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(vorschau("k", "h", "Ada", START.plusNanos(1000), null, null).laeuftAbAm())
        .isAfter(START);
  }

  @Test
  void endeUndEndgrundGibtEsNurGemeinsam() {
    assertThatThrownBy(() -> vorschau("k", "h", "Ada", ABLAUF, START, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> vorschau("k", "h", "Ada", ABLAUF, null, VorschauEndgrund.BEENDET))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void kenntGenauDieAchtEndgruende() {
    assertThat(VorschauEndgrund.values())
        .extracting(Enum::name)
        .containsExactly(
            "BEENDET",
            "ABLAUF",
            "ABMELDUNG",
            "ROLLE_ENTZOGEN",
            "ADMIN_GESPERRT",
            "SITZUNG_UNGUELTIG",
            "ZIEL_IST_ADMIN",
            "ZIEL_FEHLT");
  }
}
