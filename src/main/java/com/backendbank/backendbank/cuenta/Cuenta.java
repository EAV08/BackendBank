package com.backendbank.backendbank.cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cuentas")
public class Cuenta {

    public static final String ESTADO_ACTIVO = "activo";
    public static final String ESTADO_INACTIVO = "inactivo";
    public static final String ESTADO_BLOQUEADO = "bloqueado";

    @Id
    @Column(name = "id_cuenta", nullable = false, updatable = false)
    private UUID idCuenta;

    @Column(name = "id_cliente", nullable = false, updatable = false)
    private UUID idCliente;

    @Column(name = "numero_cuenta", nullable = false, unique = true, length = 20)
    private String numeroCuenta;

    @Column(name = "estado", nullable = false, length = 30)
    private String estado;

    @Column(name = "fecha_vinculacion", nullable = false, updatable = false)
    private OffsetDateTime fechaVinculacion;

    @PrePersist
    void asignarValoresIniciales() {
        if (idCuenta == null) {
            idCuenta = UUID.randomUUID();
        }
        if (estado == null || estado.isBlank()) {
            estado = ESTADO_ACTIVO;
        }
        if (fechaVinculacion == null) {
            fechaVinculacion = OffsetDateTime.now();
        }
    }

    public UUID getIdCuenta() {
        return idCuenta;
    }

    public UUID getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(UUID idCliente) {
        this.idCliente = idCliente;
    }

    public String getNumeroCuenta() {
        return numeroCuenta;
    }

    public void setNumeroCuenta(String numeroCuenta) {
        this.numeroCuenta = numeroCuenta;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public OffsetDateTime getFechaVinculacion() {
        return fechaVinculacion;
    }
}
