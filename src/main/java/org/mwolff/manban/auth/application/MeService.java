package org.mwolff.manban.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.ColorScheme;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liefert die Selbstauskunft ({@code /api/me}): Benutzer + Plattform-Rolle + Mitgliedschaften.
 *
 * <p><strong>In der Vorschau „Ansicht als XY“</strong> (Issue #1921, Plan #1914 E5, E14) ist der
 * Principal schon XY; die Selbstauskunft beschreibt dann XY und nennt im Feld {@code vorschau}
 * Kennung, beide Namen und den Ablauf. Die Farbwahl ist eine persönliche Einstellung von XY und
 * wird nicht ausgeliefert; an ihrer Stelle steht die des Admins, der ja vor dem Bildschirm sitzt.
 */
@Service
public class MeService {

  private final AppUserRepository users;
  private final ProjectMembershipReader memberships;
  private final VorschauKontextLeser vorschau;

  public MeService(
      AppUserRepository users, ProjectMembershipReader memberships, VorschauKontextLeser vorschau) {
    this.users = users;
    this.memberships = memberships;
    this.vorschau = vorschau;
  }

  @Transactional(readOnly = true)
  public MeView load(long userId) {
    return toView(users.findById(userId).orElseThrow(UserNotFoundException::new));
  }

  /** Ändert den Anzeigenamen des angemeldeten Benutzers (getrimmt) und liefert die neue Sicht. */
  @Transactional
  public MeView updateDisplayName(long userId, String displayName) {
    AppUser user = users.findById(userId).orElseThrow(UserNotFoundException::new);
    return toView(users.save(user.withDisplayName(displayName.trim())));
  }

  /** Setzt die Farbwahl des angemeldeten Benutzers (Issue #1911) und liefert die neue Sicht. */
  @Transactional
  public MeView updateColorScheme(long userId, ColorScheme colorScheme) {
    AppUser user = users.findById(userId).orElseThrow(UserNotFoundException::new);
    return toView(users.save(user.withColorScheme(colorScheme)));
  }

  private MeView toView(AppUser user) {
    Optional<VorschauKontext> laufend =
        vorschau.aktuell().filter(k -> k.zielId() == user.requireId());
    if (laufend.isEmpty()) {
      return toView(user, user.colorScheme(), null);
    }
    VorschauKontext kontext = laufend.get();
    AppUser admin = users.findById(kontext.adminId()).orElseThrow(UserNotFoundException::new);
    return toView(
        user,
        admin.colorScheme(),
        new VorschauView(
            kontext.kennung(), user.displayName(), admin.displayName(), kontext.laeuftAbAm()));
  }

  private MeView toView(
      AppUser user, ColorScheme colorScheme, @Nullable VorschauView vorschauView) {
    return new MeView(
        user.requireId(),
        user.email(),
        user.displayName(),
        user.platformRole(),
        memberships.findByUserId(user.requireId()),
        colorScheme,
        vorschauView);
  }

  /** Selbstauskunft eines angemeldeten Benutzers. */
  @Schema(description = "Selbstauskunft des angemeldeten Benutzers.")
  public record MeView(
      @Schema(description = "Interne ID des Kontos.", example = "7") Long userId,
      @Schema(description = "E-Mail-Adresse, klein geschrieben.", example = "ada@example.org")
          String email,
      @Schema(description = "Anzeigename.", example = "Ada Lovelace") String displayName,
      @Schema(description = "Plattform-Rolle: ADMIN oder USER.", example = "USER")
          PlatformRole platformRole,
      @Schema(description = "Projekte, in denen der Benutzer Mitglied ist, mit seiner Rolle.")
          List<ProjectMembershipReader.Membership> memberships,
      @Schema(
              description =
                  "Gewähltes Erscheinungsbild der Oberfläche: SYSTEM folgt Browser bzw."
                      + " Betriebssystem, LIGHT und DARK legen es fest.",
              example = "SYSTEM")
          ColorScheme colorScheme,
      @Schema(
              description =
                  "Die laufende Vorschau „Ansicht als …“ dieser Anmeldung; null außerhalb einer"
                      + " Vorschau. In der Vorschau beschreibt die Selbstauskunft das angesehene"
                      + " Konto.")
          @Nullable VorschauView vorschau) {}

  /** Die laufende Vorschau „Ansicht als XY“ in der Selbstauskunft (Issue #1921, Plan #1914 E14). */
  @Schema(description = "Eine laufende, nur lesende Ansicht der App als ein anderes Konto.")
  public record VorschauView(
      @Schema(description = "Kennung der Vorschau, die ein Tab in der Kopfzeile X-Vorschau nennt.")
          String kennung,
      @Schema(description = "Anzeigename des angesehenen Kontos.", example = "Ada Lovelace")
          String zielName,
      @Schema(description = "Anzeigename des Admins, der die Vorschau gestartet hat.")
          String adminName,
      @Schema(description = "Zeitpunkt, an dem die Vorschau spätestens endet.")
          Instant laeuftAbAm) {}
}
