package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
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
import java.util.HashSet;
import java.util.Set;
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
class CrearCuentaFinancieraTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Test
    void creaLaCuentaActivaConNumeroUnicoYSaldoCero() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");

        MvcResult resultado = solicitar(clienteId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("activo"))
                .andExpect(jsonPath("$.saldo").value(0))
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_EXITO))
                .andExpect(jsonPath("$.idCuenta").isNotEmpty())
                .andExpect(jsonPath("$.numeroCuenta").isNotEmpty())
                .andExpect(jsonPath("$.idCliente").value(clienteId.toString()))
                .andReturn();

        UUID idCuenta = UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.idCuenta"));
        String numero = JsonPath.read(resultado.getResponse().getContentAsString(), "$.numeroCuenta");
        Cuenta guardada = cuentaRepository.findById(idCuenta).orElseThrow();
        assertEquals(clienteId, guardada.getIdCliente());
        assertEquals(Cuenta.ESTADO_ACTIVO, guardada.getEstado());
        assertEquals(numero, guardada.getNumeroCuenta());
        assertEquals(0, guardada.getSaldo().compareTo(BigDecimal.ZERO));
        assertEquals(GeneradorNumeroCuenta.LONGITUD, guardada.getNumeroCuenta().length());
        assertNotNull(guardada.getFechaVinculacion());

        MvcResult segunda = solicitar(clienteId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldo").value(0))
                .andReturn();
        String otroNumero = JsonPath.read(segunda.getResponse().getContentAsString(), "$.numeroCuenta");
        assertNotEquals(numero, otroNumero);
        assertEquals(2, cuentaRepository.countByIdClienteAndEstado(clienteId, Cuenta.ESTADO_ACTIVO));
    }

    @Test
    void rechazaCuandoElClienteNoHaCompletadoLaValidacion() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        fijarEstado(clienteId, Cliente.ESTADO_PENDIENTE_DE_VALIDACION);

        solicitar(clienteId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_SIN_VALIDACION));

        assertEquals(0, cuentaRepository.count());
    }

    @Test
    void rechazaCuandoAlcanzaElMaximoDeCuentasActivas() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        Set<String> numeros = new HashSet<>();
        for (int indice = 0; indice < CuentaService.MAX_CUENTAS_ACTIVAS; indice++) {
            MvcResult creada = solicitar(clienteId).andExpect(status().isCreated()).andReturn();
            numeros.add(JsonPath.read(creada.getResponse().getContentAsString(), "$.numeroCuenta"));
        }
        assertEquals(CuentaService.MAX_CUENTAS_ACTIVAS, numeros.size());

        solicitar(clienteId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_LIMITE));

        assertEquals(CuentaService.MAX_CUENTAS_ACTIVAS, cuentaRepository.count());
    }

    @Test
    void rechazaCuandoElPerfilEstaBloqueado() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        fijarEstado(clienteId, Cliente.ESTADO_BLOQUEADO);

        solicitar(clienteId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_PERFIL_BLOQUEADO));

        assertEquals(0, cuentaRepository.count());
    }

    @Test
    void rechazaCuandoElPerfilEstaInactivo() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        fijarEstado(clienteId, Cliente.ESTADO_INACTIVO);

        solicitar(clienteId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("su usuario está inactivo y debe contactar al banco"));

        assertEquals(0, cuentaRepository.count());
    }

    private org.springframework.test.web.servlet.ResultActions solicitar(UUID idCliente) throws Exception {
        return mockMvc.perform(post("/cuentas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "idCliente": "%s"
                        }
                        """.formatted(idCliente)));
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

    private void fijarEstado(UUID idCliente, String estado) {
        Cliente cliente = clienteRepository.findById(idCliente).orElseThrow();
        cliente.setEstado(estado);
        clienteRepository.saveAndFlush(cliente);
    }
}
