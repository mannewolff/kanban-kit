package org.mwolff.manban.accesstoken.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.project.application.KitStrictnessWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Legt die Strenge-Stufe, die das Kit im Header {@code X-Kit-Strictness} meldet, am Projekt ab
 * (Issue #2014, Vertrag V1 aus Plan #2102 des Kits).
 *
 * <p>Eine Meldung gehört zu dem Projekt, an das das Zugriffs-Token gebunden ist ({@link
 * KanbanPrincipal#projectId()}). Anfragen per Session-Cookie und mit ungebundenem Token zählen
 * nicht. Der Wert wird getrimmt und kleingeschrieben; nur {@code light}, {@code normal} und {@code
 * strict} gelten, alles andere wird übergangen und der bisherige Wert bleibt. Ein fehlender Header
 * ändert ebenfalls nichts.
 *
 * <p>Die Anfrage läuft in jedem Fall unverändert weiter — auch wenn das Schreiben scheitert: Die
 * Stufe ist eine Anzeige, und ihretwegen darf keine Board-Arbeit des Kits fehlschlagen.
 *
 * <p>Ein Ort statt je Controller: Der Filter deckt die Kanban-Strecke und den Lauf-Ingest
 * gleichermaßen ab. Er läuft in der Security-Kette nach den Auth-Filtern und Sperrprüfungen (siehe
 * {@code SecurityConfig}) und braucht deren Authentifizierung.
 */
// PMD.AvoidCatchingGenericException: Die Stufe ist eine Anzeige, und ihretwegen darf keine Anfrage
// des Kits scheitern (Issue #2014) — jeder Fehler beim Ablegen, auch einer der Datenbank, wird
// protokolliert; der bisherige Wert bleibt stehen, und die nächste Anfrage meldet erneut.
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Component
public class KitStrictnessFilter extends OncePerRequestFilter {

  /** Header-Name aus Vertrag V1 (Plan #2102 des Kits), wörtlich. */
  public static final String HEADER = "X-Kit-Strictness";

  private static final Set<String> STUFEN = Set.of("light", "normal", "strict");
  private static final Logger LOG = LoggerFactory.getLogger(KitStrictnessFilter.class);

  private final KitStrictnessWriter writer;

  public KitStrictnessFilter(KitStrictnessWriter writer) {
    this.writer = writer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String stufe = stufe(request.getHeader(HEADER));
    Long projectId = gebundenesProjekt();
    if (stufe != null && projectId != null) {
      try {
        writer.reportStrictness(projectId, stufe);
      } catch (RuntimeException e) {
        LOG.warn("Strenge-Stufe fuer Projekt {} nicht abgelegt", projectId, e);
      }
    }
    filterChain.doFilter(request, response);
  }

  /** Der normalisierte Wert, wenn er eine der drei Stufen ist, sonst {@code null}. */
  private static @Nullable String stufe(@Nullable String header) {
    if (header == null) {
      return null;
    }
    String wert = header.trim().toLowerCase(Locale.ROOT);
    return STUFEN.contains(wert) ? wert : null;
  }

  /** Das Projekt eines gebundenen Zugriffs-Tokens der laufenden Anfrage, sonst {@code null}. */
  private static @Nullable Long gebundenesProjekt() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof KanbanPrincipal p) {
      return p.projectId();
    }
    return null;
  }
}
