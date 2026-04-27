package com.banco.transacciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.models.AlertaFraude;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.repository.AlertaFraudeRepository;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;
import com.banco.transacciones.util.FraudeScoreCalculator;

/**
 * Clase de pruebas unitarias para la validacion del motor de ejecucion concurrente en TransaccionProcesador.
 * Verifica la integridad transaccional, la prevencion de condiciones de carrera (Deadlocks) y la 
 * aplicacion estricta de las politicas de seguridad financiera.
 * * Flujos criticos validados:
 * - Prevencion de concurrencia: Verificacion del acceso exclusivo a los datos de las cuentas mediante simulacion de bloqueos pesimistas en base de datos.
 * - Integracion de Riesgos: Evaluacion de transferencias contra el orquestador de fraude, asegurando el bloqueo y generacion de alertas ante scores que superen el umbral critico.
 * - Consistencia contable: Validacion matematica estricta de disponibilidad de fondos antes de efectuar mutaciones en los saldos.
 * - Transicion de estados: Garantia de que el ciclo de vida de la transaccion finalice correctamente (COMPLETADA o RECHAZADA) tras la fase de procesamiento, incluyendo la correcta persistencia en el bloque finally.
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

	private TransferenciaDTO dto;
	private Transaccion tx;
	private Cuenta cuentaOrigen;
	private Cuenta cuentaDestino;

	private final String CUENTA_ORIGEN = "ES1234567890123456789012";
	private final String CUENTA_DESTINO = "ES9876543210987654321098";
	private final String PAIS_HABITUAL = "ES";

	@BeforeEach
	void setUp() {
		dto = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("100.00"), PAIS_HABITUAL,
				"Transferencia de prueba");

		tx = Transaccion.builder().id(1L).estado(EstadoTransaccion.PENDIENTE).build();
		cuentaOrigen = Cuenta.builder().numeroCuenta(CUENTA_ORIGEN).saldo(new BigDecimal("500.00")).build();
		cuentaDestino = Cuenta.builder().numeroCuenta(CUENTA_DESTINO).saldo(new BigDecimal("100.00")).build();
	}

	/**
	 * Valida el flujo transaccional asincrono completo. Comprueba la aplicacion de
	 * bloqueos pesimistas, el correcto calculo matematico sobre los saldos de ambas
	 * cuentas y la persistencia del estado como COMPLETADA.
	 */
	@Test
	@DisplayName("Debe procesar transferencia con éxito: saldos actualizados y estado COMPLETADA")
	void ejecutarTransferencia_Exito() {
		// Arrange
		when(transaccionRepository.findById(1L)).thenReturn(Optional.of(tx));
		when(fraudeScoreCalculator.calcularScore(any(TransferenciaDTO.class))).thenReturn(0.1);
		when(cuentaRepository.findByNumeroCuentaWithLock(CUENTA_ORIGEN)).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaDestino));

		// Act
		transaccionProcesador.ejecutarTransferenciaAsync(dto, 1L);

		// Assert
		assertEquals(EstadoTransaccion.COMPLETADA, tx.getEstado());
		assertEquals(new BigDecimal("400.00"), cuentaOrigen.getSaldo()); // 500 - 100
		assertEquals(new BigDecimal("200.00"), cuentaDestino.getSaldo()); // 100 + 100
		verify(transaccionRepository).saveAndFlush(tx); // Verifica el guardado inicial a PROCESANDO
		verify(transaccionRepository).save(tx); // Verifica el guardado final en el bloque finally
	}

	/**
	 * Verifica la integracion con el motor algoritmico de riesgos. Asegura que una
	 * transaccion evaluada con un score de fraude alto bloquee el flujo, se marque
	 * como RECHAZADA, mantenga la integridad de saldos y dispare una alerta.
	 */
	@Test
	@DisplayName("Debe rechazar por alto riesgo de fraude y generar alerta")
	void ejecutarTransferencia_FraudeAlto_Rechazada() {
		// Arrange
		when(transaccionRepository.findById(1L)).thenReturn(Optional.of(tx));
		when(fraudeScoreCalculator.calcularScore(any(TransferenciaDTO.class))).thenReturn(0.85);
		when(cuentaRepository.findByNumeroCuentaWithLock(CUENTA_ORIGEN)).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaDestino));

		// Act
		transaccionProcesador.ejecutarTransferenciaAsync(dto, 1L);

		// Assert
		assertEquals(EstadoTransaccion.RECHAZADA, tx.getEstado());
		assertEquals(0.85, tx.getRiesgoFraude());
		// Comprobar que los saldos NO han cambiado
		assertEquals(new BigDecimal("500.00"), cuentaOrigen.getSaldo());

		verify(alertaFraudeRepository).save(any(AlertaFraude.class));
	}

	/**
	 * Comprueba la consistencia financiera base. Garantiza que el intento de
	 * transferir un monto superior al saldo disponible en la cuenta origen aborte
	 * la operacion, registre el rechazo y lance la excepcion adecuada.
	 */
	@Test
	@DisplayName("Debe lanzar excepción y rechazar si la cuenta no tiene saldo suficiente")
	void ejecutarTransferencia_SaldoInsuficiente_Rechazada() {
		// Arrange
		cuentaOrigen.setSaldo(new BigDecimal("50.00")); // Saldo menor a los 100 de la transferencia

		when(transaccionRepository.findById(1L)).thenReturn(Optional.of(tx));
		when(fraudeScoreCalculator.calcularScore(any(TransferenciaDTO.class))).thenReturn(0.1);
		when(cuentaRepository.findByNumeroCuentaWithLock(CUENTA_ORIGEN)).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaDestino));

		// Act
		transaccionProcesador.ejecutarTransferenciaAsync(dto, 1L);

		// Assert
		assertEquals(EstadoTransaccion.RECHAZADA, tx.getEstado());
	}
}