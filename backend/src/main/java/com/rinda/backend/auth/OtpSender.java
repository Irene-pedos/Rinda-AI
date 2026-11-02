package com.rinda.backend.auth;

public interface OtpSender {

    void send(String phone, String code);
}
