package com.backendbank.backendbank.cliente;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistroClienteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    void registraClienteConDatosCompletos() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson("Tienda El Sol", "El Sol S.A.S.", "900123456", "contacto@elsol.com", "3001234567")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("pendiente_de_validacion"))
                .andExpect(jsonPath("$.mensaje").value("registro exitoso"))
                .andExpect(jsonPath("$.idCliente").isNotEmpty())
                .andReturn();

        UUID idCliente = idDe(resultado);
        Cliente guardado = clienteRepository.findById(idCliente).orElseThrow();
        assertEquals("pendiente_de_validacion", guardado.getEstado());
        assertEquals("Tienda El Sol", guardado.getNombreComercial());
        assertEquals("900123456", guardado.getNitDocumento());
        assertEquals("contacto@elsol.com", guardado.getEmail());
        assertEquals("3001234567", guardado.getTelefono());
        assertEquals(Cliente.ROL_CLIENTE, guardado.getRol());
        assertNotNull(guardado.getFechaRegistro());

        MvcResult segundo = mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson("Otra Tienda", "Otra S.A.S.", "800999111", "otro@elsol.com", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("pendiente_de_validacion"))
                .andExpect(jsonPath("$.mensaje").value("registro exitoso"))
                .andReturn();

        assertNotEquals(idCliente, idDe(segundo));
        assertEquals(2, clienteRepository.count());
    }

    @Test
    void rechazaCuandoFaltaUnDatoObligatorio() throws Exception {
        mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson("   ", "El Sol S.A.S.", "900123456", "contacto@elsol.com", "3001234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe completar los siguientes datos: nombre comercial"))
                .andExpect(jsonPath("$.errores[0].campo").value("nombreComercial"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El nombre comercial debe completarse"));

        assertEquals(0, clienteRepository.count());
    }

    @Test
    void rechazaCuandoElEmailTieneFormatoInvalido() throws Exception {
        mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson("Tienda El Sol", "El Sol S.A.S.", "900123456", "correo-invalido", "3001234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe corregir los siguientes datos: email"))
                .andExpect(jsonPath("$.errores[0].campo").value("email"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El email debe corregirse"));

        assertEquals(0, clienteRepository.count());
    }

    @Test
    void rechazaCuandoElDocumentoYaExiste() throws Exception {
        mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson("Tienda El Sol", "El Sol S.A.S.", "900123456", "contacto@elsol.com", "3001234567")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson("Otra Tienda", "Otra S.A.S.", "900123456", "otro@elsol.com", "3010000000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("ya existe un cliente registrado con ese documento"));

        assertEquals(1, clienteRepository.count());
    }

    @Test
    void actualizaDatosCuandoElActorEsAdministrador() throws Exception {
        UUID adminId = registrarAdministrador();
        UUID clienteId = registrar("Tienda El Sol", "El Sol S.A.S.", "900123456", "contacto@elsol.com", "3001234567");
        Cliente antes = clienteRepository.findById(clienteId).orElseThrow();

        mockMvc.perform(put("/clientes/" + clienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacionJson(
                                "Tienda El Sol Actualizada",
                                "El Sol Actualizada S.A.S.",
                                "contacto@elsol.com",
                                "3110000000",
                                adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idCliente").value(clienteId.toString()))
                .andExpect(jsonPath("$.estado").value("pendiente_de_validacion"))
                .andExpect(jsonPath("$.nombreComercial").value("Tienda El Sol Actualizada"))
                .andExpect(jsonPath("$.razonSocial").value("El Sol Actualizada S.A.S."))
                .andExpect(jsonPath("$.email").value("contacto@elsol.com"))
                .andExpect(jsonPath("$.telefono").value("3110000000"))
                .andExpect(jsonPath("$.mensaje").value("actualización exitosa"));

        Cliente despues = clienteRepository.findById(clienteId).orElseThrow();
        assertEquals("Tienda El Sol Actualizada", despues.getNombreComercial());
        assertEquals("El Sol Actualizada S.A.S.", despues.getRazonSocial());
        assertEquals("contacto@elsol.com", despues.getEmail());
        assertEquals("3110000000", despues.getTelefono());
        assertEquals(antes.getNitDocumento(), despues.getNitDocumento());
        assertEquals(antes.getEstado(), despues.getEstado());
        assertEquals(antes.getFechaRegistro(), despues.getFechaRegistro());
        assertEquals(Cliente.ROL_CLIENTE, despues.getRol());
        assertEquals(adminId, despues.getActualizadoPor());
        assertNotNull(despues.getFechaActualizacion());
    }

    @Test
    void rechazaActualizacionCuandoElClienteNoExiste() throws Exception {
        UUID adminId = registrarAdministrador();
        long antes = clienteRepository.count();

        mockMvc.perform(put("/clientes/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacionJson(
                                "Tienda Nueva",
                                "Tienda Nueva S.A.S.",
                                "nueva@elsol.com",
                                "3001234567",
                                adminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("el cliente no fue encontrado"));

        assertEquals(antes, clienteRepository.count());
    }

    @Test
    void rechazaActualizacionCuandoElEmailTieneFormatoInvalido() throws Exception {
        UUID adminId = registrarAdministrador();
        UUID clienteId = registrar("Tienda El Sol", "El Sol S.A.S.", "900123456", "contacto@elsol.com", "3001234567");

        mockMvc.perform(put("/clientes/" + clienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacionJson(
                                "Tienda El Sol",
                                "El Sol S.A.S.",
                                "correo-invalido",
                                "3001234567",
                                adminId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe corregir los siguientes datos: email"))
                .andExpect(jsonPath("$.errores[0].campo").value("email"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El email debe corregirse"));

        assertEquals("contacto@elsol.com", clienteRepository.findById(clienteId).orElseThrow().getEmail());
    }

    @Test
    void rechazaActualizacionCuandoFaltaUnDatoObligatorio() throws Exception {
        UUID adminId = registrarAdministrador();
        UUID clienteId = registrar("Tienda El Sol", "El Sol S.A.S.", "900123456", "contacto@elsol.com", "3001234567");

        mockMvc.perform(put("/clientes/" + clienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacionJson("   ", "El Sol S.A.S.", "contacto@elsol.com", "3001234567", adminId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Debe completar los siguientes datos: nombre comercial"))
                .andExpect(jsonPath("$.errores[0].campo").value("nombreComercial"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El nombre comercial debe completarse"));

        assertEquals("Tienda El Sol", clienteRepository.findById(clienteId).orElseThrow().getNombreComercial());
    }

    @Test
    void rechazaActualizacionCuandoElActorNoEstaAutorizado() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "El Sol S.A.S.", "900123456", "contacto@elsol.com", "3001234567");
        UUID otroClienteId = registrar("Otra Tienda", "Otra S.A.S.", "800999111", "otro@elsol.com", null);

        mockMvc.perform(put("/clientes/" + clienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacionJson(
                                "Nombre Cambiado",
                                "Razon Cambiada",
                                "nuevo@elsol.com",
                                "3110000000",
                                otroClienteId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("no cuento con autorización para realizar esta acción"));

        mockMvc.perform(put("/clientes/" + clienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacionJson(
                                "Nombre Cambiado",
                                "Razon Cambiada",
                                "nuevo@elsol.com",
                                "3110000000",
                                UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("no cuento con autorización para realizar esta acción"));

        mockMvc.perform(put("/clientes/" + clienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacionJson(
                                "Nombre Cambiado",
                                "Razon Cambiada",
                                "nuevo@elsol.com",
                                "3110000000",
                                null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("no cuento con autorización para realizar esta acción"));

        Cliente sinCambios = clienteRepository.findById(clienteId).orElseThrow();
        assertEquals("Tienda El Sol", sinCambios.getNombreComercial());
        assertEquals("contacto@elsol.com", sinCambios.getEmail());
        assertEquals("900123456", sinCambios.getNitDocumento());
        assertEquals("pendiente_de_validacion", sinCambios.getEstado());
        assertNull(sinCambios.getActualizadoPor());
    }

    @Test
    void rechazaActualizacionCuandoElEmailYaPerteneceAOtroCliente() throws Exception {
        UUID adminId = registrarAdministrador();
        UUID clienteId = registrar("Tienda El Sol", "El Sol S.A.S.", "900123456", "contacto@elsol.com", "3001234567");
        registrar("Otra Tienda", "Otra S.A.S.", "800999111", "otro@elsol.com", null);

        mockMvc.perform(put("/clientes/" + clienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacionJson(
                                "Tienda El Sol",
                                "El Sol S.A.S.",
                                "otro@elsol.com",
                                "3001234567",
                                adminId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("ya existe un cliente registrado con ese email"));

        assertEquals("contacto@elsol.com", clienteRepository.findById(clienteId).orElseThrow().getEmail());
    }

    private UUID registrar(String nombre, String razon, String nit, String email, String telefono) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clienteJson(nombre, razon, nit, email, telefono)))
                .andExpect(status().isCreated())
                .andReturn();
        return idDe(resultado);
    }

    private UUID registrarAdministrador() throws Exception {
        UUID adminId = registrar("Admin Bank", "Admin S.A.S.", "100000001", "admin@bank.com", "3000000001");
        Cliente admin = clienteRepository.findById(adminId).orElseThrow();
        admin.setRol(Cliente.ROL_ADMINISTRADOR);
        clienteRepository.saveAndFlush(admin);
        return adminId;
    }

    private String actualizacionJson(
            String nombreComercial,
            String razonSocial,
            String email,
            String telefono,
            UUID actualizadoPor) {
        String telefonoJson = telefono == null ? "" : ",\n  \"telefono\": \"" + telefono + "\"";
        String actorJson = actualizadoPor == null ? "" : ",\n  \"actualizadoPor\": \"" + actualizadoPor + "\"";
        return """
                {
                  "nombreComercial": "%s",
                  "razonSocial": "%s",
                  "email": "%s"%s%s
                }
                """.formatted(nombreComercial, razonSocial, email, telefonoJson, actorJson);
    }

    private UUID idDe(MvcResult resultado) throws Exception {
        String idCliente = JsonPath.read(resultado.getResponse().getContentAsString(), "$.idCliente");
        return UUID.fromString(idCliente);
    }

    private String clienteJson(String nombreComercial, String razonSocial, String nitDocumento, String email, String telefono) {
        String telefonoJson = telefono == null ? "" : ",\n  \"telefono\": \"" + telefono + "\"";
        return """
                {
                  "nombreComercial": "%s",
                  "razonSocial": "%s",
                  "nitDocumento": "%s",
                  "email": "%s"%s
                }
                """.formatted(nombreComercial, razonSocial, nitDocumento, email, telefonoJson);
    }
}
