package com.sales.maidav.model.client;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "zones")
public class Zone extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 20)
    private String number;

    @Column(name = "map_link", length = 500)
    private String mapLink;

    public Zone() {}

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getMapLink() { return mapLink; }
    public void setMapLink(String mapLink) { this.mapLink = mapLink; }
}
