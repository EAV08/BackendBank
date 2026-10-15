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

    @Column(name = "saldo", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo;

    @Column(name = "motivo", length = 200)
    private String motivo;

    @Column(name = "operaciones_suspendidas", nullable = false)
    private boolean operacionesSuspendidas;

    @Column(name = "fecha_vinculacion", nullable = false, updatable = false)
    private OffsetDateTime fechaVinculacion;

    @Column(name = "fecha_actualizacion")
    private OffsetDateTime fechaActualizacion;

    @PrePersist
    void asignarValoresIniciales() {
        if (idCuenta == null) {
            idCuenta = UUID.randomUUID();
        }
        if (estado == null || estado.isBlank()) {
            estado = ESTADO_ACTIVO;
        }
        if (saldo == null) {
            saldo = BigDecimal.ZERO.setScale(2);
        }
        if (fechaVinculacion == null) {
            fechaVinculacion = OffsetDateTime.now();
        }
    }

    public UUID getIdCuenta() {
        return idCuenta;
    }

    public void setIdCuenta(UUID idCuenta) {
        this.idCuenta = idCuenta;
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

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public boolean isOperacionesSuspendidas() {
        return operacionesSuspendidas;
    }

    public void setOperacionesSuspendidas(boolean operacionesSuspendidas) {
        this.operacionesSuspendidas = operacionesSuspendidas;
    }

    public OffsetDateTime getFechaVinculacion() {
        return fechaVinculacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
