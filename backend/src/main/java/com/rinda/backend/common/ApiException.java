package com.rinda.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** An expected failure. {@code code} is a stable value that clients can branch on. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, getMessage());
        problem.setProperty("code", code);
        return problem;
    }
}
