package com.yavanika.banking.exception;

import org.springframework.http.HttpStatus;

/** Business-rule failure that maps to a specific HTTP status and error code. */
public class BankingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BankingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static BankingException notFound(String message) {
        return new BankingException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static BankingException forbidden(String message) {
        return new BankingException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static BankingException conflict(String message) {
        return new BankingException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static BankingException unauthorized(String message) {
        return new BankingException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", message);
    }

    public static BankingException rule(String code, String message) {
        return new BankingException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
