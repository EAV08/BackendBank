package com.backendbank.backendbank.cuenta;

import com.backendbank.backendbank.cliente.Cliente;
import com.backendbank.backendbank.cliente.ClienteNoEncontradoException;
import com.backendbank.backendbank.cliente.ClienteRepository;
import com.backendbank.backendbank.cliente.ClienteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

    private static final String HASH = "$2a$10$hashDePruebaParaLasPruebasUnitarias";
    private static final String NUMERO = "1234567890";

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private GeneradorNumeroCuenta generadorNumeroCuenta;

    @Mock
    private ConsultaSaldo consultaSaldo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CuentaService cuentaService;

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

    private Cuenta cuenta(UUID idCliente, String estado) {
        Cuenta cuenta = new Cuenta();
        cuenta.setIdCuenta(UUID.randomUUID());
        cuenta.setIdCliente(idCliente);
        cuenta.setNumeroCuenta(NUMERO);
        cuenta.setEstado(estado);
        cuenta.setSaldo(new BigDecimal("150.00"));
        return cuenta;
    }

    private Cliente clienteExistente(String estado) {
        Cliente cliente = cliente(estado);
        when(clienteRepository.findById(cliente.getIdCliente())).thenReturn(Optional.of(cliente));
        return cliente;
    }

    private Cuenta cuentaDelCliente(Cliente cliente, String estado) {
        Cuenta cuenta = cuenta(cliente.getIdCliente(), estado);
        when(cuentaRepository.findByIdCuentaAndIdCliente(cuenta.getIdCuenta(), cliente.getIdCliente()))
                .thenReturn(Optional.of(cuenta));
        return cuenta;
    }

    private void guardarTalComoLlega() {
        when(cuentaRepository.saveAndFlush(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreacionCuentaRechazadaException crearConPerfil(String estado) {
        Cliente cliente = clienteExistente(estado);
        CrearCuentaRequest solicitud = new CrearCuentaRequest(cliente.getIdCliente());
        return assertThrows(CreacionCuentaRechazadaException.class, () -> cuentaService.crear(solicitud));
    }

    private ConsultaSaldoRechazadaException consultarConPerfil(String estado) {
        Cliente cliente = clienteExistente(estado);
        ConsultarSaldoRequest solicitud = new ConsultarSaldoRequest(cliente.getIdCliente(), UUID.randomUUID());
        return assertThrows(ConsultaSaldoRechazadaException.class, () -> cuentaService.consultar(solicitud));
    }

    private BloqueoCuentaRechazadoException bloquearConPerfil(String estado) {
        Cliente cliente = clienteExistente(estado);
        BloquearCuentaRequest solicitud = new BloquearCuentaRequest(cliente.getIdCliente(), UUID.randomUUID(), "fraude");
        return assertThrows(BloqueoCuentaRechazadoException.class, () -> cuentaService.bloquear(solicitud));
    }

    // ---------- crear ----------

    @Test
    void creaUnaCuentaActivaConNumeroValidoYSaldoEnCero() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        when(generadorNumeroCuenta.generar()).thenReturn(NUMERO);
        guardarTalComoLlega();

        CuentaCreadaResponse respuesta = cuentaService.crear(new CrearCuentaRequest(cliente.getIdCliente()));

        ArgumentCaptor<Cuenta> guardada = ArgumentCaptor.forClass(Cuenta.class);
        verify(cuentaRepository).saveAndFlush(guardada.capture());
        assertEquals(cliente.getIdCliente(), guardada.getValue().getIdCliente());
        assertEquals(NUMERO, respuesta.numeroCuenta());
        assertEquals(Cuenta.ESTADO_ACTIVO, respuesta.estado());
        assertEquals(0, respuesta.saldo().compareTo(BigDecimal.ZERO));
        assertEquals(CuentaService.MENSAJE_EXITO, respuesta.mensaje());
    }

    @Test
    void rechazaCrearUnaCuentaSiElClienteNoExiste() {
        UUID idInexistente = UUID.randomUUID();
        when(clienteRepository.findById(idInexistente)).thenReturn(Optional.empty());
        CrearCuentaRequest solicitud = new CrearCuentaRequest(idInexistente);

        ClienteNoEncontradoException ex = assertThrows(ClienteNoEncontradoException.class,
                () -> cuentaService.crear(solicitud));

        assertEquals(ClienteService.MENSAJE_NO_ENCONTRADO, ex.getMessage());
    }

    @Test
    void rechazaCrearUnaCuentaConElPerfilBloqueado() {
        assertEquals(CuentaService.MENSAJE_PERFIL_BLOQUEADO, crearConPerfil(Cliente.ESTADO_BLOQUEADO).getMessage());
    }

    @Test
    void rechazaCrearUnaCuentaConElPerfilInactivo() {
        assertEquals(ClienteService.MENSAJE_INACTIVO, crearConPerfil(Cliente.ESTADO_INACTIVO).getMessage());
    }

    @Test
    void rechazaCrearUnaCuentaConElPerfilPendienteDeValidacion() {
        assertEquals(CuentaService.MENSAJE_SIN_VALIDACION,
                crearConPerfil(Cliente.ESTADO_PENDIENTE_DE_VALIDACION).getMessage());
    }

    @Test
    void rechazaCrearUnaCuentaAlAlcanzarElMaximoDeCuentasActivas() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        when(cuentaRepository.countByIdClienteAndEstado(cliente.getIdCliente(), Cuenta.ESTADO_ACTIVO))
                .thenReturn((long) CuentaService.MAX_CUENTAS_ACTIVAS);
        CrearCuentaRequest solicitud = new CrearCuentaRequest(cliente.getIdCliente());

        CreacionCuentaRechazadaException ex = assertThrows(CreacionCuentaRechazadaException.class,
                () -> cuentaService.crear(solicitud));

        assertEquals(CuentaService.MENSAJE_LIMITE, ex.getMessage());
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reintentaConOtroNumeroCuandoElPrimeroYaExiste() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        when(generadorNumeroCuenta.generar()).thenReturn(NUMERO, "0987654321");
        when(cuentaRepository.existsByNumeroCuenta(NUMERO)).thenReturn(true);
        guardarTalComoLlega();

        CuentaCreadaResponse respuesta = cuentaService.crear(new CrearCuentaRequest(cliente.getIdCliente()));

        assertEquals("0987654321", respuesta.numeroCuenta());
        verify(generadorNumeroCuenta, times(2)).generar();
    }

    @Test
    void fallaSiTodosLosNumerosGeneradosYaExisten() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        when(generadorNumeroCuenta.generar()).thenReturn(NUMERO);
        when(cuentaRepository.existsByNumeroCuenta(NUMERO)).thenReturn(true);
        CrearCuentaRequest solicitud = new CrearCuentaRequest(cliente.getIdCliente());

        GeneracionCuentaException ex = assertThrows(GeneracionCuentaException.class,
                () -> cuentaService.crear(solicitud));

        assertEquals(CuentaService.MENSAJE_TECNICO, ex.getMessage());
        verify(generadorNumeroCuenta, times(CuentaService.MAX_REINTENTOS_NUMERO)).generar();
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void fallaSiElGeneradorEntregaNumerosInvalidos() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        when(generadorNumeroCuenta.generar()).thenReturn(null, "abc", "123456789a", "12345678901", "");
        CrearCuentaRequest solicitud = new CrearCuentaRequest(cliente.getIdCliente());

        GeneracionCuentaException ex = assertThrows(GeneracionCuentaException.class,
                () -> cuentaService.crear(solicitud));

        assertEquals(CuentaService.MENSAJE_TECNICO, ex.getMessage());
        verify(generadorNumeroCuenta, times(CuentaService.MAX_REINTENTOS_NUMERO)).generar();
        verify(cuentaRepository, never()).existsByNumeroCuenta(any());
    }

    @Test
    void fallaSinReintentarSiElGeneradorLanzaUnaExcepcion() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        when(generadorNumeroCuenta.generar()).thenThrow(new IllegalStateException("motor no disponible"));
        CrearCuentaRequest solicitud = new CrearCuentaRequest(cliente.getIdCliente());

        GeneracionCuentaException ex = assertThrows(GeneracionCuentaException.class,
                () -> cuentaService.crear(solicitud));

        assertEquals(CuentaService.MENSAJE_TECNICO, ex.getMessage());
        verify(generadorNumeroCuenta, times(1)).generar();
    }

    @Test
    void traduceUnaRestriccionDeLaBaseDeDatosAlGuardarLaCuenta() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        when(generadorNumeroCuenta.generar()).thenReturn(NUMERO);
        when(cuentaRepository.saveAndFlush(any(Cuenta.class)))
                .thenThrow(new DataIntegrityViolationException("número repetido"));
        CrearCuentaRequest solicitud = new CrearCuentaRequest(cliente.getIdCliente());

        GeneracionCuentaException ex = assertThrows(GeneracionCuentaException.class,
                () -> cuentaService.crear(solicitud));

        assertEquals(CuentaService.MENSAJE_TECNICO, ex.getMessage());
    }

    // ---------- consultar saldo ----------

    @Test
    void informaElSaldoDeUnaCuentaActiva() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuentaDelCliente(cliente, Cuenta.ESTADO_ACTIVO);
        when(consultaSaldo.disponible(cuenta)).thenReturn(cuenta.getSaldo());

        SaldoDisponibleResponse respuesta = cuentaService.consultar(
                new ConsultarSaldoRequest(cliente.getIdCliente(), cuenta.getIdCuenta()));

        assertEquals(new BigDecimal("150.00"), respuesta.saldo());
        assertEquals(NUMERO, respuesta.numeroCuenta());
        assertEquals(CuentaService.MENSAJE_SALDO, respuesta.mensaje());
    }

    @Test
    void rechazaConsultarElSaldoSiElClienteNoExiste() {
        UUID idInexistente = UUID.randomUUID();
        when(clienteRepository.findById(idInexistente)).thenReturn(Optional.empty());
        ConsultarSaldoRequest solicitud = new ConsultarSaldoRequest(idInexistente, UUID.randomUUID());

        ClienteNoEncontradoException ex = assertThrows(ClienteNoEncontradoException.class,
                () -> cuentaService.consultar(solicitud));

        assertEquals(ClienteService.MENSAJE_NO_ENCONTRADO, ex.getMessage());
    }

    @Test
    void rechazaConsultarElSaldoConElPerfilBloqueado() {
        assertEquals(CuentaService.MENSAJE_PERFIL_BLOQUEADO, consultarConPerfil(Cliente.ESTADO_BLOQUEADO).getMessage());
    }

    @Test
    void rechazaConsultarElSaldoConElPerfilInactivo() {
        assertEquals(ClienteService.MENSAJE_INACTIVO, consultarConPerfil(Cliente.ESTADO_INACTIVO).getMessage());
    }

    @Test
    void rechazaConsultarElSaldoConElPerfilPendienteDeValidacion() {
        assertEquals(CuentaService.MENSAJE_SIN_VALIDACION,
                consultarConPerfil(Cliente.ESTADO_PENDIENTE_DE_VALIDACION).getMessage());
    }

    @Test
    void rechazaConsultarElSaldoDeUnaCuentaQueNoEsDelCliente() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        UUID idCuenta = UUID.randomUUID();
        when(cuentaRepository.findByIdCuentaAndIdCliente(idCuenta, cliente.getIdCliente()))
                .thenReturn(Optional.empty());
        ConsultarSaldoRequest solicitud = new ConsultarSaldoRequest(cliente.getIdCliente(), idCuenta);

        CuentaNoEncontradaException ex = assertThrows(CuentaNoEncontradaException.class,
                () -> cuentaService.consultar(solicitud));

        assertEquals(CuentaService.MENSAJE_CUENTA_NO_ENCONTRADA, ex.getMessage());
    }

    @Test
    void rechazaConsultarElSaldoDeUnaCuentaBloqueada() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuentaDelCliente(cliente, Cuenta.ESTADO_BLOQUEADO);
        ConsultarSaldoRequest solicitud = new ConsultarSaldoRequest(cliente.getIdCliente(), cuenta.getIdCuenta());

        ConsultaSaldoRechazadaException ex = assertThrows(ConsultaSaldoRechazadaException.class,
                () -> cuentaService.consultar(solicitud));

        assertEquals(CuentaService.MENSAJE_CUENTA_BLOQUEADA, ex.getMessage());
    }

    @Test
    void rechazaConsultarElSaldoDeUnaCuentaInactiva() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuentaDelCliente(cliente, Cuenta.ESTADO_INACTIVO);
        ConsultarSaldoRequest solicitud = new ConsultarSaldoRequest(cliente.getIdCliente(), cuenta.getIdCuenta());

        ConsultaSaldoRechazadaException ex = assertThrows(ConsultaSaldoRechazadaException.class,
                () -> cuentaService.consultar(solicitud));

        assertEquals(CuentaService.MENSAJE_CUENTA_INACTIVA, ex.getMessage());
    }

    @Test
    void informaQueElSaldoNoEstaDisponibleCuandoLaLecturaFalla() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuentaDelCliente(cliente, Cuenta.ESTADO_ACTIVO);
        when(consultaSaldo.disponible(cuenta)).thenThrow(new IllegalStateException("sin saldo"));
        ConsultarSaldoRequest solicitud = new ConsultarSaldoRequest(cliente.getIdCliente(), cuenta.getIdCuenta());

        ConsultaSaldoNoDisponibleException ex = assertThrows(ConsultaSaldoNoDisponibleException.class,
                () -> cuentaService.consultar(solicitud));

        assertEquals(CuentaService.MENSAJE_MAS_TARDE, ex.getMessage());
    }

    @Test
    void informaQueElSaldoNoEstaDisponibleCuandoElBancoDevuelveUnaRespuestaVacia() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuentaDelCliente(cliente, Cuenta.ESTADO_ACTIVO);
        when(consultaSaldo.disponible(cuenta)).thenReturn((BigDecimal) null);
        ConsultarSaldoRequest solicitud = new ConsultarSaldoRequest(cliente.getIdCliente(), cuenta.getIdCuenta());

        ConsultaSaldoNoDisponibleException ex = assertThrows(ConsultaSaldoNoDisponibleException.class,
                () -> cuentaService.consultar(solicitud));

        assertEquals(CuentaService.MENSAJE_MAS_TARDE, ex.getMessage());
    }

    // ---------- bloquear ----------

    @Test
    void bloqueaUnaCuentaActivaPorFraudeSinImportarLasMayusculas() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuentaDelCliente(cliente, Cuenta.ESTADO_ACTIVO);
        guardarTalComoLlega();

        CuentaBloqueadaResponse respuesta = cuentaService.bloquear(
                new BloquearCuentaRequest(cliente.getIdCliente(), cuenta.getIdCuenta(), "FRAUDE"));

        assertEquals(Cuenta.ESTADO_BLOQUEADO, respuesta.estado());
        assertTrue(respuesta.operacionesSuspendidas());
        assertEquals(CuentaService.MOTIVO_FRAUDE, respuesta.motivo());
        assertEquals(CuentaService.MENSAJE_BLOQUEO, respuesta.mensaje());
        assertNotNull(cuenta.getFechaActualizacion());
    }

    @Test
    void bloqueaUnaCuentaActivaPorPerdida() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuentaDelCliente(cliente, Cuenta.ESTADO_ACTIVO);
        guardarTalComoLlega();

        CuentaBloqueadaResponse respuesta = cuentaService.bloquear(
                new BloquearCuentaRequest(cliente.getIdCliente(), cuenta.getIdCuenta(), "perdida"));

        assertEquals(CuentaService.MOTIVO_PERDIDA, respuesta.motivo());
        assertEquals(Cuenta.ESTADO_BLOQUEADO, cuenta.getEstado());
    }

    @Test
    void noVuelveAGuardarUnaCuentaQueYaEstabaBloqueada() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuentaDelCliente(cliente, Cuenta.ESTADO_BLOQUEADO);
        cuenta.setMotivo(CuentaService.MOTIVO_FRAUDE);

        CuentaBloqueadaResponse respuesta = cuentaService.bloquear(
                new BloquearCuentaRequest(cliente.getIdCliente(), cuenta.getIdCuenta(), "perdida"));

        assertEquals(CuentaService.MENSAJE_YA_BLOQUEADA, respuesta.mensaje());
        assertEquals(CuentaService.MOTIVO_FRAUDE, respuesta.motivo());
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaBloquearUnaCuentaInactiva() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuentaDelCliente(cliente, Cuenta.ESTADO_INACTIVO);
        BloquearCuentaRequest solicitud = new BloquearCuentaRequest(
                cliente.getIdCliente(), cuenta.getIdCuenta(), "fraude");

        BloqueoCuentaRechazadoException ex = assertThrows(BloqueoCuentaRechazadoException.class,
                () -> cuentaService.bloquear(solicitud));

        assertEquals(CuentaService.MENSAJE_CUENTA_INACTIVA, ex.getMessage());
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaBloquearUnaCuentaQueNoEsDelCliente() {
        Cliente cliente = clienteExistente(Cliente.ESTADO_ACTIVO);
        UUID idCuenta = UUID.randomUUID();
        when(cuentaRepository.findByIdCuentaAndIdCliente(idCuenta, cliente.getIdCliente()))
                .thenReturn(Optional.empty());
        BloquearCuentaRequest solicitud = new BloquearCuentaRequest(cliente.getIdCliente(), idCuenta, "fraude");

        CuentaNoEncontradaException ex = assertThrows(CuentaNoEncontradaException.class,
                () -> cuentaService.bloquear(solicitud));

        assertEquals(CuentaService.MENSAJE_CUENTA_NO_ENCONTRADA, ex.getMessage());
    }

    @Test
    void rechazaBloquearSiElClienteNoExiste() {
        UUID idInexistente = UUID.randomUUID();
        when(clienteRepository.findById(idInexistente)).thenReturn(Optional.empty());
        BloquearCuentaRequest solicitud = new BloquearCuentaRequest(idInexistente, UUID.randomUUID(), "fraude");

        ClienteNoEncontradoException ex = assertThrows(ClienteNoEncontradoException.class,
                () -> cuentaService.bloquear(solicitud));

        assertEquals(ClienteService.MENSAJE_NO_ENCONTRADO, ex.getMessage());
    }

    @Test
    void rechazaBloquearConElPerfilBloqueado() {
        assertEquals(CuentaService.MENSAJE_PERFIL_BLOQUEADO, bloquearConPerfil(Cliente.ESTADO_BLOQUEADO).getMessage());
    }

    @Test
    void rechazaBloquearConElPerfilInactivo() {
        assertEquals(ClienteService.MENSAJE_INACTIVO, bloquearConPerfil(Cliente.ESTADO_INACTIVO).getMessage());
    }

    @Test
    void rechazaBloquearConElPerfilPendienteDeValidacion() {
        assertEquals(CuentaService.MENSAJE_SIN_VALIDACION,
                bloquearConPerfil(Cliente.ESTADO_PENDIENTE_DE_VALIDACION).getMessage());
    }

    // ---------- consultar estado ----------

    @Test
    void muestraElEstadoCuandoLasCredencialesSonCorrectas() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        Cuenta cuenta = cuenta(cliente.getIdCliente(), Cuenta.ESTADO_ACTIVO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Clave123", HASH)).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));

        EstadoCuentaResponse respuesta = cuentaService.consultarEstado(
                new ConsultarEstadoRequest("luna", "Clave123", NUMERO));

        assertEquals(NUMERO, respuesta.numeroCuenta());
        assertEquals(Cuenta.ESTADO_ACTIVO, respuesta.estado());
        assertEquals(CuentaService.MENSAJE_ESTADO, respuesta.mensaje());
    }

    @Test
    void aceptaUnaContrasenaGuardadaEnTextoPlano() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        cliente.setContrasena("Clave123");
        Cuenta cuenta = cuenta(cliente.getIdCliente(), Cuenta.ESTADO_BLOQUEADO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuenta));

        EstadoCuentaResponse respuesta = cuentaService.consultarEstado(
                new ConsultarEstadoRequest("luna", "Clave123", NUMERO));

        assertEquals(Cuenta.ESTADO_BLOQUEADO, respuesta.estado());
    }

    @Test
    void rechazaConsultarElEstadoDeUnUsuarioInexistenteSinDelatarloPorElTiempo() {
        when(clienteRepository.findByUsuario("fantasma")).thenReturn(Optional.empty());
        ConsultarEstadoRequest solicitud = new ConsultarEstadoRequest("fantasma", "x", NUMERO);

        ClienteNoRegistradoException ex = assertThrows(ClienteNoRegistradoException.class,
                () -> cuentaService.consultarEstado(solicitud));

        assertEquals(CuentaService.MENSAJE_CLIENTE_NO_EXISTE, ex.getMessage());
        verify(passwordEncoder).matches(eq("x"), any());
    }

    @Test
    void rechazaConsultarElEstadoConUnaContrasenaIncorrecta() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        ConsultarEstadoRequest solicitud = new ConsultarEstadoRequest("luna", "mala", NUMERO);

        ContrasenaIncorrectaException ex = assertThrows(ContrasenaIncorrectaException.class,
                () -> cuentaService.consultarEstado(solicitud));

        assertEquals(CuentaService.MENSAJE_CONTRASENA, ex.getMessage());
    }

    @Test
    void rechazaConsultarElEstadoConUnaContrasenaEnTextoPlanoIncorrecta() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        cliente.setContrasena("Clave123");
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        ConsultarEstadoRequest solicitud = new ConsultarEstadoRequest("luna", "otra", NUMERO);

        assertThrows(ContrasenaIncorrectaException.class, () -> cuentaService.consultarEstado(solicitud));
    }

    @Test
    void rechazaConsultarElEstadoDeUnaCuentaQueNoExiste() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Clave123", HASH)).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta("9999999999")).thenReturn(Optional.empty());
        ConsultarEstadoRequest solicitud = new ConsultarEstadoRequest("luna", "Clave123", "9999999999");

        CuentaNoEncontradaException ex = assertThrows(CuentaNoEncontradaException.class,
                () -> cuentaService.consultarEstado(solicitud));

        assertEquals(CuentaService.MENSAJE_CUENTA_NO_EXISTE, ex.getMessage());
    }

    @Test
    void noRevelaElEstadoDeLaCuentaDeOtroCliente() {
        Cliente cliente = cliente(Cliente.ESTADO_ACTIVO);
        Cuenta cuentaAjena = cuenta(UUID.randomUUID(), Cuenta.ESTADO_ACTIVO);
        when(clienteRepository.findByUsuario("luna")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("Clave123", HASH)).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(NUMERO)).thenReturn(Optional.of(cuentaAjena));
        ConsultarEstadoRequest solicitud = new ConsultarEstadoRequest("luna", "Clave123", NUMERO);

        CuentaNoEncontradaException ex = assertThrows(CuentaNoEncontradaException.class,
                () -> cuentaService.consultarEstado(solicitud));

        assertEquals(CuentaService.MENSAJE_CUENTA_NO_EXISTE, ex.getMessage());
    }
}