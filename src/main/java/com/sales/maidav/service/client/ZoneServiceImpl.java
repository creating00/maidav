package com.sales.maidav.service.client;

import com.sales.maidav.model.client.Zone;
import com.sales.maidav.repository.client.ZoneRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ZoneServiceImpl implements ZoneService {

    private final ZoneRepository zoneRepository;

    @Override
    public List<Zone> findAll() {
        return zoneRepository.findAll();
    }

    @Override
    public Zone findById(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zona no encontrada"));
    }

    @Override
    public Zone create(Zone zone) {
        validate(zone);
        return zoneRepository.save(zone);
    }

    @Override
    public Zone update(Long id, Zone data) {
        validate(data);
        Zone zone = findById(id);
        zone.setAddress(data.getAddress());
        zone.setNumber(data.getNumber());
        zone.setMapLink(data.getMapLink());
        return zone;
    }

    @Override
    public void delete(Long id) {
        zoneRepository.deleteById(id);
    }

    private void validate(Zone zone) {
        if (zone.getAddress() == null || zone.getAddress().isBlank()) {
            throw new RuntimeException("La direccion de la zona es obligatoria");
        }
    }
}
