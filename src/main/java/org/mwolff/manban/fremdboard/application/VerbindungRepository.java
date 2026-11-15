package org.mwolff.manban.fremdboard.application;

import java.util.Optional;
import org.mwolff.manban.fremdboard.domain.Verbindung;

/**
 * Port der gespeicherten Verbindung je Projekt (Issue #2156, Plan #2152). Prüft keine Rechte; das
 * tut der Anwendungsfall, der ihn ruft.
 */
public interface VerbindungRepository {

  /** Legt die Verbindung des Projekts an oder ersetzt sie. */
  void speichere(Verbindung verbindung);

  /** Die Verbindung des Projekts; leer, wenn das Projekt kein fremdes Board hat. */
  Optional<Verbindung> finde(long projectId);

  /** Ob das Projekt eine Verbindung hat. */
  boolean existiert(long projectId);
}
