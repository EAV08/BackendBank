package com.backendbank.backendbank.cuenta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);
}
