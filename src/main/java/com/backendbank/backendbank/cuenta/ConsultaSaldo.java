package com.backendbank.backendbank.cuenta;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ConsultaSaldo {

    public BigDecimal disponible(Cuenta cuenta) {
        if (cuenta.getSaldo() == null) {
            throw new IllegalStateException("el saldo no está disponible");
        }
        return cuenta.getSaldo();
    }
}
