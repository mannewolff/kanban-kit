package org.mwolff.manban.entscheidungskette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.Laufkennung;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Die Szenarien des Fachplans #2101 an der Schnittstelle {@code GET
 * /api/cards/{cardId}/entscheidungskette} (Issue #2117, Plan #2108): Das Kit schreibt über einen
 * gebundenen Projekt-Zugang, der Mensch arbeitet in der Oberfläche, und erst die echte Filterkette
 * stempelt Herkunft, Laufkennung und die Einstellung zur Prüfung vor Ready.
 */
// PMD.TooManyMethods: Je Szenario des Fachplans und je Grenze der Schnittstelle ein Test, dazu die
// Helfer, die Kit und Mensch über die echte Schnittstelle handeln lassen; ein Zerschneiden
// verteilte die Szenarien auf Dateien, ohne etwas zu entkoppeln.
@SuppressWarnings("PMD.TooManyMethods")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EntscheidungsketteSzenarienIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String AGENT_HEADER = "X-Agent-Model";
  private static final String REVIEW_HEADER = "X-Kit-Review-Before-Ready";
  private static final String JSON = "application/json";
  private static final String MODELL = "claude-opus-5-5";
  private static final String MENSCH = "Erika Muster";
  private static final String ZUGANG = "Kit";
  private static final String NICHT_ERFASST = "NICHT_ERFASST";
  private static final String ERFASST = "ERFASST";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  /** Der Wert, den das Kit in diesem Test für die Prüfung vor Ready meldet; null = keiner. */
  private @Nullable String reviewVorReady;

  // --- Szenarien des Fachplans ------------------------------------------------------------------

  @Test
  @DisplayName("Szenario: Vollständige Kette eines neuen Pakets")
  void vollstaendigeKette() throws Exception {
    reviewVorReady = "true";
    Ort o = ort("voll");
    Item paket = ganzerWeg(o);

    JsonNode kette = kette(o.owner(), paket.id());

    assertThat(kette.get("sichtbar").asBoolean()).isTrue();
    assertThat(kette.get("art").asText()).isEqualTo("PAKET");
    assertThat(abschnittsarten(kette)).containsExactly("IDEE", "FACHPLAN", "PLAN", "PAKET");
    assertThat(gliedarten(kette, "PAKET"))
        .containsExactly(
            "HERKUNFT",
            "ISSUE_REVIEW",
            "FREIGABE",
            "UMSETZUNG",
            "CODE_REVIEW",
            "UEBERTRAGUNG",
            "VEROEFFENTLICHUNG");
    List<JsonNode> alle = alleGlieder(kette);
    assertThat(alle).hasSizeGreaterThanOrEqualTo(13);
    for (JsonNode g : alle) {
      String art = g.get("art").asText();
      assertThat(g.get("zustand").asText()).as("Zustand von %s", art).isEqualTo(ERFASST);
      assertThat(name(g.get("ausgefuehrtVon"))).as("Akteur von %s", art).isNotNull();
      assertThat(g.get("zeitpunkt").isNull()).as("Zeitpunkt von %s", art).isFalse();
      assertThat(g.get("quelle").get("karte").isNull()).as("Quelle von %s", art).isFalse();
      assertThat(g.get("hinweise")).as("Hinweise an %s", art).isEmpty();
    }
    assertThat(einziges(kette, "IDEE", "IDEE").get("ergebnis").asText())
        .startsWith("Issue #" + o.nummer("fachplan"));
    assertThat(einziges(kette, "PAKET", "HERKUNFT").get("ausgefuehrtVon").get("name").asText())
        .isEqualTo(MODELL);
    JsonNode review = einziges(kette, "PAKET", "ISSUE_REVIEW");
    assertThat(review.get("pruefung").get("pruefer").get(0).get("modell").asText())
        .isEqualTo("fable");
    JsonNode freigabe = einziges(kette, "PAKET", "FREIGABE");
    assertThat(freigabe.get("freigabe").get("fall").asText()).isEqualTo("ZUG_NACH_READY");
    assertThat(freigabe.get("freigegebenVon").get("name").asText()).isEqualTo(MENSCH);
    JsonNode umsetzung = einziges(kette, "PAKET", "UMSETZUNG").get("umsetzung");
    assertThat(umsetzung.get("ausgang").asText()).isEqualTo("ABGESCHLOSSEN");
    assertThat(umsetzung.get("commit").asText()).isEqualTo("abc1234");
    assertThat(umsetzung.get("prueflauf").get("wert").asText()).isEqualTo("gruen");
    assertThat(einziges(kette, "PAKET", "CODE_REVIEW").get("codeReview").isNull()).isFalse();
    JsonNode uebertragung = einziges(kette, "PAKET", "UEBERTRAGUNG");
    assertThat(uebertragung.get("ergebnis").asText()).isEqualTo("übertragen");
    assertThat(uebertragung.get("freigegebenVon").get("name").asText()).isEqualTo(MENSCH);
    assertThat(uebertragung.get("uebertragung").get("weg").asText()).isEqualTo("LOKAL");
    JsonNode veroeffentlichung = einziges(kette, "PAKET", "VEROEFFENTLICHUNG");
    assertThat(veroeffentlichung.get("veroeffentlichung").get("version").asText())
        .isEqualTo("1.4.0");
    assertThat(veroeffentlichung.get("ergebnis").asText()).isEqualTo("veröffentlicht");
  }

  @Test
  @DisplayName("Szenario: Lücke bei einer alten Karte")
  void lueckeBeiEinerAltenKarte() throws Exception {
    Ort o = ort("alt");
    Item plan = anlegen(o, "[Plan] Alter Weg", "Plan-Modell: " + MODELL, null);
    Item paket = anlegen(o, "Altes Paket", "Plan: Issue #" + plan.number(), plan.number());
    zieheKit(o, paket, "IN_PROGRESS", null);
    zieheKit(o, paket, "IN_REVIEW", null);

    JsonNode kette = kette(o.owner(), paket.id());

    JsonNode umsetzung = einziges(kette, "PAKET", "UMSETZUNG");
    assertThat(umsetzung.get("zustand").asText()).isEqualTo(ERFASST);
    assertThat(umsetzung.get("ausgefuehrtVon").get("name").asText()).isEqualTo(ZUGANG);
    assertThat(umsetzung.get("zeitpunkt").isNull()).isFalse();
    assertThat(umsetzung.get("umsetzung").get("commit").isNull()).isTrue();
    assertThat(einziges(kette, "PLAN", "HERKUNFT").get("zustand").asText()).isEqualTo(ERFASST);
    assertThat(einziges(kette, "PAKET", "UEBERTRAGUNG").get("zustand").asText())
        .isEqualTo(NICHT_ERFASST);
  }

  @Test
  @DisplayName("Szenario: Kette vom Fachplan aus")
  void ketteVomFachplanAus() throws Exception {
    Ort o = ort("fachplan");
    Item fachplan = anlegen(o, "[Fachlich] Anlass", "Autor-Modell: " + MODELL, null);
    kitKommentar(o, fachplan, runde("Fachplan-Review", 1));
    kitKommentar(o, fachplan, "## Einarbeitung, Runde 1\n\nAlles eingearbeitet.");
    Item plan =
        anlegen(
            o,
            "[Plan] Weg",
            "Plan-Modell: " + MODELL + "\nFachliche Quelle: Issue #" + fachplan.number(),
            fachplan.number());
    Item erstes = anlegen(o, "Paket 1", "Plan: Issue #" + plan.number(), plan.number());
    zieheKit(o, erstes, "IN_PROGRESS", null);
    List<Integer> nummern = new ArrayList<>(List.of(plan.number(), erstes.number()));
    for (int i = 2; i <= 3; i++) {
      nummern.add(
          anlegen(o, "Paket " + i, "Plan: Issue #" + plan.number(), plan.number()).number());
    }

    JsonNode kette = kette(o.owner(), fachplan.id());

    assertThat(kette.get("art").asText()).isEqualTo("FACHPLAN");
    assertThat(gliedarten(kette, "FACHPLAN")).containsExactly("HERKUNFT", "PRUEFRUNDE");
    JsonNode runde = einziges(kette, "FACHPLAN", "PRUEFRUNDE");
    assertThat(runde.get("pruefung").get("art").asText()).isEqualTo("FACHPLAN_REVIEW");
    assertThat(runde.get("pruefung").get("einarbeitung").get("zustand").asText())
        .isEqualTo(ERFASST);
    List<Integer> verwiesen = new ArrayList<>();
    for (JsonNode n : kette.get("nachfahren")) {
      verwiesen.add(n.get("verweis").get("nummer").asInt());
      assertThat(n.get("spalte").asText()).isNotBlank();
      assertThat(n.get("zuletztErreicht").isNull()).isFalse();
    }
    assertThat(verwiesen).containsExactlyElementsOf(nummern);
    assertThat(kette.get("nachfahren").get(1).get("zuletztErreicht").asText())
        .isEqualTo("UMSETZUNG");
  }

  @Test
  @DisplayName("Szenario: Freigabe in einer Nacht-Kette")
  void freigabeInEinerNachtKette() throws Exception {
    Ort o = ort("nacht");
    Item fachplan = anlegen(o, "[Fachlich] Nacht", "Autor-Modell: " + MODELL, null);
    labelSetzen(o, fachplan, "ziel:umsetzung");
    Item plan =
        anlegen(
            o,
            "[Plan] Nacht",
            "Plan-Modell: " + MODELL + "\nFachliche Quelle: Issue #" + fachplan.number(),
            fachplan.number());
    Item paket = anlegen(o, "Nachtpaket", "Plan: Issue #" + plan.number(), plan.number());
    zieheKit(o, paket, "READY", Instant.now());

    JsonNode freigabe = einziges(kette(o.owner(), paket.id()), "PAKET", "FREIGABE");

    assertThat(freigabe.get("freigabe").get("fall").asText()).isEqualTo("KENNZEICHEN");
    assertThat(freigabe.get("freigegebenVon").get("typ").asText()).isEqualTo("MENSCH");
    assertThat(freigabe.get("freigegebenVon").get("name").asText()).isEqualTo(MENSCH);
    assertThat(freigabe.get("ausgefuehrtVon").get("typ").asText()).isEqualTo("WERKZEUG");
    assertThat(freigabe.get("ausgefuehrtVon").get("name").asText()).isEqualTo(ZUGANG);
    assertThat(freigabe.get("quelle").get("nummer").asInt()).isEqualTo(fachplan.number());
    Instant gesetzt = Instant.parse(freigabe.get("zeitpunkt").asText());
    Instant gezogen = Instant.parse(freigabe.get("freigabe").get("zugNachReady").asText());
    assertThat(gesetzt).isBeforeOrEqualTo(gezogen);
  }

  @Test
  @DisplayName("Szenario: Plan ohne fachliche Anforderung")
  void planOhneFachlicheAnforderung() throws Exception {
    Ort o = ort("technisch");
    Item plan = anlegen(o, "[Plan] Rein technisch", "Plan-Modell: " + MODELL, null);
    Item paket = anlegen(o, "Technikpaket", "Plan: Issue #" + plan.number(), plan.number());

    JsonNode fachplan = einziges(kette(o.owner(), paket.id()), "FACHPLAN", "HERKUNFT");

    assertThat(fachplan.get("zustand").asText()).isEqualTo("NICHT_VORGESEHEN");
  }

  @Test
  @DisplayName("Szenario: Meldung von Hand nachgeahmt")
  void meldungVonHandNachgeahmt() throws Exception {
    Ort o = ort("nachgeahmt");
    Item paket = anlegen(o, "Paket", "Autor-Modell: " + MODELL, null);
    sitzungsKommentar(o, paket, uebertragung("1", "übertragen"));

    JsonNode glied = einziges(kette(o.owner(), paket.id()), "PAKET", "UEBERTRAGUNG");

    assertThat(glied.get("zustand").asText()).isEqualTo(NICHT_ERFASST);
    assertThat(glied.get("uebertragung").isNull()).isTrue();
    assertThat(glied.get("hinweise")).isEmpty();
  }

  @Test
  @DisplayName("Szenario: Meldung des Kits nachträglich geändert")
  void meldungDesKitsNachtraeglichGeaendert() throws Exception {
    Ort o = ort("geaendert");
    Item paket = anlegen(o, "Paket", "Autor-Modell: " + MODELL, null);
    kitKommentar(o, paket, uebertragung("1", "übertragen"));
    long kommentar = letzterKommentar(o, paket);
    mvc.perform(
            patch("/api/comments/" + kommentar)
                .cookie(o.owner())
                .contentType(JSON)
                .content(body(uebertragung("1", "übertragen") + "\nVon Hand ergänzt.")))
        .andExpect(status().isOk());

    JsonNode glied = einziges(kette(o.owner(), paket.id()), "PAKET", "UEBERTRAGUNG");

    assertThat(glied.get("zustand").asText()).isEqualTo(NICHT_ERFASST);
    assertThat(glied.get("hinweise"))
        .extracting(JsonNode::asText)
        .containsExactly("VON_HAND_GEAENDERT");
    assertThat(glied.get("uebertragung").isNull()).isTrue();
    assertThat(glied.get("ausgefuehrtVon").isNull()).isTrue();
    assertThat(glied.get("quelle").get("kommentar").asLong()).isEqualTo(kommentar);
  }

  @Test
  @DisplayName("Szenario: Fehlschlag bleibt sichtbar")
  void fehlschlagBleibtSichtbar() throws Exception {
    Ort o = ort("fehlschlag");
    Item paket = anlegen(o, "Paket", "Autor-Modell: " + MODELL, null);
    kitKommentar(o, paket, uebertragung("1", "nicht übertragen"));
    kitKommentar(o, paket, uebertragung("2", "übertragen"));

    List<JsonNode> versuche = glieder(kette(o.owner(), paket.id()), "PAKET", "UEBERTRAGUNG");

    assertThat(versuche)
        .extracting(
            g -> g.get("ergebnis").asText(), g -> g.get("uebertragung").get("commit").asText())
        .containsExactly(tuple("nicht übertragen", "c-1"), tuple("übertragen", "c-2"));
  }

  @Test
  @DisplayName("Szenario: Vorfahre ist archiviert")
  void vorfahreIstArchiviert() throws Exception {
    Ort o = ort("archiv");
    Item fachplan = anlegen(o, "[Fachlich] Archiviert", "Autor-Modell: " + MODELL, null);
    kitKommentar(o, fachplan, runde("Fachplan-Review", 1));
    Item plan =
        anlegen(
            o,
            "[Plan] Weg",
            "Plan-Modell: " + MODELL + "\nFachliche Quelle: Issue #" + fachplan.number(),
            fachplan.number());
    Item paket = anlegen(o, "Paket", "Plan: Issue #" + plan.number(), plan.number());
    mvc.perform(post("/api/cards/" + fachplan.id() + "/archive").cookie(o.owner()))
        .andExpect(status().is2xxSuccessful());

    JsonNode kette = kette(o.owner(), paket.id());

    for (JsonNode g : glieder(kette, "FACHPLAN", null)) {
      assertThat(g.get("zustand").asText()).isEqualTo(ERFASST);
      assertThat(name(g.get("ausgefuehrtVon"))).isNotNull();
      assertThat(g.get("zeitpunkt").isNull()).isFalse();
      assertThat(g.get("quelle").get("oeffenbar").asBoolean()).isFalse();
    }
    JsonNode verweis = einziges(kette, "FACHPLAN", "HERKUNFT").get("quelle");
    assertThat(verweis.get("nummer").asInt()).isEqualTo(fachplan.number());
    assertThat(verweis.get("titel").asText()).isEqualTo("[Fachlich] Archiviert");
    assertThat(gliedarten(kette, "FACHPLAN")).containsExactly("HERKUNFT", "PRUEFRUNDE");
  }

  @Test
  @DisplayName("Szenario: Vorfahre ist endgültig gelöscht")
  void vorfahreIstEndgueltigGeloescht() throws Exception {
    Ort o = ort("geloescht");
    Item fachplan = anlegen(o, "[Fachlich] Bleibt", "Autor-Modell: " + MODELL, null);
    Item plan =
        anlegen(
            o,
            "[Plan] Verschwindet",
            "Plan-Modell: " + MODELL + "\nFachliche Quelle: Issue #" + fachplan.number(),
            fachplan.number());
    Item paket =
        anlegen(
            o,
            "Paket",
            "Plan: Issue #" + plan.number() + "\nFachliche Quelle: Issue #" + fachplan.number(),
            plan.number());
    mvc.perform(delete("/api/cards/" + plan.id()).cookie(o.owner()))
        .andExpect(status().is2xxSuccessful());
    mvc.perform(delete("/api/cards/" + plan.id() + "/purge").cookie(o.owner()))
        .andExpect(status().is2xxSuccessful());

    JsonNode kette = kette(o.owner(), paket.id());

    JsonNode planGlied = einziges(kette, "PLAN", "HERKUNFT");
    assertThat(planGlied.get("zustand").asText()).isEqualTo(NICHT_ERFASST);
    assertThat(planGlied.get("quelle").get("nummer").asInt()).isEqualTo(plan.number());
    assertThat(planGlied.get("quelle").get("titel").isNull()).isTrue();
    assertThat(einziges(kette, "FACHPLAN", "HERKUNFT").get("zustand").asText()).isEqualTo(ERFASST);
    assertThat(einziges(kette, "PAKET", "HERKUNFT").get("zustand").asText()).isEqualTo(ERFASST);
  }

  @Test
  @DisplayName("Szenario: Projekt ohne Kit")
  void projektOhneKit() throws Exception {
    Ort o = ort("ohne-kit");
    long karte = sitzungsKarte(o, "Von Hand", null);

    JsonNode kette = kette(o.owner(), karte);

    assertThat(kette.get("sichtbar").asBoolean()).isFalse();
    assertThat(kette.get("abschnitte")).isEmpty();
    assertThat(kette.get("nachfahren")).isEmpty();
  }

  // --- Grenzen der Schnittstelle ----------------------------------------------------------------

  @Test
  void einNichtmitgliedBekommt404() throws Exception {
    Ort o = ort("fremd");
    Item paket = anlegen(o, "Paket", "Autor-Modell: " + MODELL, null);
    Cookie fremder = anmeldung.anmelden("kette-fremder@example.com", PlatformRole.USER);

    mvc.perform(get(pfad(paket.id())).cookie(fremder)).andExpect(status().isNotFound());
    mvc.perform(get(pfad(999_999)).cookie(o.owner())).andExpect(status().isNotFound());
    mvc.perform(get(pfad(paket.id()))).andExpect(status().isUnauthorized());
  }

  @Test
  void einGliedAusEinemAnderenProjektTraegtDenVermerkUndKeinenTitel() throws Exception {
    Ort o = ort("eigen");
    Ort anderes = ort("anderes");
    Item geheim = anlegen(anderes, "[Plan] Geheimer Plan", "Plan-Modell: " + MODELL, null);
    Item paket = anlegen(o, "Paket", "Autor-Modell: " + MODELL, null);
    jdbc.update("UPDATE card SET derived_from_card_id = ? WHERE id = ?", geheim.id(), paket.id());

    String antwort = roh(o.owner(), paket.id());
    JsonNode glied = einziges(json.readTree(antwort), "PLAN", "HERKUNFT");

    assertThat(glied.get("zustand").asText()).isEqualTo(NICHT_ERFASST);
    assertThat(glied.get("hinweise"))
        .extracting(JsonNode::asText)
        .containsExactly("ANDERES_PROJEKT");
    assertThat(glied.get("quelle").get("nummer").isNull()).isTrue();
    assertThat(glied.get("quelle").get("titel").isNull()).isTrue();
    assertThat(antwort).doesNotContain("Geheimer Plan");
  }

  @Test
  void einVorfahrImPapierkorbStehtMitNummerAlsNichtErfasst() throws Exception {
    Ort o = ort("papierkorb");
    Item plan = anlegen(o, "[Plan] Im Papierkorb", "Plan-Modell: " + MODELL, null);
    Item paket =
        anlegen(
            o,
            "Paket",
            "Plan: Issue #" + plan.number() + "\nAutor-Modell: " + MODELL,
            plan.number());
    mvc.perform(delete("/api/cards/" + plan.id()).cookie(o.owner()))
        .andExpect(status().is2xxSuccessful());

    String antwort = roh(o.owner(), paket.id());
    JsonNode glied = einziges(json.readTree(antwort), "PLAN", "HERKUNFT");

    assertThat(glied.get("zustand").asText()).isEqualTo(NICHT_ERFASST);
    assertThat(glied.get("quelle").get("nummer").asInt()).isEqualTo(plan.number());
    assertThat(antwort).doesNotContain("Im Papierkorb");
    mvc.perform(get(pfad(plan.id())).cookie(o.owner())).andExpect(status().isNotFound());
  }

  @Test
  void anIdeeUndVorhabenIstDieKetteNichtSichtbar() throws Exception {
    Ort o = ort("idee");
    Item idee = anlegen(o, "[Idee] Einfall", "Autor-Modell: " + MODELL, null);
    long vorhaben = sitzungsKarte(o, "Großes Vorhaben", "EPIC");

    assertThat(kette(o.owner(), idee.id()).get("sichtbar").asBoolean()).isFalse();
    assertThat(kette(o.owner(), vorhaben).get("sichtbar").asBoolean()).isFalse();
  }

  @Test
  void zweimalAufgerufenGibtDieKetteDieselbeAntwort() throws Exception {
    Ort o = ort("zweimal");
    Item paket = ganzerWeg(o);

    assertThat(roh(o.owner(), paket.id())).isEqualTo(roh(o.owner(), paket.id()));
  }

  // --- Aufbau -----------------------------------------------------------------------------------

  /** Idee, Fachplan, Plan und Paket den ganzen Weg bis zur Veröffentlichung. */
  private Item ganzerWeg(Ort o) throws Exception {
    Item idee = anlegen(o, "[Idee] Einfall", "Autor-Modell: " + MODELL, null);
    Item fachplan = anlegen(o, "[Fachlich] Anlass", "Autor-Modell: " + MODELL, null);
    o.nummern().put("fachplan", fachplan.number());
    aendern(
        o,
        idee,
        "[Idee] Einfall",
        "Autor-Modell: "
            + MODELL
            + "\n\n## Geklärter Stand\n\nErgebnis: Issue #"
            + fachplan.number()
            + " (Fachplan)\n");
    kitKommentar(o, fachplan, runde("Fachplan-Review", 1));
    kitKommentar(o, fachplan, "## Einarbeitung, Runde 1\n\nEingearbeitet.");
    Item plan =
        anlegen(
            o,
            "[Plan] Weg",
            "Plan-Modell: " + MODELL + "\nFachliche Quelle: Issue #" + fachplan.number(),
            fachplan.number());
    kitKommentar(o, plan, runde("Plan-Review", 1));
    kitKommentar(o, plan, "## Einarbeitung, Runde 1\n\nEingearbeitet.");
    Item paket =
        anlegen(
            o,
            "Paket",
            "Plan: Issue #"
                + plan.number()
                + "\nFachliche Quelle: Issue #"
                + fachplan.number()
                + "\nAutor-Modell: "
                + MODELL,
            plan.number());
    kitKommentar(o, paket, runde("Issue-Review", 1));
    kitKommentar(o, paket, "## Einarbeitung, Runde 1\n\nEingearbeitet.");
    zieheMensch(o, paket, "Ready");
    zieheKit(o, paket, "IN_PROGRESS", Instant.now());
    kitKommentar(
        o,
        paket,
        """
        ## Abschlussbericht Issue #%d

        ### Änderungen
        - etwas

        Commit: abc1234
        Prüflauf: gruen, 2026-11-10T09:00:00Z
        Umgesetzt von: %s
        """
            .formatted(paket.number(), MODELL));
    zieheKit(o, paket, "IN_REVIEW", Instant.now());
    kitKommentar(o, paket, "## Code-Review (Schritt 6)\n\nKeine Funde.");
    kitKommentar(o, paket, uebertragung("1", "übertragen"));
    kitKommentar(
        o,
        paket,
        """
        ## Veröffentlichung

        Versuch: v-1
        Version: 1.4.0
        Commit: c-1
        Basis: b-1
        Ausgang: veröffentlicht
        Quelle: https://example.org/pr/1
        Freigegeben von: %s, 2026-11-10T12:00:00Z
        Ausgeführt von: %s, 2026-11-10T12:30:00Z
        """
            .formatted(MENSCH, MODELL));
    return paket;
  }

  private static String runde(String art, int nummer) {
    return """
        ## %s, Runde %d

        ### Reviewer 1: Prüfer, fable

        Keine Funde.
        """
        .formatted(art, nummer);
  }

  private static String uebertragung(String versuch, String ausgang) {
    return """
        ## Übertragung auf den Hauptzweig

        Versuch: %s
        Commit: c-%s
        Basis: b-%s
        Weg: lokal
        Prüflauf: gruen, 2026-11-10T09:00:00Z
        Build-Dienst: gruen, 2026-11-10T09:30:00Z
        Quelle: Prüflauf 2026-11-10T09:00:00Z
        Ausgang: %s
        Freigegeben von: %s, 2026-11-10T10:00:00Z
        Ausgeführt von: %s, 2026-11-10T11:00:00Z
        """
        .formatted(versuch, versuch, versuch, ausgang, MENSCH, MODELL);
  }

  /** Projekt mit Eigentümerin „Erika Muster“, einem Board und einem daran gebundenen Zugang. */
  private Ort ort(String name) throws Exception {
    Cookie admin = anmeldung.anmelden("kette-admin@example.com", PlatformRole.ADMIN);
    String email = "kette-" + name + "@example.com";
    Cookie owner = anmeldung.anmelden(email, PlatformRole.USER);
    jdbc.update("UPDATE app_user SET display_name = ? WHERE email = ?", MENSCH, email);
    long projekt =
        lies(post("/api/projects")
                .cookie(admin)
                .contentType(JSON)
                .content("{\"name\":\"Kette %s\",\"ownerEmail\":\"%s\"}".formatted(name, email)))
            .get("id")
            .asLong();
    long board =
        lies(post("/api/projects/" + projekt + "/boards")
                .cookie(owner)
                .contentType(JSON)
                .content("{\"name\":\"Board\"}"))
            .get("id")
            .asLong();
    String token =
        lies(post("/api/access-tokens")
                .cookie(owner)
                .contentType(JSON)
                .content(
                    "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}"
                        .formatted(ZUGANG, projekt, board)))
            .get("plaintext")
            .asText();
    Map<String, Long> spalten = new HashMap<>();
    for (JsonNode s :
        json.readTree(
                mvc.perform(get("/api/boards/" + board).cookie(owner))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("columns")) {
      spalten.put(s.get("name").asText(), s.get("id").asLong());
    }
    return new Ort(owner, board, token, spalten, new LinkedHashMap<>());
  }

  private JsonNode lies(MockHttpServletRequestBuilder anfrage) throws Exception {
    return json.readTree(
        mvc.perform(anfrage)
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  /** Eine Anfrage des Kits: gebundener Zugang, Modell und, falls gesetzt, die Prüfung vor Ready. */
  private MockHttpServletRequestBuilder kit(MockHttpServletRequestBuilder anfrage, Ort o) {
    MockHttpServletRequestBuilder mit =
        anfrage.header(TOKEN_HEADER, o.token()).header(AGENT_HEADER, MODELL);
    return reviewVorReady == null ? mit : mit.header(REVIEW_HEADER, reviewVorReady);
  }

  private Item anlegen(Ort o, String titel, String text, @Nullable Integer herkunft)
      throws Exception {
    Map<String, Object> karte = new LinkedHashMap<>();
    karte.put("title", titel);
    karte.put("body", text);
    karte.put("column", "BACKLOG");
    if (herkunft != null) {
      karte.put("derivedFrom", herkunft);
    }
    JsonNode angelegt =
        lies(
            kit(post("/api/kanban/items"), o)
                .contentType(JSON)
                .content(json.writeValueAsString(karte)));
    return new Item(angelegt.get("id").asLong(), angelegt.get("number").asInt());
  }

  private void aendern(Ort o, Item karte, String titel, String text) throws Exception {
    mvc.perform(
            kit(put("/api/kanban/items/" + karte.id()), o)
                .contentType(JSON)
                .content(json.writeValueAsString(Map.of("title", titel, "body", text))))
        .andExpect(status().isOk());
  }

  private void kitKommentar(Ort o, Item karte, String text) throws Exception {
    mvc.perform(
            kit(post("/api/kanban/items/" + karte.id() + "/comments"), o)
                .contentType(JSON)
                .content(body(text)))
        .andExpect(status().isCreated());
  }

  private void sitzungsKommentar(Ort o, Item karte, String text) throws Exception {
    mvc.perform(
            post("/api/cards/" + karte.id() + "/comments")
                .cookie(o.owner())
                .contentType(JSON)
                .content(body(text)))
        .andExpect(status().is2xxSuccessful());
  }

  private long letzterKommentar(Ort o, Item karte) throws Exception {
    JsonNode alle =
        json.readTree(
            mvc.perform(get("/api/cards/" + karte.id() + "/comments").cookie(o.owner()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return alle.get(alle.size() - 1).get("id").asLong();
  }

  /** Ein Zug des Kits; mit Laufkennung ist es ein Zug des Runners. */
  private void zieheKit(Ort o, Item karte, String spalte, @Nullable Instant lauf) throws Exception {
    MockHttpServletRequestBuilder anfrage =
        kit(put("/api/kanban/items/" + karte.id() + "/move"), o)
            .contentType(JSON)
            .content("{\"column\":\"%s\",\"position\":0}".formatted(spalte));
    if (lauf != null) {
      anfrage = anfrage.header(Laufkennung.HEADER, lauf.toString());
    }
    mvc.perform(anfrage).andExpect(status().isOk());
  }

  private void zieheMensch(Ort o, Item karte, String spalte) throws Exception {
    mvc.perform(
            post("/api/cards/" + karte.id() + "/move")
                .cookie(o.owner())
                .contentType(JSON)
                .content("{\"columnId\":%d,\"position\":0}".formatted(o.spalten().get(spalte))))
        .andExpect(status().isOk());
  }

  private void labelSetzen(Ort o, Item karte, String name) throws Exception {
    long label =
        lies(post("/api/boards/" + o.board() + "/labels")
                .cookie(o.owner())
                .contentType(JSON)
                .content("{\"name\":\"%s\",\"color\":\"#c0392b\"}".formatted(name)))
            .get("id")
            .asLong();
    mvc.perform(post("/api/cards/" + karte.id() + "/labels/" + label).cookie(o.owner()))
        .andExpect(status().is2xxSuccessful());
  }

  /** Eine in der Oberfläche angelegte Karte; mit {@code typ} {@code EPIC} ein Vorhaben. */
  private long sitzungsKarte(Ort o, String titel, @Nullable String typ) throws Exception {
    Map<String, Object> karte = new LinkedHashMap<>();
    karte.put("title", titel);
    karte.put("columnId", o.spalten().get("Backlog"));
    if (typ != null) {
      karte.put("type", typ);
    }
    return lies(post("/api/boards/" + o.board() + "/cards")
            .cookie(o.owner())
            .contentType(JSON)
            .content(json.writeValueAsString(karte)))
        .get("id")
        .asLong();
  }

  private String body(String text) throws Exception {
    return json.writeValueAsString(Map.of("body", text));
  }

  // --- Lesen der Antwort ------------------------------------------------------------------------

  private static String pfad(long karte) {
    return "/api/cards/" + karte + "/entscheidungskette";
  }

  private String roh(Cookie wer, long karte) throws Exception {
    return mvc.perform(get(pfad(karte)).cookie(wer))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private JsonNode kette(Cookie wer, long karte) throws Exception {
    return json.readTree(roh(wer, karte));
  }

  private static List<String> abschnittsarten(JsonNode kette) {
    List<String> arten = new ArrayList<>();
    kette.get("abschnitte").forEach(a -> arten.add(a.get("art").asText()));
    return arten;
  }

  private static List<String> gliedarten(JsonNode kette, String abschnitt) {
    return glieder(kette, abschnitt, null).stream().map(g -> g.get("art").asText()).toList();
  }

  /** Die Glieder des Abschnitts, mit {@code art} nur die dieser Art. */
  private static List<JsonNode> glieder(JsonNode kette, String abschnitt, @Nullable String art) {
    List<JsonNode> glieder = new ArrayList<>();
    for (JsonNode a : kette.get("abschnitte")) {
      if (a.get("art").asText().equals(abschnitt)) {
        for (JsonNode g : a.get("glieder")) {
          if (art == null || g.get("art").asText().equals(art)) {
            glieder.add(g);
          }
        }
      }
    }
    return glieder;
  }

  private static JsonNode einziges(JsonNode kette, String abschnitt, String art) {
    List<JsonNode> alle = glieder(kette, abschnitt, art);
    assertThat(alle).as("%s in %s von %s", art, abschnitt, kette).hasSize(1);
    return alle.getFirst();
  }

  /** Alle Glieder samt der Einarbeitungen in den Prüfrunden. */
  private static List<JsonNode> alleGlieder(JsonNode kette) {
    List<JsonNode> alle = new ArrayList<>();
    for (JsonNode a : kette.get("abschnitte")) {
      for (JsonNode g : a.get("glieder")) {
        alle.add(g);
        JsonNode pruefung = g.get("pruefung");
        if (pruefung != null && !pruefung.isNull()) {
          alle.add(pruefung.get("einarbeitung"));
        }
      }
    }
    return alle;
  }

  private static @Nullable String name(@Nullable JsonNode akteur) {
    if (akteur == null || akteur.isNull()) {
      return null;
    }
    JsonNode name = akteur.get("name");
    return name == null || name.isNull() ? null : name.asText();
  }

  private record Ort(
      Cookie owner,
      long board,
      String token,
      Map<String, Long> spalten,
      Map<String, Integer> nummern) {

    int nummer(String rolle) {
      Integer n = nummern.get(rolle);
      assertThat(n).as("Nummer von %s", rolle).isNotNull();
      return n;
    }
  }

  private record Item(long id, int number) {}
}
