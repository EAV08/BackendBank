package com.backendbank.backendbank.cliente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final RegistroClienteService registroClienteService;

    public ClienteController(RegistroClienteService registroClienteService) {
        this.registroClienteService = registroClienteService;
    }

    @PostMapping
    public ResponseEntity<RegistroClienteResponse> registrar(@Valid @RequestBody RegistroClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registroClienteService.registrar(request));
    }
}

record RegistroClienteRequest(
        @NotBlank(message = "El nombre comercial debe completarse")
        @Size(max = 150, message = "El nombre comercial debe corregirse")
        String nombreComercial,

        @NotBlank(message = "La razón social debe completarse")
        @Size(max = 200, message = "La razón social debe corregirse")
        String razonSocial,

        @NotBlank(message = "El documento debe completarse")
        @Size(max = 30, message = "El documento debe corregirse")
        String nitDocumento,

        @NotBlank(message = "El email debe completarse")
        @Email(message = "El email debe corregirse")
        @Size(max = 150, message = "El email debe corregirse")
        String email,

        @Size(max = 20, message = "El teléfono debe corregirse")
        String telefono
) {
    RegistroClienteRequest {
        nombreComercial = recortar(nombreComercial);
        razonSocial = recortar(razonSocial);
        nitDocumento = recortar(nitDocumento);
        email = recortar(email);
        telefono = recortar(telefono);
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }
}

record RegistroClienteResponse(UUID idCliente, String estado, String mensaje) {
}

record CampoError(String campo, String mensaje) {
}

record ValidacionErrorResponse(String mensaje, List<CampoError> errores) {
}

record MensajeError(String mensaje) {
}

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Map<String, String> ETIQUETAS = Map.of(
            "nombreComercial", "nombre comercial",
            "razonSocial", "razón social",
            "nitDocumento", "documento",
            "email", "email",
            "telefono", "teléfono"
    );

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ValidacionErrorResponse> datosInvalidos(MethodArgumentNotValidException ex) {
        List<CampoError> errores = new ArrayList<>();
        Set<String> incompletos = new LinkedHashSet<>();
        Set<String> corregir = new LinkedHashSet<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String etiqueta = ETIQUETAS.getOrDefault(error.getField(), error.getField());
            if (esIncompleto(error.getCode())) {
                incompletos.add(etiqueta);
            } else {
                corregir.add(etiqueta);
            }
            errores.add(new CampoError(error.getField(), error.getDefaultMessage()));
        });

        return ResponseEntity.badRequest()
                .body(new ValidacionErrorResponse(construirMensaje(incompletos, corregir), errores));
    }

    @ExceptionHandler(ClienteDuplicadoException.class)
    ResponseEntity<MensajeError> clienteDuplicado(ClienteDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MensajeError(ex.getMessage()));
    }

    private boolean esIncompleto(String codigo) {
        return "NotBlank".equals(codigo) || "NotNull".equals(codigo) || "NotEmpty".equals(codigo);
    }

    private String construirMensaje(Set<String> incompletos, Set<String> corregir) {
        List<String> partes = new ArrayList<>();
        if (!incompletos.isEmpty()) {
            partes.add("Debe completar los siguientes datos: " + String.join(", ", incompletos));
        }
        if (!corregir.isEmpty()) {
            partes.add("Debe corregir los siguientes datos: " + String.join(", ", corregir));
        }
        return String.join(". ", partes);
    }
}
