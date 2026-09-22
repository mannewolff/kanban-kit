package org.mwolff.manban.ratelimit.application;

import java.time.Instant;
import java.util.List;

/**
 * Lese-Port auf die abgelegten Abweisungen wegen Überlast (Issue #1003). Das Gegenstück zum
 * Schreibweg {@link RejectionRecorder}.
 */
@FunctionalInterface
public interface OverloadRejectionReader {

  /**
   * Eine Aggregatzeile.
   *
   * @param userId die abgewiesene Person
   * @param hour Beginn der vollen Stunde
   * @param rejections Zahl der Abweisungen in dieser Stunde
   */
  record StoredRejection(long userId, Instant hour, int rejections) {}

  /** Die jüngsten Zeilen, absteigend nach Stunde, höchstens {@code limit}. */
  List<StoredRejection> recent(int limit);
}
