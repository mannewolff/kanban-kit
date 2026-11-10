package org.mwolff.manban.benachrichtigung.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Instanz;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Karte;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Mail;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Textquelle;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Uebersicht;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Zustellung;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;

/**
 * Betreff und Text der Mails (Issue #2080, Plan #2070 E9, E11, E13, E16), gegen die echten
 * Meldungsdateien.
 */
// PMD.TooManyMethods: Je Regel aus E9, E13 und E16 ein Test.
@SuppressWarnings("PMD.TooManyMethods")
class MailInhalteTest {

  private static final Instanz INSTANZ = new Instanz("kanban-kit", "https://app.example.org");
  private static final long PROJEKT = 7L;
  private static final String NAME = "Alpha";
  private static final String TITEL = "Geheimer Titel";
  private static final Karte KARTE = new Karte(42, TITEL);
  private static final String KARTEN_LINK = "https://app.example.org/projects/7/karten/42";
  private static final String LAUF_LINK = "https://app.example.org/projects/7/nachtlauf";
  private static final String KEIN_BEDARF = "Kein Handlungsbedarf";

  private static Textquelle quelle(Sprache sprache) {
    Texte texte = SprachFixture.mit(sprache);
    return (schluessel, argumente) -> texte.text(sprache, schluessel, argumente);
  }

  private static Zustellung ausLabel(AnlassArt art) {
    return new Zustellung(art, PROJEKT, NAME, false, KARTE, null);
  }

  private static Zustellung ausLauf(AnlassArt art, @Nullable Karte karte) {
    return new Zustellung(art, PROJEKT, NAME, true, karte, null);
  }

  private static Zustellung uebersicht(Uebersicht inhalt) {
    return new Zustellung(AnlassArt.UEBERSICHT, PROJEKT, NAME, true, null, inhalt);
  }

  private static Uebersicht ruhig(@Nullable BigDecimal kosten) {
    return new Uebersicht(Verdict.SUCCEEDED, false, kosten, List.of(), false);
  }

  private static Mail einzeln(Zustellung zustellung) {
    return MailInhalte.einzeln(quelle(Sprache.DE), INSTANZ, zustellung);
  }

  private static String text(Zustellung zustellung) {
    return einzeln(zustellung).text();
  }

  @Test
  void einWartenderLaufNenntProjektKarteUndWasZuTunIst() {
    String text = text(ausLauf(AnlassArt.LAUF_WARTET, KARTE));

    assertThat(text)
        .contains("Projekt: Alpha")
        .contains("Anlass: Ein Lauf wartet auf deine Entscheidung.")
        .contains("Karte: #42 Geheimer Titel")
        .contains("Nächster Schritt: Triff die offene Entscheidung im Leitstand.");
  }

  @Test
  void eineStoerungSprichtInDerGegenwartUndDieSammlungInDerVergangenheit() {
    Zustellung stoerung = ausLauf(AnlassArt.STOERUNG, null);

    assertThat(text(stoerung)).contains("Anlass: In einem Lauf ist eine Störung aufgetreten.");
    assertThat(MailInhalte.sammlung(quelle(Sprache.DE), INSTANZ, List.of(stoerung)).text())
        .contains("Anlass: Während deiner Ruhezeit trat in einem Lauf eine Störung auf.");
  }

  @Test
  void dieUebersichtInDerSammlungSprichtInDerVergangenheit() {
    Zustellung zustellung = uebersicht(ruhig(BigDecimal.ONE));

    assertThat(text(zustellung)).contains("Anlass: Ein Lauf ist beendet.");
    assertThat(MailInhalte.sammlung(quelle(Sprache.DE), INSTANZ, List.of(zustellung)).text())
        .contains("Anlass: Während deiner Ruhezeit endete ein Lauf.");
  }

  @Test
  void einKartenAnlassBleibtAuchInDerSammlungInDerGegenwart() {
    Mail mail =
        MailInhalte.sammlung(quelle(Sprache.DE), INSTANZ, List.of(ausLabel(AnlassArt.KARTE_FRAGE)));

    assertThat(mail.text()).contains("Anlass: Eine Karte trägt eine Frage an dich.");
  }

  @ParameterizedTest
  @EnumSource(
      value = AnlassArt.class,
      names = {"KARTE_FRAGE", "KARTE_GESCHUETZT", "LAUF_WARTET"})
  void einLabelAnlassVerlinktDieKarte(AnlassArt art) {
    assertThat(text(ausLabel(art))).endsWith("Zum Leitstand: " + KARTEN_LINK + "\n");
  }

  @ParameterizedTest
  @EnumSource(
      value = AnlassArt.class,
      names = {"LAUF_WARTET", "STOERUNG"})
  void einLaufAnlassVerlinktDenLaufAuchMitKarte(AnlassArt art) {
    assertThat(text(ausLauf(art, KARTE))).endsWith("Zum Leitstand: " + LAUF_LINK + "\n");
  }

  @Test
  void dieUebersichtVerlinktDenLauf() {
    assertThat(text(uebersicht(ruhig(BigDecimal.ONE)))).contains("Zum Leitstand: " + LAUF_LINK);
  }

  @Test
  void ohneKarteFehltDieAngabe() {
    assertThat(text(ausLauf(AnlassArt.STOERUNG, null))).doesNotContain("Karte:");
    assertThat(einzeln(ausLauf(AnlassArt.STOERUNG, null)).betreff()).doesNotContain("#");
  }

  @Test
  void derBetreffNenntProduktProjektArtUndNummerAberNieDenTitel() {
    Mail mail = einzeln(ausLabel(AnlassArt.KARTE_FRAGE));

    assertThat(mail.betreff()).isEqualTo("kanban-kit: Alpha – Frage an dich (Karte #42)");
    assertThat(mail.betreff()).doesNotContain(TITEL);
    assertThat(mail.text()).contains(TITEL);
  }

  @Test
  void ohneKarteBestehtDerBetreffAusProduktProjektUndArt() {
    assertThat(einzeln(ausLauf(AnlassArt.STOERUNG, null)).betreff())
        .isEqualTo("kanban-kit: Alpha – Störung im Lauf");
  }

  @ParameterizedTest
  @CsvSource({"'Alpha\nBeta'", "'Alpha\r\nBeta'", "'Alpha\rBeta'", "'Alpha Beta'"})
  void zeilenumbruecheImProjektnamenFehlenImBetreff(String name) {
    Zustellung zustellung = new Zustellung(AnlassArt.STOERUNG, PROJEKT, name, true, null, null);

    assertThat(einzeln(zustellung).betreff()).isEqualTo("kanban-kit: Alpha Beta – Störung im Lauf");
  }

  @Test
  void zeilenumbruecheImKartentitelBleibenAusDemText() {
    Zustellung zustellung =
        new Zustellung(
            AnlassArt.KARTE_FRAGE,
            PROJEKT,
            NAME,
            false,
            new Karte(42, "Titel\nZum Leitstand: https://boese.example.org"),
            null);

    assertThat(text(zustellung))
        .contains("Karte: #42 Titel Zum Leitstand: https://boese.example.org\n");
  }

  @ParameterizedTest
  @CsvSource({
    "SUCCEEDED, Gelungen",
    "FAILED, Gescheitert",
    "WAITING, Wartet auf etwas",
    "CLOSED, Von Hand beendet",
    "NO_WORK, Keine Arbeit vorgefunden",
    "NOT_STARTED, Nicht angelaufen",
    "RUNNING, Läuft noch"
  })
  void dieUebersichtNenntDenAusgang(Verdict ausgang, String wort) {
    Uebersicht inhalt = new Uebersicht(ausgang, false, BigDecimal.ONE, List.of(), false);

    assertThat(text(uebersicht(inhalt))).contains("Ausgang: " + wort + "\n");
  }

  @Test
  void einVerstummterLaufHeisstVerstummt() {
    Uebersicht inhalt = new Uebersicht(Verdict.FAILED, true, BigDecimal.ONE, List.of(), true);

    assertThat(text(uebersicht(inhalt))).contains("Ausgang: Verstummt\n");
  }

  @Test
  void gemeldeteKostenStehenInDerSchreibweiseDerSprache() {
    Zustellung zustellung = uebersicht(ruhig(new BigDecimal("1234.5")));

    assertThat(text(zustellung)).contains("Kosten: 1234,50 USD");
    assertThat(MailInhalte.einzeln(quelle(Sprache.EN), INSTANZ, zustellung).text())
        .contains("Cost: 1234.50 USD");
  }

  @Test
  void kleineBetraegeVerschwindenNichtImRunden() {
    assertThat(text(uebersicht(ruhig(new BigDecimal("0.0345"))))).contains("Kosten: 0,0345 USD");
  }

  @Test
  void fehlendeKostenSindNichtGemeldetUndNichtNull() {
    String text = text(uebersicht(ruhig(null)));

    assertThat(text)
        .contains("Kosten: nicht gemeldet")
        .doesNotContain("USD")
        .doesNotContain("0,00");
  }

  @Test
  void gemeldeteNullIstNullUndNichtFehlend() {
    assertThat(text(uebersicht(ruhig(BigDecimal.ZERO))))
        .contains("Kosten: 0,00 USD")
        .doesNotContain("nicht gemeldet");
  }

  @Test
  void ohneWartendeUndOhneStoerungKeinHandlungsbedarf() {
    String text = text(uebersicht(ruhig(BigDecimal.ONE)));

    assertThat(text)
        .contains("Nächster Schritt: " + KEIN_BEDARF)
        .contains("Wartende Menschenschritte: keine");
  }

  @Test
  void wartendeSchritteStehenInDerUebersichtUndVerlangenHandeln() {
    Uebersicht inhalt =
        new Uebersicht(
            Verdict.WAITING,
            false,
            BigDecimal.ONE,
            List.of(new Karte(42, "Erstes"), new Karte(43, "Zweites")),
            false);

    String text = text(uebersicht(inhalt));

    assertThat(text)
        .contains("Wartende Menschenschritte:\n- #42 Erstes\n- #43 Zweites\n")
        .doesNotContain(KEIN_BEDARF);
  }

  @Test
  void eineStoerungOhneWartendeVerlangtHandeln() {
    Uebersicht inhalt = new Uebersicht(Verdict.FAILED, false, BigDecimal.ONE, List.of(), true);

    assertThat(text(uebersicht(inhalt)))
        .doesNotContain(KEIN_BEDARF)
        .contains("Nächster Schritt: Sieh dir im Leitstand an, was aussteht.");
  }

  @Test
  void einZeilenumbruchImPakettitelBleibtAusDerListe() {
    Uebersicht inhalt =
        new Uebersicht(
            Verdict.WAITING, false, BigDecimal.ONE, List.of(new Karte(42, "Eins\nZwei")), false);

    assertThat(text(uebersicht(inhalt))).contains("- #42 Eins Zwei\n");
  }

  @Test
  void dieSammelmailTraegtJeZustellungEinenAbschnitt() {
    Mail mail =
        MailInhalte.sammlung(
            quelle(Sprache.DE),
            INSTANZ,
            List.of(ausLabel(AnlassArt.KARTE_FRAGE), ausLauf(AnlassArt.STOERUNG, null)));

    assertThat(mail.betreff()).isEqualTo("kanban-kit: Was während deiner Ruhezeit angefallen ist");
    assertThat(mail.text())
        .startsWith("Während deiner Ruhezeit ist Folgendes angefallen.\n\nProjekt: Alpha\n");
    assertThat(mail.text()).contains(KARTEN_LINK + "\n\nProjekt: Alpha\n");
    assertThat(mail.text()).endsWith(LAUF_LINK + "\n");
  }

  @ParameterizedTest
  @EnumSource(Sprache.class)
  void keineMailNenntEinenLabelNamen(Sprache sprache) {
    Textquelle quelle = quelle(sprache);
    for (AnlassArt art : AnlassArt.values()) {
      Zustellung zustellung =
          art == AnlassArt.UEBERSICHT
              ? uebersicht(new Uebersicht(Verdict.FAILED, true, null, List.of(KARTE), true))
              : ausLabel(art);
      Mail einzeln = MailInhalte.einzeln(quelle, INSTANZ, zustellung);
      Mail sammlung = MailInhalte.sammlung(quelle, INSTANZ, List.of(zustellung));
      Arrays.stream(Sache.values())
          .flatMap(sache -> KitNamen.STANDARD.namen(sache).stream())
          .forEach(
              name -> {
                assertThat(einzeln.betreff()).doesNotContain(name);
                assertThat(einzeln.text()).doesNotContain(name);
                assertThat(sammlung.text()).doesNotContain(name);
              });
    }
  }

  @Test
  void dieSpracheBestimmtDieTexte() {
    Mail mail =
        MailInhalte.einzeln(quelle(Sprache.EN), INSTANZ, ausLauf(AnlassArt.LAUF_WARTET, KARTE));

    assertThat(mail.betreff()).isEqualTo("kanban-kit: Alpha – Run is waiting for you (Card #42)");
    assertThat(mail.text())
        .contains("Project: Alpha")
        .contains("Reason: A run is waiting for your decision.")
        .contains("Next step: Make the open decision in the control center.");
  }

  @Test
  void keinHandlungsbedarfGibtEsAuchAufEnglisch() {
    Mail mail = MailInhalte.einzeln(quelle(Sprache.EN), INSTANZ, uebersicht(ruhig(BigDecimal.ONE)));

    assertThat(mail.text()).contains("Next step: No action needed");
  }
}
