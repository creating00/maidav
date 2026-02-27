package com.sales.maidav.service.product;

public class DuplicateProductCodeException extends RuntimeException {
    public DuplicateProductCodeException(String message) {
        super(message);
    }
}
