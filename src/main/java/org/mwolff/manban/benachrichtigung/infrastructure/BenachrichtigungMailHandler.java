package org.mwolff.manban.benachrichtigung.infrastructure;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository;
import org.mwolff.manban.benachrichtigung.application.AnlassRepository.FaelligeZustellung;
import org.mwolff.manban.benachrichtigung.application.BenachrichtigungVersand;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Instanz;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Karte;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Mail;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Textquelle;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Uebersicht;
import org.mwolff.manban.benachrichtigung.domain.MailInhalte.Zustellung;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.KartenStandView;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.mwolff.manban.nightrun.application.BenachrichtigbareLaeufe;
import org.mwolff.manban.nightrun.application.NightRunProperties;
import org.mwolff.manban.nightrun.domain.Meldegrund;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunAbortKind;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.domain.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Stellt die von {@link BenachrichtigungVersand} eingeplanten Mails zu (Issue #2080, Plan #2070 E6,
 * E9, E11, E13, E16). Läuft nach dem Commit im Outbox-Worker.
 *
 * <p><b>Prüfung vor dem Senden</b> (E6). Zwischen Einplanen und Senden können Stunden liegen. Darum
 * prüft der Handler je Zustellung noch einmal Konto und Recht. Fehlt etwas, oder ist Projekt, Lauf
 * oder Karte verschwunden, entfällt die Zustellung still und gilt als erledigt; eine Wiederholung
 * änderte daran nichts.
 *
 * <p><b>Zustände.</b> Abweichend vom {@link OutboxHandler}-Vertrag schreibt der Handler auf die
 * Fachdatenbank, wie {@code LaufNachrichtHandler}: je Nachricht zwei Aktualisierungen nach Kennung,
 * die nicht scheitern, wenn die Zeile schon fehlt. Nur eingeplante Zeilen zählen; darum sendet eine
 * Wiederholung nach einem Erfolg nichts mehr. Scheitert der Versand, wirft er, und die Outbox
 * wiederholt.
 *
 * <p>Der Versand ist über {@code manban.mail.enabled} schaltbar. Ist er aus, bleibt nur eine
 * Protokollzeile, und die Zustellungen bleiben eingeplant: gesendet wurde ja nichts.
 */
// PMD.CouplingBetweenObjects: Der Handler ist die eine Stelle, an der Konto, Recht, Lauf und Karte
// zum Versandzeitpunkt zusammenkommen; aufgeteilt läge die Prüfung (E6) an zwei Orten.
// PMD.ExcessiveImports: Dieselbe Stelle braucht die Typen all dieser Module.
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.ExcessiveImports"})
@Component
class BenachrichtigungMailHandler implements OutboxHandler {

  private static final Logger log = LoggerFactory.getLogger(BenachrichtigungMailHandler.class);

  private final AnlassRepository anlaesse;
  private final UserLookup users;
  private final PermissionChecker permissions;
  private final ProjectService projects;
  private final CardRunQueryService karten;
  private final BenachrichtigbareLaeufe laeufe;
  private final NightRunProperties nightRun;
  private final AuthProperties auth;
  private final Texte texte;
  private final JavaMailSender mailSender;
  private final Clock clock;
  private final boolean mailEnabled;
  private final String from;
  private final String productName;

  BenachrichtigungMailHandler(
      AnlassRepository anlaesse,
      UserLookup users,
      PermissionChecker permissions,
      ProjectService projects,
      CardRunQueryService karten,
      BenachrichtigbareLaeufe laeufe,
      NightRunProperties nightRun,
      AuthProperties auth,
      Texte texte,
      JavaMailSender mailSender,
      Clock clock,
      @Value("${manban.mail.enabled:false}") boolean mailEnabled,
      @Value("${manban.mail.from:no-reply@kanban-kit.local}") String from,
      @Value("${manban.mail.product-name:kanban-kit}") String productName) {
    this.anlaesse = anlaesse;
    this.users = users;
    this.permissions = permissions;
    this.projects = projects;
    this.karten = karten;
    this.laeufe = laeufe;
    this.nightRun = nightRun;
    this.auth = auth;
    this.texte = texte;
    this.mailSender = mailSender;
    this.clock = clock;
    this.mailEnabled = mailEnabled;
    this.from = from;
    this.productName = productName;
  }

  @Override
  public String eventType() {
    return BenachrichtigungVersand.TYP;
  }

  @Override
  public void handle(String payload) {
    if (!mailEnabled) {
      log.info("[DEV] Benachrichtigung nicht versandt, Mailversand ist aus: {}", payload);
      return;
    }
    List<FaelligeZustellung> zustellungen =
        anlaesse.eingeplanteZustellungen(
            Arrays.stream(payload.split(",")).map(Long::valueOf).toList());
    if (zustellungen.isEmpty()) {
      return;
    }
    long userId = zustellungen.getFirst().userId();
    Optional<String> adresse =
        users.findById(userId).filter(user -> users.istAktiv(userId)).map(UserSummary::email);
    if (adresse.isEmpty()) {
      anlaesse.setzeEingeplanteEntfallen(
          zustellungen.stream().map(FaelligeZustellung::id).toList());
      log.info("Benachrichtigung an Konto {} entfällt: Konto nicht aktiv", userId);
      return;
    }
    zustellen(zustellungen, adresse.get());
  }

  private void zustellen(List<FaelligeZustellung> zustellungen, String adresse) {
    Map<Long, KartenStandView> staende = staende(zustellungen);
    Instant jetzt = clock.instant();
    List<Long> zuzustellen = new ArrayList<>();
    List<Long> entfallen = new ArrayList<>();
    List<Zustellung> inhalte = new ArrayList<>();
    for (FaelligeZustellung zustellung : zustellungen) {
      Optional<Zustellung> inhalt =
          darfSehen(zustellung) ? inhalt(zustellung, staende, jetzt) : Optional.empty();
      if (inhalt.isPresent()) {
        zuzustellen.add(zustellung.id());
        inhalte.add(inhalt.get());
      } else {
        entfallen.add(zustellung.id());
      }
    }
    anlaesse.setzeEingeplanteEntfallen(entfallen);
    if (inhalte.isEmpty()) {
      return;
    }
    senden(adresse, mail(inhalte, zustellungen.getFirst().verschoben()));
    anlaesse.setzeZugestellt(zuzustellen);
  }

  private Map<Long, KartenStandView> staende(List<FaelligeZustellung> zustellungen) {
    return karten
        .kartenStand(
            zustellungen.stream()
                .map(FaelligeZustellung::cardId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
        .stream()
        .collect(Collectors.toMap(KartenStandView::id, Function.identity()));
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

  private Optional<Zustellung> inhalt(
      FaelligeZustellung zustellung, Map<Long, KartenStandView> staende, Instant jetzt) {
    boolean ausLauf = ausLauf(zustellung);
    KartenStandView stand = zustellung.cardId() == null ? null : staende.get(zustellung.cardId());
    if (!ausLauf && stand == null) {
      return Optional.empty();
    }
    Karte karte = stand == null ? null : new Karte(stand.number(), stand.title());
    AnlassArt art = zustellung.art();
    long projectId = zustellung.projectId();
    Optional<String> name = projects.findName(projectId);
    if (art != AnlassArt.UEBERSICHT) {
      return name.map(n -> new Zustellung(art, projectId, n, ausLauf, karte, null));
    }
    return name.flatMap(
        n ->
            uebersicht(zustellung, jetzt)
                .map(inhalt -> new Zustellung(art, projectId, n, ausLauf, karte, inhalt)));
  }

  private Optional<Uebersicht> uebersicht(FaelligeZustellung zustellung, Instant jetzt) {
    long laufId = Objects.requireNonNull(zustellung.laufId());
    return laeufe
        .laufMitPaketen(zustellung.projectId(), laufId)
        .map(lauf -> uebersicht(lauf.lauf(), lauf.pakete(), jetzt));
  }

  /**
   * Der Abbruchgrund bleibt draußen: Er ist ein Freitext des Kits. Verstummt heißt, der Lauf blieb
   * seine Abmeldung schuldig oder der Wächter hat sie nachgeholt (E13).
   */
  private Uebersicht uebersicht(NightRun run, List<NightRunItem> pakete, Instant jetzt) {
    Duration stilleFrist = nightRun.stilleFrist();
    Verdict ausgang =
        NightRunOutcome.of(
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
    boolean verstummt =
        ausgang == Verdict.FAILED
            && (!run.complete() || run.abortKind() == NightRunAbortKind.SILENCED);
    boolean stoerung =
        ausgang == Verdict.FAILED
            || Meldegrund.anlaesse(run, pakete, jetzt, stilleFrist).stoerung() != null;
    List<Karte> wartend =
        pakete.stream()
            .filter(paket -> paket.errorClass() == NightRunErrorClass.AWAITING_DECISION)
            .map(paket -> new Karte(paket.cardNumber(), paket.title()))
            .toList();
    return new Uebersicht(ausgang, verstummt, kosten(run.usage()), wartend, stoerung);
  }

  private static @Nullable BigDecimal kosten(@Nullable NightRunUsage usage) {
    return usage == null ? null : usage.costUsd();
  }

  /** Die Sprache bestimmt der Handler beim Versand (E11); Verschobenes kommt als Sammelmail. */
  private Mail mail(List<Zustellung> inhalte, boolean verschoben) {
    Sprache sprache = texte.eingestellte();
    Textquelle quelle = (schluessel, argumente) -> texte.text(sprache, schluessel, argumente);
    Instanz instanz = new Instanz(productName, auth.baseUrl());
    return verschoben
        ? MailInhalte.sammlung(quelle, instanz, inhalte)
        : MailInhalte.einzeln(quelle, instanz, inhalte.getFirst());
  }

  private void senden(String adresse, Mail mail) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(adresse);
    message.setSubject(mail.betreff());
    message.setText(mail.text());
    mailSender.send(message);
  }
}
