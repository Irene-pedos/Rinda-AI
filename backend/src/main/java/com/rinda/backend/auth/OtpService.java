package com.rinda.backend.auth;

import com.rinda.backend.common.ApiException;
import com.rinda.backend.config.RindaProperties;
import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class OtpService {

    private final OtpCodeRepository repository;
    private final PasswordEncoder encoder;
    private final OtpSender sender;
    private final RindaProperties.Otp config;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpCodeRepository repository, PasswordEncoder encoder, OtpSender sender,
                      RindaProperties properties) {
        this.repository = repository;
        this.encoder = encoder;
        this.sender = sender;
        this.config = properties.otp();
    }

    public void request(String phone) {
        request(phone, OtpPurpose.LOGIN);
    }

    /** Sends a code for one purpose. Each purpose has its own resend limit and its own latest code. */
    public void request(String phone, OtpPurpose purpose) {
        Instant now = Instant.now();
        repository.findFirstByPhoneAndPurposeOrderByCreatedAtDescIdDesc(phone, purpose)
                .filter(last -> last.getCreatedAt().plus(config.resendInterval()).isAfter(now))
                .ifPresent(last -> {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RATE_LIMITED",
                            "Please wait before asking for another code.");
                });

        String code = "%06d".formatted(random.nextInt(1_000_000));
        repository.save(new OtpCode(phone, encoder.encode(code), now.plus(config.ttl()), now, purpose));
        sender.send(phone, code);
    }

    /** Checks the latest code without using it up. Call {@link #consume} once the login can go ahead. */
    public OtpCode check(String phone, String code) {
        return check(phone, code, OtpPurpose.LOGIN);
    }

    /** Checks the latest code sent for this purpose, without using it up. */
    public OtpCode check(String phone, String code, OtpPurpose purpose) {
        Instant now = Instant.now();
        OtpCode otp = repository.findFirstByPhoneAndPurposeOrderByCreatedAtDescIdDesc(phone, purpose)
                .filter(o -> o.getConsumedAt() == null)
                .filter(o -> o.getExpiresAt().isAfter(now))
                .filter(o -> o.getAttempts() < config.maxAttempts())
                .orElseThrow(OtpService::invalid);
        if (!encoder.matches(code, otp.getCodeHash())) {
            repository.incrementAttempts(otp.getId());
            throw invalid();
        }
        return otp;
    }

    public void consume(OtpCode otp) {
        if (repository.consume(otp.getId(), Instant.now()) != 1) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", "The code is wrong or has expired.");
    }
}
