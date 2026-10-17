-- 1. clientes

CREATE TABLE IF NOT EXISTS clientes (
                                        id_cliente          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        nombre_comercial    VARCHAR(150) NOT NULL,
                                        razon_social        VARCHAR(200) NOT NULL,
                                        nit_documento       VARCHAR(30)  NOT NULL UNIQUE,
                                        email               VARCHAR(150) NOT NULL UNIQUE,
                                        usuario             VARCHAR(80)  NOT NULL UNIQUE,
                                        contrasena          VARCHAR(255) NOT NULL,
                                        telefono            VARCHAR(20),
                                        estado              VARCHAR(30)  NOT NULL DEFAULT 'activo'
                                            CHECK (estado IN ('pendiente_de_validacion', 'activo', 'inactivo', 'bloqueado')),
                                        fecha_registro      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                        rol                 VARCHAR(20)  NOT NULL DEFAULT 'CLIENTE'
                                            CHECK (rol IN ('CLIENTE', 'ADMINISTRADOR')),
                                        actualizado_por     UUID,
                                        fecha_actualizacion TIMESTAMPTZ,
                                        motivo              VARCHAR(200),
                                        intentos_fallidos   INTEGER      NOT NULL DEFAULT 0
);

ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS intentos_fallidos INTEGER NOT NULL DEFAULT 0;

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_clientes_actualizado_por') THEN
            ALTER TABLE clientes
                ADD CONSTRAINT fk_clientes_actualizado_por
                    FOREIGN KEY (actualizado_por) REFERENCES clientes (id_cliente);
        END IF;
    END $$;



-- 2. cuentas

CREATE TABLE IF NOT EXISTS cuentas (
                                       id_cuenta               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       id_cliente              UUID           NOT NULL REFERENCES clientes (id_cliente),
                                       numero_cuenta           VARCHAR(20)    NOT NULL UNIQUE,
                                       estado                  VARCHAR(30)    NOT NULL DEFAULT 'activo'
                                           CHECK (estado IN ('activo', 'inactivo', 'bloqueado')),
                                       saldo                   NUMERIC(19, 2) NOT NULL DEFAULT 0
                                           CHECK (saldo >= 0),
                                       motivo                  VARCHAR(200),
                                       operaciones_suspendidas BOOLEAN        NOT NULL DEFAULT FALSE,
                                       fecha_vinculacion       TIMESTAMPTZ    NOT NULL DEFAULT now(),
                                       fecha_actualizacion     TIMESTAMPTZ
);


CREATE INDEX IF NOT EXISTS idx_cuentas_cliente_estado
    ON cuentas (id_cliente, estado);


-- 3. credenciales_api

CREATE TABLE IF NOT EXISTS credenciales_api (
                                                id_credencial     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                id_cliente        UUID         NOT NULL REFERENCES clientes (id_cliente),
                                                api_key           VARCHAR(64)  NOT NULL UNIQUE,
                                                api_secret_hash   VARCHAR(255) NOT NULL,
                                                ambiente          VARCHAR(20)  NOT NULL
                                                    CHECK (ambiente IN ('SANDBOX', 'PRODUCCION')),
                                                estado            VARCHAR(20)  NOT NULL DEFAULT 'activo'
                                                    CHECK (estado IN ('activo', 'revocado', 'expirado')),
                                                fecha_creacion    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                                fecha_expiracion  TIMESTAMPTZ,
                                                CHECK (fecha_expiracion IS NULL OR fecha_expiracion > fecha_creacion)
);

CREATE INDEX IF NOT EXISTS idx_credenciales_api_cliente
    ON credenciales_api (id_cliente);


-- 4. reportes

CREATE TABLE IF NOT EXISTS reportes (
                                        id_reporte        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        id_cliente        UUID        NOT NULL REFERENCES clientes (id_cliente),
                                        tipo_reporte      VARCHAR(50) NOT NULL,
                                        periodo_inicio    DATE        NOT NULL,
                                        periodo_fin       DATE        NOT NULL,
                                        fecha_generacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
                                        contenido         JSONB,
                                        CHECK (periodo_fin >= periodo_inicio)
);

CREATE INDEX IF NOT EXISTS idx_reportes_cliente
    ON reportes (id_cliente);


-- 5. transacciones

CREATE TABLE IF NOT EXISTS transacciones (
                                             id_transaccion      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                             id_cliente          UUID          NOT NULL REFERENCES clientes (id_cliente),
                                             id_cuenta_origen    UUID          NOT NULL REFERENCES cuentas (id_cuenta),
                                             id_cuenta_destino   UUID          NOT NULL REFERENCES cuentas (id_cuenta),
                                             monto               NUMERIC(14,2) NOT NULL CHECK (monto > 0),
                                             moneda              CHAR(3)       NOT NULL CHECK (moneda ~ '^[A-Z]{3}$'),
                                             estado              VARCHAR(20)   NOT NULL DEFAULT 'pendiente'
                                                 CHECK (estado IN ('pendiente', 'completada', 'rechazada', 'reversada')),
                                             referencia_externa  VARCHAR(100)  UNIQUE,
                                             fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                             fecha_actualizacion TIMESTAMPTZ,
                                             CHECK (id_cuenta_origen <> id_cuenta_destino)
);

CREATE INDEX IF NOT EXISTS idx_transacciones_cliente
    ON transacciones (id_cliente);
CREATE INDEX IF NOT EXISTS idx_transacciones_cuenta_origen
    ON transacciones (id_cuenta_origen);
CREATE INDEX IF NOT EXISTS idx_transacciones_cuenta_destino
    ON transacciones (id_cuenta_destino);


-- 6. eventos_transaccion

CREATE TABLE IF NOT EXISTS eventos_transaccion (
                                                   id_evento        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                                   id_transaccion   UUID        NOT NULL REFERENCES transacciones (id_transaccion),
                                                   tipo_evento      VARCHAR(50) NOT NULL,
                                                   estado_anterior  VARCHAR(20),
                                                   estado_nuevo     VARCHAR(20),
                                                   detalle          TEXT,
                                                   fecha_evento     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_eventos_transaccion_transaccion
    ON eventos_transaccion (id_transaccion);
