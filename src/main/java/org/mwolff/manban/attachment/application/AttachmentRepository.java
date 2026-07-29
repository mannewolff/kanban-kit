package org.mwolff.manban.attachment.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.attachment.domain.Attachment;

/** Ausgehender Port für die Persistenz von Anhang-Metadaten. */
public interface AttachmentRepository {

  Attachment save(Attachment attachment);

  Optional<Attachment> findById(long id);

  List<Attachment> findByCardId(long cardId);

  /** Anhänge aller übergebenen Karten — für die Purge-Kaskade (Issue #503). */
  List<Attachment> findByCardIds(Collection<Long> cardIds);

  long countByCardId(long cardId);

  /** Alle Object-Keys — für den Abgleich mit dem Objektspeicher (Issue #503). */
  List<String> findAllObjectKeys();

  void deleteById(long id);
}
