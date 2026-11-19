package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteNoEncontradoException;
import com.backendbank.backendbank.cliente.ClienteRepository;
import com.backendbank.backendbank.cliente.ClienteService;
import com.backendbank.backendbank.transaccion.EventoTransaccion;
import com.backendbank.backendbank.transaccion.EventoTransaccionRepository;
import com.backendbank.backendbank.transaccion.Transaccion;
import com.backendbank.backendbank.transaccion.TransaccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Service
public class DepositoService {

    static final String MENSAJE_CONFIRME = "confirme el depósito";
    static final String MENSAJE_CONSTANCIA = "constancia de depósito";
    static final String MENSAJE_CUENTA_NO_EXISTE = "la cuenta no existe";
    static final String MENSAJE_CUENTA_INACTIVA = "la cuenta está inactiva";
    static final String MENSAJE_CUENTA_BLOQUEADA = "la cuenta está bloqueada";
    static final String MENSAJE_MONTO = "el monto no es válido";

    private final CuentaRepository cuentaRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final TransaccionRepository transaccionRepository;
    private final EventoTransaccionRepository eventoTransaccionRepository;

    public DepositoService(
            CuentaRepository cuentaRepository,
            ClienteRepository clienteRepository,
            ClienteService clienteService,
            TransaccionRepository transaccionRepository,
            EventoTransaccionRepository eventoTransaccionRepository) {
        this.cuentaRepository = cuentaRepository;
        this.clienteRepository = clienteRepository;
        this.clienteService = clienteService;
        this.transaccionRepository = transaccionRepository;
        this.eventoTransaccionRepository = eventoTransaccionRepository;
    }

    @Transactional(readOnly = true, noRollbackFor = {
            MontoDepositoInvalidoException.class,
            CuentaDepositoNoEncontradaException.class,
            DepositoRechazadoException.class,
            ClienteNoEncontradoException.class
    })
    public ConfirmacionDepositoResponse preparar(DepositoRequest request, String autorizacion) {
        clienteService.clienteIdentificado(autorizacion);
        BigDecimal monto = exigirMonto(request.monto());
        Cuenta cuenta = exigirCuentaDisponible(request.numeroCuenta());
        Cliente dueno = duenoDe(cuenta);
        return new ConfirmacionDepositoResponse(
                cuenta.getNumeroCuenta(),
                dueno.getNombreComercial(),
                monto,
                MENSAJE_CONFIRME);
    }

    @Transactional(noRollbackFor = {
            MontoDepositoInvalidoException.class,
            CuentaDepositoNoEncontradaException.class,
            DepositoRechazadoException.class,
            ClienteNoEncontradoException.class
    })
    public DepositoRealizadoResponse confirmar(DepositoRequest request, String autorizacion) {
        Cliente quien = clienteService.clienteIdentificado(autorizacion);
        BigDecimal monto = exigirMonto(request.monto());
        Cuenta cuenta = exigirCuentaDisponible(request.numeroCuenta());
        Cliente dueno = duenoDe(cuenta);

        BigDecimal saldo = cuenta.getSaldo().add(monto).setScale(2, RoundingMode.UNNECESSARY);
        cuenta.setSaldo(saldo);
        cuenta.setFechaActualizacion(OffsetDateTime.now());
        cuentaRepository.saveAndFlush(cuenta);

        Transaccion transaccion = new Transaccion();
        transaccion.setIdCliente(quien.getIdCliente());
        transaccion.setIdCuentaDestino(cuenta.getIdCuenta());
        transaccion.setMonto(monto);
        transaccion.setMoneda(Transaccion.MONEDA_COP);
        transaccion.setEstado(Transaccion.ESTADO_APROBADO);
        Transaccion guardada = transaccionRepository.saveAndFlush(transaccion);

        EventoTransaccion evento = new EventoTransaccion();
        evento.setIdTransaccion(guardada.getIdTransaccion());
        evento.setTipoEvento(EventoTransaccion.DEPOSITO);
        evento.setEstadoNuevo(Transaccion.ESTADO_APROBADO);
        evento.setDetalle(MENSAJE_CONSTANCIA + " por " + monto.toPlainString());
        eventoTransaccionRepository.saveAndFlush(evento);

        return new DepositoRealizadoResponse(
                guardada.getIdTransaccion(),
                cuenta.getNumeroCuenta(),
                dueno.getNombreComercial(),
                monto,
                saldo,
                MENSAJE_CONSTANCIA + " por " + monto.toPlainString());
    }

    private Cliente duenoDe(Cuenta cuenta) {
        return clienteRepository.findById(cuenta.getIdCliente())
                .orElseThrow(() -> new ClienteNoEncontradoException(ClienteService.MENSAJE_NO_ENCONTRADO));
    }

    private Cuenta exigirCuentaDisponible(String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new CuentaDepositoNoEncontradaException(MENSAJE_CUENTA_NO_EXISTE));
        if (Cuenta.ESTADO_BLOQUEADO.equals(cuenta.getEstado())) {
            throw new DepositoRechazadoException(MENSAJE_CUENTA_BLOQUEADA);
        }
        if (!Cuenta.ESTADO_ACTIVO.equals(cuenta.getEstado())) {
            throw new DepositoRechazadoException(MENSAJE_CUENTA_INACTIVA);
        }
        return cuenta;
    }

    private BigDecimal exigirMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MontoDepositoInvalidoException(MENSAJE_MONTO);
        }
        try {
            BigDecimal normalizado = monto.setScale(2, RoundingMode.UNNECESSARY);
            if (normalizado.precision() > 14) {
                throw new MontoDepositoInvalidoException(MENSAJE_MONTO);
            }
            return normalizado;
        } catch (ArithmeticException ex) {
            throw new MontoDepositoInvalidoException(MENSAJE_MONTO);
        }
    }
}

class MontoDepositoInvalidoException extends RuntimeException {

    MontoDepositoInvalidoException(String mensaje) {
        super(mensaje);
    }
}

class CuentaDepositoNoEncontradaException extends RuntimeException {

    CuentaDepositoNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}

class DepositoRechazadoException extends RuntimeException {

    DepositoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
