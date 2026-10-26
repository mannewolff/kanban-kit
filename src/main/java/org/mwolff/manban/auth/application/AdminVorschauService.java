package org.mwolff.manban.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.domain.VorschauEndgrund;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starten, Prüfen, Beenden und Protokoll der nur lesenden „Ansicht als XY“ (Issue #1917, Plan #1914
 * E2, E3, E9, E10, E11, E12).
 *
 * <p><strong>Geheimnis:</strong> Das Cookie trägt eine öffentliche Kennung und ein Geheimnis aus
 * {@link SecureTokens}. Gespeichert wird nur dessen SHA-256; der Vergleich läuft in konstanter
 * Zeit.
 *
 * <p><strong>Bindung an die Anmeldung (E3):</strong> Die Vorschau gilt nur für den Admin, der sie
 * gestartet hat, und nur, solange seine Sitzungs-Generation dieselbe ist wie beim Start. Der Dienst
 * liest die Generation selbst über {@link SessionGenerations}, statt sie vom Aufrufer zu nehmen:
 * Eine gültige Sitzung trägt ohnehin die aktuelle Generation ({@code SignedSessionTokens}), und so
 * kann kein Aufrufer einen falschen Wert hineinreichen.
 *
 * <p><strong>Endfälle (E9):</strong> {@link #pruefe} stellt sie fest, schreibt das Ende und liefert
 * den Grund; der Filter eines späteren Pakets ruft nur diese Methode. Das Schreiben ist atomar —
 * stellen zwei Tabs gleichzeitig ein Ende fest, gewinnt genau einer, und beide melden den
 * gespeicherten Grund.
 */
@Service
public class AdminVorschauService {

  /** Längste Dauer einer Vorschau (AK 7 aus #1910). */
  static final Duration DAUER = Duration.ofMinutes(30);

  private final AppUserRepository users;
  private final SessionGenerations generations;
  private final AdminVorschauRepository vorschauen;
  private final Clock clock;

  public AdminVorschauService(
      AppUserRepository users,
      SessionGenerations generations,
      AdminVorschauRepository vorschauen,
      Clock clock) {
    this.users = users;
    this.generations = generations;
    this.vorschauen = vorschauen;
    this.clock = clock;
  }

  /**
   * Startet eine Vorschau als das Zielkonto (E10, E11). Offene Vorschauen desselben Admins aus
   * anderen Anmeldungen bleiben unberührt.
   *
   * @param adminId angemeldeter Admin
   * @param zielId Konto, als das die App gezeigt werden soll; gesperrt oder wartend ist erlaubt
   * @param vorschauCookieVorhanden ob die Anfrage schon ein Vorschau-Cookie mitbringt
   * @throws AdminAccessDeniedException wenn der Aufrufer kein aktiver Plattform-Admin ist
   * @throws VorschauLaeuftBereitsException wenn ein Vorschau-Cookie mitkommt
   * @throws VorschauZielUnzulaessigException als das eigene Konto oder als ein Plattform-Admin
   * @throws UserNotFoundException wenn es das Zielkonto nicht gibt
   */
  @Transactional
  public VorschauStart starte(long adminId, long zielId, boolean vorschauCookieVorhanden) {
    AppUser admin =
        users
            .findById(adminId)
            .filter(AdminVorschauService::aktiverAdmin)
            .orElseThrow(AdminAccessDeniedException::new);
    long generation = generations.current(adminId).orElseThrow(AdminAccessDeniedException::new);
    if (vorschauCookieVorhanden) {
      throw new VorschauLaeuftBereitsException();
    }
    if (adminId == zielId) {
      throw new VorschauZielUnzulaessigException();
    }
    AppUser ziel = users.findById(zielId).orElseThrow(UserNotFoundException::new);
    if (ziel.platformRole() == PlatformRole.ADMIN) {
      throw new VorschauZielUnzulaessigException();
    }
    String geheimnis = SecureTokens.newToken();
    Instant now = clock.instant();
    AdminVorschau vorschau =
        vorschauen.save(
            new AdminVorschau(
                null,
                SecureTokens.newToken(),
                SecureTokens.sha256Hex(geheimnis),
                adminId,
                admin.displayName(),
                zielId,
                generation,
                now,
                now.plus(DAUER),
                null,
                null));
    return new VorschauStart(vorschau, geheimnis);
  }

  /**
   * Prüft die Vorschau einer Anfrage (E3, E9). Trifft ein Endfall zu, wird das Ende geschrieben.
   *
   * @param kennung Kennung aus dem Cookie
   * @param geheimnis Geheimnis aus dem Cookie
   * @param adminId Konto der Sitzung, mit der das Cookie kam
   */
  @Transactional
  public VorschauPruefung pruefe(String kennung, String geheimnis, long adminId) {
    Optional<AdminVorschau> gefunden = passende(kennung, geheimnis, adminId);
    if (gefunden.isEmpty()) {
      return new VorschauPruefung.Unbekannt();
    }
    AdminVorschau vorschau = gefunden.get();
    VorschauEndgrund schonBeendet = vorschau.endgrund();
    if (schonBeendet != null) {
      return new VorschauPruefung.Beendet(schonBeendet);
    }
    Instant now = clock.instant();
    if (!now.isBefore(vorschau.laeuftAbAm())) {
      return beendet(vorschau, vorschau.laeuftAbAm(), VorschauEndgrund.ABLAUF);
    }
    Optional<VorschauEndgrund> endfall = endfall(vorschau, adminId);
    if (endfall.isPresent()) {
      return beendet(vorschau, now, endfall.get());
    }
    return new VorschauPruefung.Gueltig(vorschau);
  }

  /** „Beenden“ im Balken: beendet die Vorschau mit {@link VorschauEndgrund#BEENDET}. */
  @Transactional
  public void beende(String kennung, String geheimnis, long adminId) {
    beendeMit(kennung, geheimnis, adminId, VorschauEndgrund.BEENDET);
  }

  /** Abmelden: beendet die Vorschau mit {@link VorschauEndgrund#ABMELDUNG}. */
  @Transactional
  public void beendeBeimAbmelden(String kennung, String geheimnis, long adminId) {
    beendeMit(kennung, geheimnis, adminId, VorschauEndgrund.ABMELDUNG);
  }

  /**
   * Die Einträge zu einem Zielkonto, die zuletzt gestartete zuerst (E12). Vorher werden offene,
   * aber abgelaufene Einträge mit ihrem Ablaufzeitpunkt und {@link VorschauEndgrund#ABLAUF}
   * nachgeschrieben — so steht keine abgelaufene Vorschau als laufend im Protokoll.
   */
  @Transactional
  public List<AdminVorschau> protokoll(long zielId) {
    vorschauen.beendeAbgelaufene(zielId, clock.instant());
    return vorschauen.findByZiel(zielId);
  }

  private void beendeMit(
      String kennung, String geheimnis, long adminId, VorschauEndgrund endgrund) {
    passende(kennung, geheimnis, adminId)
        .filter(v -> !v.beendet())
        .ifPresent(v -> vorschauen.beende(v.requireId(), clock.instant(), endgrund));
  }

  /** Die Vorschau zur Kennung, wenn Geheimnis und Admin passen. */
  private Optional<AdminVorschau> passende(String kennung, String geheimnis, long adminId) {
    return vorschauen
        .findByKennung(kennung)
        .filter(v -> geheimnisPasst(v, geheimnis))
        .filter(v -> Long.valueOf(adminId).equals(v.adminId()));
  }

  private static boolean geheimnisPasst(AdminVorschau vorschau, String geheimnis) {
    return MessageDigest.isEqual(
        vorschau.geheimnisHash().getBytes(StandardCharsets.UTF_8),
        SecureTokens.sha256Hex(geheimnis).getBytes(StandardCharsets.UTF_8));
  }

  /** Der Endfall aus E9 außer dem Ablauf, der zutrifft — der Admin zuerst, dann das Ziel. */
  private Optional<VorschauEndgrund> endfall(AdminVorschau vorschau, long adminId) {
    Optional<AppUser> admin = users.findById(adminId);
    if (admin.isEmpty()) {
      return Optional.of(VorschauEndgrund.SITZUNG_UNGUELTIG);
    }
    if (admin.get().platformRole() != PlatformRole.ADMIN) {
      return Optional.of(VorschauEndgrund.ROLLE_ENTZOGEN);
    }
    if (admin.get().disabled()) {
      return Optional.of(VorschauEndgrund.ADMIN_GESPERRT);
    }
    if (!generations.current(adminId).equals(OptionalLong.of(vorschau.sitzungsGeneration()))) {
      return Optional.of(VorschauEndgrund.SITZUNG_UNGUELTIG);
    }
    Optional<AppUser> ziel = users.findById(vorschau.zielId());
    if (ziel.isEmpty()) {
      return Optional.of(VorschauEndgrund.ZIEL_FEHLT);
    }
    if (ziel.get().platformRole() == PlatformRole.ADMIN) {
      return Optional.of(VorschauEndgrund.ZIEL_IST_ADMIN);
    }
    return Optional.empty();
  }

  /** Schreibt das Ende; hat ein gleichzeitiger Aufruf gewonnen, gilt dessen Grund. */
  private VorschauPruefung beendet(AdminVorschau vorschau, Instant am, VorschauEndgrund endgrund) {
    if (vorschauen.beende(vorschau.requireId(), am, endgrund)) {
      return new VorschauPruefung.Beendet(endgrund);
    }
    VorschauEndgrund gespeichert =
        vorschauen.findByKennung(vorschau.kennung()).map(AdminVorschau::endgrund).orElse(endgrund);
    return new VorschauPruefung.Beendet(gespeichert);
  }

  private static boolean aktiverAdmin(AppUser user) {
    return user.platformRole() == PlatformRole.ADMIN && !user.disabled();
  }
}
