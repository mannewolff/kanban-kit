package org.mwolff.manban.deployment.application;

import java.util.List;
import org.mwolff.manban.deployment.domain.Auslieferungsversuch;
import org.mwolff.manban.deployment.domain.GemeldeterCommit;

/**
 * Die Rohmeldungen eines Projekts für die {@code AuslieferungsErmittlung} (Issue #1833, Plan #1831
 * E12): alle Versuche und die Commits, deren Auslieferung ins Fenster fallen kann.
 */
public record Auslieferungsdaten(
    List<Auslieferungsversuch> versuche, List<GemeldeterCommit> commits) {

  public Auslieferungsdaten {
    versuche = List.copyOf(versuche);
    commits = List.copyOf(commits);
  }
}
