package com.backendbank.backendbank.cuenta;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class GeneradorNumeroCuenta {

    static final int LONGITUD = 10;

    private final SecureRandom aleatorio = new SecureRandom();

    public String generar() {
        StringBuilder numero = new StringBuilder(LONGITUD);
        for (int indice = 0; indice < LONGITUD; indice++) {
            numero.append(aleatorio.nextInt(10));
        }
        return numero.toString();
    }
}
