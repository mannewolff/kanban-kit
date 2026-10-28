package org.mwolff.manban.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * Der Pfad eines Aufrufs innerhalb der Anwendung, so wie das Routing ihn sieht (Issue #1973).
 *
 * <p><b>Warum nicht {@code getRequestURI()}:</b> Die rohe URI ist noch kodiert. Spring Security und
 * MVC entscheiden dagegen auf dem dekodierten Pfad — {@code /api/auth/code/%76erify} erreicht
 * denselben Handler wie {@code /api/auth/code/verify}. Ein Filter, der die rohe URI vergleicht,
 * sieht darin einen fremden Pfad und lässt den Aufruf ungeprüft durch.
 *
 * <p>Geliefert wird der Pfad ohne Context-Path, dekodiert, ohne Matrix-Parameter ({@code ;…}) und
 * mit zusammengefassten doppelten Schrägstrichen — die Sicht von {@link UrlPathHelper}, auf die
 * auch Springs Pfadauflösung zurückgreift.
 */
public final class Anwendungspfad {

  private Anwendungspfad() {}

  /** Der dekodierte und normalisierte Pfad des Aufrufs, ohne Context-Path. */
  public static String von(HttpServletRequest request) {
    return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
  }
}
