package com.minicalendly.service;

/** The request is valid but clashes with the current state (overlap, already booked, ...). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
