package com.backendbank.backendbank.cliente;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ClienteService {

    static final String MENSAJE_EXITO = "registro exitoso";
    static final String MENSAJE_ACTUALIZACION_EXITOSA = "actualización exitosa";
    static final String MENSAJE_DOCUMENTO_DUPLICADO = "ya existe un cliente registrado con ese documento";
    static final String MENSAJE_EMAIL_DUPLICADO = "ya existe un cliente registrado con ese email";
    static final String MENSAJE_NO_ENCONTRADO = "el cliente no fue encontrado";
    static final String MENSAJE_NO_AUTORIZADO = "no cuento con autorización para realizar esta acción";

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @Transactional
    public RegistroClienteResponse registrar(RegistroClienteRequest request) {
        String nombreComercial = request.nombreComercial().trim();
        String razonSocial = request.razonSocial().trim();
        String nitDocumento = request.nitDocumento().trim();
        String email = request.email().trim();
        String telefono = normalizarTelefono(request.telefono());

        if (clienteRepository.existsByNitDocumento(nitDocumento)) {
            throw new ClienteDuplicadoException(MENSAJE_DOCUMENTO_DUPLICADO);
        }
        if (clienteRepository.existsByEmail(email)) {
            throw new ClienteDuplicadoException(MENSAJE_EMAIL_DUPLICADO);
        }

        Cliente cliente = new Cliente();
        cliente.setNombreComercial(nombreComercial);
        cliente.setRazonSocial(razonSocial);
        cliente.setNitDocumento(nitDocumento);
        cliente.setEmail(email);
        cliente.setTelefono(telefono);
        cliente.setEstado(Cliente.ESTADO_PENDIENTE_DE_VALIDACION);
        cliente.setRol(Cliente.ROL_CLIENTE);

        try {
            Cliente guardado = clienteRepository.saveAndFlush(cliente);
            return new RegistroClienteResponse(guardado.getIdCliente(), guardado.getEstado(), MENSAJE_EXITO);
        } catch (DataIntegrityViolationException ex) {
            throw duplicadoDesdeRestriccion(ex);
        }
    }

    @Transactional
    public ActualizarClienteResponse actualizar(UUID idCliente, ActualizarClienteRequest request) {
        Cliente actor = exigirAdministrador(request.actualizadoPor());
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ClienteNoEncontradoException(MENSAJE_NO_ENCONTRADO));

        String email = request.email().trim();
        if (clienteRepository.existsByEmailAndIdClienteNot(email, idCliente)) {
            throw new ClienteDuplicadoException(MENSAJE_EMAIL_DUPLICADO);
        }

        cliente.setNombreComercial(request.nombreComercial().trim());
        cliente.setRazonSocial(request.razonSocial().trim());
        cliente.setEmail(email);
        cliente.setTelefono(normalizarTelefono(request.telefono()));
        cliente.setActualizadoPor(actor.getIdCliente());
        cliente.setFechaActualizacion(OffsetDateTime.now());

        try {
            Cliente guardado = clienteRepository.saveAndFlush(cliente);
            return new ActualizarClienteResponse(
                    guardado.getIdCliente(),
                    guardado.getEstado(),
                    guardado.getNombreComercial(),
                    guardado.getRazonSocial(),
                    guardado.getEmail(),
                    guardado.getTelefono(),
                    MENSAJE_ACTUALIZACION_EXITOSA);
        } catch (DataIntegrityViolationException ex) {
            throw new ClienteDuplicadoException(MENSAJE_EMAIL_DUPLICADO);
        }
    }

    private Cliente exigirAdministrador(UUID actualizadoPor) {
        if (actualizadoPor == null) {
            throw new AccesoDenegadoException(MENSAJE_NO_AUTORIZADO);
        }
        Cliente actor = clienteRepository.findById(actualizadoPor)
                .orElseThrow(() -> new AccesoDenegadoException(MENSAJE_NO_AUTORIZADO));
        if (!Cliente.ROL_ADMINISTRADOR.equals(actor.getRol())) {
            throw new AccesoDenegadoException(MENSAJE_NO_AUTORIZADO);
        }
        return actor;
    }

    private ClienteDuplicadoException duplicadoDesdeRestriccion(DataIntegrityViolationException ex) {
        String detalle = ex.getMostSpecificCause().getMessage();
        if (detalle != null && detalle.toLowerCase().contains("email")) {
            return new ClienteDuplicadoException(MENSAJE_EMAIL_DUPLICADO);
        }
        return new ClienteDuplicadoException(MENSAJE_DOCUMENTO_DUPLICADO);
    }

    private String normalizarTelefono(String telefono) {
        if (telefono == null) {
            return null;
        }
        String limpio = telefono.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}

class ClienteDuplicadoException extends RuntimeException {

    ClienteDuplicadoException(String mensaje) {
        super(mensaje);
    }
}

class ClienteNoEncontradoException extends RuntimeException {

    ClienteNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}

class AccesoDenegadoException extends RuntimeException {

    AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
