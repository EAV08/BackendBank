package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteNoEncontradoException;
import com.backendbank.backendbank.cliente.ClienteRepository;
import com.backendbank.backendbank.cliente.ClienteService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CuentaService {

    static final String MENSAJE_EXITO = "cuenta creada exitosamente";
    static final String MENSAJE_SIN_VALIDACION =
            "debe hacer el proceso de validación como cliente antes de poder obtener algún servicio";
    static final String MENSAJE_LIMITE =
            "ha alcanzado el número máximo de cuentas activas vinculadas a un perfil";
    static final String MENSAJE_PERFIL_BLOQUEADO = "su perfil está bloqueado";
    static final String MENSAJE_TECNICO =
            "hay un problema técnico temporal, por favor intente el proceso más tarde";
    static final int MAX_CUENTAS_ACTIVAS = 3;
    static final int MAX_REINTENTOS_NUMERO = 5;

    private final CuentaRepository cuentaRepository;
    private final ClienteRepository clienteRepository;
    private final GeneradorNumeroCuenta generadorNumeroCuenta;

    public CuentaService(
            CuentaRepository cuentaRepository,
            ClienteRepository clienteRepository,
            GeneradorNumeroCuenta generadorNumeroCuenta) {
        this.cuentaRepository = cuentaRepository;
        this.clienteRepository = clienteRepository;
        this.generadorNumeroCuenta = generadorNumeroCuenta;
    }

    @Transactional(noRollbackFor = {
            CreacionCuentaRechazadaException.class,
            GeneracionCuentaException.class,
            ClienteNoEncontradoException.class
    })
    public CuentaCreadaResponse crear(CrearCuentaRequest request) {
        Cliente cliente = clienteRepository.findById(request.idCliente())
                .orElseThrow(() -> new ClienteNoEncontradoException(ClienteService.MENSAJE_NO_ENCONTRADO));
        exigirPerfilHabilitado(cliente);
        if (cuentaRepository.countByIdClienteAndEstado(cliente.getIdCliente(), Cuenta.ESTADO_ACTIVO) >= MAX_CUENTAS_ACTIVAS) {
            throw new CreacionCuentaRechazadaException(MENSAJE_LIMITE);
        }

        Cuenta cuenta = new Cuenta();
        cuenta.setIdCliente(cliente.getIdCliente());
        cuenta.setNumeroCuenta(reservarNumero());
        cuenta.setEstado(Cuenta.ESTADO_ACTIVO);
        cuenta.setSaldo(BigDecimal.ZERO.setScale(2));

        try {
            Cuenta guardada = cuentaRepository.saveAndFlush(cuenta);
            return new CuentaCreadaResponse(
                    guardada.getIdCuenta(),
                    guardada.getNumeroCuenta(),
                    guardada.getIdCliente(),
                    guardada.getEstado(),
                    guardada.getSaldo(),
                    MENSAJE_EXITO);
        } catch (DataIntegrityViolationException ex) {
            throw new GeneracionCuentaException(MENSAJE_TECNICO);
        }
    }

    private void exigirPerfilHabilitado(Cliente cliente) {
        if (Cliente.ESTADO_BLOQUEADO.equals(cliente.getEstado())) {
            throw new CreacionCuentaRechazadaException(MENSAJE_PERFIL_BLOQUEADO);
        }
        if (Cliente.ESTADO_INACTIVO.equals(cliente.getEstado())) {
            throw new CreacionCuentaRechazadaException(ClienteService.MENSAJE_INACTIVO);
        }
        if (!Cliente.ESTADO_ACTIVO.equals(cliente.getEstado())) {
            throw new CreacionCuentaRechazadaException(MENSAJE_SIN_VALIDACION);
        }
    }

    private String reservarNumero() {
        for (int intento = 0; intento < MAX_REINTENTOS_NUMERO; intento++) {
            String numero = solicitarNumero();
            if (numeroValido(numero) && !cuentaRepository.existsByNumeroCuenta(numero)) {
                return numero;
            }
        }
        throw new GeneracionCuentaException(MENSAJE_TECNICO);
    }

    private String solicitarNumero() {
        try {
            return generadorNumeroCuenta.generar();
        } catch (RuntimeException ex) {
            throw new GeneracionCuentaException(MENSAJE_TECNICO);
        }
    }

    private boolean numeroValido(String numero) {
        return numero != null && numero.length() == GeneradorNumeroCuenta.LONGITUD && numero.chars().allMatch(Character::isDigit);
    }
}

class CreacionCuentaRechazadaException extends RuntimeException {

    CreacionCuentaRechazadaException(String mensaje) {
        super(mensaje);
    }
}

class GeneracionCuentaException extends RuntimeException {

    GeneracionCuentaException(String mensaje) {
        super(mensaje);
    }
}
