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
public class TransferenciaService {

    static final String MENSAJE_EXITO = "transferencia exitosa";
    static final String MENSAJE_DETALLE = "constancia de transferencia";
    static final String MENSAJE_MONTO = "el monto no es válido";
    static final String MENSAJE_AJENA = "solo puedo transferir desde una cuenta propia";
    static final String MENSAJE_SALDO = "el saldo no es suficiente";
    static final String MENSAJE_MISMA_CUENTA = "la cuenta destino debe ser diferente de la cuenta origen";

    private final CuentaRepository cuentaRepository;
    private final ClienteService clienteService;
    private final TransaccionRepository transaccionRepository;
    private final EventoTransaccionRepository eventoTransaccionRepository;

    public TransferenciaService(
            CuentaRepository cuentaRepository,
            ClienteService clienteService,
            TransaccionRepository transaccionRepository,
            EventoTransaccionRepository eventoTransaccionRepository) {
        this.cuentaRepository = cuentaRepository;
        this.clienteService = clienteService;
        this.transaccionRepository = transaccionRepository;
        this.eventoTransaccionRepository = eventoTransaccionRepository;
    }

    @Transactional(noRollbackFor = {
            MontoTransferenciaInvalidoException.class,
            TransferenciaRechazadaException.class,
            CuentaNoEncontradaException.class
    })
    public TransferenciaRealizadaResponse transferir(TransferenciaRequest request, String autorizacion) {
        Cliente quien = clienteService.clienteAutenticado(autorizacion);
        exigirPerfilActivo(quien);
        BigDecimal monto = exigirMonto(request.monto());
        Cuenta origen = exigirCuentaOrigen(request.numeroCuentaOrigen(), quien);
        Cuenta destino = exigirCuentaDestino(request.numeroCuentaDestino());

        if (origen.getIdCuenta().equals(destino.getIdCuenta())) {
            throw new TransferenciaRechazadaException(MENSAJE_MISMA_CUENTA);
        }
        if (origen.getSaldo().compareTo(monto) < 0) {
            throw new TransferenciaRechazadaException(MENSAJE_SALDO);
        }

        BigDecimal saldoOrigen = origen.getSaldo().subtract(monto).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoDestino = destino.getSaldo().add(monto).setScale(2, RoundingMode.UNNECESSARY);
        OffsetDateTime ahora = OffsetDateTime.now();
        origen.setSaldo(saldoOrigen);
        origen.setFechaActualizacion(ahora);
        destino.setSaldo(saldoDestino);
        destino.setFechaActualizacion(ahora);
        cuentaRepository.saveAndFlush(origen);
        cuentaRepository.saveAndFlush(destino);

        Transaccion transaccion = new Transaccion();
        transaccion.setIdCliente(quien.getIdCliente());
        transaccion.setIdCuentaOrigen(origen.getIdCuenta());
        transaccion.setIdCuentaDestino(destino.getIdCuenta());
        transaccion.setMonto(monto);
        transaccion.setMoneda(Transaccion.MONEDA_COP);
        transaccion.setEstado(Transaccion.ESTADO_APROBADO);
        Transaccion guardada = transaccionRepository.saveAndFlush(transaccion);

        EventoTransaccion evento = new EventoTransaccion();
        evento.setIdTransaccion(guardada.getIdTransaccion());
        evento.setTipoEvento(EventoTransaccion.TRANSFERENCIA);
        evento.setEstadoNuevo(Transaccion.ESTADO_APROBADO);
        evento.setDetalle(MENSAJE_DETALLE + " por " + monto.toPlainString());
        eventoTransaccionRepository.saveAndFlush(evento);

        return new TransferenciaRealizadaResponse(
                guardada.getIdTransaccion(),
                origen.getNumeroCuenta(),
                destino.getNumeroCuenta(),
                monto,
                saldoOrigen,
                MENSAJE_EXITO);
    }

    private void exigirPerfilActivo(Cliente cliente) {
        if (Cliente.ESTADO_BLOQUEADO.equals(cliente.getEstado())) {
            throw new TransferenciaRechazadaException(CuentaService.MENSAJE_PERFIL_BLOQUEADO);
        }
        if (Cliente.ESTADO_INACTIVO.equals(cliente.getEstado())) {
            throw new TransferenciaRechazadaException(ClienteService.MENSAJE_INACTIVO);
        }
        if (!Cliente.ESTADO_ACTIVO.equals(cliente.getEstado())) {
            throw new TransferenciaRechazadaException(CuentaService.MENSAJE_SIN_VALIDACION);
        }
    }

    private Cuenta exigirCuentaOrigen(String numeroCuenta, Cliente quien) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(CuentaService.MENSAJE_CUENTA_NO_EXISTE));
        if (!quien.getIdCliente().equals(cuenta.getIdCliente())) {
            throw new TransferenciaRechazadaException(MENSAJE_AJENA);
        }
        if (Cuenta.ESTADO_BLOQUEADO.equals(cuenta.getEstado())) {
            throw new TransferenciaRechazadaException(CuentaService.MENSAJE_CUENTA_BLOQUEADA);
        }
        if (!Cuenta.ESTADO_ACTIVO.equals(cuenta.getEstado())) {
            throw new TransferenciaRechazadaException(CuentaService.MENSAJE_CUENTA_INACTIVA);
        }
        return cuenta;
    }

    private Cuenta exigirCuentaDestino(String numeroCuenta) {
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(CuentaService.MENSAJE_CUENTA_NO_EXISTE));
        if (Cuenta.ESTADO_BLOQUEADO.equals(cuenta.getEstado())) {
            throw new TransferenciaRechazadaException(CuentaService.MENSAJE_CUENTA_BLOQUEADA);
        }
        if (!Cuenta.ESTADO_ACTIVO.equals(cuenta.getEstado())) {
            throw new TransferenciaRechazadaException(CuentaService.MENSAJE_CUENTA_INACTIVA);
        }
        return cuenta;
    }

    private BigDecimal exigirMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MontoTransferenciaInvalidoException(MENSAJE_MONTO);
        }
        try {
            BigDecimal normalizado = monto.setScale(2, RoundingMode.UNNECESSARY);
            if (normalizado.precision() > 14) {
                throw new MontoTransferenciaInvalidoException(MENSAJE_MONTO);
            }
            return normalizado;
        } catch (ArithmeticException ex) {
            throw new MontoTransferenciaInvalidoException(MENSAJE_MONTO);
        }
    }
}

class MontoTransferenciaInvalidoException extends RuntimeException {

    MontoTransferenciaInvalidoException(String mensaje) {
        super(mensaje);
    }
}

class TransferenciaRechazadaException extends RuntimeException {

    TransferenciaRechazadaException(String mensaje) {
        super(mensaje);
    }
}
