CREATE TABLE clientes (
    id_cliente       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre_comercial VARCHAR(150) NOT NULL,
    razon_social     VARCHAR(200) NOT NULL,
    nit_documento    VARCHAR(30)  NOT NULL UNIQUE,
    email            VARCHAR(150) NOT NULL UNIQUE,
    telefono         VARCHAR(20),
    estado           VARCHAR(20)  NOT NULL DEFAULT 'activo'
                      CHECK (estado IN ('activo', 'inactivo', 'suspendido')),
    fecha_registro   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
