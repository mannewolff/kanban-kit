package org.mwolff.manban.auth.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.mwolff.manban.auth.application.VorschauKontextLeser;
import org.mwolff.manban.common.i18n.Texte;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hält die Vorschau „Ansicht als XY“ nur lesend und Persönliches von XY verborgen (Issue #1920,
 * Plan #1914 E4, E5). Läuft nach dem {@link DisabledUserGuardFilter} und erkennt die Vorschau am
 * Kontext, den der {@link VorschauFilter} ablegt; außerhalb der Vorschau reicht er alles durch.
 *
 * <ul>
 *   <li><strong>Nur lesend (E4):</strong> Jede Methode außer {@code GET}, {@code HEAD} und {@code
 *       OPTIONS} wird mit 403 und dem Problem-Typ {@value #NUR_LESEND} abgewiesen. Ausgenommen sind
 *       allein {@code POST /api/auth/logout} und {@code DELETE /api/vorschau}, jeweils genau dieser
 *       Pfad.
 *   <li><strong>Persönliches (E5):</strong> Auch lesende Aufrufe auf {@code /api/access-tokens}
 *       samt Unterpfaden und auf {@code /api/me/vorschau-protokoll} werden abgewiesen, mit dem
 *       Problem-Typ {@value #PERSOENLICH}.
 * </ul>
 *
 * <p>Die Antwort schreibt der Filter selbst, nach dem Muster von {@code
 * ApiAusprobierFilter.rejectAsForbidden}: Vor dem MVC-Stack greift kein {@code @ControllerAdvice}.
 */
@Component
public class VorschauSchreibsperreFilter extends OncePerRequestFilter {

  /** Problem-Typ der Abweisung eines verändernden Aufrufs in der Vorschau. */
  public static final String NUR_LESEND = "vorschau-nur-lesend";

  /** Problem-Typ der Abweisung eines persönlichen Bereichs in der Vorschau. */
  public static final String PERSOENLICH = "vorschau-persoenlich";

  /** Schlüssel des Hinweises zu {@value #NUR_LESEND}. */
  static final String NUR_LESEND_TEXT = "auth.vorschau-nur-lesend";

  /** Schlüssel des Hinweises zu {@value #PERSOENLICH}. */
  static final String PERSOENLICH_TEXT = "auth.vorschau-persoenlich";

  private static final Set<String> LESEND =
      Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

  private static final String ACCESS_TOKENS = "/api/access-tokens";
  private static final String VORSCHAU_PROTOKOLL = "/api/me/vorschau-protokoll";

  private final VorschauKontextLeser kontext;
  private final Texte texte;

  public VorschauSchreibsperreFilter(VorschauKontextLeser kontext, Texte texte) {
    this.kontext = kontext;
    this.texte = texte;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (kontext.aktuell().isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }
    String methode = request.getMethod();
    String pfad = request.getRequestURI().substring(request.getContextPath().length());
    if (!LESEND.contains(methode) && !ausgenommen(methode, pfad)) {
      rejectAsForbidden(response, NUR_LESEND, NUR_LESEND_TEXT);
      return;
    }
    if (persoenlicherPfad(pfad)) {
      rejectAsForbidden(response, PERSOENLICH, PERSOENLICH_TEXT);
      return;
    }
    filterChain.doFilter(request, response);
  }

  /** Abmelden und Beenden der Vorschau wirken auch in ihr (AK 7). */
  private static boolean ausgenommen(String methode, String pfad) {
    return (HttpMethod.POST.matches(methode) && "/api/auth/logout".equals(pfad))
        || (HttpMethod.DELETE.matches(methode) && "/api/vorschau".equals(pfad));
  }

  private static boolean persoenlicherPfad(String pfad) {
    return unter(pfad, ACCESS_TOKENS) || unter(pfad, VORSCHAU_PROTOKOLL);
  }

  /** Der Pfad selbst oder einer darunter — nicht ein Pfad, der nur mit demselben Text beginnt. */
  private static boolean unter(String pfad, String basis) {
    return pfad.equals(basis) || pfad.startsWith(basis + "/");
  }

  private void rejectAsForbidden(HttpServletResponse response, String typ, String schluessel)
      throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    // Literal nach dem Vorbild ApiAusprobierFilter.rejectAsForbidden, ohne Serialisierung: Typ
    // und Text enthalten weder Anführungszeichen noch Backslash.
    response
        .getWriter()
        .write(
            "{\"type\":\"%s\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"%s\"}"
                .formatted(typ, texte.text(texte.spracheDerAnfrage(), schluessel)));
  }
}
