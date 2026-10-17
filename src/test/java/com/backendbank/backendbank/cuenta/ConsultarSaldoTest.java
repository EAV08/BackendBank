package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.ClienteRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsultarSaldoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Test
    void informaElSaldoDisponibleDeUnaCuentaActiva() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("150.50"));

        mockMvc.perform(post("/cuentas/saldo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson(clienteId, cuenta.getIdCuenta())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(150.50))
                .andExpect(jsonPath("$.estado").value("activo"))
                .andExpect(jsonPath("$.numeroCuenta").value("1000000001"))
                .andExpect(jsonPath("$.idCliente").value(clienteId.toString()))
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_SALDO));

        assertEquals(0, new BigDecimal("150.50").compareTo(
                cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow().getSaldo()));
    }

    @Test
    void noMuestraElSaldoCuandoLaCuentaEstaBloqueada() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000002", Cuenta.ESTADO_BLOQUEADO, new BigDecimal("80.00"));

        mockMvc.perform(post("/cuentas/saldo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson(clienteId, cuenta.getIdCuenta())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_BLOQUEADA))
                .andExpect(jsonPath("$.saldo").doesNotExist());
    }

    @Test
    void noMuestraElSaldoCuandoLaCuentaEstaInactiva() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000003", Cuenta.ESTADO_INACTIVO, new BigDecimal("40.00"));

        mockMvc.perform(post("/cuentas/saldo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson(clienteId, cuenta.getIdCuenta())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_INACTIVA))
                .andExpect(jsonPath("$.saldo").doesNotExist());
    }

    @Test
    void noMuestraElSaldoDeUnaCuentaAjena() throws Exception {
        UUID duenoId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        UUID otroId = registrar("Otra Tienda", "800999111", "otro@elsol.com");
        Cuenta cuenta = guardarCuenta(duenoId, "1000000004", Cuenta.ESTADO_ACTIVO, new BigDecimal("10.00"));

        mockMvc.perform(post("/cuentas/saldo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson(otroId, cuenta.getIdCuenta())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_NO_ENCONTRADA))
                .andExpect(jsonPath("$.saldo").doesNotExist());
    }

    private String consultaJson(UUID idCliente, UUID idCuenta) {
        return """
                {
                  "idCliente": "%s",
                  "idCuenta": "%s"
                }
                """.formatted(idCliente, idCuenta);
    }

    private Cuenta guardarCuenta(UUID idCliente, String numero, String estado, BigDecimal saldo) {
        Cuenta cuenta = new Cuenta();
        cuenta.setIdCliente(idCliente);
        cuenta.setNumeroCuenta(numero);
        cuenta.setEstado(estado);
        cuenta.setSaldo(saldo);
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
