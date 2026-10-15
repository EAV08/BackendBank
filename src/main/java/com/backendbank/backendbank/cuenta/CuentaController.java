package com.backendbank.backendbank.cuenta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/cuentas")
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @PostMapping("/estado")
    public ResponseEntity<EstadoCuentaResponse> consultar(@Valid @RequestBody ConsultarEstadoRequest request) {
        return ResponseEntity.ok(cuentaService.consultar(request));
    }
}

record ConsultarEstadoRequest(
        @NotBlank(message = "El usuario debe completarse")
        @Size(max = 80, message = "El usuario debe corregirse")
        String usuario,

        @NotBlank(message = "La contraseña debe completarse")
        @Size(max = 255, message = "La contraseña debe corregirse")
        String contrasena,

        @NotBlank(message = "El número de cuenta debe completarse")
        @Size(max = 20, message = "El número de cuenta debe corregirse")
        String numeroCuenta
) {
    ConsultarEstadoRequest {
        usuario = recortar(usuario);
        contrasena = recortar(contrasena);
        numeroCuenta = recortar(numeroCuenta);
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }
}

record EstadoCuentaResponse(String numeroCuenta, String estado, String mensaje) {
}

record MensajeError(String mensaje) {
}

@RestControllerAdvice
class CuentaExceptionHandler {

    @ExceptionHandler(ClienteNoRegistradoException.class)
    ResponseEntity<MensajeError> clienteNoRegistrado(ClienteNoRegistradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(ContrasenaIncorrectaException.class)
    ResponseEntity<MensajeError> contrasenaIncorrecta(ContrasenaIncorrectaException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(CuentaNoEncontradaException.class)
    ResponseEntity<MensajeError> cuentaNoEncontrada(CuentaNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MensajeError(ex.getMessage()));
    }
}
