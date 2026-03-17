package com.sales.maidav.service.dashboard;

public class DashboardFilterOption {

    private final Long id;
    private final String label;

    public DashboardFilterOption(Long id, String label) {
        this.id = id;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}
