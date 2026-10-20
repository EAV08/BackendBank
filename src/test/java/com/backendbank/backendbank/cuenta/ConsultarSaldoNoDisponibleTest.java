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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsultarSaldoNoDisponibleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CuentaRepository cuentaRepository;

    @MockitoBean
    private ConsultaSaldo consultaSaldo;

    @Test
    void noMuestraElSaldoCuandoElBancoNoPuedeInformarlo() throws Exception {
        when(consultaSaldo.disponible(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("banco"));
        UUID clienteId = registrar("Tienda El Sol", "900555444", "saldo@elsol.com");
        Cuenta cuenta = new Cuenta();
        cuenta.setIdCliente(clienteId);
        cuenta.setNumeroCuenta("1000000099");
        cuenta.setEstado(Cuenta.ESTADO_ACTIVO);
        cuenta.setSaldo(new BigDecimal("25.00"));
        cuenta = cuentaRepository.saveAndFlush(cuenta);

        mockMvc.perform(post("/cuentas/saldo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idCliente": "%s",
                                  "idCuenta": "%s"
                                }
                                """.formatted(clienteId, cuenta.getIdCuenta())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_MAS_TARDE))
                .andExpect(jsonPath("$.saldo").doesNotExist());

        assertEquals(0, new BigDecimal("25.00").compareTo(
                cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow().getSaldo()));
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
