
# EAV08-2026-2

### Diagrama Entidad Relación 

```mermaid
erDiagram
    CLIENTES ||--o{ CUENTAS_BANCARIAS : posee
    CLIENTES ||--o{ CREDENCIALES_API : genera
    CLIENTES ||--o{ TRANSACCIONES : origina
    CLIENTES ||--o{ REPORTES : solicita
    CUENTAS_BANCARIAS ||--o{ TRANSACCIONES : "cuenta origen"
    CUENTAS_BANCARIAS ||--o{ TRANSACCIONES : "cuenta destino"
    TRANSACCIONES ||--o{ EVENTOS_TRANSACCION : registra

    CLIENTES {
        uuid id_cliente PK
        varchar nombre_comercial
        varchar razon_social
        varchar nit_documento UK
        varchar email UK
        varchar telefono
        varchar estado
        timestamptz fecha_registro
    }

    CUENTAS_BANCARIAS {
        uuid id_cuenta PK
        uuid id_cliente FK
        varchar numero_cuenta
        varchar banco
        varchar tipo_cuenta
        char moneda
        varchar estado
        timestamptz fecha_vinculacion
    }

    CREDENCIALES_API {
        uuid id_credencial PK
        uuid id_cliente FK
        varchar api_key UK
        varchar api_secret_hash
        varchar ambiente
        varchar estado
        timestamptz fecha_creacion
        timestamptz fecha_expiracion
    }

    TRANSACCIONES {
        uuid id_transaccion PK
        uuid id_cliente FK
        uuid id_cuenta_origen FK
        uuid id_cuenta_destino FK
        numeric monto
        char moneda
        varchar estado
        varchar referencia_externa UK
        timestamptz fecha_creacion
        timestamptz fecha_actualizacion
    }

    EVENTOS_TRANSACCION {
        bigint id_evento PK
        uuid id_transaccion FK
        varchar tipo_evento
        varchar estado_anterior
        varchar estado_nuevo
        text detalle
        timestamptz fecha_evento
    }

    REPORTES {
        uuid id_reporte PK
        uuid id_cliente FK
        varchar tipo_reporte
        date periodo_inicio
        date periodo_fin
        timestamptz fecha_generacion
        jsonb contenido
    }
```

# Modelo lógico

![Diagrama ERD](Diagramas/ERD.jpg)

---
# Diagrama de paquetes

![Diagrama de paquetes](./Diagramas/diagrama_de_paquetes_EAV08.drawio.png)

# Diagrama de despliegue

![Diagrama de paquetes](./Diagramas/diagrama_de_desplique_EAV08.drawio.png)

# Diagrama de componentes
![Diagrama de paquetes](./Diagramas/diagrama_de_componentes_EAV08.drawio.png)


# Criterio 4- Modelo Fisico

Ver [`schema.sql`](./schema.sql)






