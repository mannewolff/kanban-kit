package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.stereotype.Component;

/** Adapter, der den {@link AppUserRepository}-Port auf Spring Data JPA abbildet. */
@Component
class AppUserRepositoryAdapter implements AppUserRepository {

  private final AppUserJpaRepository jpa;

  AppUserRepositoryAdapter(AppUserJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public AppUser save(AppUser user) {
    AppUserEntity saved = jpa.save(toEntity(user));
    return toDomain(saved);
  }

  @Override
  public Optional<AppUser> findById(Long id) {
    return jpa.findById(id).map(AppUserRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<AppUser> findByEmail(String email) {
    return jpa.findByEmail(email).map(AppUserRepositoryAdapter::toDomain);
  }

  @Override
  public boolean existsByEmail(String email) {
    return jpa.existsByEmail(email);
  }

  @Override
  public List<AppUser> findAll() {
    return jpa.findAll().stream().map(AppUserRepositoryAdapter::toDomain).toList();
  }

  @Override
  public List<AppUser> findByPlatformRole(PlatformRole platformRole) {
    return jpa.findByPlatformRole(platformRole).stream()
        .map(AppUserRepositoryAdapter::toDomain)
        .toList();
  }

  private static AppUserEntity toEntity(AppUser u) {
    return new AppUserEntity(
        u.id(),
        u.email(),
        u.passwordHash(),
        u.displayName(),
        u.emailVerified(),
        u.platformRole(),
        u.approvedAt(),
        u.approvedBy());
  }

  private static AppUser toDomain(AppUserEntity e) {
    return new AppUser(
        e.getId(),
        e.getEmail(),
        e.getPasswordHash(),
        e.getDisplayName(),
        e.isEmailVerified(),
        e.getPlatformRole(),
        e.getApprovedAt(),
        e.getApprovedBy());
  }
}
