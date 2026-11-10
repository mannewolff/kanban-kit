package org.mwolff.manban.accesstoken.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.accesstoken.domain.AccessToken;

/**
 * Ausgehender Port für die Persistenz von API-Zugriffstokens.
 *
 * <p>{@link #save} trägt ausschließlich das Anlegen. Nutzung und Widerruf haben je eine eigene,
 * spaltenscharfe Operation, damit keins von beiden das andere überschreibt (Issue #878).
 */
public interface AccessTokenRepository {

  AccessToken save(AccessToken token);

  Optional<AccessToken> findById(long id);

  List<AccessToken> findByUserId(long userId);

  Optional<AccessToken> findByTokenHash(String tokenHash);

  /** Der wirksame Zugang der App am Board; leer, wenn keiner besteht (Plan #2100, E6). */
  Optional<AccessToken> findWirksamenAppZugang(long boardId);

  /** Die wirksamen Zugänge der App dieser Boards; je Board höchstens einer (Plan #2100, E6). */
  List<AccessToken> findWirksameAppZugaenge(Collection<Long> boardIds);

  /**
   * Stempelt die Nutzung, sofern das Token nicht widerrufen ist — schreibt nur {@code lastUsedAt},
   * und zwar in einer <strong>eigenen, kurzen</strong> Transaktion (Issue #997).
   *
   * <p>Der {@code UPDATE} nimmt einen Zeilen-Lock auf der Token-Zeile, und die gleichzeitigen
   * Befehle einer Person teilen sich genau diese eine Zeile. Lief der Stempel in der
   * Auflösungstransaktion mit, bestand der Lock bis zu deren Ende — die Befehle konnten strukturell
   * nicht parallel laufen. In einer eigenen Transaktion ist er nach dem {@code UPDATE} wieder frei.
   */
  void touchLastUsedAtInOwnTransaction(long id, Instant when);

  /** Widerruft das Token — schreibt nur {@code revoked}; idempotent. */
  void markRevoked(long id);

  /**
   * Widerruft das Token, sofern es noch nicht widerrufen ist. Anders als {@link #markRevoked} sagt
   * die Antwort, ob dieser Aufruf es war: Von zwei gleichzeitigen Aufrufen bekommt genau einer
   * {@code true}.
   */
  boolean markRevokedIfActive(long id);

  /**
   * Widerruft das Token in einer <strong>eigenen, kurzen</strong> Transaktion, nach dem Muster von
   * {@link #touchLastUsedAtInOwnTransaction}: für die Auflösung, die ohne umschließende Transaktion
   * läuft (Issue #997, #2104).
   */
  void markRevokedInOwnTransaction(long id);

  /**
   * Serialisiert Ausgabe und Entzug der Zugänge der App dieses Inhabers bis zum Ende der laufenden
   * Transaktion (Issue #2122). Anlegen und Erneuern nehmen sie vor ihrer Rechteprüfung, der
   * Widerruf bei Rollenverlust oder Kontosperre vor seinem Sammelwiderruf. So sieht entweder die
   * Ausgabe den committeten Verlust, oder der Sammelwiderruf sieht das committete neue Token — eine
   * Zeile, die zwischen beiden entsteht, gibt es nicht mehr. Verlangt eine laufende Transaktion.
   */
  void sperreAppZugangAusgabe(long userId);

  /**
   * Widerruft die wirksamen Zugänge der App dieses Inhabers in allen Projekten (Plan #2100, E4).
   */
  void markAppZugaengeRevoked(long userId);

  /** Widerruft die wirksamen Zugänge der App dieses Inhabers in dem Projekt (Plan #2100, E4). */
  void markAppZugaengeRevoked(long userId, long projectId);
}
