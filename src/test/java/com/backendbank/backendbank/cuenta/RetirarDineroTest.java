package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteRepository;
import com.backendbank.backendbank.seguridad.TokenService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RetirarDineroTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private RetiroRepository retiroRepository;

    @Autowired
    private TokenService tokenService;

    @Test
    void retiraDeLaCuentaPropiaYLoDejaEnElHistorial() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("500000.00"));

        mockMvc.perform(post("/cuentas/retiro")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retiroJson("1000000001", "150000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCuenta").value("1000000001"))
                .andExpect(jsonPath("$.monto").value(150000.00))
                .andExpect(jsonPath("$.saldo").value(350000.00))
                .andExpect(jsonPath("$.mensaje").value(RetiroService.MENSAJE_EXITO))
                .andExpect(jsonPath("$.idRetiro").isNotEmpty());

        assertEquals(0, new BigDecimal("350000.00").compareTo(saldoDe(cuenta)));
        Retiro historial = retiroRepository.findByIdCuentaOrderByFechaAsc(cuenta.getIdCuenta()).get(0);
        assertEquals(clienteId, historial.getIdCliente());
        assertEquals(0, new BigDecimal("150000.00").compareTo(historial.getMonto()));
    }

    @Test
    void rechazaMontoCeroYNegativoSinMoverElSaldo() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("100000.00"));

        mockMvc.perform(post("/cuentas/retiro")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retiroJson("1000000001", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(RetiroService.MENSAJE_MONTO));

        mockMvc.perform(post("/cuentas/retiro")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retiroJson("1000000001", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(RetiroService.MENSAJE_MONTO));

        assertEquals(0, new BigDecimal("100000.00").compareTo(saldoDe(cuenta)));
        assertTrue(retiroRepository.findByIdCuentaOrderByFechaAsc(cuenta.getIdCuenta()).isEmpty());
    }

    @Test
    void rechazaCuandoLaCuentaEsDeOtroCliente() throws Exception {
        UUID duenoId = registrar("Otra Tienda", "800999111", "otro@elsol.com");
        Cuenta ajena = guardarCuenta(duenoId, "555000111", Cuenta.ESTADO_ACTIVO, new BigDecimal("500000.00"));
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");

        mockMvc.perform(post("/cuentas/retiro")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retiroJson("555000111", "150000")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(RetiroService.MENSAJE_AJENA));

        assertEquals(0, new BigDecimal("500000.00").compareTo(saldoDe(ajena)));
        assertTrue(retiroRepository.findByIdCuentaOrderByFechaAsc(ajena.getIdCuenta()).isEmpty());
    }

    @Test
    void rechazaCuandoNoHaySesion() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "1000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("500000.00"));

        mockMvc.perform(post("/cuentas/retiro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retiroJson("1000000001", "150000")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("debe ingresar de nuevo"));

        assertEquals(0, new BigDecimal("500000.00").compareTo(saldoDe(cuenta)));
        assertEquals(0, retiroRepository.count());
    }

    private BigDecimal saldoDe(Cuenta cuenta) {
        return cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow().getSaldo();
    }

    private String autorizacion(UUID idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente).orElseThrow();
        return "Bearer " + tokenService.generar(cliente.getIdCliente(), cliente.getUsuario(), cliente.getRol());
    }

    private String retiroJson(String numeroCuenta, String monto) {
        return """
                {
                  "numeroCuenta": "%s",
                  "monto": %s
                }
                """.formatted(numeroCuenta, monto);
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
