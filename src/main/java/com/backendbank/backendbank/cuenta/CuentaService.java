package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteNoEncontradoException;
import com.backendbank.backendbank.cliente.ClienteRepository;
import com.backendbank.backendbank.cliente.ClienteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class CuentaService {

    static final String MENSAJE_EXITO = "la cuenta ha sido bloqueada exitosamente";
    static final String MENSAJE_YA_BLOQUEADA = "la cuenta ya se encuentra en estado de bloqueo";
    static final String MENSAJE_CUENTA_INACTIVA = "su cuenta se encuentra en estado inactiva";
    static final String MENSAJE_CUENTA_NO_ENCONTRADA = "la cuenta no fue encontrada";
    static final String MENSAJE_SIN_VALIDACION =
            "debe hacer el proceso de validación como cliente antes de poder obtener algún servicio";
    static final String MENSAJE_PERFIL_BLOQUEADO = "su perfil está bloqueado";
    static final String MOTIVO_PERDIDA = "pérdida";
    static final String MOTIVO_FRAUDE = "fraude";

    private final CuentaRepository cuentaRepository;
    private final ClienteRepository clienteRepository;

    public CuentaService(CuentaRepository cuentaRepository, ClienteRepository clienteRepository) {
        this.cuentaRepository = cuentaRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional(noRollbackFor = {
            BloqueoCuentaRechazadoException.class,
            CuentaNoEncontradaException.class,
            ClienteNoEncontradoException.class
    })
    public CuentaBloqueadaResponse bloquear(BloquearCuentaRequest request) {
        Cliente cliente = clienteRepository.findById(request.idCliente())
                .orElseThrow(() -> new ClienteNoEncontradoException(ClienteService.MENSAJE_NO_ENCONTRADO));
        exigirClienteValidado(cliente);

        Cuenta cuenta = cuentaRepository.findByIdCuentaAndIdCliente(request.idCuenta(), cliente.getIdCliente())
                .orElseThrow(() -> new CuentaNoEncontradaException(MENSAJE_CUENTA_NO_ENCONTRADA));
        if (Cuenta.ESTADO_BLOQUEADO.equals(cuenta.getEstado())) {
            return respuesta(cuenta, MENSAJE_YA_BLOQUEADA);
        }
        if (!Cuenta.ESTADO_ACTIVO.equals(cuenta.getEstado())) {
            throw new BloqueoCuentaRechazadoException(MENSAJE_CUENTA_INACTIVA);
        }

        cuenta.setEstado(Cuenta.ESTADO_BLOQUEADO);
        cuenta.setOperacionesSuspendidas(true);
        cuenta.setMotivo(normalizarMotivo(request.motivo()));
        cuenta.setFechaActualizacion(OffsetDateTime.now());
        return respuesta(cuentaRepository.saveAndFlush(cuenta), MENSAJE_EXITO);
    }

    private void exigirClienteValidado(Cliente cliente) {
        if (Cliente.ESTADO_BLOQUEADO.equals(cliente.getEstado())) {
            throw new BloqueoCuentaRechazadoException(MENSAJE_PERFIL_BLOQUEADO);
        }
        if (Cliente.ESTADO_INACTIVO.equals(cliente.getEstado())) {
            throw new BloqueoCuentaRechazadoException(ClienteService.MENSAJE_INACTIVO);
        }
        if (!Cliente.ESTADO_ACTIVO.equals(cliente.getEstado())) {
            throw new BloqueoCuentaRechazadoException(MENSAJE_SIN_VALIDACION);
        }
    }

    private String normalizarMotivo(String motivo) {
        if (MOTIVO_FRAUDE.equalsIgnoreCase(motivo)) {
            return MOTIVO_FRAUDE;
        }
        return MOTIVO_PERDIDA;
    }

    private CuentaBloqueadaResponse respuesta(Cuenta cuenta, String mensaje) {
        return new CuentaBloqueadaResponse(
                cuenta.getIdCuenta(),
                cuenta.getNumeroCuenta(),
                cuenta.getIdCliente(),
                cuenta.getEstado(),
                cuenta.isOperacionesSuspendidas(),
                cuenta.getMotivo(),
                mensaje);
    }
}

class BloqueoCuentaRechazadoException extends RuntimeException {

    BloqueoCuentaRechazadoException(String mensaje) {
        super(mensaje);
    }
}

class CuentaNoEncontradaException extends RuntimeException {

    CuentaNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
