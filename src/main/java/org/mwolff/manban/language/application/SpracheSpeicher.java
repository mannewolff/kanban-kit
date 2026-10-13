package org.mwolff.manban.language.application;

import java.util.Optional;
import org.mwolff.manban.common.i18n.Sprache;

/** Port auf den gespeicherten Wert der Sprache der Installation (Issue #1639). */
public interface SpracheSpeicher {

  /** Der gespeicherte Wert; leer, solange keiner gesetzt ist oder er keine bekannte Sprache ist. */
  Optional<Sprache> lesen();

  /** Legt den Wert an oder überschreibt ihn. */
  void schreiben(Sprache sprache);
}
