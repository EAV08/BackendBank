package com.backendbank.backendbank.cliente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    public ResponseEntity<RegistroClienteResponse> registrar(@Valid @RequestBody RegistroClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.registrar(request));
    }

    @PutMapping("/{idCliente}")
    public ResponseEntity<ActualizarClienteResponse> actualizar(
            @PathVariable UUID idCliente,
            @Valid @RequestBody ActualizarClienteRequest request) {
        return ResponseEntity.ok(clienteService.actualizar(idCliente, request));
    }

    @PatchMapping("/{idCliente}/estado")
    public ResponseEntity<CambiarEstadoResponse> cambiarEstado(
            @PathVariable UUID idCliente,
            @Valid @RequestBody CambiarEstadoRequest request) {
        return ResponseEntity.ok(clienteService.cambiarEstado(idCliente, request));
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

record ActualizarClienteRequest(
        @NotBlank(message = "El nombre comercial debe completarse")
        @Size(max = 150, message = "El nombre comercial debe corregirse")
        String nombreComercial,

        @NotBlank(message = "La razón social debe completarse")
        @Size(max = 200, message = "La razón social debe corregirse")
        String razonSocial,

        @NotBlank(message = "El email debe completarse")
        @Email(message = "El email debe corregirse")
        @Size(max = 150, message = "El email debe corregirse")
        String email,

        @Size(max = 20, message = "El teléfono debe corregirse")
        String telefono,

        @NotNull
        UUID actualizadoPor
) {
    ActualizarClienteRequest {
        nombreComercial = recortar(nombreComercial);
        razonSocial = recortar(razonSocial);
        email = recortar(email);
        telefono = recortar(telefono);
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }
}

record ActualizarClienteResponse(
        UUID idCliente,
        String estado,
        String nombreComercial,
        String razonSocial,
        String email,
        String telefono,
        String mensaje
) {
}

record CambiarEstadoRequest(
        @NotBlank(message = "El estado debe completarse")
        @Pattern(regexp = "(activo|inactivo)?", message = "El estado debe corregirse")
        String estado,

        @NotBlank(message = "El motivo debe completarse")
        @Size(max = 200, message = "El motivo debe corregirse")
        String motivo,

        @NotNull
        UUID actualizadoPor
) {
    CambiarEstadoRequest {
        estado = recortar(estado);
        motivo = recortar(motivo);
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }
}

record CambiarEstadoResponse(UUID idCliente, String estado, String motivo, String mensaje) {
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
            "telefono", "teléfono",
            "estado", "estado",
            "motivo", "motivo"
    );

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> datosInvalidos(MethodArgumentNotValidException ex) {
        List<FieldError> datos = ex.getBindingResult().getFieldErrors().stream()
                .filter(error -> !"actualizadoPor".equals(error.getField()))
                .toList();
        if (datos.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MensajeError(ClienteService.MENSAJE_NO_AUTORIZADO));
        }

        List<CampoError> errores = new ArrayList<>();
        Set<String> incompletos = new LinkedHashSet<>();
        Set<String> corregir = new LinkedHashSet<>();
        datos.forEach(error -> {
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

    @ExceptionHandler(CambioEstadoRechazadoException.class)
    ResponseEntity<MensajeError> cambioEstadoRechazado(CambioEstadoRechazadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(ClienteDuplicadoException.class)
    ResponseEntity<MensajeError> clienteDuplicado(ClienteDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    ResponseEntity<MensajeError> clienteNoEncontrado(ClienteNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MensajeError(ex.getMessage()));
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    ResponseEntity<MensajeError> accesoDenegado(AccesoDenegadoException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MensajeError(ex.getMessage()));
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
