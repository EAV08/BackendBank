package com.backendbank.backendbank.cliente;

import com.backendbank.backendbank.seguridad.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    private static final String HASH = "$2a$10$hashDePruebaParaLasPruebasUnitarias";

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private ClienteService clienteService;

    // ---------- datos de apoyo ----------

    private Cliente cliente(String estado) {
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "idCliente", UUID.randomUUID());
        cliente.setUsuario("luna");
        cliente.setContrasena(HASH);
        cliente.setEstado(estado);
        cliente.setRol(Cliente.ROL_CLIENTE);
        return cliente;
    }

    private Cliente administrador() {
        Cliente administrador = cliente(Cliente.ESTADO_ACTIVO);
        administrador.setRol(Cliente.ROL_ADMINISTRADOR);
        return administrador;
    }

    private RegistroClienteRequest solicitudRegistro() {
        return new RegistroClienteRequest("Panadería Luna", "Luna SAS",
                "900123456", "luna@correo.com", "luna", "Clave123", "3001234567");
    }

    // doReturn permite configurar varias búsquedas por id en una misma prueba
    // sin que Mockito estricto lo tome por un error de argumentos.
    private void existe(Cliente cliente) {
        doReturn(Optional.of(cliente)).when(clienteRepository).findById(cliente.getIdCliente());
    }

    private void noExiste(UUID idCliente) {
        doReturn(Optional.empty()).when(clienteRepository).findById(idCliente);
    }

    private void guardarTalComoLlega() {
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ClienteDuplicadoException registrarFallandoConRestriccion(String detalle) {
        when(clienteRepository.saveAndFlush(any(Cliente.class)))
                .thenThrow(new DataIntegrityViolationException("violación de restricción", new RuntimeException(detalle)));
        RegistroClienteRequest solicitud = solicitudRegistro();
        return assertThrows(ClienteDuplicadoException.class, () -> clienteService.registrar(solicitud));
    }

    // ---------- registrar ----------

    @Test
    void registraUnClienteNuevoYGuardaLaContrasenaCifrada() {
        when(passwordEncoder.encode("Clave123")).thenReturn(HASH);
        guardarTalComoLlega();

        RegistroClienteResponse respuesta = clienteService.registrar(solicitudRegistro());

        ArgumentCaptor<Cliente> guardado = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).saveAndFlush(guardado.capture());
        assertEquals(HASH, guardado.getValue().getContrasena());
        assertEquals(Cliente.ESTADO_ACTIVO, guardado.getValue().getEstado());
        assertEquals(Cliente.ROL_CLIENTE, guardado.getValue().getRol());
        assertEquals("3001234567", guardado.getValue().getTelefono());
        assertEquals(Cliente.ESTADO_ACTIVO, respuesta.estado());
        assertEquals(ClienteService.MENSAJE_EXITO, respuesta.mensaje());
    }

    @Test
    void guardaElTelefonoEnBlancoComoNulo() {
        guardarTalComoLlega();
        RegistroClienteRequest solicitud = new RegistroClienteRequest("Panadería Luna", "Luna SAS",
                "900123456", "luna@correo.com", "luna", "Clave123", "   ");

        clienteService.registrar(solicitud);

        ArgumentCaptor<Cliente> guardado = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).saveAndFlush(guardado.capture());
        assertNull(guardado.getValue().getTelefono());
    }

    @Test
    void rechazaRegistrarConUnDocumentoYaExistente() {
        when(clienteRepository.existsByNitDocumento("900123456")).thenReturn(true);
        RegistroClienteRequest solicitud = solicitudRegistro();

        ClienteDuplicadoException ex = assertThrows(ClienteDuplicadoException.class,
                () -> clienteService.registrar(solicitud));

        assertEquals(ClienteService.MENSAJE_DOCUMENTO_DUPLICADO, ex.getMessage());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaRegistrarConUnEmailYaExistente() {
        when(clienteRepository.existsByEmail("luna@correo.com")).thenReturn(true);
        RegistroClienteRequest solicitud = solicitudRegistro();

        ClienteDuplicadoException ex = assertThrows(ClienteDuplicadoException.class,
                () -> clienteService.registrar(solicitud));

        assertEquals(ClienteService.MENSAJE_EMAIL_DUPLICADO, ex.getMessage());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaRegistrarConUnUsuarioYaExistente() {
        when(clienteRepository.existsByUsuario("luna")).thenReturn(true);
        RegistroClienteRequest solicitud = solicitudRegistro();

        ClienteDuplicadoException ex = assertThrows(ClienteDuplicadoException.class,
                () -> clienteService.registrar(solicitud));

        assertEquals(ClienteService.MENSAJE_USUARIO_DUPLICADO, ex.getMessage());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void traduceUnaRestriccionDeEmailDeLaBaseDeDatos() {
        ClienteDuplicadoException ex = registrarFallandoConRestriccion("duplicate key value violates uk_email");

        assertEquals(ClienteService.MENSAJE_EMAIL_DUPLICADO, ex.getMessage());
    }

    @Test
    void traduceUnaRestriccionDeUsuarioDeLaBaseDeDatos() {
        ClienteDuplicadoException ex = registrarFallandoConRestriccion("duplicate key value violates uk_usuario");

        assertEquals(ClienteService.MENSAJE_USUARIO_DUPLICADO, ex.getMessage());
    }

    @Test
    void traduceCualquierOtraRestriccionComoDocumentoDuplicado() {
        ClienteDuplicadoException ex = registrarFallandoConRestriccion("duplicate key value violates uk_nit_documento");

        assertEquals(ClienteService.MENSAJE_DOCUMENTO_DUPLICADO, ex.getMessage());
    }

    // ---------- actualizar ----------

    @Test
    void actualizaLosDatosCuandoElActorEsAdministrador() {
        Cliente administrador = administrador();
        Cliente objetivo = cliente(Cliente.ESTADO_ACTIVO);
        existe(administrador);
        existe(objetivo);
        guardarTalComoLlega();
        ActualizarClienteRequest solicitud = new ActualizarClienteRequest("Nuevo Nombre", "Nueva Razón SAS",
                "nuevo@correo.com", "  3109876543  ", administrador.getIdCliente());

        ActualizarClienteResponse respuesta = clienteService.actualizar(objetivo.getIdCliente(), solicitud);

        assertEquals("Nuevo Nombre", respuesta.nombreComercial());
        assertEquals("Nueva Razón SAS", respuesta.razonSocial());
        assertEquals("nuevo@correo.com", respuesta.email());
        assertEquals("3109876543", respuesta.telefono());
        assertEquals(ClienteService.MENSAJE_ACTUALIZACION_EXITOSA, respuesta.mensaje());
        assertEquals(administrador.getIdCliente(), objetivo.getActualizadoPor());
        assertNotNull(objetivo.getFechaActualizacion());
    }

    @Test
    void rechazaActualizarConElEmailDeOtroCliente() {
        Cliente administrador = administrador();
        Cliente objetivo = cliente(Cliente.ESTADO_ACTIVO);
        existe(administrador);
        existe(objetivo);
        when(clienteRepository.existsByEmailAndIdClienteNot("otro@correo.com", objetivo.getIdCliente()))
                .thenReturn(true);
        ActualizarClienteRequest solicitud = new ActualizarClienteRequest("Nombre", "Razón",
                "otro@correo.com", null, administrador.getIdCliente());

        ClienteDuplicadoException ex = assertThrows(ClienteDuplicadoException.class,
                () -> clienteService.actualizar(objetivo.getIdCliente(), solicitud));

        assertEquals(ClienteService.MENSAJE_EMAIL_DUPLICADO, ex.getMessage());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaActualizarUnClienteQueNoExiste() {
        Cliente administrador = administrador();
        UUID idInexistente = UUID.randomUUID();
        existe(administrador);
        noExiste(idInexistente);
        ActualizarClienteRequest solicitud = new ActualizarClienteRequest("Nombre", "Razón",
                "nuevo@correo.com", null, administrador.getIdCliente());

        ClienteNoEncontradoException ex = assertThrows(ClienteNoEncontradoException.class,
                () -> clienteService.actualizar(idInexistente, solicitud));

        assertEquals(ClienteService.MENSAJE_NO_ENCONTRADO, ex.getMessage());
    }

    @Test
    void traduceUnaRestriccionDeLaBaseDeDatosAlActualizar() {
        Cliente administrador = administrador();
        Cliente objetivo = cliente(Cliente.ESTADO_ACTIVO);
        existe(administrador);
        existe(objetivo);
        when(clienteRepository.saveAndFlush(any(Cliente.class)))
                .thenThrow(new DataIntegrityViolationException("violación de restricción"));
        ActualizarClienteRequest solicitud = new ActualizarClienteRequest("Nombre", "Razón",
                "nuevo@correo.com", null, administrador.getIdCliente());

        ClienteDuplicadoException ex = assertThrows(ClienteDuplicadoException.class,
                () -> clienteService.actualizar(objetivo.getIdCliente(), solicitud));

        assertEquals(ClienteService.MENSAJE_EMAIL_DUPLICADO, ex.getMessage());
    }

    @Test
    void rechazaActualizarSinIndicarQuienLoHace() {
        UUID idObjetivo = UUID.randomUUID();
        ActualizarClienteRequest solicitud = new ActualizarClienteRequest("Nombre", "Razón",
                "nuevo@correo.com", null, null);

        SesionNoVigenteException ex = assertThrows(SesionNoVigenteException.class,
                () -> clienteService.actualizar(idObjetivo, solicitud));

        assertEquals(ClienteService.MENSAJE_SESION, ex.getMessage());
    }

    // ---------- cambiar estado ----------

    @Test
    void inactivaAUnClienteActivoCuandoElActorEsAdministrador() {
        Cliente administrador = administrador();
        Cliente objetivo = cliente(Cliente.ESTADO_ACTIVO);
        existe(administrador);
        existe(objetivo);
        guardarTalComoLlega();
        CambiarEstadoRequest solicitud = new CambiarEstadoRequest(
                Cliente.ESTADO_INACTIVO, "Solicitud del cliente", administrador.getIdCliente());

        CambiarEstadoResponse respuesta = clienteService.cambiarEstado(objetivo.getIdCliente(), solicitud);

        assertEquals(Cliente.ESTADO_INACTIVO, respuesta.estado());
        assertEquals("Solicitud del cliente", respuesta.motivo());
        assertEquals(ClienteService.MENSAJE_CAMBIO_EXITOSO, respuesta.mensaje());
        assertEquals(administrador.getIdCliente(), objetivo.getActualizadoPor());
        assertNotNull(objetivo.getFechaActualizacion());
    }

    @Test
    void reactivaAUnClienteInactivo() {
        Cliente administrador = administrador();
        Cliente objetivo = cliente(Cliente.ESTADO_INACTIVO);
        existe(administrador);
        existe(objetivo);
        guardarTalComoLlega();
        CambiarEstadoRequest solicitud = new CambiarEstadoRequest(
                Cliente.ESTADO_ACTIVO, "Regularizó su situación", administrador.getIdCliente());

        CambiarEstadoResponse respuesta = clienteService.cambiarEstado(objetivo.getIdCliente(), solicitud);

        assertEquals(Cliente.ESTADO_ACTIVO, respuesta.estado());
    }

    @Test
    void rechazaCambiarAlEstadoEnElQueYaEsta() {
        Cliente administrador = administrador();
        Cliente objetivo = cliente(Cliente.ESTADO_ACTIVO);
        existe(administrador);
        existe(objetivo);
        CambiarEstadoRequest solicitud = new CambiarEstadoRequest(
                Cliente.ESTADO_ACTIVO, "Motivo", administrador.getIdCliente());

        CambioEstadoRechazadoException ex = assertThrows(CambioEstadoRechazadoException.class,
                () -> clienteService.cambiarEstado(objetivo.getIdCliente(), solicitud));

        assertEquals(ClienteService.MENSAJE_MISMO_ESTADO, ex.getMessage());
    }

    @Test
    void rechazaUnaTransicionDeEstadoNoPermitida() {
        Cliente administrador = administrador();
        Cliente objetivo = cliente(Cliente.ESTADO_BLOQUEADO);
        existe(administrador);
        existe(objetivo);
        CambiarEstadoRequest solicitud = new CambiarEstadoRequest(
                Cliente.ESTADO_ACTIVO, "Motivo", administrador.getIdCliente());

        CambioEstadoRechazadoException ex = assertThrows(CambioEstadoRechazadoException.class,
                () -> clienteService.cambiarEstado(objetivo.getIdCliente(), solicitud));

        assertEquals(ClienteService.MENSAJE_CAMBIO_NO_PERMITIDO, ex.getMessage());
        assertEquals(Cliente.ESTADO_BLOQUEADO, objetivo.getEstado());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaCambiarElEstadoSiElActorNoEsAdministrador() {
        Cliente actor = cliente(Cliente.ESTADO_ACTIVO);
        existe(actor);
        UUID idObjetivo = UUID.randomUUID();
        CambiarEstadoRequest solicitud = new CambiarEstadoRequest(
                Cliente.ESTADO_INACTIVO, "Motivo", actor.getIdCliente());

        AccesoDenegadoException ex = assertThrows(AccesoDenegadoException.class,
                () -> clienteService.cambiarEstado(idObjetivo, solicitud));

        assertEquals(ClienteService.MENSAJE_NO_AUTORIZADO, ex.getMessage());
    }

    @Test
    void rechazaCambiarElEstadoSiElActorNoEstaActivo() {
        Cliente actor = administrador();
        actor.setEstado(Cliente.ESTADO_INACTIVO);
        existe(actor);
        UUID idObjetivo = UUID.randomUUID();
        CambiarEstadoRequest solicitud = new CambiarEstadoRequest(
                Cliente.ESTADO_INACTIVO, "Motivo", actor.getIdCliente());

        SesionNoVigenteException ex = assertThrows(SesionNoVigenteException.class,
                () -> clienteService.cambiarEstado(idObjetivo, solicitud));

        assertEquals(ClienteService.MENSAJE_SESION, ex.getMessage());
    }

    @Test
    void rechazaCambiarElEstadoSiElActorNoExiste() {
        UUID idActor = UUID.randomUUID();
        noExiste(idActor);
        UUID idObjetivo = UUID.randomUUID();
        CambiarEstadoRequest solicitud = new CambiarEstadoRequest(Cliente.ESTADO_INACTIVO, "Motivo", idActor);

        assertThrows(SesionNoVigenteException.class, () -> clienteService.cambiarEstado(idObjetivo, solicitud));
    }

    @Test
    void rechazaCambiarElEstadoDeUnClienteQueNoExiste() {
        Cliente administrador = administrador();
        UUID idInexistente = UUID.randomUUID();
        existe(administrador);
        noExiste(idInexistente);
        CambiarEstadoRequest solicitud = new CambiarEstadoRequest(
                Cliente.ESTADO_INACTIVO, "Motivo", administrador.getIdCliente());

        ClienteNoEncontradoException ex = assertThrows(ClienteNoEncontradoException.class,
                () -> clienteService.cambiarEstado(idInexistente, solicitud));

        assertEquals(ClienteService.MENSAJE_NO_ENCONTRADO, ex.getMessage());
    }

    // ---------- ingresar ----------

    @Test
    void ingresaYEntregaUnTokenCuandoLaContrasenaCoincide() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Clave123", HASH)).thenReturn(true);
        when(tokenService.generar(cliente.getIdCliente(), "luna", Cliente.ROL_CLIENTE))
                .thenReturn("token-de-prueba");

        IngresoResponse respuesta = clienteService.ingresar(new IngresoRequest("luna", "Clave123"));

        assertEquals("token-de-prueba", respuesta.token());
        assertEquals(Cliente.ESTADO_ACTIVO, respuesta.estado());
        assertEquals(ClienteService.MENSAJE_INGRESO, respuesta.mensaje());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void reiniciaLosIntentosFallidosAlIngresarCorrectamente() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        cliente.setIntentosFallidos(2);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Clave123", HASH)).thenReturn(true);

        clienteService.ingresar(new IngresoRequest("luna", "Clave123"));

        assertEquals(0, cliente.getIntentosFallidos());
        verify(clienteRepository).saveAndFlush(cliente);
    }

    @Test
    void rechazaElIngresoDeUnUsuarioInexistenteSinDelatarloPorElTiempo() {
        when(clienteRepository.findByUsuario("fantasma")).thenReturn(Optional.empty());
        IngresoRequest solicitud = new IngresoRequest("fantasma", "x");

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> clienteService.ingresar(solicitud));

        assertEquals(ClienteService.MENSAJE_CREDENCIALES, ex.getMessage());
        verify(passwordEncoder).matches(eq("x"), any());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void sumaUnIntentoFallidoSinBloquearTodavia() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        IngresoRequest solicitud = new IngresoRequest("luna", "mala");

        assertThrows(CredencialesInvalidasException.class, () -> clienteService.ingresar(solicitud));

        assertEquals(1, cliente.getIntentosFallidos());
        assertEquals(Cliente.ESTADO_ACTIVO, cliente.getEstado());
        verify(clienteRepository).saveAndFlush(cliente);
    }

    @Test
    void bloqueaAlClienteEnElTercerIntentoFallido() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        cliente.setIntentosFallidos(ClienteService.MAX_INTENTOS_FALLIDOS - 1);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        IngresoRequest solicitud = new IngresoRequest("luna", "mala");

        IngresoRechazadoException ex = assertThrows(IngresoRechazadoException.class,
                () -> clienteService.ingresar(solicitud));

        assertEquals(ClienteService.MENSAJE_BLOQUEADO, ex.getMessage());
        assertEquals(Cliente.ESTADO_BLOQUEADO, cliente.getEstado());
        assertEquals(ClienteService.MOTIVO_BLOQUEO, cliente.getMotivo());
        assertEquals(ClienteService.MAX_INTENTOS_FALLIDOS, cliente.getIntentosFallidos());
        assertNotNull(cliente.getFechaActualizacion());
    }

    @Test
    void noSumaIntentosCuandoElClienteNoEstaActivo() {
        Cliente cliente = cliente(Cliente.ESTADO_INACTIVO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        IngresoRequest solicitud = new IngresoRequest("luna", "mala");

        assertThrows(CredencialesInvalidasException.class, () -> clienteService.ingresar(solicitud));

        assertEquals(0, cliente.getIntentosFallidos());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaElIngresoDeUnClienteInactivoConLaContrasenaCorrecta() {
        Cliente cliente = cliente(Cliente.ESTADO_INACTIVO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Clave123", HASH)).thenReturn(true);
        IngresoRequest solicitud = new IngresoRequest("luna", "Clave123");

        IngresoRechazadoException ex = assertThrows(IngresoRechazadoException.class,
                () -> clienteService.ingresar(solicitud));

        assertEquals(ClienteService.MENSAJE_INACTIVO, ex.getMessage());
    }

    @Test
    void rechazaElIngresoDeUnClienteBloqueadoConLaContrasenaCorrecta() {
        Cliente cliente = cliente(Cliente.ESTADO_BLOQUEADO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Clave123", HASH)).thenReturn(true);
        IngresoRequest solicitud = new IngresoRequest("luna", "Clave123");

        IngresoRechazadoException ex = assertThrows(IngresoRechazadoException.class,
                () -> clienteService.ingresar(solicitud));

        assertEquals(ClienteService.MENSAJE_BLOQUEADO, ex.getMessage());
    }

    @Test
    void rechazaElIngresoDeUnClientePendienteDeValidacion() {
        Cliente cliente = cliente(Cliente.ESTADO_PENDIENTE_DE_VALIDACION);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Clave123", HASH)).thenReturn(true);
        IngresoRequest solicitud = new IngresoRequest("luna", "Clave123");

        IngresoRechazadoException ex = assertThrows(IngresoRechazadoException.class,
                () -> clienteService.ingresar(solicitud));

        assertEquals(ClienteService.MENSAJE_PENDIENTE, ex.getMessage());
    }

    @Test
    void migraLaContrasenaGuardadaEnTextoPlanoAlIngresar() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        cliente.setContrasena("Clave123");
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.encode("Clave123")).thenReturn(HASH);

        IngresoResponse respuesta = clienteService.ingresar(new IngresoRequest("luna", "Clave123"));

        assertEquals(HASH, cliente.getContrasena());
        assertEquals(ClienteService.MENSAJE_INGRESO, respuesta.mensaje());
        verify(clienteRepository).saveAndFlush(cliente);
    }

    @Test
    void noCambiaLaContrasenaEnTextoPlanoCuandoLaClaveEsIncorrecta() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        cliente.setContrasena("Clave123");
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        IngresoRequest solicitud = new IngresoRequest("luna", "otra");

        assertThrows(CredencialesInvalidasException.class, () -> clienteService.ingresar(solicitud));

        assertEquals("Clave123", cliente.getContrasena());
        assertEquals(1, cliente.getIntentosFallidos());
    }
}