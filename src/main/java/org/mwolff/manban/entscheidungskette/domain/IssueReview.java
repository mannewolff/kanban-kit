package org.mwolff.manban.entscheidungskette.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.ReviewVorReady;

/** Das Glied Issue-Review am Paket (Issue #2116, Plan #2108 E18). */
final class IssueReview {

  private IssueReview() {}

  /**
   * Die Runden des Issue-Reviews. Ohne Meldung steht „nicht vorgesehen“ nur bei gemeldetem {@code
   * false}, das sich nach dem Zug nach Ready nicht geändert hat; sonst „nicht erfasst“.
   *
   * @param zugNachReady wann das Paket zur Umsetzung freigegeben wurde; {@code null} ohne Zug
   */
  static List<Glied> glieder(
      Kartenglieder kartenglieder,
      Karte paket,
      @Nullable ReviewVorReady meldung,
      @Nullable Instant zugNachReady) {
    List<Glied> runden = kartenglieder.issueReviews(paket);
    if (!runden.isEmpty()) {
      return runden;
    }
    return List.of(
        Glied.ohneAngaben(
            Gliedart.ISSUE_REVIEW, ohneMeldung(meldung, zugNachReady), Verweis.KEINER, Set.of()));
  }

  private static Zustand ohneMeldung(
      @Nullable ReviewVorReady meldung, @Nullable Instant zugNachReady) {
    if (meldung == null || meldung.verlangt()) {
      return Zustand.NICHT_ERFASST;
    }
    Instant geaendert = meldung.geaendertAm();
    boolean danachGeaendert =
        geaendert != null && zugNachReady != null && geaendert.isAfter(zugNachReady);
    return danachGeaendert ? Zustand.NICHT_ERFASST : Zustand.NICHT_VORGESEHEN;
  }
}
