package com.sales.maidav.service.client;

public class DuplicateNationalIdException extends RuntimeException {
    public DuplicateNationalIdException(String message) {
        super(message);
    }
}
