package org.mwolff.manban.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.push.application.PushProperties;
import org.slf4j.LoggerFactory;

/**
 * Prüft die Startprüfung der Push-Schlüssel (Issue #1996, Plan #1989 E16): Ganz ohne Schlüssel
 * startet die Anwendung, nur halbe oder ungültige Angaben brechen den Start ab — ohne dass eine
 * Meldung einen Schlüsselwert nennt.
 */
class PushSchluesselStartupCheckTest {

  private static final String OEFFENTLICH =
      "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
  private static final String PRIVAT = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

  /** Privater Schlüssel des Nutzeragenten aus RFC 8291, Anhang A — gültig, passt aber nicht. */
  private static final String FREMDER_PRIVATER = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";

  private static final String SUBJECT = "mailto:betrieb@example.org";

  private ListAppender<ILoggingEvent> logWatcher;
  private Logger logger;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(PushSchluesselStartupCheck.class);
    logWatcher = new ListAppender<>();
    logWatcher.start();
    logger.addAppender(logWatcher);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logWatcher);
  }

  @Test
  void ohneSchluessel_startetDieAnwendung_undPushBleibtAus() {
    // Given
    PushProperties props = new PushProperties(null, null, null, null);

    // When / Then
    assertThatCode(() -> new PushSchluesselStartupCheck(props)).doesNotThrowAnyException();
    assertThat(props.aktiv()).isFalse();
    assertThat(meldungen(Level.INFO)).hasSize(1);
  }

  @Test
  void leereWerte_geltenWieKeineSchluessel() {
    // Given: So kommen ungesetzte Umgebungsvariablen aus application.yml an.
    PushProperties props = new PushProperties("", " ", "", null);

    // When / Then
    assertThatCode(() -> new PushSchluesselStartupCheck(props)).doesNotThrowAnyException();
    assertThat(props.aktiv()).isFalse();
  }

  @Test
  void nurOeffentlicherSchluessel_verweigertDenStart() {
    assertThatThrownBy(
            () ->
                new PushSchluesselStartupCheck(
                    new PushProperties(OEFFENTLICH, null, SUBJECT, null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MANBAN_PUSH_VAPID_PRIVATE_KEY");
    assertThat(meldungen(Level.ERROR)).hasSize(1);
  }

  @Test
  void nurPrivaterSchluessel_verweigertDenStart() {
    assertThatThrownBy(
            () -> new PushSchluesselStartupCheck(new PushProperties(null, PRIVAT, SUBJECT, null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MANBAN_PUSH_VAPID_PUBLIC_KEY");
  }

  @Test
  void schluesselOhneSubject_verweigertDenStart() {
    assertThatThrownBy(
            () ->
                new PushSchluesselStartupCheck(new PushProperties(OEFFENTLICH, PRIVAT, null, null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MANBAN_PUSH_SUBJECT");
  }

  @Test
  void nurSubject_verweigertDenStart() {
    assertThatThrownBy(
            () -> new PushSchluesselStartupCheck(new PushProperties(null, null, SUBJECT, null)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void unlesbarerSchluessel_verweigertDenStart() {
    assertThatThrownBy(
            () ->
                new PushSchluesselStartupCheck(
                    new PushProperties("kein-schluessel", PRIVAT, SUBJECT, null)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new PushSchluesselStartupCheck(
                    new PushProperties(OEFFENTLICH, "%%%", SUBJECT, null)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void schluesselDieNichtZusammenpassen_verweigernDenStart() {
    assertThatThrownBy(
            () ->
                new PushSchluesselStartupCheck(
                    new PushProperties(OEFFENTLICH, FREMDER_PRIVATER, SUBJECT, null)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void skalarAusserhalbDesKurvenbereichs_verweigertDenStart() {
    // Given: 32 Bytes 0xFF liegen über der Ordnung n von P-256.
    byte[] zuGross = new byte[32];
    Arrays.fill(zuGross, (byte) 0xFF);
    String privat = Base64.getUrlEncoder().withoutPadding().encodeToString(zuGross);

    // When / Then
    assertThatThrownBy(
            () ->
                new PushSchluesselStartupCheck(
                    new PushProperties(OEFFENTLICH, privat, SUBJECT, null)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void subjectOhneMailtoOderHttps_verweigertDenStart() {
    assertThatThrownBy(
            () ->
                new PushSchluesselStartupCheck(
                    new PushProperties(OEFFENTLICH, PRIVAT, "betrieb@example.org", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MANBAN_PUSH_SUBJECT");
  }

  @Test
  void gueltigeSchluessel_startenMitPush() {
    // Given
    PushProperties mail = new PushProperties(OEFFENTLICH, PRIVAT, SUBJECT, null);
    PushProperties web = new PushProperties(OEFFENTLICH, PRIVAT, "https://example.org", null);

    // When / Then
    assertThatCode(() -> new PushSchluesselStartupCheck(mail)).doesNotThrowAnyException();
    assertThatCode(() -> new PushSchluesselStartupCheck(web)).doesNotThrowAnyException();
    assertThat(mail.aktiv()).isTrue();
    assertThat(meldungen(Level.ERROR)).isEmpty();
  }

  @Test
  void aktiv_nurWennAlleDreiAngabenGesetztSind() {
    assertThat(new PushProperties(null, PRIVAT, SUBJECT, null).aktiv()).isFalse();
    assertThat(new PushProperties(OEFFENTLICH, null, SUBJECT, null).aktiv()).isFalse();
    assertThat(new PushProperties(OEFFENTLICH, PRIVAT, null, null).aktiv()).isFalse();
  }

  @Test
  void meldeTakt_hatDieVorgabeEineMinute() {
    assertThat(new PushProperties(null, null, null, null).meldeTakt())
        .isEqualTo(Duration.ofMinutes(1));
    assertThat(new PushProperties(null, null, null, Duration.ZERO).meldeTakt())
        .isEqualTo(Duration.ofMinutes(1));
    assertThat(new PushProperties(null, null, null, Duration.ofSeconds(-5)).meldeTakt())
        .isEqualTo(Duration.ofMinutes(1));
    assertThat(new PushProperties(null, null, null, Duration.ofSeconds(30)).meldeTakt())
        .isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void keineAusgabeNenntEinenSchluesselwert() {
    // When
    String meldung =
        catchThrowable(
                () ->
                    new PushSchluesselStartupCheck(
                        new PushProperties(OEFFENTLICH, FREMDER_PRIVATER, SUBJECT, null)))
            .getMessage();

    // Then
    assertThat(meldung).doesNotContain(OEFFENTLICH).doesNotContain(FREMDER_PRIVATER);
    assertThat(logWatcher.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
        .isNotEmpty()
        .allSatisfy(
            zeile ->
                assertThat(zeile).doesNotContain(OEFFENTLICH).doesNotContain(FREMDER_PRIVATER));
  }

  /** Vergleich über {@code levelInt}: {@link Level} überschreibt {@code equals} nicht. */
  private List<String> meldungen(Level level) {
    return logWatcher.list.stream()
        .filter(event -> event.getLevel().levelInt == level.levelInt)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }
}
