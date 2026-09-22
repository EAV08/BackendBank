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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
