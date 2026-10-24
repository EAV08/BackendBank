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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsultarEstadoCuentaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Test
    void muestraElEstadoDeLaCuentaCuandoLasCredencialesSonCorrectas() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        guardarCuenta(clienteId, "1000000001", Cuenta.ESTADO_ACTIVO);

        mockMvc.perform(post("/cuentas/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson("contacto", "clave123", "1000000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCuenta").value("1000000001"))
                .andExpect(jsonPath("$.estado").value("activo"))
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_ESTADO));

        Cuenta bloqueada = guardarCuenta(clienteId, "1000000002", Cuenta.ESTADO_BLOQUEADO);
        mockMvc.perform(post("/cuentas/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson("contacto", "clave123", bloqueada.getNumeroCuenta())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("bloqueado"))
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_ESTADO));
    }

    @Test
    void rechazaCuandoElUsuarioNoExisteYNoMuestraEstado() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        guardarCuenta(clienteId, "1000000001", Cuenta.ESTADO_ACTIVO);

        mockMvc.perform(post("/cuentas/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson("desconocido", "clave123", "1000000001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CLIENTE_NO_EXISTE))
                .andExpect(jsonPath("$.estado").doesNotExist());
    }

    @Test
    void rechazaCuandoLaContrasenaEsIncorrectaYNoMuestraEstado() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        guardarCuenta(clienteId, "1000000001", Cuenta.ESTADO_ACTIVO);

        mockMvc.perform(post("/cuentas/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson("contacto", "clave-incorrecta", "1000000001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CONTRASENA))
                .andExpect(jsonPath("$.estado").doesNotExist());

        assertEquals(0, clienteRepository.findByUsuario("contacto").orElseThrow().getIntentosFallidos());
    }

    @Test
    void rechazaCuandoElNumeroDeCuentaNoExisteYNoMuestraEstado() throws Exception {
        registrar("Tienda El Sol", "900123456", "contacto@elsol.com");

        mockMvc.perform(post("/cuentas/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson("contacto", "clave123", "9999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_NO_EXISTE))
                .andExpect(jsonPath("$.estado").doesNotExist());
    }

    @Test
    void noMuestraElEstadoDeUnaCuentaDeOtroCliente() throws Exception {
        UUID duenoId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        registrar("Otra Tienda", "800999111", "otro@elsol.com");
        guardarCuenta(duenoId, "1000000001", Cuenta.ESTADO_ACTIVO);

        mockMvc.perform(post("/cuentas/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson("otro", "clave123", "1000000001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CUENTA_NO_EXISTE))
                .andExpect(jsonPath("$.estado").doesNotExist());
    }

    @Test
    void muestraElEstadoCuandoLaCuentaEstaInactiva() throws Exception {
        UUID clienteId = registrar("Tienda El Sol", "900123456", "contacto@elsol.com");
        guardarCuenta(clienteId, "1000000003", Cuenta.ESTADO_INACTIVO);

        mockMvc.perform(post("/cuentas/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson("contacto", "clave123", "1000000003")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroCuenta").value("1000000003"))
                .andExpect(jsonPath("$.estado").value("inactivo"))
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_ESTADO));
    }

    @Test
    void rechazaLaContrasenaAunqueElNumeroDeCuentaNoExista() throws Exception {
        registrar("Tienda El Sol", "900123456", "contacto@elsol.com");

        mockMvc.perform(post("/cuentas/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consultaJson("contacto", "clave-incorrecta", "9999999999")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(CuentaService.MENSAJE_CONTRASENA))
                .andExpect(jsonPath("$.estado").doesNotExist());

        assertEquals(0, clienteRepository.findByUsuario("contacto").orElseThrow().getIntentosFallidos());
    }

    private String consultaJson(String usuario, String contrasena, String numeroCuenta) {
        return """
                {
                  "usuario": "%s",
                  "contrasena": "%s",
                  "numeroCuenta": "%s"
                }
                """.formatted(usuario, contrasena, numeroCuenta);
    }

    private Cuenta guardarCuenta(UUID idCliente, String numero, String estado) {
        Cuenta cuenta = new Cuenta();
        cuenta.setIdCliente(idCliente);
        cuenta.setNumeroCuenta(numero);
        cuenta.setEstado(estado);
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
