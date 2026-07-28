package org.mwolff.manban.auth.infrastructure.persistence;

/** Spring-Data-Repository für {@link PasswordResetTokenEntity}. */
interface PasswordResetTokenJpaRepository
    extends AuthTokenJpaRepository<PasswordResetTokenEntity> {}
