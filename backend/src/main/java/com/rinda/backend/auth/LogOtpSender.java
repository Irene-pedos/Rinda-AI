package com.rinda.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev-only OTP delivery. Without this property the app has no sender and refuses to start. */
@Component
@ConditionalOnProperty(name = "rinda.otp.log-to-console", havingValue = "true")
public class LogOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LogOtpSender.class);

    @Override
    public void send(String phone, String code) {
        log.info("[DEV OTP] phone={} code={}", phone, code);
    }
}
