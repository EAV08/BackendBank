package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteRepository;
import com.backendbank.backendbank.seguridad.TokenService;
import com.backendbank.backendbank.transaccion.EventoTransaccion;
import com.backendbank.backendbank.transaccion.EventoTransaccionRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DepositarDineroTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private TransaccionRepository transaccionRepository;

    @Autowired
    private EventoTransaccionRepository eventoTransaccionRepository;

    @Autowired
    private TokenService tokenService;

    @Test
    void muestraElPropietarioYNoCambiaElSaldoAntesDeConfirmar() throws Exception {
        UUID duenoId = registrar("Lucía Ríos", "900123456", "lucia@rios.com");
        Cuenta cuenta = guardarCuenta(duenoId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        UUID quienId = registrar("Cajero Norte", "800111222", "cajero@norte.com");

        mockMvc.perform(post("/cuentas/deposito")
                        .header("Authorization", autorizacion(quienId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositoJson("4000000001", "7.25")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCuenta").value("4000000001"))
                .andExpect(jsonPath("$.nombrePropietario").value("Lucía Ríos"))
                .andExpect(jsonPath("$.monto").value(7.25))
                .andExpect(jsonPath("$.mensaje").value(DepositoService.MENSAJE_CONFIRME))
                .andExpect(jsonPath("$.saldo").doesNotExist());

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(cuenta)));
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void completaElDepositoCuandoElClienteConfirma() throws Exception {
        UUID duenoId = registrar("Lucía Ríos", "900123456", "lucia@rios.com");
        Cuenta cuenta = guardarCuenta(duenoId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        UUID quienId = registrar("Cajero Norte", "800111222", "cajero@norte.com");

        mockMvc.perform(post("/cuentas/deposito/confirmacion")
                        .header("Authorization", autorizacion(quienId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositoJson("4000000001", "7.25")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroCuenta").value("4000000001"))
                .andExpect(jsonPath("$.nombrePropietario").value("Lucía Ríos"))
                .andExpect(jsonPath("$.monto").value(7.25))
                .andExpect(jsonPath("$.saldo").value(47.25))
                .andExpect(jsonPath("$.mensaje").value("constancia de depósito por 7.25"))
                .andExpect(jsonPath("$.idTransaccion").isNotEmpty());

        assertEquals(0, new BigDecimal("47.25").compareTo(saldoDe(cuenta)));
        Transaccion movimiento = transaccionRepository.findByIdCuentaDestinoOrderByFechaCreacionAsc(cuenta.getIdCuenta())
                .get(0);
        assertEquals(quienId, movimiento.getIdCliente());
        assertEquals(cuenta.getIdCuenta(), movimiento.getIdCuentaDestino());
        assertEquals(null, movimiento.getIdCuentaOrigen());
        assertEquals(Transaccion.ESTADO_APROBADO, movimiento.getEstado());
        assertEquals(Transaccion.MONEDA_COP, movimiento.getMoneda());
        assertEquals(0, new BigDecimal("7.25").compareTo(movimiento.getMonto()));
        EventoTransaccion evento = eventoTransaccionRepository.findByIdTransaccion(movimiento.getIdTransaccion()).get(0);
        assertEquals(EventoTransaccion.DEPOSITO, evento.getTipoEvento());
        assertEquals(Transaccion.ESTADO_APROBADO, evento.getEstadoNuevo());
    }

    @Test
    void noCambiaElSaldoSiElClienteNoConfirma() throws Exception {
        UUID duenoId = registrar("Lucía Ríos", "900123456", "lucia@rios.com");
        Cuenta cuenta = guardarCuenta(duenoId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        UUID quienId = registrar("Cajero Norte", "800111222", "cajero@norte.com");

        mockMvc.perform(post("/cuentas/deposito")
                        .header("Authorization", autorizacion(quienId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositoJson("4000000001", "7.25")))
                .andExpect(status().isOk());

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(cuenta)));
        assertTrue(transaccionRepository.findByIdCuentaDestinoOrderByFechaCreacionAsc(cuenta.getIdCuenta()).isEmpty());
    }

    @Test
    void rechazaCuandoLaCuentaNoExiste() throws Exception {
        UUID quienId = registrar("Cajero Norte", "800111222", "cajero@norte.com");

        mockMvc.perform(post("/cuentas/deposito")
                        .header("Authorization", autorizacion(quienId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositoJson("999999999", "7.25")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(DepositoService.MENSAJE_CUENTA_NO_EXISTE));

        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void rechazaCuandoLaCuentaEstaInactiva() throws Exception {
        UUID duenoId = registrar("Lucía Ríos", "900123456", "lucia@rios.com");
        Cuenta cuenta = guardarCuenta(duenoId, "4000000001", Cuenta.ESTADO_INACTIVO, new BigDecimal("40.00"));
        UUID quienId = registrar("Cajero Norte", "800111222", "cajero@norte.com");

        mockMvc.perform(post("/cuentas/deposito/confirmacion")
                        .header("Authorization", autorizacion(quienId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositoJson("4000000001", "7.25")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(DepositoService.MENSAJE_CUENTA_INACTIVA));

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(cuenta)));
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void rechazaMontoCeroYNegativoSinMoverElSaldo() throws Exception {
        UUID duenoId = registrar("Lucía Ríos", "900123456", "lucia@rios.com");
        Cuenta cuenta = guardarCuenta(duenoId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        UUID quienId = registrar("Cajero Norte", "800111222", "cajero@norte.com");

        mockMvc.perform(post("/cuentas/deposito")
                        .header("Authorization", autorizacion(quienId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositoJson("4000000001", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(DepositoService.MENSAJE_MONTO));

        mockMvc.perform(post("/cuentas/deposito/confirmacion")
                        .header("Authorization", autorizacion(quienId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositoJson("4000000001", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(DepositoService.MENSAJE_MONTO));

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(cuenta)));
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void rechazaCuandoElClienteNoEstaIdentificado() throws Exception {
        UUID duenoId = registrar("Lucía Ríos", "900123456", "lucia@rios.com");
        Cuenta cuenta = guardarCuenta(duenoId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));

        mockMvc.perform(post("/cuentas/deposito")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depositoJson("4000000001", "7.25")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("debe identificarse para continuar"));

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(cuenta)));
        assertEquals(0, transaccionRepository.count());
    }

    private BigDecimal saldoDe(Cuenta cuenta) {
        return cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow().getSaldo();
    }

    private String autorizacion(UUID idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente).orElseThrow();
        return "Bearer " + tokenService.generar(cliente.getIdCliente(), cliente.getUsuario(), cliente.getRol());
    }

    private String depositoJson(String numeroCuenta, String monto) {
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
