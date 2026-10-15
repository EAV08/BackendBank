package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteNoEncontradoException;
import com.backendbank.backendbank.cliente.ClienteRepository;
import com.backendbank.backendbank.cliente.ClienteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CuentaService {

    static final String MENSAJE_SALDO = "saldo disponible actualizado";
    static final String MENSAJE_CUENTA_BLOQUEADA = "su cuenta se encuentra en estado bloqueado";
    static final String MENSAJE_CUENTA_INACTIVA = "su cuenta se encuentra en estado inactiva";
    static final String MENSAJE_MAS_TARDE = "intente más tarde";
    static final String MENSAJE_CUENTA_NO_ENCONTRADA = "la cuenta no fue encontrada";
    static final String MENSAJE_SIN_VALIDACION =
            "debe hacer el proceso de validación como cliente antes de poder obtener algún servicio";
    static final String MENSAJE_PERFIL_BLOQUEADO = "su perfil está bloqueado";

    private final CuentaRepository cuentaRepository;
    private final ClienteRepository clienteRepository;
    private final ConsultaSaldo consultaSaldo;

    public CuentaService(
            CuentaRepository cuentaRepository,
            ClienteRepository clienteRepository,
            ConsultaSaldo consultaSaldo) {
        this.cuentaRepository = cuentaRepository;
        this.clienteRepository = clienteRepository;
        this.consultaSaldo = consultaSaldo;
    }

    @Transactional(readOnly = true, noRollbackFor = {
            ConsultaSaldoRechazadaException.class,
            ConsultaSaldoNoDisponibleException.class,
            CuentaNoEncontradaException.class,
            ClienteNoEncontradoException.class
    })
    public SaldoDisponibleResponse consultar(ConsultarSaldoRequest request) {
        Cliente cliente = clienteRepository.findById(request.idCliente())
                .orElseThrow(() -> new ClienteNoEncontradoException(ClienteService.MENSAJE_NO_ENCONTRADO));
        exigirClienteValidado(cliente);

        Cuenta cuenta = cuentaRepository.findByIdCuentaAndIdCliente(request.idCuenta(), cliente.getIdCliente())
                .orElseThrow(() -> new CuentaNoEncontradaException(MENSAJE_CUENTA_NO_ENCONTRADA));
        if (Cuenta.ESTADO_BLOQUEADO.equals(cuenta.getEstado())) {
            throw new ConsultaSaldoRechazadaException(MENSAJE_CUENTA_BLOQUEADA);
        }
        if (Cuenta.ESTADO_INACTIVO.equals(cuenta.getEstado())) {
            throw new ConsultaSaldoRechazadaException(MENSAJE_CUENTA_INACTIVA);
        }
        if (!Cuenta.ESTADO_ACTIVO.equals(cuenta.getEstado())) {
            throw new ConsultaSaldoRechazadaException(MENSAJE_CUENTA_INACTIVA);
        }

        BigDecimal saldo = leerSaldo(cuenta);
        return new SaldoDisponibleResponse(
                cuenta.getIdCuenta(),
                cuenta.getNumeroCuenta(),
                cuenta.getIdCliente(),
                cuenta.getEstado(),
                saldo,
                MENSAJE_SALDO);
    }

    private void exigirClienteValidado(Cliente cliente) {
        if (Cliente.ESTADO_BLOQUEADO.equals(cliente.getEstado())) {
            throw new ConsultaSaldoRechazadaException(MENSAJE_PERFIL_BLOQUEADO);
        }
        if (Cliente.ESTADO_INACTIVO.equals(cliente.getEstado())) {
            throw new ConsultaSaldoRechazadaException(ClienteService.MENSAJE_INACTIVO);
        }
        if (!Cliente.ESTADO_ACTIVO.equals(cliente.getEstado())) {
            throw new ConsultaSaldoRechazadaException(MENSAJE_SIN_VALIDACION);
        }
    }

    private BigDecimal leerSaldo(Cuenta cuenta) {
        try {
            BigDecimal saldo = consultaSaldo.disponible(cuenta);
            if (saldo == null) {
                throw new ConsultaSaldoNoDisponibleException(MENSAJE_MAS_TARDE);
            }
            return saldo;
        } catch (ConsultaSaldoNoDisponibleException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ConsultaSaldoNoDisponibleException(MENSAJE_MAS_TARDE);
        }
    }
}

class ConsultaSaldoRechazadaException extends RuntimeException {

    ConsultaSaldoRechazadaException(String mensaje) {
        super(mensaje);
    }
}

class ConsultaSaldoNoDisponibleException extends RuntimeException {

    ConsultaSaldoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}

class CuentaNoEncontradaException extends RuntimeException {

    CuentaNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
