package org.mwolff.manban.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.web.security.PatAuthenticationFilter;
import org.springframework.context.NoSuchMessageException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** Textauflösung und Sprachwahl nach Zugangsart (Issue #1640, Plan #1638 A5). */
class TexteTest {

  private static final String INTERN = "common.fehler.intern";
  private static final String MINUTEN = "ratelimit.zu-viele-versuche.minuten";

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static void angemeldetMit(String... authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new TestingAuthenticationToken(
                "user", null, AuthorityUtils.createAuthorityList(List.of(authorities))));
  }

  @Test
  void textLoestSchluesselInBeidenSprachenAuf() {
    Texte texte = SprachFixture.mit(Sprache.DE);

    assertThat(texte.text(Sprache.DE, INTERN))
        .isEqualTo("Interner Fehler. Bitte später erneut versuchen.");
    assertThat(texte.text(Sprache.EN, INTERN)).isEqualTo("Internal error. Please try again later.");
  }

  @Test
  void textSetztArgumenteOhneTausendertrennerEin() {
    Texte texte = SprachFixture.mit(Sprache.DE);

    assertThat(texte.text(Sprache.DE, MINUTEN, 1200L))
        .isEqualTo("Zu viele Versuche. Bitte in 1200 Minuten erneut versuchen.");
    assertThat(texte.text(Sprache.EN, MINUTEN, 1200L))
        .isEqualTo("Too many attempts. Please try again in 1200 minutes.");
  }

  @Test
  void textEinerUebersetzbarenMeldungNimmtSchluesselUndArgumente() {
    UebersetzbareMeldung meldung =
        new UebersetzbareMeldung() {
          @Override
          public String schluessel() {
            return MINUTEN;
          }

          @Override
          public Object[] argumente() {
            return new Object[] {3L};
          }
        };

    assertThat(SprachFixture.mit(Sprache.DE).text(Sprache.EN, meldung))
        .isEqualTo("Too many attempts. Please try again in 3 minutes.");
  }

  @Test
  void meldungOhneArgumenteHatLeereArgumente() {
    UebersetzbareMeldung meldung = () -> INTERN;

    assertThat(meldung.argumente()).isEmpty();
    assertThat(SprachFixture.mit(Sprache.DE).text(Sprache.DE, meldung))
        .isEqualTo("Interner Fehler. Bitte später erneut versuchen.");
  }

  @Test
  void fehlenderSchluesselWirft() {
    Texte texte = SprachFixture.mit(Sprache.DE);

    assertThatThrownBy(() -> texte.text(Sprache.EN, "gibt.es.nicht"))
        .isInstanceOf(NoSuchMessageException.class);
  }

  @Test
  void eingestellteLiefertDieSpracheDerQuelle() {
    assertThat(SprachFixture.mit(Sprache.EN).eingestellte()).isEqualTo(Sprache.EN);
    assertThat(SprachFixture.mit(Sprache.DE).eingestellte()).isEqualTo(Sprache.DE);
  }

  @Test
  void eingestellteFaelltBeiScheiternderQuelleAufDeutsch() {
    Texte texte =
        SprachFixture.mit(
            () -> {
              throw new DataAccessResourceFailureException("Datenbank weg");
            });

    assertThat(texte.eingestellte()).isEqualTo(Sprache.DE);
  }

  @Test
  void anonymeAnfrageBekommtDieEingestellteSprache() {
    assertThat(SprachFixture.mit(Sprache.EN).spracheDerAnfrage()).isEqualTo(Sprache.EN);
  }

  @Test
  void sessionAnfrageBekommtDieEingestellteSprache() {
    angemeldetMit("ROLE_USER");

    assertThat(SprachFixture.mit(Sprache.EN).spracheDerAnfrage()).isEqualTo(Sprache.EN);
  }

  @Test
  void anfrageMitTokenBekommtDeutsch() {
    angemeldetMit("ROLE_USER", Texte.TOKEN_AUTORITAET);

    assertThat(SprachFixture.mit(Sprache.EN).spracheDerAnfrage()).isEqualTo(Sprache.DE);
  }

  @Test
  void anfrageMitUngebundenemTokenBekommtDeutsch() {
    angemeldetMit(Texte.UNGEBUNDENE_TOKEN_AUTORITAET);

    assertThat(SprachFixture.mit(Sprache.EN).spracheDerAnfrage()).isEqualTo(Sprache.DE);
  }

  @Test
  void tokenAutoritaetenGleichenDenenDesTokenFilters() {
    assertThat(Texte.TOKEN_AUTORITAET).isEqualTo(PatAuthenticationFilter.AUTHORITY);
    assertThat(Texte.UNGEBUNDENE_TOKEN_AUTORITAET)
        .isEqualTo(PatAuthenticationFilter.UNBOUND_AUTHORITY);
  }
}
