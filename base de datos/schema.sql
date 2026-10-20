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
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_clientes_actualizado_por'
    ) THEN
        ALTER TABLE clientes
            ADD CONSTRAINT fk_clientes_actualizado_por
            FOREIGN KEY (actualizado_por) REFERENCES clientes (id_cliente);
    END IF;
END $$;

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
