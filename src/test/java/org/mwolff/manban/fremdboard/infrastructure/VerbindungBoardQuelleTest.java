package org.mwolff.manban.fremdboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.fremdboard.application.FremdBoardStand;
import org.mwolff.manban.fremdboard.application.VerbindungRepository;
import org.mwolff.manban.fremdboard.domain.Verbindung;

/** Fremd ist ein Board genau dann, wenn sein Projekt eine Verbindung hat (Issue #2156, A10). */
class VerbindungBoardQuelleTest {

  private static final long MIT_VERBINDUNG = 3L;
  private static final long OHNE_VERBINDUNG = 4L;

  private final VerbindungBoardQuelle quelle =
      new VerbindungBoardQuelle(new Verbindungen(Set.of(MIT_VERBINDUNG)));

  @Test
  void einProjektMitVerbindungHatEinFremdesBoard() {
    assertThat(quelle.istFremd(MIT_VERBINDUNG)).isTrue();
  }

  @Test
  void einProjektOhneVerbindungHatSeinEigenesBoard() {
    assertThat(quelle.istFremd(OHNE_VERBINDUNG)).isFalse();
  }

  /**
   * Kennt nur die Projekte mit Verbindung; Speichern, Lesen und Abgleich braucht dieser Test nicht.
   */
  private record Verbindungen(Set<Long> projekte) implements VerbindungRepository {

    @Override
    public void speichere(Verbindung verbindung) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Verbindung> finde(long projectId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean existiert(long projectId) {
      return projekte.contains(projectId);
    }

    @Override
    public Optional<FremdBoardStand> stand(long projectId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Long> faellige(Instant grenze) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean beanspruchen(long projectId, @Nullable Instant bisher, Instant jetzt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void abgleichGelungen(
        long projectId, Instant beansprucht, @Nullable Instant vollabgleichAm) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void abgleichGescheitert(
        long projectId, Instant beansprucht, @Nullable Instant bisher, String fehler) {
      throw new UnsupportedOperationException();
    }
  }
}
