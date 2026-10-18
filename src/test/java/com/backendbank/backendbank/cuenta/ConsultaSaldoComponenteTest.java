package com.backendbank.backendbank.cuenta;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsultaSaldoComponenteTest {

    private final ConsultaSaldo consultaSaldo = new ConsultaSaldo();

    @Test
    void devuelveElSaldoDeLaCuenta() {
        Cuenta cuenta = new Cuenta();
        cuenta.setSaldo(new BigDecimal("75.50"));

        assertEquals(new BigDecimal("75.50"), consultaSaldo.disponible(cuenta));
    }

    @Test
    void fallaCuandoLaCuentaNoTieneSaldo() {
        Cuenta cuenta = new Cuenta();
        cuenta.setSaldo(null);

        assertThrows(IllegalStateException.class, () -> consultaSaldo.disponible(cuenta));
    }
}