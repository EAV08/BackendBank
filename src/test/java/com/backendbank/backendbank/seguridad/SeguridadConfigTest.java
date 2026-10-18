package com.backendbank.backendbank.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeguridadConfigTest {

    private final PasswordEncoder encoder = new SeguridadConfig().passwordEncoder();

    @Test
    void cifraLaContrasenaConBcryptYPermiteVerificarla() {
        String hash = encoder.encode("Clave123");

        assertNotEquals("Clave123", hash);
        assertTrue(hash.startsWith("$2"));
        assertTrue(encoder.matches("Clave123", hash));
        assertFalse(encoder.matches("otra", hash));
    }
}