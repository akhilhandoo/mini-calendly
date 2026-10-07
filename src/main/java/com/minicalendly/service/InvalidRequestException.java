package com.minicalendly.service;

/** The request violates a business rule that bean validation cannot express. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
