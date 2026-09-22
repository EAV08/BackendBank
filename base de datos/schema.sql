CREATE TABLE clientes (
    id_cliente       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre_comercial VARCHAR(150) NOT NULL,
    razon_social     VARCHAR(200) NOT NULL,
    nit_documento    VARCHAR(30)  NOT NULL UNIQUE,
    email            VARCHAR(150) NOT NULL UNIQUE,
    usuario          VARCHAR(80)  NOT NULL UNIQUE,
    contrasena       VARCHAR(255) NOT NULL,
    telefono         VARCHAR(20),
    estado              VARCHAR(30)  NOT NULL DEFAULT 'activo'
                         CHECK (estado IN ('pendiente_de_validacion', 'activo', 'inactivo', 'bloqueado')),
    fecha_registro      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rol                 VARCHAR(20)  NOT NULL DEFAULT 'CLIENTE'
                         CHECK (rol IN ('CLIENTE', 'ADMINISTRADOR')),
    actualizado_por     UUID,
    fecha_actualizacion TIMESTAMPTZ,
    motivo              VARCHAR(200)
);
