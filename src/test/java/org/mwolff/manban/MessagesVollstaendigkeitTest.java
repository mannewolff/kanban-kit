package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;

/**
 * Die Meldungsdateien sind vollständig und die deutschen Fassungen laufen nicht auseinander (Issue
 * #1640, Plan #1638 A5/E6).
 *
 * <ul>
 *   <li>{@code i18n/messages_de} und {@code _en} tragen dieselben Schlüssel, ebenso {@code
 *       ValidationMessages_de} und {@code _en}; kein Wert ist leer.
 *   <li>Jede Produktionsklasse, die {@link UebersetzbareMeldung} trägt, nennt einen Schlüssel, der
 *       in beiden Sprachen existiert, und eine Exception liefert mit {@code getMessage()} genau den
 *       aufgelösten deutschen Text. Instanziiert wird über den Konstruktor mit den wenigsten
 *       Parametern, belegt aus {@link #BEISPIELWERTE}. Trägt die Klasse statische Fabrikmethoden,
 *       die sie selbst liefern, wird stattdessen jede davon geprüft (Issue #1641): Eine Meldung mit
 *       mehreren Fassungen hat je Fassung eine Fabrikmethode.
 * </ul>
 */
class MessagesVollstaendigkeitTest {

  /** Beispielwerte je Parametertyp für die Konstruktoren der Meldungsklassen. */
  private static final Map<Class<?>, Object> BEISPIELWERTE =
      Map.of(
          String.class,
          "Beispiel",
          long.class,
          7L,
          Long.class,
          7L,
          int.class,
          7,
          Integer.class,
          7,
          boolean.class,
          true,
          Boolean.class,
          true,
          UUID.class,
          UUID.fromString("00000000-0000-0000-0000-000000000007"));

  private static Properties laden(String pfad) throws IOException {
    InputStream stream =
        Objects.requireNonNull(
            Thread.currentThread().getContextClassLoader().getResourceAsStream(pfad), pfad);
    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      Properties properties = new Properties();
      properties.load(reader);
      return properties;
    }
  }

  @ParameterizedTest
  @CsvSource({
    "i18n/messages_de.properties, i18n/messages_en.properties",
    "ValidationMessages_de.properties, ValidationMessages_en.properties"
  })
  void beideSprachenTragenDieselbenSchluesselOhneLeereWerte(String deutsch, String englisch)
      throws IOException {
    Properties de = laden(deutsch);
    Properties en = laden(englisch);

    assertThat(de).isNotEmpty();
    assertThat(en.stringPropertyNames()).isEqualTo(de.stringPropertyNames());
    for (Properties properties : List.of(de, en)) {
      properties.forEach(
          (schluessel, wert) -> assertThat(wert).as("%s", schluessel).asString().isNotBlank());
    }
  }

  @Test
  void basisdateiTraegtKeineTexte() throws IOException {
    assertThat(laden("i18n/messages.properties")).isEmpty();
  }

  @Test
  void jedeMeldungsklasseIstInBeidenSprachenAufloesbarUndDeutschGleich() throws Exception {
    Texte texte = SprachFixture.mit(Sprache.DE);
    List<JavaClass> meldungsklassen =
        ArchitekturKlassen.PRODUKTIONSKLASSEN.stream()
            .filter(klasse -> klasse.isAssignableTo(UebersetzbareMeldung.class))
            .filter(klasse -> !klasse.isInterface())
            .filter(klasse -> !klasse.getModifiers().contains(JavaModifier.ABSTRACT))
            .toList();

    for (JavaClass klasse : meldungsklassen) {
      for (UebersetzbareMeldung meldung : beispiele(klasse.reflect())) {
        pruefe(texte, meldung);
      }
    }
  }

  @Test
  void fabrikmethodenWerdenJedeGeprueft() throws Exception {
    Texte texte = SprachFixture.mit(Sprache.DE);

    List<UebersetzbareMeldung> meldungen = beispiele(MeldungMitFabriken.class);

    assertThat(meldungen)
        .extracting(UebersetzbareMeldung::schluessel)
        .containsExactlyInAnyOrder("common.fehler.intern", "common.fehler.konflikt");
    assertThatThrownBy(
            () -> {
              for (UebersetzbareMeldung meldung : beispiele(MeldungMitFalscherFabrik.class)) {
                pruefe(texte, meldung);
              }
            })
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("MeldungMitFalscherFabrik");
  }

  @Test
  void ohneFabrikmethodeZaehltDerKonstruktor() throws Exception {
    assertThat(beispiele(PassendeMeldung.class))
        .singleElement()
        .isInstanceOf(PassendeMeldung.class);
  }

  @Test
  void pruefungErkenntAbweichendesDeutsch() throws Exception {
    Texte texte = SprachFixture.mit(Sprache.DE);

    pruefe(texte, beispiel(PassendeMeldung.class));
    assertThatThrownBy(() -> pruefe(texte, beispiel(AbweichendeMeldung.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("AbweichendeMeldung");
  }

  @Test
  void beispielOhneBekanntenParametertypNenntDieKlasse() {
    assertThatThrownBy(() -> beispiel(MeldungMitUnbekanntemParameter.class))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("MeldungMitUnbekanntemParameter")
        .hasMessageContaining("java.time.Instant");
  }

  private static void pruefe(Texte texte, UebersetzbareMeldung meldung) {
    String deutsch = texte.text(Sprache.DE, meldung);
    assertThat(texte.text(Sprache.EN, meldung)).isNotBlank();
    if (meldung instanceof Throwable throwable) {
      assertThat(throwable.getMessage()).as(meldung.getClass().getName()).isEqualTo(deutsch);
    }
  }

  /**
   * Je Fabrikmethode der Klasse ein Beispiel; ohne Fabrikmethode eines über den Konstruktor mit den
   * wenigsten Parametern.
   */
  // PMD.AvoidAccessibilityAlteration: Die Meldungsklassen liegen in fremden Paketen; ihre
  // Fabrikmethoden sind dort nicht immer öffentlich.
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static List<UebersetzbareMeldung> beispiele(Class<?> klasse)
      throws InstantiationException, IllegalAccessException, InvocationTargetException {
    List<Method> fabriken =
        Arrays.stream(klasse.getDeclaredMethods())
            .filter(methode -> Modifier.isStatic(methode.getModifiers()))
            .filter(methode -> methode.getReturnType().equals(klasse))
            .toList();
    if (fabriken.isEmpty()) {
      return List.of(beispiel(klasse));
    }
    List<UebersetzbareMeldung> meldungen = new ArrayList<>();
    for (Method fabrik : fabriken) {
      fabrik.setAccessible(true);
      meldungen.add((UebersetzbareMeldung) fabrik.invoke(null, argumente(fabrik, klasse)));
    }
    return meldungen;
  }

  // PMD.AvoidAccessibilityAlteration: Die Meldungsklassen liegen in fremden Paketen und haben oft
  // paketprivate Konstruktoren; nur so lässt sich jede einzelne prüfen.
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static UebersetzbareMeldung beispiel(Class<?> klasse)
      throws InstantiationException, IllegalAccessException, InvocationTargetException {
    Constructor<?> konstruktor =
        Arrays.stream(klasse.getDeclaredConstructors())
            .min(Comparator.comparingInt(Constructor::getParameterCount))
            .orElseThrow();
    Object[] argumente = argumente(konstruktor, klasse);
    konstruktor.setAccessible(true);
    return (UebersetzbareMeldung) konstruktor.newInstance(argumente);
  }

  /** Beispielwerte für die Parameter eines Konstruktors oder einer Fabrikmethode. */
  private static Object[] argumente(Executable aufruf, Class<?> klasse) {
    Object[] argumente = new Object[aufruf.getParameterCount()];
    Class<?>[] typen = aufruf.getParameterTypes();
    for (int i = 0; i < typen.length; i++) {
      if (!BEISPIELWERTE.containsKey(typen[i])) {
        throw new AssertionError(
            "Kein Beispielwert für %s im Konstruktor von %s — BEISPIELWERTE ergänzen"
                .formatted(typen[i].getName(), klasse.getName()));
      }
      argumente[i] = BEISPIELWERTE.get(typen[i]);
    }
    return argumente;
  }

  /** Deutsch gleich dem aufgelösten Text, mit Argument. */
  static final class PassendeMeldung extends RuntimeException implements UebersetzbareMeldung {

    private final long minuten;

    PassendeMeldung(long minuten) {
      super("Zu viele Versuche. Bitte in %d Minuten erneut versuchen.".formatted(minuten));
      this.minuten = minuten;
    }

    @Override
    public String schluessel() {
      return "ratelimit.zu-viele-versuche.minuten";
    }

    @Override
    public Object[] argumente() {
      return new Object[] {minuten};
    }
  }

  /** Deutsch weicht vom aufgelösten Text ab. */
  static final class AbweichendeMeldung extends RuntimeException implements UebersetzbareMeldung {

    AbweichendeMeldung() {
      super("Interner Fehler.");
    }

    @Override
    public String schluessel() {
      return "common.fehler.intern";
    }
  }

  /** Zwei Fassungen, je eine Fabrikmethode, beide deutsch gleich dem aufgelösten Text. */
  static final class MeldungMitFabriken extends RuntimeException implements UebersetzbareMeldung {

    private final String meldungsschluessel;

    private MeldungMitFabriken(String schluessel, String text) {
      super(text);
      this.meldungsschluessel = schluessel;
    }

    static MeldungMitFabriken intern() {
      return new MeldungMitFabriken(
          "common.fehler.intern", "Interner Fehler. Bitte später erneut versuchen.");
    }

    static MeldungMitFabriken konflikt(String unbenutzt) {
      return new MeldungMitFabriken(
          "common.fehler.konflikt",
          "Die Anfrage kollidierte mit dem aktuellen Datenstand. Bitte erneut versuchen.");
    }

    @Override
    public String schluessel() {
      return meldungsschluessel;
    }
  }

  /** Eine Fabrikmethode mit abweichendem Deutsch — der Konstruktor allein fiele nicht auf. */
  static final class MeldungMitFalscherFabrik extends RuntimeException
      implements UebersetzbareMeldung {

    private final String meldungsschluessel;

    private MeldungMitFalscherFabrik(String schluessel, String text) {
      super(text);
      this.meldungsschluessel = schluessel;
    }

    static MeldungMitFalscherFabrik richtig() {
      return new MeldungMitFalscherFabrik("common.fehler.ungueltiger-wert", "Ungültiger Wert");
    }

    static MeldungMitFalscherFabrik falsch() {
      return new MeldungMitFalscherFabrik("common.fehler.ungueltiger-wert", "Falscher Wert");
    }

    @Override
    public String schluessel() {
      return meldungsschluessel;
    }
  }

  /** Konstruktor mit einem Typ ohne Beispielwert. */
  static final class MeldungMitUnbekanntemParameter extends RuntimeException
      implements UebersetzbareMeldung {

    MeldungMitUnbekanntemParameter(Instant zeitpunkt) {
      super(zeitpunkt.toString());
    }

    @Override
    public String schluessel() {
      return "common.fehler.intern";
    }
  }
}
