package com.backendbank.backendbank.cliente;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroClienteService {

    static final String MENSAJE_EXITO = "registro exitoso";
    static final String MENSAJE_DOCUMENTO_DUPLICADO = "ya existe un cliente registrado con ese documento";
    static final String MENSAJE_EMAIL_DUPLICADO = "ya existe un cliente registrado con ese email";

    private final ClienteRepository clienteRepository;

    public RegistroClienteService(ClienteRepository clienteRepository) {
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

        try {
            Cliente guardado = clienteRepository.saveAndFlush(cliente);
            return new RegistroClienteResponse(guardado.getIdCliente(), guardado.getEstado(), MENSAJE_EXITO);
        } catch (DataIntegrityViolationException ex) {
            throw duplicadoDesdeRestriccion(ex);
        }
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
