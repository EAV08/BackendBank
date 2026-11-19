package com.backendbank.backendbank.cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "retiros")
public class Retiro {

    @Id
    @Column(name = "id_retiro", nullable = false, updatable = false)
    private UUID idRetiro;

    @Column(name = "id_cliente", nullable = false, updatable = false)
    private UUID idCliente;

    @Column(name = "id_cuenta", nullable = false, updatable = false)
    private UUID idCuenta;

    @Column(name = "monto", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(name = "fecha", nullable = false, updatable = false)
    private OffsetDateTime fecha;

    @PrePersist
    void asignarValoresIniciales() {
        if (idRetiro == null) {
            idRetiro = UUID.randomUUID();
        }
        if (fecha == null) {
            fecha = OffsetDateTime.now();
        }
    }

    public UUID getIdRetiro() {
        return idRetiro;
    }

    public UUID getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(UUID idCliente) {
        this.idCliente = idCliente;
    }

    public UUID getIdCuenta() {
        return idCuenta;
    }

    public void setIdCuenta(UUID idCuenta) {
        this.idCuenta = idCuenta;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }
}
