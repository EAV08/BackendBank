package com.backendbank.backendbank.seguridad;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-con-mas-de-32-bytes!!";

    private final TokenService tokenService = new TokenService(SECRETO, 60);

    private TokenService.TokenInvalidoException rechazar(String token) {
        return assertThrows(TokenService.TokenInvalidoException.class, () -> tokenService.validar(token));
    }

    @Test
    void generaUnTokenQuePuedeValidarseYConservaLosDatos() {
        UUID idCliente = UUID.randomUUID();

        String token = tokenService.generar(idCliente, "luna", "CLIENTE");
        TokenService.SesionToken sesion = tokenService.validar(token);

        assertEquals(idCliente, sesion.idCliente());
        assertEquals("luna", sesion.usuario());
        assertEquals("CLIENTE", sesion.rol());
    }

    @Test
    void elTokenVenceDentroDelTiempoConfigurado() {
        String token = tokenService.generar(UUID.randomUUID(), "luna", "CLIENTE");

        Instant expira = tokenService.validar(token).expira();

        assertTrue(expira.isAfter(Instant.now().plus(Duration.ofMinutes(59))));
        assertFalse(expira.isAfter(Instant.now().plus(Duration.ofMinutes(61))));
    }

    @Test
    void rechazaUnTokenNuloOEnBlanco() {
        assertEquals(TokenService.MENSAJE_INVALIDO, rechazar(null).getMessage());
        assertEquals(TokenService.MENSAJE_INVALIDO, rechazar("   ").getMessage());
    }

    @Test
    void rechazaUnTokenQueNoTieneTresPartes() {
        rechazar("abc.def");
        rechazar("a.b.c.d");
    }

    @Test
    void rechazaUnTokenConLaFirmaAlterada() {
        String token = tokenService.generar(UUID.randomUUID(), "luna", "CLIENTE");
        String alterado = token.substring(0, token.lastIndexOf('.') + 1) + "firma-invalida";

        rechazar(alterado);
    }

    @Test
    void rechazaUnTokenCuyaFirmaNoEsBase64() {
        rechazar("a.b.***");
    }

    @Test
    void rechazaUnTokenFirmadoConOtroSecreto() {
        TokenService otro = new TokenService("otro-secreto-distinto-con-mas-de-32-bytes", 60);
        String token = otro.generar(UUID.randomUUID(), "luna", "CLIENTE");

        rechazar(token);
    }

    @Test
    void rechazaUnTokenExpirado() {
        TokenService sinVigencia = new TokenService(SECRETO, 0);
        String token = sinVigencia.generar(UUID.randomUUID(), "luna", "CLIENTE");

        assertThrows(TokenService.TokenInvalidoException.class, () -> sinVigencia.validar(token));
    }

    @Test
    void noPermiteUnSecretoDeMenosDe32Bytes() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new TokenService("corto", 60));

        assertEquals("seguridad.jwt.secreto debe tener al menos 32 bytes", ex.getMessage());
    }

    @Test
    void aceptaUnSecretoDeExactamente32Bytes() {
        String secretoJusto = "12345678901234567890123456789012";

        assertDoesNotThrow(() -> new TokenService(secretoJusto, 60));
    }
}