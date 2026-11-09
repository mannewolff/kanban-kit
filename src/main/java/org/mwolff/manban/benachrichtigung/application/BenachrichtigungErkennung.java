package org.mwolff.manban.benachrichtigung.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.LaufAnlass;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.NeueZustellung;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungEinstellungenRepository.EingeschaltetePerson;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe.BenachrichtigbarerLauf;
import org.mwolff.manban.nightrun.application.NightRunProperties;
import org.mwolff.manban.nightrun.domain.Meldegrund;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erkennt Anlässe und legt je berechtigter Person eine Zustellung an (Issue #2077, Plan #2070 E2,
 * E4, E5, E6, E7). Versendet wird hier nichts.
 *
 * <p><b>Höchstens eine Mail je Lauf und Art.</b> Nur der Aufruf, der den Anlass neu anlegt, legt
 * Zustellungen an; jeder weitere Takt findet ihn vor. Rollt die Transaktion zurück, verschwinden
 * Anlass und Zustellungen gemeinsam, und der nächste Takt versucht es erneut.
 *
 * <p><b>Der Ausgang wird nicht gespeichert.</b> Die Übersicht braucht nur den Anlass; „verstummt“
 * und „von Hand beendet“ liest die Mail später aus {@link NightRunOutcome}.
 */
@Service
public class BenachrichtigungErkennung {

  private final BenachrichtigbareLaeufe laeufe;
  private final AnlassRepository anlaesse;
  private final BenachrichtigungEinstellungenRepository einstellungen;
  private final PermissionChecker permissions;
  private final CardRunQueryService karten;
  private final NightRunProperties nightRun;
  private final Clock clock;

  public BenachrichtigungErkennung(
      BenachrichtigbareLaeufe laeufe,
      AnlassRepository anlaesse,
      BenachrichtigungEinstellungenRepository einstellungen,
      PermissionChecker permissions,
      CardRunQueryService karten,
      NightRunProperties nightRun,
      Clock clock) {
    this.laeufe = laeufe;
    this.anlaesse = anlaesse;
    this.einstellungen = einstellungen;
    this.permissions = permissions;
    this.karten = karten;
    this.nightRun = nightRun;
    this.clock = clock;
  }

  /**
   * Prüft die Läufe im Fenster auf neue Anlässe.
   *
   * @return die Zahl der neu angelegten Anlässe
   */
  @Transactional
  public int erkenneLaeufe() {
    Instant jetzt = clock.instant();
    return laeufe.laeufe().stream().mapToInt(eintrag -> erkenne(eintrag, jetzt)).sum();
  }

  private int erkenne(BenachrichtigbarerLauf eintrag, Instant jetzt) {
    NightRun run = eintrag.lauf();
    List<NightRunItem> pakete = eintrag.pakete();
    Verdict ausgang = ausgang(run, pakete, jetzt);
    if (ausgang == Verdict.NO_WORK) {
      return 0;
    }
    Meldegrund.Anlaesse gefunden = Meldegrund.anlaesse(run, pakete, jetzt, nightRun.stilleFrist());
    int angelegt = 0;
    Meldegrund wartet = gefunden.wartet();
    if (wartet != null) {
      angelegt += lege(AnlassArt.LAUF_WARTET, run, karte(run, wartet), jetzt);
    }
    Meldegrund stoerung = gefunden.stoerung();
    // Ein verstummter Lauf meldet sich allein über seine Übersicht (E5).
    if (stoerung != null && stoerung.grund() != Meldegrund.Grund.VERSTUMMT) {
      angelegt += lege(AnlassArt.STOERUNG, run, karte(run, stoerung), jetzt);
    }
    if (ausgang != Verdict.RUNNING) {
      angelegt += lege(AnlassArt.UEBERSICHT, run, null, jetzt);
    }
    return angelegt;
  }

  private Verdict ausgang(NightRun run, List<NightRunItem> pakete, Instant jetzt) {
    Duration stilleFrist = nightRun.stilleFrist();
    return NightRunOutcome.of(
            run.complete(),
            run.closedAt(),
            run.noWorkReason(),
            run.abortReason(),
            run.abortKind(),
            run.mode(),
            pakete,
            run.startedAt(),
            run.updatedAt(),
            jetzt,
            stilleFrist)
        .verdict();
  }

  private @Nullable Long karte(NightRun run, Meldegrund grund) {
    return Optional.ofNullable(grund.paket())
        .flatMap(paket -> karten.kartenId(run.projectId(), paket.cardNumber()))
        .orElse(null);
  }

  private int lege(AnlassArt art, NightRun run, @Nullable Long cardId, Instant jetzt) {
    Instant eintritt = run.updatedAt() == null ? run.startedAt() : run.updatedAt();
    Optional<Long> anlass =
        anlaesse.legeAnlassZumLaufAn(
            new LaufAnlass(art, run.projectId(), run.requireId(), cardId, eintritt, jetzt));
    anlass.ifPresent(id -> legeZustellungenAn(id, run.projectId(), eintritt, jetzt));
    return anlass.isPresent() ? 1 : 0;
  }

  /** Was vor dem Einschalten eintrat, wird nicht nachgemeldet: Beide Schalter müssen älter sein. */
  private void legeZustellungenAn(long anlassId, long projectId, Instant eintritt, Instant jetzt) {
    boolean projektAn =
        einstellungen.projektEingeschaltetSeit(projectId).filter(eintritt::isAfter).isPresent();
    if (!projektAn) {
      return;
    }
    for (EingeschaltetePerson person : einstellungen.eingeschaltetePersonen(projectId)) {
      if (eintritt.isAfter(person.seit())
          && permissions.hatNachtlaufZugriff(person.userId(), projectId)) {
        Instant faelligAb =
            einstellungen.ruhezeit(person.userId()).map(z -> z.faelligAb(jetzt)).orElse(jetzt);
        anlaesse.legeZustellungAn(
            new NeueZustellung(anlassId, person.userId(), faelligAb, faelligAb.isAfter(jetzt)));
      }
    }
  }
}
