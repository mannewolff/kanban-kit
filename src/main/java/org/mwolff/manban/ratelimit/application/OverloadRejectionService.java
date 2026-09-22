package org.mwolff.manban.ratelimit.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Sicht des Plattform-Admins auf Abweisungen wegen Last (Issue #1003, Plan #995 E20): wann und
 * bei wem die Durchsatzbremse abgewiesen hat, je Person und Stunde.
 *
 * <p>Autorisiert wird über {@link PlatformAdminChecker} und nicht über {@code AdminService}: Dieses
 * Modul darf aus {@code auth.application} nur die Fassade sehen, und {@code AdminService} gehört
 * nicht dazu. Die Tabelle trägt nur die {@code userId}; die Namen kommen über {@link UserLookup}.
 */
@Service
public class OverloadRejectionService {

  /**
   * Obergrenze der ausgelieferten Zeilen. 90 Tage Aufbewahrung ergeben bei vielen Personen weit
   * mehr Stunden-Zeilen, als eine Übersicht zeigen kann; gefragt ist „wann zuletzt und bei wem".
   */
  static final int LIMIT = 500;

  private final OverloadRejectionReader reader;
  private final PlatformAdminChecker admins;
  private final UserLookup users;

  public OverloadRejectionService(
      OverloadRejectionReader reader, PlatformAdminChecker admins, UserLookup users) {
    this.reader = reader;
    this.admins = admins;
    this.users = users;
  }

  /**
   * Eine Zeile der Admin-Sicht.
   *
   * @param userId die abgewiesene Person
   * @param displayName ihr Anzeigename; {@code #<id>}, falls sie nicht mehr aufzufinden ist
   * @param hour Beginn der vollen Stunde
   * @param rejections Zahl der Abweisungen in dieser Stunde
   */
  public record RejectionView(long userId, String displayName, Instant hour, int rejections) {}

  /**
   * Die jüngsten Abweisungen, absteigend nach Stunde. Nur für Plattform-Admins.
   *
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist (403)
   */
  @Transactional(readOnly = true)
  public List<RejectionView> list(long actorUserId) {
    if (!admins.isPlatformAdmin(actorUserId)) {
      throw new AdminAccessDeniedException();
    }
    Map<Long, String> names = new HashMap<>();
    return reader.recent(LIMIT).stream()
        .map(
            row ->
                new RejectionView(
                    row.userId(),
                    names.computeIfAbsent(row.userId(), this::displayName),
                    row.hour(),
                    row.rejections()))
        .toList();
  }

  private String displayName(long userId) {
    return users.findById(userId).map(UserSummary::displayName).orElse("#" + userId);
  }
}
