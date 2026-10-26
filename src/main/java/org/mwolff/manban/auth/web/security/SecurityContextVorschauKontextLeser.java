package org.mwolff.manban.auth.web.security;

import java.util.Optional;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.auth.application.VorschauKontextLeser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Liest den {@link VorschauKontext} aus den Details der Authentifizierung (Issue #1918, Plan #1914
 * E1, E6). Dort legt ihn der Filter der Vorschau ab; außerhalb der Vorschau ist das Ergebnis leer.
 */
@Component
class SecurityContextVorschauKontextLeser implements VorschauKontextLeser {

  @Override
  public Optional<VorschauKontext> aktuell() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getDetails() instanceof VorschauKontext kontext) {
      return Optional.of(kontext);
    }
    return Optional.empty();
  }
}
