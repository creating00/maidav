package com.sales.maidav.service.finance;

public class InvalidFinancePeriodException extends RuntimeException {
    public InvalidFinancePeriodException(String message) {
        super(message);
    }
}
