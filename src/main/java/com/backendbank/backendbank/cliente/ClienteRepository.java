package com.backendbank.backendbank.cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    boolean existsByNitDocumento(String nitDocumento);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdClienteNot(String email, UUID idCliente);

    boolean existsByUsuario(String usuario);

    Optional<Cliente> findByUsuario(String usuario);
}
