package org.mwolff.manban.push.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.push.domain.PushAbo;

/** Persistenz-Port der Push-Abos samt ihrer Board-Schalter (Issue #1997, Plan #1989 E13). */
public interface PushAboRepository {

  /** Das Abo zum Endpunkt, gleich welchem Konto es gehört. */
  Optional<PushAbo> findByEndpoint(String endpoint);

  /** Das Abo zur Kennung. */
  Optional<PushAbo> findById(long id);

  /** Alle Abos, nach Kennung geordnet — für den Meldejob (Issue #1998). */
  List<PushAbo> findAll();

  /** Legt das Abo an oder schreibt es samt seinen abgeschalteten Boards zurück. */
  PushAbo save(PushAbo abo);

  /** Löscht das Abo; seine Board-Schalter gehen mit. */
  void deleteById(long id);
}
