package org.mwolff.manban.language.application;

import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.common.i18n.Sprache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die installationsweite Sprache lesen und — nur als Plattform-Admin — setzen (Issue #1639, Plan
 * #1638 A2/E3).
 *
 * <p>Ohne gespeicherten Wert gilt Deutsch; neue und bestehende Installationen bleiben so ohne
 * Migration auf Deutsch. Gelesen wird je Aufruf aus dem {@link SpracheSpeicher}, ohne Cache: Eine
 * Umstellung wirkt beim nächsten Laden.
 */
@Service
public class SpracheService {

  private final SpracheSpeicher speicher;
  private final PlatformAdminChecker platformAdminChecker;

  public SpracheService(SpracheSpeicher speicher, PlatformAdminChecker platformAdminChecker) {
    this.speicher = speicher;
    this.platformAdminChecker = platformAdminChecker;
  }

  /** Die geltende Sprache; ohne gespeicherten Wert {@link Sprache#DE}. Ohne Rechteprüfung. */
  @Transactional(readOnly = true)
  public Sprache lesen() {
    return speicher.lesen().orElse(Sprache.DE);
  }

  /**
   * Setzt die Sprache der Installation.
   *
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist
   */
  @Transactional
  public Sprache setzen(long actorUserId, Sprache sprache) {
    if (!platformAdminChecker.isPlatformAdmin(actorUserId)) {
      throw new AdminAccessDeniedException();
    }
    speicher.schreiben(sprache);
    return sprache;
  }
}
