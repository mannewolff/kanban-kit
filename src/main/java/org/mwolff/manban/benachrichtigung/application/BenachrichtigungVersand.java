package org.mwolff.manban.benachrichtigung.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.FaelligeZustellung;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.KartenStandView;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe.LaufMitPaketen;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft die fälligen Zustellungen und plant sie in die Outbox ein (Issue #2079, Plan #2070 E6 bis
 * E9). Versendet wird hier nichts.
 *
 * <p><b>Einmaligkeit.</b> Einplanen und Zustandswechsel liegen in einer Transaktion; rollt sie
 * zurück, bleiben die Zustellungen offen, und der nächste Takt versucht es erneut.
 *
 * <p><b>Rechte zum Zeitpunkt des Einplanens</b> (E6). Zwischen Erkennen und Einplanen kann eine
 * Ruhezeit liegen; eine Zustellung ohne Recht entfällt still.
 *
 * <p><b>Erledigt</b> (E8). Ein Label-Anlass ist erledigt, sobald die Karte kein Label des Paars
 * mehr trägt. An der Karte eines wartenden Pakets setzt das Kit kein Label, darum zählt dort die
 * letzte Meldung des Laufs; das bloße Ende des Laufs erledigt nichts, denn der Halt bleibt offen.
 */
@Service
public class BenachrichtigungVersand {

  /** Der Ereignistyp der Outbox für diese Mails. */
  public static final String TYP = "mail.benachrichtigung";

  private static final Map<AnlassArt, List<String>> LABEL_PAARE =
      Arrays.stream(Sache.values())
          .filter(sache -> AnlassArt.ausSache(sache).isPresent())
          .collect(
              Collectors.toMap(
                  sache -> AnlassArt.ausSache(sache).orElseThrow(), KitNamen.STANDARD::namen));

  private final AnlassRepository anlaesse;
  private final BenachrichtigbareLaeufe laeufe;
  private final CardRunQueryService karten;
  private final PermissionChecker permissions;
  private final OutboxWriter outbox;
  private final Clock clock;

  public BenachrichtigungVersand(
      AnlassRepository anlaesse,
      BenachrichtigbareLaeufe laeufe,
      CardRunQueryService karten,
      PermissionChecker permissions,
      OutboxWriter outbox,
      Clock clock) {
    this.anlaesse = anlaesse;
    this.laeufe = laeufe;
    this.karten = karten;
    this.permissions = permissions;
    this.outbox = outbox;
    this.clock = clock;
  }

  /**
   * Plant alle fälligen Zustellungen ein oder lässt sie entfallen.
   *
   * @return die Zahl der eingeplanten Nachrichten
   */
  @Transactional
  public int planeEin() {
    List<FaelligeZustellung> faellig = anlaesse.faelligeZustellungen(clock.instant());
    Map<Long, KartenStandView> staende =
        karten
            .kartenStand(
                faellig.stream()
                    .map(FaelligeZustellung::cardId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(KartenStandView::id, Function.identity()));
    Map<Long, Optional<LaufMitPaketen>> laufStaende = new HashMap<>();
    List<FaelligeZustellung> gueltig = new ArrayList<>();
    List<Long> entfallen = new ArrayList<>();
    for (FaelligeZustellung zustellung : faellig) {
      if (darfSehen(zustellung) && !istErledigt(zustellung, staende, laufStaende)) {
        gueltig.add(zustellung);
      } else {
        entfallen.add(zustellung.id());
      }
    }
    anlaesse.setzeEntfallen(entfallen);
    anlaesse.setzeEingeplant(gueltig.stream().map(FaelligeZustellung::id).toList());
    return plane(gueltig);
  }

  private boolean darfSehen(FaelligeZustellung zustellung) {
    return ausLauf(zustellung)
        ? permissions.hatNachtlaufZugriff(zustellung.userId(), zustellung.projectId())
        : permissions.hasPermission(
            zustellung.userId(), zustellung.projectId(), Permission.TICKET_READ);
  }

  private static boolean ausLauf(FaelligeZustellung zustellung) {
    return zustellung.laufId() != null;
  }

  private boolean istErledigt(
      FaelligeZustellung zustellung,
      Map<Long, KartenStandView> staende,
      Map<Long, Optional<LaufMitPaketen>> laufStaende) {
    if (zustellung.art() == AnlassArt.STOERUNG || zustellung.art() == AnlassArt.UEBERSICHT) {
      return false;
    }
    // Mutations-Altlast: äquivalent, HashMap.get(null) liefert ebenfalls null (#2093, 2026-11-10)
    KartenStandView karte = zustellung.cardId() == null ? null : staende.get(zustellung.cardId());
    if (ausLauf(zustellung)) {
      return karteIstWeg(zustellung, karte) || haltIstVorbei(zustellung, laufStaende);
    }
    return karteIstWeg(zustellung, karte) || !traegtLabelDesPaars(zustellung.art(), karte);
  }

  private static boolean karteIstWeg(
      FaelligeZustellung zustellung, @Nullable KartenStandView karte) {
    if (zustellung.cardId() == null) {
      return false;
    }
    return karte == null || karte.archiviert() || karte.imPapierkorb();
  }

  private static boolean traegtLabelDesPaars(AnlassArt art, @Nullable KartenStandView karte) {
    return karte != null
        && LABEL_PAARE.getOrDefault(art, List.of()).stream().anyMatch(karte.labels()::contains);
  }

  private boolean haltIstVorbei(
      FaelligeZustellung zustellung, Map<Long, Optional<LaufMitPaketen>> laufStaende) {
    long laufId = Objects.requireNonNull(zustellung.laufId());
    Optional<LaufMitPaketen> stand =
        laufStaende.computeIfAbsent(
            laufId, id -> laeufe.laufMitPaketen(zustellung.projectId(), id));
    return stand
        .map(lauf -> vonHandBeendet(lauf.lauf()) || !paketWartet(zustellung, lauf))
        .orElse(true);
  }

  /** Dieselbe Regel wie im Befund: Ein abgeschlossener Lauf gilt nie als von Hand beendet. */
  private static boolean vonHandBeendet(NightRun run) {
    return !run.complete() && run.closedAt() != null;
  }

  private boolean paketWartet(FaelligeZustellung zustellung, LaufMitPaketen lauf) {
    return lauf.pakete().stream()
        .filter(paket -> paket.errorClass() == NightRunErrorClass.AWAITING_DECISION)
        .anyMatch(
            paket ->
                Objects.equals(
                    karten.kartenId(zustellung.projectId(), paket.cardNumber()).orElse(null),
                    zustellung.cardId()));
  }

  /** Nicht Verschobenes geht einzeln, Verschobenes je Person in einer Nachricht (E7). */
  private int plane(List<FaelligeZustellung> gueltig) {
    Map<Long, List<Long>> sammlungen = new LinkedHashMap<>();
    int nachrichten = 0;
    for (FaelligeZustellung zustellung : gueltig) {
      if (zustellung.verschoben()) {
        sammlungen
            .computeIfAbsent(zustellung.userId(), id -> new ArrayList<>())
            .add(zustellung.id());
      } else {
        plane(zustellung.userId(), List.of(zustellung.id()));
        nachrichten++;
      }
    }
    sammlungen.forEach(this::plane);
    return nachrichten + sammlungen.size();
  }

  /** Die Kennungen kommen aufsteigend; die erste ist damit die kleinste (E9). */
  private void plane(long userId, List<Long> zustellungen) {
    outbox.schedule(
        new OutboxMessage(
            TYP,
            "benachrichtigung:" + userId + ":" + zustellungen.getFirst(),
            zustellungen.stream().map(String::valueOf).collect(Collectors.joining(","))));
  }
}
