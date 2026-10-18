package com.backendbank.backendbank.cuenta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneradorNumeroCuentaTest {

    private final GeneradorNumeroCuenta generador = new GeneradorNumeroCuenta();

    @Test
    void generaNumerosDeLaLongitudEsperadaFormadosSoloPorDigitos() {
        for (int i = 0; i < 50; i++) {
            String numero = generador.generar();

            assertEquals(GeneradorNumeroCuenta.LONGITUD, numero.length());
            assertTrue(numero.chars().allMatch(Character::isDigit));
        }
    }
}