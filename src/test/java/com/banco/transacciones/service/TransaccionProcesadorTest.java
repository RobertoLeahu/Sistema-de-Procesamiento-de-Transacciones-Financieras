package com.banco.transacciones.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banco.transacciones.domain.enums.EstadoCuenta;
import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.enums.TipoTransaccion;
import com.banco.transacciones.domain.models.AlertaFraude;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.dto.response.ResumenLoteDTO;
import com.banco.transacciones.exception.CuentaBloqueadaException;
import com.banco.transacciones.exception.CuentaNotFoundException;
import com.banco.transacciones.exception.SaldoInsuficienteException;
import com.banco.transacciones.exception.TransaccionNotFoundException;
import com.banco.transacciones.repository.AlertaFraudeRepository;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;
import com.banco.transacciones.util.FraudeScoreCalculator;

/**
 * Pruebas unitarias para la validación de la lógica de negocio y concurrencia.
 * Adaptado a la refactorización arquitectónica para el manejo seguro asíncrono.
 */
@ExtendWith(MockitoExtension.class)
class TransaccionProcesadorTest {

	@Mock
	private CuentaRepository cuentaRepository;

	@Mock
	private TransaccionRepository transaccionRepository;

	@Mock
	private FraudeScoreCalculator fraudeScoreCalculator;

	@Mock
	private AlertaFraudeRepository alertaFraudeRepository;

	@InjectMocks
	private TransaccionProcesador transaccionProcesador;

	@Captor
	private ArgumentCaptor<Transaccion> txCaptor;

	private TransferenciaDTO dto;
	private Cuenta cuentaOrigen;
	private Cuenta cuentaDestino;
	private Transaccion transaccion;

	@BeforeEach
	void setUp() throws Exception {
		// Inyectamos el self-reference para que las delegaciones internas se procesen
		// como el objeto real
		Field selfField = TransaccionProcesador.class.getDeclaredField("self");
		selfField.setAccessible(true);
		selfField.set(transaccionProcesador, transaccionProcesador);

		dto = new TransferenciaDTO("ES11", "ES22", new BigDecimal("100.00"), "ES", "Prueba");

		cuentaOrigen = Cuenta.builder().numeroCuenta("ES11").saldo(new BigDecimal("500.00"))
				.estado(EstadoCuenta.ACTIVADA).build();
		cuentaDestino = Cuenta.builder().numeroCuenta("ES22").saldo(new BigDecimal("100.00"))
				.estado(EstadoCuenta.ACTIVADA).build();

		transaccion = Transaccion.builder().id(1L).cuentaOrigen("ES11").cuentaDestino("ES22")
				.monto(new BigDecimal("100.00")).estado(EstadoTransaccion.PENDIENTE).tipo(TipoTransaccion.TRANSFERENCIA)
				.fechaHora(Instant.now()).build();
	}

	@Test
	@DisplayName("Procesar Transferencia Async Por ID - Exitoso sin bloquear hilo")
	void testProcesarTransferenciaAsyncPorId_Exito() {
		when(transaccionRepository.findById(1L)).thenReturn(Optional.of(transaccion));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));
		when(fraudeScoreCalculator.calcularScore(dto)).thenReturn(0.1);

		// Ejecución asíncrona segura
		assertDoesNotThrow(() -> transaccionProcesador.procesarTransferenciaAsync(1L, dto));

		// Comprobación de estado persistido final
		assertEquals(EstadoTransaccion.COMPLETADA, transaccion.getEstado());
		assertEquals(0.1, transaccion.getRiesgoFraude());
		verify(transaccionRepository, times(1)).save(transaccion);
	}

	@Test
	@DisplayName("Procesar Transferencia Async Por ID - Captura de Excepciones Segura")
	void testProcesarTransferenciaAsyncPorId_ExcepcionOculta() {
		// Simulamos fallo crítico en BDD
		when(transaccionRepository.findById(1L)).thenThrow(new RuntimeException("Fallo de red BD"));

		// El decorador @Async debe garantizar que la excepción no revienta la
		// aplicación, solo se loguea
		assertDoesNotThrow(() -> transaccionProcesador.procesarTransferenciaAsync(1L, dto));
		verify(transaccionRepository, times(1)).findById(1L);
	}

	@Test
	@DisplayName("Lógica de Negocio - Transacción Inicial No Encontrada")
	void testProcesarTransferencia_TransaccionNoEncontrada() {
		when(transaccionRepository.findById(1L)).thenReturn(Optional.empty());

		assertThrows(TransaccionNotFoundException.class, () -> transaccionProcesador.procesarTransferencia(1L, dto));
	}

	@Test
	@DisplayName("Lógica de Negocio - Excepción por Origen Inexistente")
	void testEjecutarLogicaTransaccion_OrigenNotFound() {
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.empty());
		assertThrows(CuentaNotFoundException.class, () -> transaccionProcesador.procesarTransferencia(dto));
	}

	@Test
	@DisplayName("Lógica de Negocio - Cuentas Inactivas o Bloqueadas")
	void testEjecutarLogicaTransaccion_CuentasInactivas() {
		cuentaOrigen.setEstado(EstadoCuenta.BLOQUEADA);

		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));

		assertThrows(CuentaBloqueadaException.class, () -> transaccionProcesador.procesarTransferencia(dto));
	}

	@Test
	@DisplayName("Lógica de Negocio - Riesgo Medio (Genera alerta, no bloquea operación)")
	void testEjecutarLogicaTransaccion_RiesgoMedio() {
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));

		// Score 0.60 está en el rango (0.50, 0.75]
		when(fraudeScoreCalculator.calcularScore(dto)).thenReturn(0.60);

		assertDoesNotThrow(() -> transaccionProcesador.procesarTransferencia(dto));

		verify(alertaFraudeRepository, times(1)).save(any(AlertaFraude.class));
		verify(transaccionRepository, times(2)).save(txCaptor.capture());

		// Verificamos que se actualizó el estado correctamente al final de la lógica
		assertEquals(EstadoTransaccion.COMPLETADA, txCaptor.getAllValues().get(1).getEstado());
	}

	@Test
	@DisplayName("Lógica de Negocio - Saldo Insuficiente en Origen")
	void testEjecutarLogicaTransaccion_SaldoInsuficiente() {
		cuentaOrigen.setSaldo(new BigDecimal("10.00")); // Requerido 100

		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));

		assertThrows(SaldoInsuficienteException.class, () -> transaccionProcesador.procesarTransferencia(dto));
	}

	@Test
	@DisplayName("Lógica de Negocio - Riesgo Crítico (>0.75), Rechazo Total")
	void testEjecutarLogicaTransaccion_RiesgoCritico() {
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));
		when(fraudeScoreCalculator.calcularScore(dto)).thenReturn(0.85);

		assertDoesNotThrow(() -> transaccionProcesador.procesarTransferencia(dto));

		verify(transaccionRepository, times(2)).save(txCaptor.capture());
		assertEquals(EstadoTransaccion.RECHAZADA, txCaptor.getAllValues().get(1).getEstado());
		verify(alertaFraudeRepository, times(1)).save(any(AlertaFraude.class));
	}

	@Test
	@DisplayName("Ejecución por Lotes - Procesamiento Paralelo Exitoso y Gestión de Fallos (100% Cobertura)")
	void testProcesarSubloteAsync_Mixto() {
		// Configuramos un sublote con diferentes casuísticas de fallo real en un
		// ThreadPool
		TransferenciaDTO dtoExito = new TransferenciaDTO("ES11", "ES22", new BigDecimal("10.00"), "ES", "Exito");
		TransferenciaDTO dtoErrorDb = new TransferenciaDTO("ES33", "ES44", new BigDecimal("10.00"), "ES", "DB Error");
		TransferenciaDTO dtoDeadlock = new TransferenciaDTO("ES55", "ES66", new BigDecimal("10.00"), "ES", "Deadlock");
		TransferenciaDTO dtoUnknown = new TransferenciaDTO("ES77", "ES88", new BigDecimal("10.00"), "ES", "Unknown");

		List<TransferenciaDTO> sublote = List.of(dtoExito, dtoErrorDb, dtoDeadlock, dtoUnknown);

		// Lenient se usa para evitar 'UnnecessaryStubbingException' ya que algunos
		// fallan a la mitad
		lenient().when(transaccionRepository.save(any(Transaccion.class))).thenReturn(transaccion);

		// Para dtoExito
		lenient().when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		lenient().when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));
		lenient().when(fraudeScoreCalculator.calcularScore(dtoExito)).thenReturn(0.1);

		// Para dtoErrorDb
		lenient().when(cuentaRepository.findByNumeroCuentaWithLock("ES33"))
				.thenThrow(new RuntimeException("JDBC exception: Connection timeout"));

		// Para dtoDeadlock
		lenient().when(cuentaRepository.findByNumeroCuentaWithLock("ES55"))
				.thenThrow(new RuntimeException("JDBC exception: Deadlock found when trying to get lock"));

		// Para dtoUnknown (Mensaje null simulado)
		lenient().when(cuentaRepository.findByNumeroCuentaWithLock("ES77"))
				.thenThrow(new RuntimeException((String) null));

		// Ejecutamos con offset inicial 10
		CompletableFuture<ResumenLoteDTO> future = transaccionProcesador.procesarSubloteAsync(sublote, 10);
		ResumenLoteDTO result = future.join();

		assertEquals(4, result.totalRecibidas());
		assertEquals(1, result.totalExitosas());
		assertEquals(3, result.totalFallidas());

		// Validamos el parseo e interceptación de los mensajes críticos de base de
		// datos
		String resultadosString = result.detallesRechazo().toString();
		assertTrue(resultadosString.contains("Error interno de base de datos al procesar la transacción."));
		assertTrue(resultadosString.contains("Interbloqueo detectado por alta concurrencia"));
		assertTrue(resultadosString.contains("Error desconocido del servidor."));
	}
}