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

import java.util.UUID;

@RestController
@RequestMapping("/cuentas")
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @PostMapping("/bloqueo")
    public ResponseEntity<CuentaBloqueadaResponse> bloquear(@Valid @RequestBody BloquearCuentaRequest request) {
        return ResponseEntity.ok(cuentaService.bloquear(request));
    }
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

    @ExceptionHandler(BloqueoCuentaRechazadoException.class)
    ResponseEntity<MensajeError> bloqueoRechazado(BloqueoCuentaRechazadoException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(CuentaNoEncontradaException.class)
    ResponseEntity<MensajeError> cuentaNoEncontrada(CuentaNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MensajeError(ex.getMessage()));
    }
}
