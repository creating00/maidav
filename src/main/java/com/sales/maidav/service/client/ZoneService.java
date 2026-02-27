package com.sales.maidav.service.client;

import com.sales.maidav.model.client.Zone;

import java.util.List;

public interface ZoneService {
    List<Zone> findAll();
    Zone findById(Long id);
    Zone create(Zone zone);
    Zone update(Long id, Zone zone);
    void delete(Long id);
}
