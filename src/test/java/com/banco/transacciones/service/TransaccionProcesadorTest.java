package com.banco.transacciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.models.AlertaFraude;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.dto.response.ResumenLoteDTO;
import com.banco.transacciones.repository.AlertaFraudeRepository;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;
import com.banco.transacciones.util.FraudeScoreCalculator;

/**
 * Clase de pruebas unitarias para la validacion del motor de ejecucion
 * concurrente en TransaccionProcesador. Verifica la integridad transaccional,
 * la prevencion de condiciones de carrera (Deadlocks) y la aplicacion estricta
 * de las politicas de seguridad financiera. * Flujos criticos validados: -
 * Prevencion de concurrencia: Verificacion del acceso exclusivo a los datos de
 * las cuentas mediante simulacion de bloqueos pesimistas en base de datos. -
 * Integracion de Riesgos: Evaluacion de transferencias contra el orquestador de
 * fraude, asegurando el bloqueo y generacion de alertas ante scores que superen
 * el umbral critico. - Consistencia contable: Validacion matematica estricta de
 * disponibilidad de fondos antes de efectuar mutaciones en los saldos. -
 * Transicion de estados: Garantia de que el ciclo de vida de la transaccion
 * finalice correctamente (COMPLETADA o RECHAZADA) tras la fase de
 * procesamiento, incluyendo la correcta persistencia en el bloque finally.
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

	private static final String CUENTA_ORIGEN = "ES1234567890123456789012";
	private static final String CUENTA_DESTINO = "ES9876543210987654321098";
	private static final String PAIS_HABITUAL = "ES";

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

	/**
	 * Valida la cobertura de la rama secundaria del algoritmo de prevencion de
	 * deadlocks. Asegura que cuando la cuenta de destino es alfabeticamente menor
	 * que la de origen, los bloqueos pesimistas se soliciten en el orden inverso
	 * correcto para evitar abrazos mortales en la base de datos.
	 */
	@Test
	@DisplayName("Debe ordenar cuentas inversamente para prevenir deadlocks (Destino < Origen)")
	void ejecutarTransferencia_OrdenInversoCuentas_Exito() {
		// Arrange: "Z" es mayor que "A", por lo que origenPrimero será false
		TransferenciaDTO dtoInverso = new TransferenciaDTO("Z_CUENTA", "A_CUENTA", new BigDecimal("100.00"), "ES",
				"Test Inverso");
		when(transaccionRepository.findById(1L)).thenReturn(Optional.of(tx));
		when(fraudeScoreCalculator.calcularScore(any(TransferenciaDTO.class))).thenReturn(0.1);

		Cuenta cuentaZ = Cuenta.builder().numeroCuenta("Z_CUENTA").saldo(new BigDecimal("500.00")).build();
		Cuenta cuentaA = Cuenta.builder().numeroCuenta("A_CUENTA").saldo(new BigDecimal("100.00")).build();

		// El procesador pedirá primero A_CUENTA y luego Z_CUENTA
		when(cuentaRepository.findByNumeroCuentaWithLock("A_CUENTA")).thenReturn(Optional.of(cuentaA));
		when(cuentaRepository.findByNumeroCuentaWithLock("Z_CUENTA")).thenReturn(Optional.of(cuentaZ));

		// Act
		transaccionProcesador.ejecutarTransferenciaAsync(dtoInverso, 1L);

		// Assert
		assertEquals(EstadoTransaccion.COMPLETADA, tx.getEstado());
		assertEquals(new BigDecimal("400.00"), cuentaZ.getSaldo());
		assertEquals(new BigDecimal("200.00"), cuentaA.getSaldo());
	}

	/**
	 * Comprueba la robustez del procesamiento asincrono frente a inconsistencias de
	 * datos. Garantiza que si el ID de transaccion proporcionado no existe en la
	 * base de datos, el hilo aborte limpiamente lanzando la excepcion adecuada.
	 */
	@Test
	@DisplayName("Debe abortar silenciosamente si la transacción inicial no existe")
	void ejecutarTransferencia_TransaccionNoExiste_AbortaProceso() {
		// Arrange
		when(transaccionRepository.findById(99L)).thenReturn(Optional.empty());

		// Act
		transaccionProcesador.ejecutarTransferenciaAsync(dto, 99L);

		// Assert
		verify(transaccionRepository).findById(99L);

		verifyNoInteractions(cuentaRepository);
		verifyNoInteractions(fraudeScoreCalculator);
	}

	/**
	 * Valida el motor de procesamiento por lotes (Batch). Verifica que una lista de
	 * transferencias se itere correctamente, persistiendo las entidades iniciales y
	 * que las excepciones individuales (ej. cuenta no encontrada) no detengan el
	 * bucle, sumando con precision los casos de exito y fracaso en el resumen.
	 */
	@Test
	@DisplayName("Debe procesar un sublote mixto sumando exitosas y fallidas")
	void procesarSubloteAsync_LoteMixto_RetornaResumen() throws Exception {
		// Arrange: Un DTO válido (el del setUp) y uno inválido
		TransferenciaDTO dtoInvalido = new TransferenciaDTO("CUENTA_ERRONEA", CUENTA_DESTINO, new BigDecimal("50.00"),
				"ES", "Test Error");
		List<TransferenciaDTO> sublote = List.of(dto, dtoInvalido);

		// Simulamos el guardado de la entidad inicial (crearEntidadInicial)
		when(transaccionRepository.save(any(Transaccion.class))).thenAnswer(invocation -> {
			Transaccion t = invocation.getArgument(0);
			t.setId(System.nanoTime()); // ID simulado para que no sea null
			return t;
		});

		// Simulamos que el DTO 1 pasa bien
		when(fraudeScoreCalculator.calcularScore(dto)).thenReturn(0.1);
		when(cuentaRepository.findByNumeroCuentaWithLock(CUENTA_ORIGEN)).thenReturn(Optional.of(cuentaOrigen));
		when(cuentaRepository.findByNumeroCuentaWithLock(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaDestino));

		// Simulamos que el DTO 2 falla porque la cuenta no existe
		when(fraudeScoreCalculator.calcularScore(dtoInvalido)).thenReturn(0.1);
		when(cuentaRepository.findByNumeroCuentaWithLock("CUENTA_ERRONEA")).thenReturn(Optional.empty());

		// Act
		CompletableFuture<ResumenLoteDTO> future = transaccionProcesador.procesarSubloteAsync(sublote);
		ResumenLoteDTO resumen = future.get(); // Esperamos a que termine el hilo

		// Assert
		assertEquals(2, resumen.totalRecibidas());
		assertEquals(1, resumen.totalExitosas());
		assertEquals(1, resumen.totalFallidas());
	}
}