package org.mwolff.manban.auth.web.security;

/**
 * Inhalt des Cookies {@value SessionCookieManager#VORSCHAU_COOKIE_NAME} (Issue #1919, Plan #1914
 * E2): öffentliche Kennung und Geheimnis der Vorschau „Ansicht als XY“. Im Cookie stehen beide als
 * {@code <kennung>.<geheimnis>}; beide stammen aus {@code SecureTokens} und enthalten keinen Punkt.
 *
 * @param kennung öffentliche Kennung der Vorschau
 * @param geheimnis Klartext des Geheimnisses; gespeichert ist allein sein Hash
 */
public record VorschauCookie(String kennung, String geheimnis) {

  /** Ohne Geheimnis, damit es in keinem Protokoll landet. */
  @Override
  public String toString() {
    return "VorschauCookie[kennung=" + kennung + "]";
  }
}
