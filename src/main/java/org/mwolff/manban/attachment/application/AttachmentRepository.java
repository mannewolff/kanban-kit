package org.mwolff.manban.attachment.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.attachment.domain.Attachment;

/** Ausgehender Port für die Persistenz von Anhang-Metadaten. */
public interface AttachmentRepository {

    Attachment save(Attachment attachment);

    Optional<Attachment> findById(long id);

    List<Attachment> findByCardId(long cardId);

    long countByCardId(long cardId);

    void deleteById(long id);
}
