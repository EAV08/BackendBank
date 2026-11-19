package com.backendbank.backendbank.transaccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transacciones")
public class Transaccion {

    public static final String ESTADO_CREADO = "creado";
    public static final String ESTADO_APROBADO = "aprobado";
    public static final String ESTADO_RECHAZADO = "rechazado";
    public static final String ESTADO_REEMBOLSADO = "reembolsado";
    public static final String MONEDA_COP = "COP";

    @Id
    @Column(name = "id_transaccion", nullable = false, updatable = false)
    private UUID idTransaccion;

    @Column(name = "id_cliente", nullable = false, updatable = false)
    private UUID idCliente;

    @Column(name = "id_cuenta_origen", updatable = false)
    private UUID idCuentaOrigen;

    @Column(name = "id_cuenta_destino", updatable = false)
    private UUID idCuentaDestino;

    @Column(name = "monto", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal monto;

    @Column(name = "moneda", nullable = false, length = 3, updatable = false)
    private String moneda;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "referencia_externa", unique = true, length = 100)
    private String referenciaExterna;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    @PrePersist
    void asignarValoresIniciales() {
        if (idTransaccion == null) {
            idTransaccion = UUID.randomUUID();
        }
        if (moneda == null || moneda.isBlank()) {
            moneda = MONEDA_COP;
        }
        if (estado == null || estado.isBlank()) {
            estado = ESTADO_CREADO;
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = ahora;
        }
        if (fechaActualizacion == null) {
            fechaActualizacion = ahora;
        }
    }

    public UUID getIdTransaccion() {
        return idTransaccion;
    }

    public UUID getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(UUID idCliente) {
        this.idCliente = idCliente;
    }

    public UUID getIdCuentaOrigen() {
        return idCuentaOrigen;
    }

    public void setIdCuentaOrigen(UUID idCuentaOrigen) {
        this.idCuentaOrigen = idCuentaOrigen;
    }

    public UUID getIdCuentaDestino() {
        return idCuentaDestino;
    }

    public void setIdCuentaDestino(UUID idCuentaDestino) {
        this.idCuentaDestino = idCuentaDestino;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public void setReferenciaExterna(String referenciaExterna) {
        this.referenciaExterna = referenciaExterna;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
