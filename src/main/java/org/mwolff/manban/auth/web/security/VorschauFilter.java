package org.mwolff.manban.auth.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.mwolff.manban.auth.application.AdminVorschauService;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.auth.application.VorschauPruefung;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.common.i18n.Texte;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Macht aus einer Anfrage des Admins in der Vorschau „Ansicht als XY“ eine Anfrage als XY (Issue
 * #1919, Plan #1914 E1, E3, E8, E9). Läuft nach dem {@link SessionAuthenticationFilter} und vor dem
 * {@link DisabledUserGuardFilter}, nur unter {@code /api/}: Das Laden der Seite selbst trägt nie
 * die Kopfzeile und braucht keine Anmeldung.
 *
 * <ul>
 *   <li><strong>Bindung (E3):</strong> Das Cookie {@value
 *       SessionCookieManager#VORSCHAU_COOKIE_NAME} gilt nur zusammen mit einer gültigen Sitzung
 *       desselben Admins. Ohne Sitzung, bei einem Projekt-Token oder mit der Sitzung eines anderen
 *       Kontos gilt es nicht und wird gelöscht.
 *   <li><strong>Endfälle (E9):</strong> {@link AdminVorschauService#pruefe} stellt sie fest und
 *       schreibt das Ende; der Filter löscht das Cookie und weist mit 409 ab — auch auf den
 *       ausgenommenen Pfaden, damit dieser Zugriff nichts mehr aus der Vorschau zeigt.
 *   <li><strong>Principal-Tausch (E1):</strong> In einer gültigen Vorschau ist der Principal die Id
 *       von XY, die Authorities bleiben die der Sitzung, und die Details tragen den {@link
 *       VorschauKontext} mit dem handelnden Admin.
 *   <li><strong>Abgleich (E8):</strong> Die Kopfzeile {@value #HEADER} nennt, was der Tab zu sehen
 *       glaubt: die Kennung der Vorschau oder {@value #KEINE}; fehlt sie, gilt {@value #KEINE}.
 *       Weicht sie vom Stand am Server ab, antwortet der Filter 409 mit dem Problem-Typ {@value
 *       #PROBLEM_TYP} und führt nichts aus. Ausgenommen sind nur {@code GET} auf die
 *       Board-Ereignisse und auf den direkten Link eines Anhangs: Sie löst der Browser selbst aus,
 *       ohne Kopfzeile, und sie laufen mit dem Stand am Server.
 * </ul>
 */
@Component
public class VorschauFilter extends OncePerRequestFilter {

  /** Kopfzeile, mit der ein Tab nennt, welche Vorschau er zeigt. */
  public static final String HEADER = "X-Vorschau";

  /** Wert der Kopfzeile außerhalb einer Vorschau. */
  public static final String KEINE = "keine";

  /** Problem-Typ der Abweisung, an dem der Client das Neuladen erkennt. */
  public static final String PROBLEM_TYP = "vorschau-gewechselt";

  /** Schlüssel des Hinweises in der Abweisung. */
  static final String VORSCHAU_GEWECHSELT = "auth.vorschau-gewechselt";

  private static final String API = "/api/";

  /** Board-Ereignisse und direkter Anhang-Link, je mit genau einem Pfadsegment als Id. */
  private static final Pattern OHNE_ABGLEICH =
      Pattern.compile("/api/boards/[^/]+/events|/api/attachments/[^/]+");

  private final SessionCookieManager cookies;
  private final AdminVorschauService vorschauen;
  private final Texte texte;

  public VorschauFilter(
      SessionCookieManager cookies, AdminVorschauService vorschauen, Texte texte) {
    this.cookies = cookies;
    this.vorschauen = vorschauen;
    this.texte = texte;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !pfad(request).startsWith(API);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String stand = KEINE;
    Optional<VorschauPruefung> pruefung = cookies.readVorschau(request).map(this::pruefe);
    if (pruefung.isPresent()) {
      if (pruefung.get() instanceof VorschauPruefung.Gueltig gueltig) {
        stand = alsZiel(gueltig.vorschau());
      } else {
        loescheCookie(response);
      }
      if (pruefung.get() instanceof VorschauPruefung.Beendet) {
        rejectAsConflict(response);
        return;
      }
    }
    if (!stand.equals(Objects.requireNonNullElse(request.getHeader(HEADER), KEINE))
        && !ohneAbgleich(request)) {
      rejectAsConflict(response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  /** Prüft das Cookie gegen die Sitzung; ohne Sitzung gilt es nicht (E3). */
  private VorschauPruefung pruefe(VorschauCookie cookie) {
    return sitzung()
        .map(s -> vorschauen.pruefe(cookie.kennung(), cookie.geheimnis(), (Long) s.getPrincipal()))
        .orElseGet(VorschauPruefung.Unbekannt::new);
  }

  /** Die Anmeldung per Sitzungs-Cookie; ein Projekt-Token wird nie zur Vorschau (E3). */
  private static Optional<Authentication> sitzung() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .filter(a -> a.getPrincipal() instanceof Long)
        .filter(
            a ->
                a.getAuthorities().stream()
                    .anyMatch(g -> SessionAuthenticationFilter.AUTHORITY.equals(g.getAuthority())));
  }

  /** Macht XY zum Principal und legt den Admin als Kontext daneben; liefert die Kennung. */
  private static String alsZiel(AdminVorschau vorschau) {
    Authentication sitzung = sitzung().orElseThrow();
    var alsZiel =
        UsernamePasswordAuthenticationToken.authenticated(
            vorschau.zielId(), null, sitzung.getAuthorities());
    alsZiel.setDetails(
        new VorschauKontext(
            vorschau.kennung(),
            (Long) sitzung.getPrincipal(),
            vorschau.zielId(),
            vorschau.laeuftAbAm()));
    SecurityContextHolder.getContext().setAuthentication(alsZiel);
    return vorschau.kennung();
  }

  private static boolean ohneAbgleich(HttpServletRequest request) {
    return HttpMethod.GET.matches(request.getMethod())
        && OHNE_ABGLEICH.matcher(pfad(request)).matches();
  }

  private static String pfad(HttpServletRequest request) {
    return request.getRequestURI().substring(request.getContextPath().length());
  }

  private void loescheCookie(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearVorschau().toString());
  }

  private void rejectAsConflict(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.CONFLICT.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    // Literal nach dem Vorbild ApiAusprobierFilter.rejectAsForbidden, ohne Serialisierung: Der
    // Text stammt aus den Meldungsdateien und enthält weder Anführungszeichen noch Backslash.
    response
        .getWriter()
        .write(
            ("{\"type\":\"vorschau-gewechselt\",\"title\":\"Conflict\",\"status\":409,"
                    + "\"detail\":\"%s\"}")
                .formatted(texte.text(texte.spracheDerAnfrage(), VORSCHAU_GEWECHSELT)));
  }
}
