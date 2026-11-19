package com.backendbank.backendbank.transaccion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoTransaccionRepository extends JpaRepository<EventoTransaccion, Long> {

    List<EventoTransaccion> findByIdTransaccion(UUID idTransaccion);
}
