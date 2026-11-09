package com.rinda.backend.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByPhoneAndPurposeOrderByCreatedAtDescIdDesc(String phone, OtpPurpose purpose);

    @Transactional
    @Modifying
    @Query("update OtpCode o set o.attempts = o.attempts + 1 where o.id = :id")
    void incrementAttempts(long id);

    /** Returns 1 for the one caller that wins, so a code can only be used once. */
    @Transactional
    @Modifying
    @Query("update OtpCode o set o.consumedAt = :now where o.id = :id and o.consumedAt is null")
    int consume(long id, Instant now);
}
