package org.mwolff.manban.fremdboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ZugangsTresorTest {

  private static final String SCHLUESSEL =
      "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
  private static final String ANDERER_SCHLUESSEL =
      "ffeeddccbbaa99887766554433221100ffeeddccbbaa99887766554433221100";

  private final ZugangsTresor tresor = new ZugangsTresor(SCHLUESSEL, new SecureRandom());

  @Test
  void hinUndRueckwegLiefertDenKlartext() {
    String gespeichert = tresor.verschluesseln("glpat-geheim-123");

    assertThat(tresor.entschluesseln(gespeichert)).isEqualTo("glpat-geheim-123");
  }

  @Test
  void gespeicherterWertVerraetDenZugangNicht() {
    String gespeichert = tresor.verschluesseln("glpat-geheim-123");

    assertThat(gespeichert).doesNotContain("glpat");
    assertThat(new String(Base64.getDecoder().decode(gespeichert), StandardCharsets.ISO_8859_1))
        .doesNotContain("glpat");
  }

  @Test
  void jedeVerschluesselungNimmtEinenNeuenStartwert() {
    assertThat(tresor.verschluesseln("gleich")).isNotEqualTo(tresor.verschluesseln("gleich"));
  }

  @Test
  void schluesselDarfGrossbuchstabenUndRandleerzeichenTragen() {
    ZugangsTresor gross =
        new ZugangsTresor("  " + SCHLUESSEL.toUpperCase(Locale.ROOT) + "\n", new SecureRandom());

    assertThat(gross.verfuegbar()).isTrue();
    assertThat(tresor.entschluesseln(gross.verschluesseln("x"))).isEqualTo("x");
  }

  @Test
  void falscherSchluesselWirftEigeneAusnahme() {
    String gespeichert = tresor.verschluesseln("glpat-geheim-123");
    ZugangsTresor anderer = new ZugangsTresor(ANDERER_SCHLUESSEL, new SecureRandom());

    assertThatThrownBy(() -> anderer.entschluesseln(gespeichert))
        .isInstanceOf(ZugangNichtLesbarException.class)
        .hasMessageNotContaining("glpat");
  }

  @Test
  void veraenderterWertWirftEigeneAusnahme() {
    byte[] roh = Base64.getDecoder().decode(tresor.verschluesseln("glpat-geheim-123"));
    roh[roh.length - 1] ^= 1;
    String veraendert = Base64.getEncoder().encodeToString(roh);

    assertThatThrownBy(() -> tresor.entschluesseln(veraendert))
        .isInstanceOf(ZugangNichtLesbarException.class);
  }

  @Test
  void keinBase64WirftEigeneAusnahme() {
    assertThatThrownBy(() -> tresor.entschluesseln("kein base64 %%"))
        .isInstanceOf(ZugangNichtLesbarException.class);
  }

  @Test
  void zuKurzerWertWirftEigeneAusnahme() {
    String nurStartwert = Base64.getEncoder().encodeToString(new byte[12]);

    assertThatThrownBy(() -> tresor.entschluesseln(nurStartwert))
        .isInstanceOf(ZugangNichtLesbarException.class);
  }

  @Test
  void wertMitStartwertUndLeeremRumpfOhneMarkeWirftEigeneAusnahme() {
    String ohneMarke = Base64.getEncoder().encodeToString(new byte[12 + 15]);

    assertThatThrownBy(() -> tresor.entschluesseln(ohneMarke))
        .isInstanceOf(ZugangNichtLesbarException.class);
  }

  @Test
  void fehlenderSchluesselMachtDenTresorUnverfuegbar() {
    ZugangsTresor ohne = new ZugangsTresor(null, new SecureRandom());
    ZugangsTresor leer = new ZugangsTresor("   ", new SecureRandom());

    assertThat(ohne.verfuegbar()).isFalse();
    assertThat(leer.verfuegbar()).isFalse();
    assertThat(tresor.verfuegbar()).isTrue();
  }

  @Test
  void ohneSchluesselWirdNichtVerschluesselt() {
    ZugangsTresor ohne = new ZugangsTresor(null, new SecureRandom());

    assertThatThrownBy(() -> ohne.verschluesseln("glpat"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MANBAN_SECRET_KEY");
  }

  @Test
  void ohneSchluesselIstEinGespeicherterWertNichtLesbar() {
    String gespeichert = tresor.verschluesseln("glpat-geheim-123");
    ZugangsTresor ohne = new ZugangsTresor(null, new SecureRandom());

    assertThatThrownBy(() -> ohne.entschluesseln(gespeichert))
        .isInstanceOf(ZugangNichtLesbarException.class)
        .hasMessageContaining("MANBAN_SECRET_KEY");
  }

  @Test
  void schluesselFalscherLaengeBrichtDenStartAb() {
    assertThatThrownBy(() -> new ZugangsTresor("0011", new SecureRandom()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MANBAN_SECRET_KEY")
        .hasMessageContaining("openssl rand -hex 32");
  }

  @Test
  void schluesselMitFremdenZeichenBrichtDenStartAb() {
    String keinHex = "zz" + SCHLUESSEL.substring(2);

    assertThatThrownBy(() -> new ZugangsTresor(keinHex, new SecureRandom()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MANBAN_SECRET_KEY")
        .hasMessageNotContaining(keinHex);
  }
}
