package com.sales.maidav.model.settings;

import com.sales.maidav.model.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "company_settings")
public class CompanySettings extends BaseEntity {

    @Column(length = 150)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "logo_path", length = 300)
    private String logoPath;

    @Column(name = "calc_recargo", precision = 8, scale = 4)
    private BigDecimal calcRecargo;

    @Column(name = "calc_mult_contado", precision = 8, scale = 4)
    private BigDecimal calcMultContado;

    @Column(name = "calc_mult_debito", precision = 8, scale = 4)
    private BigDecimal calcMultDebito;

    @Column(name = "calc_dias")
    private Integer calcDias;

    @Column(name = "calc_int_dia", precision = 8, scale = 4)
    private BigDecimal calcIntDia;

    @Column(name = "calc_semanas")
    private Integer calcSemanas;

    @Column(name = "calc_int_sem", precision = 8, scale = 4)
    private BigDecimal calcIntSem;

    @Column(name = "calc_meses_corto")
    private Integer calcMesesCorto;

    @Column(name = "calc_int_mes_corto", precision = 8, scale = 4)
    private BigDecimal calcIntMesCorto;

    @Column(name = "calc_meses_largo")
    private Integer calcMesesLargo;

    @Column(name = "calc_int_mes_largo", precision = 8, scale = 4)
    private BigDecimal calcIntMesLargo;

    public CompanySettings() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public BigDecimal getCalcRecargo() { return calcRecargo; }
    public void setCalcRecargo(BigDecimal calcRecargo) { this.calcRecargo = calcRecargo; }

    public BigDecimal getCalcMultContado() { return calcMultContado; }
    public void setCalcMultContado(BigDecimal calcMultContado) { this.calcMultContado = calcMultContado; }

    public BigDecimal getCalcMultDebito() { return calcMultDebito; }
    public void setCalcMultDebito(BigDecimal calcMultDebito) { this.calcMultDebito = calcMultDebito; }

    public Integer getCalcDias() { return calcDias; }
    public void setCalcDias(Integer calcDias) { this.calcDias = calcDias; }

    public BigDecimal getCalcIntDia() { return calcIntDia; }
    public void setCalcIntDia(BigDecimal calcIntDia) { this.calcIntDia = calcIntDia; }

    public Integer getCalcSemanas() { return calcSemanas; }
    public void setCalcSemanas(Integer calcSemanas) { this.calcSemanas = calcSemanas; }

    public BigDecimal getCalcIntSem() { return calcIntSem; }
    public void setCalcIntSem(BigDecimal calcIntSem) { this.calcIntSem = calcIntSem; }

    public Integer getCalcMesesCorto() { return calcMesesCorto; }
    public void setCalcMesesCorto(Integer calcMesesCorto) { this.calcMesesCorto = calcMesesCorto; }

    public BigDecimal getCalcIntMesCorto() { return calcIntMesCorto; }
    public void setCalcIntMesCorto(BigDecimal calcIntMesCorto) { this.calcIntMesCorto = calcIntMesCorto; }

    public Integer getCalcMesesLargo() { return calcMesesLargo; }
    public void setCalcMesesLargo(Integer calcMesesLargo) { this.calcMesesLargo = calcMesesLargo; }

    public BigDecimal getCalcIntMesLargo() { return calcIntMesLargo; }
    public void setCalcIntMesLargo(BigDecimal calcIntMesLargo) { this.calcIntMesLargo = calcIntMesLargo; }
}
