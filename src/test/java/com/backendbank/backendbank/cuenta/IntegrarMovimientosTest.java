package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteRepository;
import com.backendbank.backendbank.seguridad.TokenService;
import com.backendbank.backendbank.transaccion.Transaccion;
import com.backendbank.backendbank.transaccion.TransaccionRepository;
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
class IntegrarMovimientosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private RetiroRepository retiroRepository;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private TokenService tokenService;

    @Test
    void depositaYLuegoRetiraSobreLaMismaCuenta() throws Exception {
        UUID clienteId = registrar("Lucía Ríos", "900123456", "lucia@rios.com");
        BigDecimal saldoInicial = new BigDecimal("40.00");
        BigDecimal monto = new BigDecimal("7.25");
        Cuenta cuenta = guardarCuenta(clienteId, "4000000001", saldoInicial);
        String token = autorizacion(clienteId);

        mockMvc.perform(post("/cuentas/deposito/confirmacion")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movimientoJson("4000000001", monto.toPlainString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldo").value(saldoInicial.add(monto).doubleValue()));

        mockMvc.perform(post("/cuentas/retiro")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movimientoJson("4000000001", monto.toPlainString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(saldoInicial.doubleValue()));

        assertEquals(0, saldoInicial.compareTo(saldoDe(cuenta)));
        Transaccion deposito = transaccionRepository
                .findByIdCuentaDestinoOrderByFechaCreacionAsc(cuenta.getIdCuenta()).get(0);
        Transaccion retiro = transaccionRepository
                .findByIdCuentaOrigenOrderByFechaCreacionAsc(cuenta.getIdCuenta()).get(0);
        assertEquals(Transaccion.ESTADO_APROBADO, deposito.getEstado());
        assertEquals(null, deposito.getIdCuentaOrigen());
        assertEquals(cuenta.getIdCuenta(), retiro.getIdCuentaOrigen());
        assertEquals(null, retiro.getIdCuentaDestino());
        assertEquals(1, retiroRepository.findByIdCuentaOrderByFechaAsc(cuenta.getIdCuenta()).size());
    }

    @Test
    void unMontoNuloNoCreaTransaccionNiMueveElSaldo() throws Exception {
        UUID clienteId = registrar("Lucía Ríos", "900123456", "lucia@rios.com");
        BigDecimal saldoInicial = new BigDecimal("40.00");
        Cuenta cuenta = guardarCuenta(clienteId, "4000000001", saldoInicial);
        String token = autorizacion(clienteId);
        String sinMonto = """
                {
                  "numeroCuenta": "4000000001"
                }
                """;

        mockMvc.perform(post("/cuentas/deposito/confirmacion")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sinMonto))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/cuentas/retiro")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sinMonto))
                .andExpect(status().isBadRequest());

        assertEquals(0, saldoInicial.compareTo(saldoDe(cuenta)));
        assertEquals(0, transaccionRepository.count());
        assertEquals(0, retiroRepository.count());
    }

    private BigDecimal saldoDe(Cuenta cuenta) {
        return cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow().getSaldo();
    }

    private String autorizacion(UUID idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente).orElseThrow();
        return "Bearer " + tokenService.generar(cliente.getIdCliente(), cliente.getUsuario(), cliente.getRol());
    }

    private String movimientoJson(String numeroCuenta, String monto) {
        return """
                {
                  "numeroCuenta": "%s",
                  "monto": %s
                }
                """.formatted(numeroCuenta, monto);
    }

    private Cuenta guardarCuenta(UUID idCliente, String numero, BigDecimal saldo) {
        Cuenta cuenta = new Cuenta();
        cuenta.setIdCliente(idCliente);
        cuenta.setNumeroCuenta(numero);
        cuenta.setEstado(Cuenta.ESTADO_ACTIVO);
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
