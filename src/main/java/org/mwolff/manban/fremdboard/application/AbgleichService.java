package org.mwolff.manban.fremdboard.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.FremdKartenBestand;
import org.mwolff.manban.card.application.FremdKartenSchreiber;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremdeKarte;
import org.mwolff.manban.card.application.FremdKartenSchreiber.FremderAkteur;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.SpiegelNotiz;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.FremdBoardQuelle;
import org.mwolff.manban.fremdboard.domain.FremdEreignis;
import org.mwolff.manban.fremdboard.domain.FremdKarte;
import org.mwolff.manban.fremdboard.domain.FremdNotiz;
import org.mwolff.manban.fremdboard.domain.FremdNotizen;
import org.mwolff.manban.fremdboard.domain.FremdNummern;
import org.mwolff.manban.fremdboard.domain.FremdStatus;
import org.mwolff.manban.fremdboard.domain.HerkunftAusText;
import org.mwolff.manban.fremdboard.domain.KartenZustand;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.StatusZuordnung;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gleicht eine Karte mit dem fremden System ab (Issue #2167, Plan #2152 A1, A4, A6, A11, A12). Es
 * gilt immer der Stand dort, mit dem Zeitpunkt dort.
 *
 * <p>Gelesen wird außerhalb jeder Transaktion: Karte, Wechsel und Notizen. Neu ist ein Wechsel ab
 * {@code angebunden_am}, dessen Kennung das {@link EreignisRegister} noch nicht kennt; ältere
 * Wechsel zählen nicht (gezählt wird ab der Anbindung). Den Stand vor den neuen Wechseln rechnet
 * {@link KartenZustand} rückwärts aus dem heutigen aus; eine noch nicht gespiegelte Karte tritt in
 * diesem Stand ein — beim Anbinden schon vorhandene mit {@code angebunden_am}, später angelegte mit
 * ihrem Anlagezeitpunkt dort.
 *
 * <p>Danach wird Zeitpunkt für Zeitpunkt vorwärts übernommen, je Zeitpunkt in einer eigenen
 * Transaktion, weil die Flusserfassung je Karte und Transaktion höchstens einen Wechsel bucht:
 * Kennungen eintragen, Labels spiegeln, den Status des Stands danach buchen. Wechsel mit demselben
 * Zeitpunkt gehören zusammen — das fremde System hält einen Spaltenwechsel als Abnehmen des alten
 * und Setzen des neuen Labels fest; einzeln gebucht entstünde dazwischen ein Aufenthalt ohne Dauer.
 * Trägt ein Zeitpunkt keine neue Kennung mehr, hat ihn ein gleichzeitiger Abgleich schon
 * übernommen; er bleibt dann unberührt. So zählt nichts doppelt.
 *
 * <p>Die Statuslabels selbst werden nicht als Labels gespiegelt — sie stehen als Spalte da. Notizen
 * werden alle gespiegelt, auch ältere als die Anbindung: Sie sind Inhalt der Karte, keine Messung.
 * Fehlt die Karte im fremden System, bleibt sie hier unberührt; gelöschte Karten erkennt der
 * Vollabgleich.
 *
 * <p>Den regelmäßigen Abgleich ganzer Projekte (Issue #2168) treibt {@link AbgleichJob}: {@link
 * #inkrementellAbgleichen} für die geänderten Karten, {@link #vollAbgleichen} nach eingestelltem
 * Abstand auch für fehlende und gelöschte.
 */
// PMD.CouplingBetweenObjects: Übersetzungsschicht vom Port des fremden Systems auf die Fassaden
// von card und comment (Plan #2152 A1) — wie KanbanCompatService für die Kanban-Strecke. Die reine
// Zustandsrechnung liegt schon in KartenZustand; ein weiterer Schnitt trennte nur, was je Karte
// gemeinsam gelesen und geschrieben wird.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
public class AbgleichService {

  private final List<FremdBoardQuelle> quellen;
  private final FremdKartenBestand bestand;
  private final FremdKartenSchreiber schreiber;
  private final CommentService kommentare;
  private final EreignisRegister register;
  private final TransactionTemplate transaktion;
  private final Clock clock;

  public AbgleichService(
      List<FremdBoardQuelle> quellen,
      FremdKartenBestand bestand,
      FremdKartenSchreiber schreiber,
      CommentService kommentare,
      EreignisRegister register,
      PlatformTransactionManager transaktionen,
      Clock clock) {
    this.quellen = List.copyOf(quellen);
    this.bestand = bestand;
    this.schreiber = schreiber;
    this.kommentare = kommentare;
    this.register = register;
    this.transaktion = new TransactionTemplate(transaktionen);
    this.clock = clock;
  }

  /**
   * Liest die Karte mit dieser Nummer aus dem fremden System und übernimmt, was sich geändert hat.
   *
   * @throws org.mwolff.manban.fremdboard.domain.FremdAbrufException wenn das fremde System nicht
   *     antwortet — dann ist nichts übernommen
   */
  public void karteAbgleichen(Verbindung verbindung, int nummer) {
    FremdBoardQuelle quelle = quelle(verbindung.art());
    quelle.karte(verbindung, nummer).ifPresent(karte -> abgleichen(verbindung, quelle, karte));
  }

  /**
   * Der regelmäßige Abgleich (Issue #2168, Plan #2152 A7): gleicht jede Karte ab, die sich im
   * fremden System nach {@code seit} geändert hat. Die Karte selbst kommt aus der Liste und wird
   * nicht ein zweites Mal gelesen.
   *
   * @throws org.mwolff.manban.fremdboard.domain.FremdAbrufException wenn das fremde System nicht
   *     antwortet — was bis dahin übernommen war, bleibt übernommen
   */
  public void inkrementellAbgleichen(Verbindung verbindung, Instant seit) {
    FremdBoardQuelle quelle = quelle(verbindung.art());
    for (FremdKarte karte : quelle.geaenderteKarten(verbindung, seit)) {
      abgleichen(verbindung, quelle, karte);
    }
  }

  /**
   * Der Vollabgleich (Issue #2168, Plan #2152 A7, A8, A9): gleicht wie {@link
   * #inkrementellAbgleichen} die seit {@code seit} geänderten Karten ab, liest jede Karte einzeln,
   * die es im fremden System gibt und hier noch nicht, und markiert jede gespiegelte Karte als
   * gelöscht, die es dort nicht mehr gibt. Ihre Messungen bleiben.
   *
   * <p>Eine Karte, die dort vertraulich ist, entfernt er endgültig, gleich ob sie auf dem Board, im
   * Archiv oder im Papierkorb liegt (Issue #2200): Der Zugang des Spiegels darf dort mehr lesen als
   * ein Mitglied des Projekts. Das geschieht gleich nach dem Lesen der Nummern, vor jedem weiteren
   * Abruf — die Vertraulichkeit ist ausdrücklich übertragen, nicht aus einem Fehlen geschlossen.
   *
   * <p>Die Nummern hier werden <em>vor</em> denen dort gelesen: Eine Karte, die dazwischen dort
   * angelegt und hier von einem Anstoß gespiegelt wird, fehlt so in beiden Listen und nicht nur in
   * der fremden. Gelöscht wird erst, wenn alles gelesen ist; scheitert ein Abruf, ist nichts
   * gelöscht.
   *
   * @param seit ab hier gelten Karten als geändert; der Vollabgleich holt damit auch nach, was ein
   *     abgebrochener Abgleich seit dem letzten Vollabgleich versäumt hat
   * @throws org.mwolff.manban.fremdboard.domain.FremdAbrufException wenn das fremde System nicht
   *     antwortet
   */
  public void vollAbgleichen(Verbindung verbindung, Instant seit) {
    FremdBoardQuelle quelle = quelle(verbindung.art());
    Set<Integer> hier = new HashSet<>(bestand.nummern(verbindung.projectId()));
    FremdNummern nummern = quelle.nummern(verbindung);
    List<Integer> vertraulich =
        nummern.vertraulich().stream()
            .filter(n -> n <= Integer.MAX_VALUE)
            .map(Long::intValue)
            .toList();
    if (!vertraulich.isEmpty()) {
      schreiber.vertraulicheEntfernen(verbindung.boardId(), vertraulich);
      hier.removeAll(vertraulich);
    }
    Set<Long> dort = new HashSet<>(nummern.sichtbar());
    Set<Long> gelesen = new HashSet<>();
    for (FremdKarte karte : quelle.geaenderteKarten(verbindung, seit)) {
      abgleichen(verbindung, quelle, karte);
      gelesen.add(karte.nummer());
    }
    for (long nummer : dort) {
      if (!gelesen.contains(nummer) && !hier.contains(Math.toIntExact(nummer))) {
        quelle.karte(verbindung, nummer).ifPresent(karte -> abgleichen(verbindung, quelle, karte));
      }
    }
    Instant festgestellt = clock.instant();
    for (Integer nummer : hier) {
      if (!dort.contains(nummer.longValue())) {
        schreiber.alsGeloeschtMarkieren(verbindung.boardId(), nummer, festgestellt);
      }
    }
  }

  /** Übernimmt, was sich an der gelesenen Karte geändert hat. */
  private void abgleichen(Verbindung verbindung, FremdBoardQuelle quelle, FremdKarte karte) {
    long nummer = karte.nummer();
    List<FremdEreignis> neue = neueEreignisse(verbindung, quelle.ereignisse(verbindung, nummer));
    FremdNotizen notizen = quelle.notizen(verbindung, nummer);
    String adresse = quelle.adresse(verbindung, nummer);
    StatusZuordnung zuordnung = new StatusZuordnung(verbindung.statuslabels());

    KartenZustand zustand = KartenZustand.von(karte).vor(neue);
    Optional<Long> vorhanden = bestand.kartenId(verbindung.projectId(), nummer(karte));
    if (vorhanden.isPresent()) {
      schreiber.aktualisieren(
          verbindung.boardId(),
          nummer(karte),
          karte.titel(),
          karte.beschreibung(),
          adresse,
          karte.geaendertAm());
    }
    long cardId =
        vorhanden.isPresent()
            ? vorhanden.get()
            : angelegt(verbindung, karte, adresse, zustand, zuordnung);
    for (List<FremdEreignis> gruppe : nachZeitpunkt(neue)) {
      for (FremdEreignis ereignis : gruppe) {
        zustand = zustand.nach(ereignis);
      }
      uebernehmen(verbindung, karte, gruppe, zustand.status(karte, zuordnung));
    }
    angeglichen(verbindung, karte, zuordnung);
    herkunft(verbindung, karte);
    notizen(verbindung, cardId, notizen);
  }

  /** Die Wechsel ab der Anbindung, die noch nicht übernommen sind, ältester zuerst. */
  private List<FremdEreignis> neueEreignisse(
      Verbindung verbindung, List<FremdEreignis> ereignisse) {
    List<FremdEreignis> abAnbindung =
        ereignisse.stream().filter(e -> !e.zeit().isBefore(verbindung.angebundenAm())).toList();
    Set<String> bekannt =
        register.bekannte(
            verbindung.projectId(), abAnbindung.stream().map(FremdEreignis::kennung).toList());
    return abAnbindung.stream()
        .filter(e -> !bekannt.contains(e.kennung()))
        .sorted(Comparator.comparing(FremdEreignis::zeit))
        .toList();
  }

  /** Legt die Karte im Stand vor den neuen Wechseln an, samt ihrer Labels in diesem Stand. */
  private long angelegt(
      Verbindung verbindung,
      FremdKarte karte,
      String adresse,
      KartenZustand zustand,
      StatusZuordnung zuordnung) {
    Instant eintritt =
        karte.angelegtAm().isBefore(verbindung.angebundenAm())
            ? verbindung.angebundenAm()
            : karte.angelegtAm();
    String status = zustand.status(karte, zuordnung).orElse(FremdStatus.BACKLOG).name();
    FremderAkteur unbekannt = FremderAkteur.von(null, verbindung.kitKonto());
    Long cardId =
        transaktion.execute(
            tx -> {
              long id =
                  schreiber.anlegen(
                      verbindung.boardId(),
                      new FremdeKarte(
                          nummer(karte), karte.titel(), karte.beschreibung(), status, adresse),
                      eintritt,
                      unbekannt);
              for (String label : zustand.labels()) {
                if (!istStatuslabel(verbindung.statuslabels(), label)) {
                  schreiber.labelSetzen(
                      verbindung.boardId(), nummer(karte), label, eintritt, unbekannt);
                }
              }
              return id;
            });
    return Objects.requireNonNull(cardId);
  }

  /** Übernimmt die Wechsel eines Zeitpunkts in einer Transaktion. */
  private void uebernehmen(
      Verbindung verbindung,
      FremdKarte karte,
      List<FremdEreignis> gruppe,
      Optional<FremdStatus> status) {
    FremdEreignis letztes = gruppe.getLast();
    FremderAkteur akteur = FremderAkteur.von(letztes.akteur(), verbindung.kitKonto());
    transaktion.executeWithoutResult(
        tx -> {
          boolean neu = false;
          for (FremdEreignis ereignis : gruppe) {
            neu |=
                register.eintragen(
                    verbindung.projectId(), ereignis.art().name(), ereignis.kennung());
          }
          if (!neu) {
            return;
          }
          for (FremdEreignis ereignis : gruppe) {
            labelSpiegeln(verbindung, karte, ereignis);
          }
          status.ifPresent(
              s ->
                  schreiber.wechseln(
                      verbindung.boardId(), nummer(karte), s.name(), letztes.zeit(), akteur));
        });
  }

  private void labelSpiegeln(Verbindung verbindung, FremdKarte karte, FremdEreignis ereignis) {
    String label = ereignis.label();
    if (label == null || istStatuslabel(verbindung.statuslabels(), label)) {
      return;
    }
    switch (ereignis.art()) {
      case LABEL_GESETZT ->
          schreiber.labelSetzen(
              verbindung.boardId(),
              nummer(karte),
              label,
              ereignis.zeit(),
              FremderAkteur.von(ereignis.akteur(), verbindung.kitKonto()));
      case LABEL_ENTFERNT -> schreiber.labelEntfernen(verbindung.boardId(), nummer(karte), label);
      default -> {
        // Schließen und Wiederöffnen tragen kein Label.
      }
    }
  }

  /**
   * Steht die Karte nach allen Wechseln nicht in der Spalte ihres heutigen Status — etwa weil die
   * Statuslabels der Verbindung geändert wurden —, folgt sie ihm mit dem Änderungszeitpunkt dort.
   * Im Regelfall hat der letzte Wechsel sie schon dorthin gebracht, und es bleibt keine Spur.
   */
  private void angeglichen(Verbindung verbindung, FremdKarte karte, StatusZuordnung zuordnung) {
    Optional<FremdStatus> heute = zuordnung.status(karte);
    if (heute.isEmpty()) {
      return;
    }
    Instant zeitpunkt =
        karte.geaendertAm().isBefore(verbindung.angebundenAm())
            ? verbindung.angebundenAm()
            : karte.geaendertAm();
    transaktion.executeWithoutResult(
        tx ->
            schreiber.wechseln(
                verbindung.boardId(),
                nummer(karte),
                heute.get().name(),
                zeitpunkt,
                FremderAkteur.von(null, verbindung.kitKonto())));
  }

  /** Herkunft aus dem Text: der Plan, sonst die fachliche Quelle (A12). */
  private void herkunft(Verbindung verbindung, FremdKarte karte) {
    Integer vorfahr =
        HerkunftAusText.plan(karte.beschreibung())
            .or(() -> HerkunftAusText.fachlicheQuelle(karte.beschreibung()))
            .filter(n -> n <= Integer.MAX_VALUE)
            .map(Long::intValue)
            .orElse(null);
    schreiber.herkunftSetzen(verbindung.boardId(), nummer(karte), vorfahr);
  }

  /**
   * Entfernt zuerst die Kommentare, deren Notiz dort intern ist (Issue #2200), dann spiegelt er die
   * übrigen und kennzeichnet die fehlenden.
   */
  private void notizen(Verbindung verbindung, long cardId, FremdNotizen gelesen) {
    kommentare.spiegelVertraulicheEntfernen(cardId, gelesen.vertraulich());
    List<FremdNotiz> notizen = gelesen.sichtbar();
    for (FremdNotiz notiz : notizen) {
      kommentare.spiegeln(
          cardId,
          new SpiegelNotiz(
              notiz.kennung(),
              FremderAkteur.von(notiz.autor(), verbindung.kitKonto()),
              notiz.text(),
              notiz.angelegtAm(),
              notiz.geaendertAm()));
    }
    kommentare.spiegelFehlendeKennzeichnen(
        cardId,
        notizen.stream().map(FremdNotiz::kennung).collect(Collectors.toUnmodifiableSet()),
        clock.instant());
  }

  /** Die Wechsel in Gruppen gleichen Zeitpunkts; die Liste ist schon nach Zeit geordnet. */
  private static List<List<FremdEreignis>> nachZeitpunkt(List<FremdEreignis> ereignisse) {
    List<List<FremdEreignis>> gruppen = new ArrayList<>();
    @Nullable Instant bisher = null;
    for (FremdEreignis ereignis : ereignisse) {
      if (!ereignis.zeit().equals(bisher)) {
        gruppen.add(new ArrayList<>());
        bisher = ereignis.zeit();
      }
      gruppen.getLast().add(ereignis);
    }
    return gruppen;
  }

  private static boolean istStatuslabel(StatusLabels labels, String label) {
    return label.equals(labels.backlog())
        || label.equals(labels.ready())
        || label.equals(labels.inProgress())
        || label.equals(labels.inReview());
  }

  private static int nummer(FremdKarte karte) {
    return Math.toIntExact(karte.nummer());
  }

  private FremdBoardQuelle quelle(FremdBoardArt art) {
    return quellen.stream()
        .filter(q -> q.art() == art)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Keine Quelle für " + art));
  }
}
