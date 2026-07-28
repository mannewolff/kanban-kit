package org.mwolff.manban.auth.infrastructure.persistence;

/** Spring-Data-Repository für {@link EmailVerificationTokenEntity}. */
interface EmailVerificationTokenJpaRepository
    extends AuthTokenJpaRepository<EmailVerificationTokenEntity> {}
