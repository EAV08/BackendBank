package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
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
public class RetiroService {

    static final String MENSAJE_EXITO = "retiro exitoso";
    static final String MENSAJE_MONTO = "el monto no es válido";
    static final String MENSAJE_AJENA = "solo puedo retirar de una cuenta propia";
    static final String MENSAJE_SALDO = "el saldo no es suficiente";

    private final CuentaRepository cuentaRepository;
    private final RetiroRepository retiroRepository;
    private final TransaccionRepository transaccionRepository;
    private final EventoTransaccionRepository eventoTransaccionRepository;
    private final ClienteService clienteService;

    public RetiroService(
            CuentaRepository cuentaRepository,
            RetiroRepository retiroRepository,
            TransaccionRepository transaccionRepository,
            EventoTransaccionRepository eventoTransaccionRepository,
            ClienteService clienteService) {
        this.cuentaRepository = cuentaRepository;
        this.retiroRepository = retiroRepository;
        this.transaccionRepository = transaccionRepository;
        this.eventoTransaccionRepository = eventoTransaccionRepository;
        this.clienteService = clienteService;
    }

    @Transactional(noRollbackFor = {
            MontoRetiroInvalidoException.class,
            RetiroRechazadoException.class,
            CuentaNoEncontradaException.class
    })
    public RetiroRealizadoResponse retirar(RetiroRequest request, String autorizacion) {
        Cliente quien = clienteService.clienteAutenticado(autorizacion);
        exigirPerfilActivo(quien);
        BigDecimal monto = exigirMonto(request.monto());
        Cuenta cuenta = exigirCuentaPropia(request.numeroCuenta(), quien);

        if (cuenta.getSaldo().compareTo(monto) < 0) {
            throw new RetiroRechazadoException(MENSAJE_SALDO);
        }

        BigDecimal saldo = cuenta.getSaldo().subtract(monto).setScale(2, RoundingMode.HALF_UP);
        cuenta.setSaldo(saldo);
        cuenta.setFechaActualizacion(OffsetDateTime.now());
        cuentaRepository.saveAndFlush(cuenta);

        Retiro retiro = new Retiro();
        retiro.setIdCliente(quien.getIdCliente());
        retiro.setIdCuenta(cuenta.getIdCuenta());
        retiro.setMonto(monto);
        Retiro guardado = retiroRepository.saveAndFlush(retiro);

        Transaccion transaccion = new Transaccion();
        transaccion.setIdCliente(quien.getIdCliente());
        transaccion.setIdCuentaOrigen(cuenta.getIdCuenta());
        transaccion.setMonto(monto);
        transaccion.setMoneda(Transaccion.MONEDA_COP);
        transaccion.setEstado(Transaccion.ESTADO_APROBADO);
        Transaccion guardadaTransaccion = transaccionRepository.saveAndFlush(transaccion);

        EventoTransaccion evento = new EventoTransaccion();
        evento.setIdTransaccion(guardadaTransaccion.getIdTransaccion());
        evento.setTipoEvento(EventoTransaccion.RETIRO);
        evento.setEstadoNuevo(Transaccion.ESTADO_APROBADO);
        evento.setDetalle(MENSAJE_EXITO + " por " + monto.toPlainString());
        eventoTransaccionRepository.saveAndFlush(evento);

        return new RetiroRealizadoResponse(
                guardado.getIdRetiro(),
                cuenta.getNumeroCuenta(),
                monto,
                saldo,
                MENSAJE_EXITO);
    }

    private void exigirPerfilActivo(Cliente cliente) {
        if (Cliente.ESTADO_BLOQUEADO.equals(cliente.getEstado())) {
            throw new RetiroRechazadoException(CuentaService.MENSAJE_PERFIL_BLOQUEADO);
        }
        if (Cliente.ESTADO_INACTIVO.equals(cliente.getEstado())) {
            throw new RetiroRechazadoException(ClienteService.MENSAJE_INACTIVO);
        }
        if (!Cliente.ESTADO_ACTIVO.equals(cliente.getEstado())) {
            throw new RetiroRechazadoException(CuentaService.MENSAJE_SIN_VALIDACION);
        }
    }

    private Cuenta exigirCuentaPropia(String numeroCuenta, Cliente quien) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(CuentaService.MENSAJE_CUENTA_NO_EXISTE));
        if (!quien.getIdCliente().equals(cuenta.getIdCliente())) {
            throw new RetiroRechazadoException(MENSAJE_AJENA);
        }
        if (Cuenta.ESTADO_BLOQUEADO.equals(cuenta.getEstado())) {
            throw new RetiroRechazadoException(CuentaService.MENSAJE_CUENTA_BLOQUEADA);
        }
        if (!Cuenta.ESTADO_ACTIVO.equals(cuenta.getEstado())) {
            throw new RetiroRechazadoException(CuentaService.MENSAJE_CUENTA_INACTIVA);
        }
        return cuenta;
    }

    private BigDecimal exigirMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MontoRetiroInvalidoException(MENSAJE_MONTO);
        }
        try {
            BigDecimal normalizado = monto.setScale(2, RoundingMode.UNNECESSARY);
            if (normalizado.precision() > 14) {
                throw new MontoRetiroInvalidoException(MENSAJE_MONTO);
            }
            return normalizado;
        } catch (ArithmeticException ex) {
            throw new MontoRetiroInvalidoException(MENSAJE_MONTO);
        }
    }
}

class MontoRetiroInvalidoException extends RuntimeException {

    MontoRetiroInvalidoException(String mensaje) {
        super(mensaje);
    }
}

class RetiroRechazadoException extends RuntimeException {

    RetiroRechazadoException(String mensaje) {
        super(mensaje);
    }
}
