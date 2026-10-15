package com.backendbank.backendbank.cuenta;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CrearCuentaGeneracionFallidaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CuentaRepository cuentaRepository;

    @MockitoBean
    private GeneradorNumeroCuenta generadorNumeroCuenta;

    @Test
    void noCreaLaCuentaCuandoFallaElMotorDeNumeros() throws Exception {
        when(generadorNumeroCuenta.generar()).thenThrow(new IllegalStateException("motor no disponible"));
        UUID clienteId = registrar("Tienda El Sol", "900777666", "motor@elsol.com");
        long antes = cuentaRepository.count();

        mockMvc.perform(post("/cuentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idCliente": "%s"
                                }
                                """.formatted(clienteId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_TECNICO));

        assertEquals(antes, cuentaRepository.count());
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
