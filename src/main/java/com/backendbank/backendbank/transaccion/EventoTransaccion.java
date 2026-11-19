package com.backendbank.backendbank.transaccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "eventos_transaccion")
public class EventoTransaccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento", nullable = false, updatable = false)
    private Long idEvento;

    @Column(name = "id_transaccion", nullable = false, updatable = false)
    private UUID idTransaccion;

    @Column(name = "tipo_evento", nullable = false, length = 50, updatable = false)
    private String tipoEvento;

    @Column(name = "estado_anterior", length = 20, updatable = false)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", length = 20, updatable = false)
    private String estadoNuevo;

    @Column(name = "detalle", updatable = false)
    private String detalle;

    @Column(name = "fecha_evento", nullable = false, updatable = false)
    private OffsetDateTime fechaEvento;

    @PrePersist
    void asignarFecha() {
        if (fechaEvento == null) {
            fechaEvento = OffsetDateTime.now();
        }
    }

    public Long getIdEvento() {
        return idEvento;
    }

    public UUID getIdTransaccion() {
        return idTransaccion;
    }

    public void setIdTransaccion(UUID idTransaccion) {
        this.idTransaccion = idTransaccion;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(String estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public String getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(String estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public OffsetDateTime getFechaEvento() {
        return fechaEvento;
    }
}
