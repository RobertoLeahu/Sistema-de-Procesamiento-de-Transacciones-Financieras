package com.banco.transacciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

	private TransferenciaDTO dto;
	private Cuenta cuentaOrigen;
	private Cuenta cuentaDestino;
	private Transaccion transaccion;

	@BeforeEach
	void setUp() throws Exception {
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
	@DisplayName("Procesar Transferencia Async Por ID - Exitoso")
	void testProcesarTransferenciaAsyncPorId_Exito() {
		when(transaccionRepository.findById(1L)).thenReturn(Optional.of(transaccion));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));
		when(fraudeScoreCalculator.calcularScore(dto)).thenReturn(0.1);
		when(transaccionRepository.save(any(Transaccion.class))).thenReturn(transaccion);

		CompletableFuture<Transaccion> future = transaccionProcesador.procesarTransferenciaAsync(1L, dto);
		Transaccion resultado = future.join();

		assertEquals(EstadoTransaccion.COMPLETADA, resultado.getEstado());
		assertEquals(0.1, resultado.getRiesgoFraude());
	}

	@Test
	@DisplayName("Procesar Transferencia Async Por ID - Manejo de Excepciones y Futuro Fallido")
	void testProcesarTransferenciaAsyncPorId_Excepcion() {
		when(transaccionRepository.findById(1L)).thenThrow(new RuntimeException("Fallo simulado BD"));

		CompletableFuture<Transaccion> future = transaccionProcesador.procesarTransferenciaAsync(1L, dto);

		assertTrue(future.isCompletedExceptionally());
	}

	@Test
	@DisplayName("Lógica de Transacción - No Encuentra ID")
	void testProcesarTransferenciaPorId_TransaccionNoEncontrada() {
		when(transaccionRepository.findById(1L)).thenReturn(Optional.empty());

		assertThrows(TransaccionNotFoundException.class,
				() -> transaccionProcesador.procesarTransferenciaPorId(1L, dto));
	}

	@Test
	@DisplayName("Lógica de Transacción - Origen No Encontrado")
	void testEjecutarLogicaTransaccion_OrigenNotFound() {
		when(transaccionRepository.save(any(Transaccion.class))).thenReturn(transaccion);
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.empty());

		assertThrows(CuentaNotFoundException.class, () -> transaccionProcesador.procesarTransferencia(dto));

		// Verificamos que el catch haya seteado a RECHAZADA antes de hacer el throw
		assertEquals(EstadoTransaccion.RECHAZADA, transaccion.getEstado());
	}

	@Test
	@DisplayName("Lógica de Transacción - Destino No Encontrado")
	void testEjecutarLogicaTransaccion_DestinoNotFound() {
		when(transaccionRepository.save(any(Transaccion.class))).thenReturn(transaccion);
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.empty());

		assertThrows(CuentaNotFoundException.class, () -> transaccionProcesador.procesarTransferencia(dto));
	}

	@Test
	@DisplayName("Lógica de Transacción - Cuentas Inactivas o Bloqueadas")
	void testEjecutarLogicaTransaccion_CuentasInactivas() {
		cuentaOrigen.setEstado(EstadoCuenta.BLOQUEADA);

		when(transaccionRepository.save(any(Transaccion.class))).thenReturn(transaccion);
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));

		assertThrows(CuentaBloqueadaException.class, () -> transaccionProcesador.procesarTransferencia(dto));
	}

	@Test
	@DisplayName("Lógica de Transacción - Riesgo Medio (Continúa pero avisa en logs)")
	void testEjecutarLogicaTransaccion_RiesgoMedio() {
		when(transaccionRepository.save(any(Transaccion.class))).thenReturn(transaccion);
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));

		// Score 0.60 está en el rango [0.50, 0.75]
		when(fraudeScoreCalculator.calcularScore(dto)).thenReturn(0.60);

		Transaccion resultado = transaccionProcesador.procesarTransferencia(dto);

		assertEquals(EstadoTransaccion.COMPLETADA, resultado.getEstado());
		assertEquals(0.60, resultado.getRiesgoFraude());
	}

	@Test
	@DisplayName("Lógica de Transacción - Saldo Insuficiente")
	void testEjecutarLogicaTransaccion_SaldoInsuficiente() {
		cuentaOrigen.setSaldo(new BigDecimal("10.00")); // Requerido 100

		when(transaccionRepository.save(any(Transaccion.class))).thenReturn(transaccion);
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));
		when(fraudeScoreCalculator.calcularScore(dto)).thenReturn(0.1);

		assertThrows(SaldoInsuficienteException.class, () -> transaccionProcesador.procesarTransferencia(dto));
	}

	@Test
	@DisplayName("Lógica de Transacción - Riesgo Crítico (>0.75) y Generación de Alerta")
	void testEjecutarLogicaTransaccion_RiesgoCritico() {
		when(transaccionRepository.save(any(Transaccion.class))).thenReturn(transaccion);
		when(cuentaRepository.findByNumeroCuentaWithLock("ES11")).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock("ES22")).thenReturn(Optional.of(cuentaDestino));
		when(fraudeScoreCalculator.calcularScore(dto)).thenReturn(0.85); // Mayor a 0.75

		Transaccion resultado = transaccionProcesador.procesarTransferencia(dto);

		assertEquals(EstadoTransaccion.RECHAZADA, resultado.getEstado());
		verify(alertaFraudeRepository).save(any(AlertaFraude.class));
	}
}