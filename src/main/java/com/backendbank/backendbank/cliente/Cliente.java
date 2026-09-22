package com.backendbank.backendbank.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "clientes")
public class Cliente {

    public static final String ESTADO_PENDIENTE_DE_VALIDACION = "pendiente_de_validacion";

    @Id
    @Column(name = "id_cliente", nullable = false, updatable = false)
    private UUID idCliente;

    @Column(name = "nombre_comercial", nullable = false, length = 150)
    private String nombreComercial;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Column(name = "nit_documento", nullable = false, unique = true, length = 30)
    private String nitDocumento;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "estado", nullable = false, length = 30)
    private String estado;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private OffsetDateTime fechaRegistro;

    @PrePersist
    void asignarValoresIniciales() {
        if (idCliente == null) {
            idCliente = UUID.randomUUID();
        }
        if (estado == null || estado.isBlank()) {
            estado = ESTADO_PENDIENTE_DE_VALIDACION;
        }
        if (fechaRegistro == null) {
            fechaRegistro = OffsetDateTime.now();
        }
    }

    public UUID getIdCliente() {
        return idCliente;
    }

    public String getNombreComercial() {
        return nombreComercial;
    }

    public void setNombreComercial(String nombreComercial) {
        this.nombreComercial = nombreComercial;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getNitDocumento() {
        return nitDocumento;
    }

    public void setNitDocumento(String nitDocumento) {
        this.nitDocumento = nitDocumento;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
