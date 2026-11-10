package org.mwolff.manban.entscheidungskette.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.KartenKetteLeser;
import org.mwolff.manban.card.application.KartenKetteLeser.GliedZustand;
import org.mwolff.manban.card.application.KartenKetteLeser.KartenKette;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenAktivitaet;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenGlied;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenKarte;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenLabelEreignis;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.KettenKommentarView;
import org.mwolff.manban.common.KitMeldungen;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.AbschnittView;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.AkteurView;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.GliedView;
import org.mwolff.manban.entscheidungskette.domain.Akteurtyp;
import org.mwolff.manban.entscheidungskette.domain.Dokumentart;
import org.mwolff.manban.entscheidungskette.domain.Freigabefall;
import org.mwolff.manban.entscheidungskette.domain.Gliedart;
import org.mwolff.manban.entscheidungskette.domain.Hinweis;
import org.mwolff.manban.entscheidungskette.domain.Umsetzungsausgang;
import org.mwolff.manban.entscheidungskette.domain.Zustand;
import org.mwolff.manban.project.application.KitReviewVorReadyReader;
import org.mwolff.manban.project.application.KitReviewVorReadyReader.Meldung;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Der Dienst der Entscheidungskette mit gestellten Fassaden (Issue #2117). */
// PMD.ExcessiveImports: Der Test stellt die Sichten von card, comment, project und auth und prüft
// die Ausgabe mit den Typen der Rechnung — die Importe zählen genau diese Grenze des Dienstes.
@SuppressWarnings("PMD.ExcessiveImports")
class EntscheidungsketteServiceTest {

  private static final long NUTZER = 7;
  private static final long PROJEKT = 3;
  private static final long PAKET = 30;
  private static final long PLAN = 20;
  private static final long IDEE = 5;
  private static final long ERIKA = 41;
  private static final long GELOESCHT = 42;
  private static final String MODELL = "claude-opus-5-5";
  private static final Instant T0 = Instant.parse("2026-11-10T08:00:00Z");

  private final CardService cards = mock(CardService.class);
  private final PermissionChecker permissions = mock(PermissionChecker.class);
  private final KartenKetteLeser leser = mock(KartenKetteLeser.class);
  private final CommentService comments = mock(CommentService.class);
  private final KitReviewVorReadyReader reviewVorReady = mock(KitReviewVorReadyReader.class);
  private final UserLookup users = mock(UserLookup.class);
  private final EntscheidungsketteService service =
      new EntscheidungsketteService(cards, permissions, leser, comments, reviewVorReady, users);

  @BeforeEach
  void stelle() {
    when(cards.requireProjectId(anyLong())).thenReturn(PROJEKT);
    when(leser.traegtKitKarte(PROJEKT)).thenReturn(true);
    when(reviewVorReady.meldung(PROJEKT)).thenReturn(Optional.empty());
    when(users.findById(ERIKA))
        .thenReturn(Optional.of(new UserSummary(ERIKA, "e@example.com", "Erika Muster", true)));
    when(users.findById(GELOESCHT)).thenReturn(Optional.empty());
  }

  @Test
  void einNichtmitgliedErfaehrtNichtsUeberDieKarte() {
    ProjectNotFoundException fremd = new ProjectNotFoundException();
    when(permissions.requireMembership(NUTZER, PROJEKT)).thenThrow(fremd);

    assertThatThrownBy(() -> service.kette(NUTZER, PAKET))
        .isInstanceOf(CardNotFoundException.class)
        .hasCause(fremd);
    verify(leser, never()).lade(anyLong());
  }

  @Test
  void eineUnbekannteKarteIstNichtGefunden() {
    when(cards.requireProjectId(PAKET)).thenThrow(new CardNotFoundException());

    assertThatThrownBy(() -> service.kette(NUTZER, PAKET))
        .isInstanceOf(CardNotFoundException.class);
    verify(permissions, never()).requireMembership(anyLong(), anyLong());
  }

  @Test
  void ohneKitGibtEsKeinenAbschnitt() {
    when(leser.lade(PAKET)).thenReturn(kette(karte(PAKET, 30, "Paket", "ARBEITSPAKET")));
    when(leser.traegtKitKarte(PROJEKT)).thenReturn(false);
    when(comments.projektTraegtKitKommentar(PROJEKT, KitMeldungen.festeAnfaenge()))
        .thenReturn(false);

    assertThat(service.kette(NUTZER, PAKET)).isEqualTo(EntscheidungsketteView.UNSICHTBAR);
    verify(comments, never()).kettenKommentare(anyCollection());
  }

  @Test
  void einKommentarDesKitsGenuegtAlsBelegFuerDasKit() {
    when(leser.lade(PAKET)).thenReturn(kette(karte(PAKET, 30, "Paket", "ARBEITSPAKET")));
    when(leser.traegtKitKarte(PROJEKT)).thenReturn(false);
    when(comments.projektTraegtKitKommentar(PROJEKT, KitMeldungen.festeAnfaenge()))
        .thenReturn(true);

    EntscheidungsketteView sicht = service.kette(NUTZER, PAKET);

    assertThat(sicht.sichtbar()).isTrue();
    assertThat(sicht.art()).isEqualTo(Dokumentart.PAKET);
  }

  @Test
  void eineKitKarteMachtDieKommentarabfrageUeberfluessig() {
    when(leser.lade(PAKET)).thenReturn(kette(karte(PAKET, 30, "Paket", "ARBEITSPAKET")));

    assertThat(service.kette(NUTZER, PAKET).sichtbar()).isTrue();
    verify(comments, never()).projektTraegtKitKommentar(anyLong(), any());
  }

  @Test
  void anIdeeUndVorhabenGibtEsKeinenAbschnitt() {
    when(leser.lade(IDEE)).thenReturn(kette(karte(IDEE, 5, "[Idee] Einfall", "IDEE")));
    when(leser.lade(99L)).thenReturn(kette(karte(99, 99, "Vorhaben", "VORHABEN")));

    assertThat(service.kette(NUTZER, IDEE)).isEqualTo(EntscheidungsketteView.UNSICHTBAR);
    assertThat(service.kette(NUTZER, 99L)).isEqualTo(EntscheidungsketteView.UNSICHTBAR);
    verify(comments, never()).kettenKommentare(anyCollection());
  }

  /**
   * Alle Karten der Kette im Bestand liefern ihre Kommentare; eine Karte im Papierkorb nicht. Die
   * Einstellung zur Prüfung vor Ready geht in die Rechnung ein.
   */
  @Test
  void kommentareKommenVonAllenKartenDerKetteImBestand() {
    KettenKarte paket = karte(PAKET, 30, "Paket", "ARBEITSPAKET");
    KettenKarte plan = karte(PLAN, 20, "[Plan] Weg", "PLAN");
    KettenKarte idee = karte(IDEE, 5, "[Idee] Einfall", "IDEE");
    when(leser.lade(PAKET))
        .thenReturn(
            new KartenKette(
                paket,
                List.of(
                    new KettenGlied(GliedZustand.VORHANDEN, 20, plan),
                    new KettenGlied(GliedZustand.PAPIERKORB, 10, null)),
                List.of(),
                List.of(idee),
                List.of(),
                List.of()));
    when(reviewVorReady.meldung(PROJEKT)).thenReturn(Optional.of(new Meldung(false, null)));

    EntscheidungsketteView sicht = service.kette(NUTZER, PAKET);

    // Ein Captor für Collection<Long> lässt sich nur über die rohe Klasse anlegen.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
    verify(comments).kettenKommentare(ids.capture());
    assertThat(ids.getValue()).containsExactly(PAKET, PLAN, IDEE);
    assertThat(glieder(sicht, Dokumentart.PAKET, Gliedart.ISSUE_REVIEW))
        .extracting(GliedView::zustand)
        .containsExactly(Zustand.NICHT_VORGESEHEN);
    assertThat(glieder(sicht, Dokumentart.FACHPLAN, Gliedart.HERKUNFT))
        .extracting(g -> g.quelle().nummer(), GliedView::zustand)
        .containsExactly(tuple(10, Zustand.NICHT_ERFASST));
  }

  /** Ein Mensch erscheint mit Anzeigenamen, ein gelöschter Nutzer ohne; ein Modell mit Namen. */
  @Test
  void menschenErscheinenMitAnzeigenamen_geloeschteAlsNichtErfasst() {
    KettenKarte paket = karte(PAKET, 30, "Paket", "ARBEITSPAKET");
    KettenKarte plan = karte(PLAN, 20, "[Plan] Weg", "PLAN");
    when(leser.lade(PAKET))
        .thenReturn(
            new KartenKette(
                paket,
                List.of(new KettenGlied(GliedZustand.VORHANDEN, 20, plan)),
                List.of(),
                List.of(),
                List.of(
                    zug(PLAN, ERIKA, "CREATED", null, T0),
                    zug(PAKET, GELOESCHT, "CREATED", null, T0.plusSeconds(10)),
                    zug(PAKET, ERIKA, "MOVED", "READY", T0.plusSeconds(20)),
                    zug(PAKET, GELOESCHT, "MOVED", "IN_PROGRESS", T0.plusSeconds(30))),
                List.of()));
    when(comments.kettenKommentare(anyCollection()))
        .thenReturn(
            List.of(
                kommentar(
                    1,
                    PAKET,
                    "## Abschlussbericht Issue #30\n\nCommit: abc\nUmgesetzt von: " + MODELL + "\n",
                    T0.plusSeconds(40))));

    EntscheidungsketteView sicht = service.kette(NUTZER, PAKET);

    assertThat(glieder(sicht, Dokumentart.PLAN, Gliedart.HERKUNFT).getFirst().ausgefuehrtVon())
        .isEqualTo(new AkteurView(Akteurtyp.MENSCH, "Erika Muster", null));
    assertThat(glieder(sicht, Dokumentart.PAKET, Gliedart.HERKUNFT).getFirst().ausgefuehrtVon())
        .isEqualTo(new AkteurView(Akteurtyp.MENSCH, null, null));
    GliedView freigabe = glieder(sicht, Dokumentart.PAKET, Gliedart.FREIGABE).getFirst();
    assertThat(freigabe.freigegebenVon())
        .isEqualTo(new AkteurView(Akteurtyp.MENSCH, "Erika Muster", null));
    assertThat(freigabe.freigabe()).isNotNull();
    assertThat(freigabe.freigabe().fall()).isEqualTo(Freigabefall.ZUG_NACH_READY);
    assertThat(freigabe.umsetzung()).isNull();
    GliedView umsetzung = glieder(sicht, Dokumentart.PAKET, Gliedart.UMSETZUNG).getFirst();
    assertThat(umsetzung.ausgefuehrtVon())
        .isEqualTo(new AkteurView(Akteurtyp.MODELL, "claude-opus-5-5", null));
    assertThat(umsetzung.umsetzung()).isNotNull();
    assertThat(umsetzung.umsetzung().ausgang()).isEqualTo(Umsetzungsausgang.ABGESCHLOSSEN);
    assertThat(umsetzung.umsetzung().commit()).isEqualTo("abc");
    assertThat(umsetzung.freigabe()).isNull();
    assertThat(umsetzung.codeReview()).isNull();
    assertThat(umsetzung.uebertragung()).isNull();
    assertThat(umsetzung.veroeffentlichung()).isNull();
    // Einmal je Person aufgelöst, auch wenn sie mehrfach vorkommt.
    verify(users, times(1)).findById(ERIKA);
  }

  /** Prüfrunde mit Einarbeitung, Code-Review, Übertragung und Veröffentlichung in ihrem Feld. */
  @Test
  void jedeArtTraegtIhreAngabenImEigenenFeld() {
    KettenKarte paket = karte(PAKET, 30, "Paket", "ARBEITSPAKET");
    KettenKarte plan = karte(PLAN, 20, "[Plan] Weg", "PLAN");
    when(leser.lade(PAKET))
        .thenReturn(
            new KartenKette(
                paket,
                List.of(new KettenGlied(GliedZustand.VORHANDEN, 20, plan)),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    when(comments.kettenKommentare(anyCollection()))
        .thenReturn(
            List.of(
                kommentar(
                    1, PLAN, "## Plan-Review, Runde 1\n\n### Reviewer 1: Prüfer, fable\n", T0),
                kommentar(2, PLAN, "## Einarbeitung, Runde 1\n", T0.plusSeconds(1)),
                kommentar(3, PAKET, "## Code-Review (Schritt 6)\n", T0.plusSeconds(2)),
                kommentar(
                    4,
                    PAKET,
                    "## Übertragung auf den Hauptzweig\n\nVersuch: 1\nAusgang: übertragen\n",
                    T0.plusSeconds(3)),
                kommentar(
                    5,
                    PAKET,
                    "## Veröffentlichung\n\nVersion: 1.2.3\nAusgang: veröffentlicht\n",
                    T0.plusSeconds(4))));

    EntscheidungsketteView sicht = service.kette(NUTZER, PAKET);

    GliedView runde = glieder(sicht, Dokumentart.PLAN, Gliedart.PRUEFRUNDE).getFirst();
    assertThat(runde.pruefung()).isNotNull();
    assertThat(runde.pruefung().art()).isEqualTo(Meldungsart.PLAN_REVIEW);
    assertThat(runde.pruefung().runde()).isEqualTo(1);
    assertThat(runde.pruefung().pruefer()).extracting(r -> r.modell()).containsExactly("fable");
    assertThat(runde.pruefung().einarbeitung().zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(runde.pruefung().einarbeitung().ausgefuehrtVon())
        .isEqualTo(new AkteurView(Akteurtyp.WERKZEUG, "Kit", "claude-opus-5-5"));
    assertThat(runde.hinweise()).isEmpty();
    assertThat(glieder(sicht, Dokumentart.PAKET, Gliedart.CODE_REVIEW).getFirst().codeReview())
        .isNotNull();
    assertThat(glieder(sicht, Dokumentart.PAKET, Gliedart.UEBERTRAGUNG).getFirst().uebertragung())
        .isNotNull();
    GliedView veroeffentlichung =
        glieder(sicht, Dokumentart.PAKET, Gliedart.VEROEFFENTLICHUNG).getFirst();
    assertThat(veroeffentlichung.veroeffentlichung()).isNotNull();
    assertThat(veroeffentlichung.veroeffentlichung().version()).isEqualTo("1.2.3");
  }

  /** Nachfahren am Plan kommen mit; ein Hinweis bleibt am Glied stehen. */
  @Test
  void nachfahrenUndHinweiseGehenDurch() {
    KettenKarte plan = karte(PLAN, 20, "[Plan] Weg", "PLAN");
    KettenKarte paket = karte(PAKET, 30, "Paket", "ARBEITSPAKET");
    when(leser.lade(PLAN))
        .thenReturn(
            new KartenKette(
                plan,
                List.of(new KettenGlied(GliedZustand.ANDERES_PROJEKT, null, null)),
                List.of(new KettenGlied(GliedZustand.VORHANDEN, 30, paket)),
                List.of(),
                List.of(),
                List.of(
                    new KettenLabelEreignis(PLAN, "ziel:umsetzung", T0, ERIKA, "SESSION", null))));

    EntscheidungsketteView sicht = service.kette(NUTZER, PLAN);

    assertThat(sicht.art()).isEqualTo(Dokumentart.PLAN);
    assertThat(sicht.nachfahren()).extracting(n -> n.verweis().nummer()).containsExactly(30);
    assertThat(glieder(sicht, Dokumentart.FACHPLAN, Gliedart.HERKUNFT).getFirst().hinweise())
        .containsExactly(Hinweis.ANDERES_PROJEKT);
  }

  @Test
  void zweimalGerechnetErgibtDieselbeAntwort() {
    when(leser.lade(PAKET)).thenReturn(kette(karte(PAKET, 30, "Paket", "ARBEITSPAKET")));

    assertThat(service.kette(NUTZER, PAKET)).isEqualTo(service.kette(NUTZER, PAKET));
  }

  private static List<GliedView> glieder(
      EntscheidungsketteView sicht, Dokumentart dokument, Gliedart art) {
    return sicht.abschnitte().stream()
        .filter(a -> a.art() == dokument)
        .map(AbschnittView::glieder)
        .flatMap(List::stream)
        .filter(g -> g.art() == art)
        .toList();
  }

  private static KartenKette kette(KettenKarte karte) {
    return new KartenKette(karte, List.of(), List.of(), List.of(), List.of(), List.of());
  }

  private static KettenKarte karte(long id, int nummer, String titel, String art) {
    return new KettenKarte(id, nummer, titel, null, art, "Ready", "READY", false, null);
  }

  private static KettenAktivitaet zug(
      long karte, long person, String typ, @Nullable String spalte, Instant zeit) {
    return new KettenAktivitaet(karte, person, typ, "", zeit, "SESSION", null, null, spalte, null);
  }

  private static KettenKommentarView kommentar(long id, long karte, String text, Instant zeit) {
    return new KettenKommentarView(
        id, karte, "Kit", text, zeit, "TOKEN", "Kit", "claude-opus-5-5", null, null);
  }
}
