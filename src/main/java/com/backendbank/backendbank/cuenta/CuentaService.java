package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CuentaService {

    static final String MENSAJE_EXITO = "consulta exitosa";
    static final String MENSAJE_CLIENTE_NO_EXISTE = "no existe un cliente con ese usuario";
    static final String MENSAJE_CONTRASENA = "la contraseña es incorrecta";
    static final String MENSAJE_CUENTA_NO_EXISTE = "no existe una cuenta con ese número";

    private final CuentaRepository cuentaRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final String hashFicticio;

    public CuentaService(
            CuentaRepository cuentaRepository,
            ClienteRepository clienteRepository,
            PasswordEncoder passwordEncoder) {
        this.cuentaRepository = cuentaRepository;
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
        this.hashFicticio = passwordEncoder.encode("contrasena-ficticia");
    }

    @Transactional(readOnly = true, noRollbackFor = {
            ClienteNoRegistradoException.class,
            ContrasenaIncorrectaException.class,
            CuentaNoEncontradaException.class
    })
    public EstadoCuentaResponse consultar(ConsultarEstadoRequest request) {
        Cliente cliente = clienteRepository.findByUsuario(request.usuario()).orElse(null);
        if (cliente == null) {
            passwordEncoder.matches(request.contrasena(), hashFicticio);
            throw new ClienteNoRegistradoException(MENSAJE_CLIENTE_NO_EXISTE);
        }
        if (!contrasenaCoincide(cliente, request.contrasena())) {
            throw new ContrasenaIncorrectaException(MENSAJE_CONTRASENA);
        }

        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(request.numeroCuenta()).orElse(null);
        if (cuenta == null || !cliente.getIdCliente().equals(cuenta.getIdCliente())) {
            throw new CuentaNoEncontradaException(MENSAJE_CUENTA_NO_EXISTE);
        }
        return new EstadoCuentaResponse(cuenta.getNumeroCuenta(), cuenta.getEstado(), MENSAJE_EXITO);
    }

    private boolean contrasenaCoincide(Cliente cliente, String plana) {
        String almacenada = cliente.getContrasena();
        if (almacenada != null && (almacenada.startsWith("$2a$") || almacenada.startsWith("$2b$") || almacenada.startsWith("$2y$"))) {
            return passwordEncoder.matches(plana, almacenada);
        }
        return plana.equals(almacenada);
    }
}

class ClienteNoRegistradoException extends RuntimeException {

    ClienteNoRegistradoException(String mensaje) {
        super(mensaje);
    }
}

class ContrasenaIncorrectaException extends RuntimeException {

    ContrasenaIncorrectaException(String mensaje) {
        super(mensaje);
    }
}

class CuentaNoEncontradaException extends RuntimeException {

    CuentaNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
