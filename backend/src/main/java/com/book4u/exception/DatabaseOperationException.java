package com.book4u.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a low-level Spring JDBC or relational database operation fails.
 */
public class DatabaseOperationException extends Book4uException {

    public DatabaseOperationException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public DatabaseOperationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
