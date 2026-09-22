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
    static final String MENSAJE_CAMBIO_EXITOSO = "el cambio se realizó exitosamente";
    static final String MENSAJE_MISMO_ESTADO = "el cliente ya se encuentra en dicho estado";
    static final String MENSAJE_CAMBIO_NO_PERMITIDO = "el cambio de estado no está permitido desde el estado actual";
    static final String MENSAJE_INGRESO = "ingreso exitoso";
    static final String MENSAJE_CREDENCIALES = "el usuario o la contraseña son inválidos";
    static final String MENSAJE_INACTIVO = "su usuario está inactivo y debe contactar al banco";
    static final String MENSAJE_BLOQUEADO = "su usuario está bloqueado y debe contactar al banco";
    static final String MENSAJE_PENDIENTE = "su usuario aún no está activo y debe contactar al banco";

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

    @Transactional
    public CambiarEstadoResponse cambiarEstado(UUID idCliente, CambiarEstadoRequest request) {
        Cliente actor = exigirAdministrador(request.actualizadoPor());
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new ClienteNoEncontradoException(MENSAJE_NO_ENCONTRADO));

        String estadoSolicitado = request.estado().trim();
        if (estadoSolicitado.equals(cliente.getEstado())) {
            throw new CambioEstadoRechazadoException(MENSAJE_MISMO_ESTADO);
        }
        if (!transicionPermitida(cliente, estadoSolicitado)) {
            throw new CambioEstadoRechazadoException(MENSAJE_CAMBIO_NO_PERMITIDO);
        }

        cliente.setEstado(estadoSolicitado);
        cliente.setMotivo(request.motivo().trim());
        cliente.setActualizadoPor(actor.getIdCliente());
        cliente.setFechaActualizacion(OffsetDateTime.now());

        Cliente guardado = clienteRepository.saveAndFlush(cliente);
        return new CambiarEstadoResponse(
                guardado.getIdCliente(),
                guardado.getEstado(),
                guardado.getMotivo(),
                MENSAJE_CAMBIO_EXITOSO);
    }

    private boolean transicionPermitida(Cliente cliente, String estadoSolicitado) {
        if (Cliente.ESTADO_INACTIVO.equals(estadoSolicitado)) {
            return Cliente.ESTADO_ACTIVO.equals(cliente.getEstado());
        }
        if (Cliente.ESTADO_ACTIVO.equals(estadoSolicitado)) {
            return Cliente.ESTADO_INACTIVO.equals(cliente.getEstado());
        }
        return false;
    }

    @Transactional(readOnly = true)
    public IngresoResponse ingresar(IngresoRequest request) {
        Cliente cliente = clienteRepository.findById(request.idCliente()).orElse(null);
        if (cliente == null) {
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES);
        }
        if (!request.nitDocumento().trim().equals(cliente.getNitDocumento())) {
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES);
        }
        if (Cliente.ESTADO_ACTIVO.equals(cliente.getEstado())) {
            return new IngresoResponse(cliente.getIdCliente(), cliente.getEstado(), MENSAJE_INGRESO);
        }
        if (Cliente.ESTADO_INACTIVO.equals(cliente.getEstado())) {
            throw new IngresoRechazadoException(MENSAJE_INACTIVO);
        }
        if (Cliente.ESTADO_BLOQUEADO.equals(cliente.getEstado())) {
            throw new IngresoRechazadoException(MENSAJE_BLOQUEADO);
        }
        throw new IngresoRechazadoException(MENSAJE_PENDIENTE);
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

class CambioEstadoRechazadoException extends RuntimeException {

    CambioEstadoRechazadoException(String mensaje) {
        super(mensaje);
    }
}

class CredencialesInvalidasException extends RuntimeException {

    CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}

class IngresoRechazadoException extends RuntimeException {

    IngresoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
