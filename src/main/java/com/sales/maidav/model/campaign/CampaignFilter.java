package com.sales.maidav.model.campaign;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CampaignFilter {
    ACTIVE("Activas"), FUTURE("Futuras"), EXPIRED("Vencidas"), CANCELLED("Canceladas"), ALL("Todas");
    private final String label;
}
