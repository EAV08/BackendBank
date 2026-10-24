package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteRepository;
import com.backendbank.backendbank.cliente.ClienteService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BloquearCuentaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    void bloqueaLaCuentaActivaYSuspendeLasOperaciones() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000001", Cuenta.ESTADO_ACTIVO, null, false, null);

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(clienteId, cuenta.getIdCuenta(), "fraude")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("bloqueado"))
                .andExpect(jsonPath("$.operacionesSuspendidas").value(true))
                .andExpect(jsonPath("$.motivo").value("fraude"))
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_BLOQUEO));

        Cuenta guardada = cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow();
        assertEquals(Cuenta.ESTADO_BLOQUEADO, guardada.getEstado());
        assertTrue(guardada.isOperacionesSuspendidas());
        assertEquals(CuentaService.MOTIVO_FRAUDE, guardada.getMotivo());
    }

    @Test
    void noHaceNadaMasCuandoLaCuentaYaEstaBloqueada() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        OffsetDateTime bloqueadaEn = OffsetDateTime.parse("2026-01-15T10:15:30Z");
        Cuenta cuenta = guardarCuenta(
                clienteId, "1000000002", Cuenta.ESTADO_BLOQUEADO, "fraude", true, bloqueadaEn);

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(clienteId, cuenta.getIdCuenta(), "pérdida")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("bloqueado"))
                .andExpect(jsonPath("$.operacionesSuspendidas").value(true))
                .andExpect(jsonPath("$.motivo").value("fraude"))
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_YA_BLOQUEADA));

        Cuenta despues = cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow();
        assertEquals(Cuenta.ESTADO_BLOQUEADO, despues.getEstado());
        assertTrue(despues.isOperacionesSuspendidas());
        assertEquals("fraude", despues.getMotivo());
        assertEquals(bloqueadaEn, despues.getFechaActualizacion());
    }

    @Test
    void noBloqueaUnaCuentaInactiva() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000003", Cuenta.ESTADO_INACTIVO, null, false, null);

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(clienteId, cuenta.getIdCuenta(), "pérdida")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_INACTIVA));

        Cuenta despues = cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow();
        assertEquals(Cuenta.ESTADO_INACTIVO, despues.getEstado());
        assertFalse(despues.isOperacionesSuspendidas());
        assertNull(despues.getMotivo());
    }

    @Test
    void noBloqueaLaCuentaDeOtroCliente() throws Exception {
        UUID duenoId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        UUID otroId = registrar("Otra Tienda", "800999111", "otro@elsol.com");
        Cuenta cuenta = guardarCuenta(duenoId, "1000000004", Cuenta.ESTADO_ACTIVO, null, false, null);

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(otroId, cuenta.getIdCuenta(), "fraude")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_NO_ENCONTRADA));

        Cuenta despues = cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow();
        assertEquals(Cuenta.ESTADO_ACTIVO, despues.getEstado());
        assertFalse(despues.isOperacionesSuspendidas());
    }

    @Test
    void rechazaCuandoElClienteNoExiste() throws Exception {
        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(
                                UUID.fromString("00000000-0000-0000-0000-000000000000"),
                                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                "fraude")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(ClienteService.MENSAJE_NO_ENCONTRADO));

        assertEquals(0, cuentaRepository.count());
    }

    @Test
    void noBloqueaCuandoElPerfilEstaBloqueado() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        fijarEstado(clienteId, Cliente.ESTADO_BLOQUEADO);
        Cuenta cuenta = guardarCuenta(clienteId, "1000000005", Cuenta.ESTADO_ACTIVO, null, false, null);

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(clienteId, cuenta.getIdCuenta(), "fraude")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_PERFIL_BLOQUEADO));

        Cuenta despues = cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow();
        assertEquals(Cuenta.ESTADO_ACTIVO, despues.getEstado());
        assertFalse(despues.isOperacionesSuspendidas());
    }

    @Test
    void noBloqueaCuandoElPerfilEstaInactivo() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        fijarEstado(clienteId, Cliente.ESTADO_INACTIVO);
        Cuenta cuenta = guardarCuenta(clienteId, "1000000006", Cuenta.ESTADO_ACTIVO, null, false, null);

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(clienteId, cuenta.getIdCuenta(), "fraude")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ClienteService.MENSAJE_INACTIVO));

        assertEquals(Cuenta.ESTADO_ACTIVO, cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow().getEstado());
    }

    @Test
    void noBloqueaCuandoElPerfilNoEstaValidado() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        fijarEstado(clienteId, Cliente.ESTADO_PENDIENTE_DE_VALIDACION);
        Cuenta cuenta = guardarCuenta(clienteId, "1000000007", Cuenta.ESTADO_ACTIVO, null, false, null);

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(clienteId, cuenta.getIdCuenta(), "pérdida")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_SIN_VALIDACION));

        assertEquals(Cuenta.ESTADO_ACTIVO, cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow().getEstado());
    }

    @Test
    void noBloqueaCuandoLaCuentaNoExiste() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(
                                clienteId,
                                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                "fraude")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_NO_ENCONTRADA));

        assertEquals(0, cuentaRepository.count());
    }

    @Test
    void guardaElMotivoPerdidaSinAcentoComoPerdida() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000008", Cuenta.ESTADO_ACTIVO, null, false, null);

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(clienteId, cuenta.getIdCuenta(), "perdida")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("bloqueado"))
                .andExpect(jsonPath("$.operacionesSuspendidas").value(true))
                .andExpect(jsonPath("$.motivo").value("pérdida"))
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_BLOQUEO));

        assertEquals(CuentaService.MOTIVO_PERDIDA, cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow().getMotivo());
    }

    @Test
    void rechazaCuandoElMotivoNoEsPerdidaNiFraude() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000009", Cuenta.ESTADO_ACTIVO, null, false, null);

        mockMvc.perform(post("/cuentas/bloqueo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bloqueoJson(clienteId, cuenta.getIdCuenta(), "robo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe corregir los siguientes datos: motivo"));

        Cuenta despues = cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow();
        assertEquals(Cuenta.ESTADO_ACTIVO, despues.getEstado());
        assertFalse(despues.isOperacionesSuspendidas());
        assertNull(despues.getMotivo());
    }

    private void fijarEstado(UUID idCliente, String estado) {
        Cliente cliente = clienteRepository.findById(idCliente).orElseThrow();
        cliente.setEstado(estado);
        clienteRepository.saveAndFlush(cliente);
    }

    private String bloqueoJson(UUID idCliente, UUID idCuenta, String motivo) {
        return """
                {
                  "idCliente": "%s",
                  "idCuenta": "%s",
                  "motivo": "%s"
                }
                """.formatted(idCliente, idCuenta, motivo);
    }

    private Cuenta guardarCuenta(
            UUID idCliente,
            String numero,
            String estado,
            String motivo,
            boolean operacionesSuspendidas,
            OffsetDateTime fechaActualizacion) {
        Cuenta cuenta = new Cuenta();
        cuenta.setIdCliente(idCliente);
        cuenta.setNumeroCuenta(numero);
        cuenta.setEstado(estado);
        cuenta.setMotivo(motivo);
        cuenta.setOperacionesSuspendidas(operacionesSuspendidas);
        cuenta.setFechaActualizacion(fechaActualizacion);
        return cuentaRepository.saveAndFlush(cuenta);
    }

    private UUID registrar(String nombre, String nit, String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreComercial": "%s",
                                  "razonSocial": "%s S.A.S.",
                                  "nitDocumento": "%s",
                                  "email": "%s",
                                  "usuario": "%s",
                                  "contrasena": "clave123"
                                }
                                """.formatted(nombre, nombre, nit, email, email.substring(0, email.indexOf('@')))))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(resultado.getResponse().getContentAsString(), "$.idCliente");
        return UUID.fromString(id);
    }
}
