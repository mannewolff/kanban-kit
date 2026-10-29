package org.mwolff.manban.push.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.push.application.PushAboRepository;
import org.mwolff.manban.push.domain.PushAbo;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Adapter des {@link PushAboRepository}-Ports auf Spring Data JPA. */
@Component
class PushAboRepositoryAdapter implements PushAboRepository {

  private final PushAboJpaRepository jpa;

  PushAboRepositoryAdapter(PushAboJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<PushAbo> findByEndpoint(String endpoint) {
    return jpa.findByEndpoint(endpoint).map(PushAboRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<PushAbo> findById(long id) {
    return jpa.findById(id).map(PushAboRepositoryAdapter::toDomain);
  }

  @Override
  public List<PushAbo> findAll() {
    return jpa.findAll(Sort.by("id")).stream().map(PushAboRepositoryAdapter::toDomain).toList();
  }

  @Override
  public PushAbo save(PushAbo abo) {
    return toDomain(jpa.save(toEntity(abo)));
  }

  @Override
  public void deleteById(long id) {
    jpa.deleteById(id);
  }

  private static PushAboEntity toEntity(PushAbo a) {
    return new PushAboEntity(
        a.id(),
        a.userId(),
        a.endpoint(),
        a.p256dh(),
        a.auth(),
        a.angelegtAt(),
        a.giltBis(),
        a.boardsAus());
  }

  private static PushAbo toDomain(PushAboEntity e) {
    return new PushAbo(
        e.getId(),
        e.getUserId(),
        e.getEndpoint(),
        e.getP256dh(),
        e.getAuth(),
        e.getAngelegtAt(),
        e.getGiltBis(),
        e.getBoardsAus());
  }
}
