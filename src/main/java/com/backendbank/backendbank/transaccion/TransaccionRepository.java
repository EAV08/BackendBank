package com.backendbank.backendbank.transaccion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransaccionRepository extends JpaRepository<Transaccion, UUID> {

    List<Transaccion> findByIdCuentaDestinoOrderByFechaCreacionAsc(UUID idCuentaDestino);
}
