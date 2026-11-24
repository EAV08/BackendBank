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
class TransferirDineroTest {

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
    void transfiereEntreCuentasPropiasYRegistraLaTransaccion() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta origen = guardarCuenta(clienteId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        Cuenta destino = guardarCuenta(clienteId, "4000000002", Cuenta.ESTADO_ACTIVO, new BigDecimal("10.00"));

        mockMvc.perform(post("/cuentas/transferencia")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000001", "4000000002", "7.25")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCuentaOrigen").value("4000000001"))
                .andExpect(jsonPath("$.numeroCuentaDestino").value("4000000002"))
                .andExpect(jsonPath("$.monto").value(7.25))
                .andExpect(jsonPath("$.saldoOrigen").value(32.75))
                .andExpect(jsonPath("$.mensaje").value(TransferenciaService.MENSAJE_EXITO))
                .andExpect(jsonPath("$.idTransaccion").isNotEmpty());

        assertEquals(0, new BigDecimal("32.75").compareTo(saldoDe(origen)));
        assertEquals(0, new BigDecimal("17.25").compareTo(saldoDe(destino)));
        Transaccion transaccion = transaccionRepository
                .findByIdCuentaOrigenOrderByFechaCreacionAsc(origen.getIdCuenta()).get(0);
        assertEquals(destino.getIdCuenta(), transaccion.getIdCuentaDestino());
        assertEquals(0, new BigDecimal("7.25").compareTo(transaccion.getMonto()));
        assertEquals(Transaccion.ESTADO_APROBADO, transaccion.getEstado());
        EventoTransaccion evento = eventoTransaccionRepository
                .findByIdTransaccion(transaccion.getIdTransaccion()).get(0);
        assertEquals(EventoTransaccion.TRANSFERENCIA, evento.getTipoEvento());
    }

    @Test
    void transfiereACuentaDeOtroCliente() throws Exception {
        UUID origenId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta origen = guardarCuenta(origenId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        UUID destinoId = registrar("Otra Tienda", "800999111", "otro@elsol.com");
        Cuenta destino = guardarCuenta(destinoId, "4000000002", Cuenta.ESTADO_ACTIVO, BigDecimal.ZERO);

        mockMvc.perform(post("/cuentas/transferencia")
                        .header("Authorization", autorizacion(origenId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000001", "4000000002", "15.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCuentaDestino").value("4000000002"));

        assertEquals(0, new BigDecimal("25.00").compareTo(saldoDe(origen)));
        assertEquals(0, new BigDecimal("15.00").compareTo(saldoDe(destino)));
    }

    @Test
    void rechazaMontoCeroYNegativoSinMoverSaldos() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta origen = guardarCuenta(clienteId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        Cuenta destino = guardarCuenta(clienteId, "4000000002", Cuenta.ESTADO_ACTIVO, new BigDecimal("10.00"));

        mockMvc.perform(post("/cuentas/transferencia")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000001", "4000000002", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(TransferenciaService.MENSAJE_MONTO));

        mockMvc.perform(post("/cuentas/transferencia")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000001", "4000000002", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(TransferenciaService.MENSAJE_MONTO));

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(origen)));
        assertEquals(0, new BigDecimal("10.00").compareTo(saldoDe(destino)));
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void rechazaCuandoElSaldoEsInsuficiente() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta origen = guardarCuenta(clienteId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("5.00"));
        Cuenta destino = guardarCuenta(clienteId, "4000000002", Cuenta.ESTADO_ACTIVO, new BigDecimal("10.00"));

        mockMvc.perform(post("/cuentas/transferencia")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000001", "4000000002", "10.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(TransferenciaService.MENSAJE_SALDO));

        assertEquals(0, new BigDecimal("5.00").compareTo(saldoDe(origen)));
        assertEquals(0, new BigDecimal("10.00").compareTo(saldoDe(destino)));
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void rechazaCuandoLaCuentaOrigenEsAjena() throws Exception {
        UUID duenoId = registrar("Otra Tienda", "800999111", "otro@elsol.com");
        Cuenta ajena = guardarCuenta(duenoId, "4000000002", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta propia = guardarCuenta(clienteId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));

        mockMvc.perform(post("/cuentas/transferencia")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000002", "4000000001", "7.25")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(TransferenciaService.MENSAJE_AJENA));

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(ajena)));
        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(propia)));
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void rechazaCuandoLaCuentaDestinoNoExiste() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta origen = guardarCuenta(clienteId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));

        mockMvc.perform(post("/cuentas/transferencia")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000001", "9999999999", "7.25")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_NO_EXISTE));

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(origen)));
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void rechazaCuandoLaCuentaDestinoEstaBloqueada() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta origen = guardarCuenta(clienteId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        Cuenta destino = guardarCuenta(clienteId, "4000000002", Cuenta.ESTADO_BLOQUEADO, new BigDecimal("10.00"));

        mockMvc.perform(post("/cuentas/transferencia")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000001", "4000000002", "7.25")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_BLOQUEADA));

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(origen)));
        assertEquals(0, new BigDecimal("10.00").compareTo(saldoDe(destino)));
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void rechazaTransferirALaMismaCuenta() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta cuenta = guardarCuenta(clienteId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));

        mockMvc.perform(post("/cuentas/transferencia")
                        .header("Authorization", autorizacion(clienteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000001", "4000000001", "7.25")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(TransferenciaService.MENSAJE_MISMA_CUENTA));

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(cuenta)));
        assertEquals(0, transaccionRepository.count());
    }

    @Test
    void rechazaCuandoNoHaySesion() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Cuenta origen = guardarCuenta(clienteId, "4000000001", Cuenta.ESTADO_ACTIVO, new BigDecimal("40.00"));
        guardarCuenta(clienteId, "4000000002", Cuenta.ESTADO_ACTIVO, new BigDecimal("10.00"));

        mockMvc.perform(post("/cuentas/transferencia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferenciaJson("4000000001", "4000000002", "7.25")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("debe ingresar de nuevo"));

        assertEquals(0, new BigDecimal("40.00").compareTo(saldoDe(origen)));
        assertEquals(0, transaccionRepository.count());
    }

    private BigDecimal saldoDe(Cuenta cuenta) {
        return cuentaRepository.findById(cuenta.getIdCuenta()).orElseThrow().getSaldo();
    }

    private String autorizacion(UUID idCliente) {
        Cliente cliente = clienteRepository.findById(idCliente).orElseThrow();
        return "Bearer " + tokenService.generar(cliente.getIdCliente(), cliente.getUsuario(), cliente.getRol());
    }

    private String transferenciaJson(String origen, String destino, String monto) {
        return """
                {
                  "numeroCuentaOrigen": "%s",
                  "numeroCuentaDestino": "%s",
                  "monto": %s
                }
                """.formatted(origen, destino, monto);
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
