package org.mwolff.manban.attachment.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.attachment.application.AttachmentRepository;
import org.mwolff.manban.attachment.domain.Attachment;
import org.springframework.stereotype.Component;

/** Adapter des {@link AttachmentRepository}-Ports auf Spring Data JPA. */
@Component
class AttachmentRepositoryAdapter implements AttachmentRepository {

    private final AttachmentJpaRepository jpa;

    AttachmentRepositoryAdapter(AttachmentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Attachment save(Attachment attachment) {
        return toDomain(jpa.save(toEntity(attachment)));
    }

    @Override
    public Optional<Attachment> findById(long id) {
        return jpa.findById(id).map(AttachmentRepositoryAdapter::toDomain);
    }

    @Override
    public List<Attachment> findByCardId(long cardId) {
        return jpa.findByCardIdOrderByCreatedAt(cardId).stream().map(AttachmentRepositoryAdapter::toDomain).toList();
    }

    @Override
    public long countByCardId(long cardId) {
        return jpa.countByCardId(cardId);
    }

    @Override
    public void deleteById(long id) {
        jpa.deleteById(id);
    }

    private static AttachmentEntity toEntity(Attachment a) {
        return new AttachmentEntity(a.id(), a.cardId(), a.filename(), a.contentType(), a.size(),
                a.objectKey(), a.createdAt());
    }

    private static Attachment toDomain(AttachmentEntity e) {
        return new Attachment(e.getId(), e.getCardId(), e.getFilename(), e.getContentType(), e.getSize(),
                e.getObjectKey(), e.getCreatedAt());
    }
}
