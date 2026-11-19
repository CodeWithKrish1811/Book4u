package com.book4u.exception;

import org.springframework.http.HttpStatus;

/**
 * Base custom runtime exception for Book4u platform domain errors.
 */
public class Book4uException extends RuntimeException {

    private final HttpStatus status;

    public Book4uException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public Book4uException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public Book4uException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
