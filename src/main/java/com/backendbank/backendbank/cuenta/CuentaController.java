package com.backendbank.backendbank.cuenta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/cuentas")
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @PostMapping
    public ResponseEntity<CuentaCreadaResponse> crear(@Valid @RequestBody CrearCuentaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cuentaService.crear(request));
    }

    @PostMapping("/saldo")
    public ResponseEntity<SaldoDisponibleResponse> consultar(@Valid @RequestBody ConsultarSaldoRequest request) {
        return ResponseEntity.ok(cuentaService.consultar(request));
    }

    @PostMapping("/bloqueo")
    public ResponseEntity<CuentaBloqueadaResponse> bloquear(@Valid @RequestBody BloquearCuentaRequest request) {
        return ResponseEntity.ok(cuentaService.bloquear(request));
    }
}

record CrearCuentaRequest(
        @NotNull(message = "El id cliente debe completarse")
        UUID idCliente
) {
}

record CuentaCreadaResponse(
        UUID idCuenta,
        String numeroCuenta,
        UUID idCliente,
        String estado,
        BigDecimal saldo,
        String mensaje
) {
}

record ConsultarSaldoRequest(
        @NotNull(message = "El id cliente debe completarse")
        UUID idCliente,

        @NotNull(message = "El id cuenta debe completarse")
        UUID idCuenta
) {
}

record SaldoDisponibleResponse(
        UUID idCuenta,
        String numeroCuenta,
        UUID idCliente,
        String estado,
        BigDecimal saldo,
        String mensaje
) {
}

record BloquearCuentaRequest(
        @NotNull(message = "El id cliente debe completarse")
        UUID idCliente,

        @NotNull(message = "El id cuenta debe completarse")
        UUID idCuenta,

        @NotBlank(message = "El motivo debe completarse")
        @Size(max = 200, message = "El motivo debe corregirse")
        @Pattern(regexp = "(?i)(pérdida|perdida|fraude)", message = "El motivo debe corregirse")
        String motivo
) {
    BloquearCuentaRequest {
        motivo = motivo == null ? null : motivo.trim();
    }
}

record CuentaBloqueadaResponse(
        UUID idCuenta,
        String numeroCuenta,
        UUID idCliente,
        String estado,
        boolean operacionesSuspendidas,
        String motivo,
        String mensaje
) {
}

record MensajeError(String mensaje) {
}

@RestControllerAdvice
class CuentaExceptionHandler {

    @ExceptionHandler(CreacionCuentaRechazadaException.class)
    ResponseEntity<MensajeError> creacionRechazada(CreacionCuentaRechazadaException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(GeneracionCuentaException.class)
    ResponseEntity<MensajeError> generacionFallida(GeneracionCuentaException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(ConsultaSaldoRechazadaException.class)
    ResponseEntity<MensajeError> consultaRechazada(ConsultaSaldoRechazadaException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(ConsultaSaldoNoDisponibleException.class)
    ResponseEntity<MensajeError> saldoNoDisponible(ConsultaSaldoNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(CuentaNoEncontradaException.class)
    ResponseEntity<MensajeError> cuentaNoEncontrada(CuentaNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(BloqueoCuentaRechazadoException.class)
    ResponseEntity<MensajeError> bloqueoRechazado(BloqueoCuentaRechazadoException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MensajeError(ex.getMessage()));
    }
}
