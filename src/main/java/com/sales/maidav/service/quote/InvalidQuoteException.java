package com.sales.maidav.service.quote;

public class InvalidQuoteException extends RuntimeException {

    public InvalidQuoteException(String message) {
        super(message);
    }
}
