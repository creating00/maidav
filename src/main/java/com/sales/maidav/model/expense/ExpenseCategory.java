package com.sales.maidav.model.expense;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExpenseCategory {
    RENT("Alquiler"), SERVICES("Servicios"), SALARIES("Sueldos"), FUEL("Combustible"),
    REPAIRS("Reparaciones"), TAXES("Impuestos"), ADMINISTRATIVE("Administrativos"), OTHER("Otros");

    private final String label;
}
