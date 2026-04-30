package com.banco.transacciones.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banco.transacciones.domain.models.Cliente;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.dto.response.ResultadoFraude;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;

/**
 * Clase de pruebas unitarias para la validación de reglas en
 * {@link FraudeScoreCalculator}. * Verifica el correcto cálculo del "score"
 * (puntuación) de riesgo de fraude sumando diferentes pesos basados en las
 * siguientes reglas de negocio: - Monto elevado: La transferencia supera un
 * umbral establecido (ej. +0.30). - Horario nocturno: La transacción se realiza
 * de madrugada (ej. +0.20). - Alta frecuencia: El origen tiene demasiadas
 * operaciones en poco tiempo (ej. +0.25). - Cuenta reciente: La cuenta destino
 * tiene pocos días de antigüedad (ej. +0.15). - País inusual: El país de la
 * transferencia no coincide con el habitual del cliente (ej. +0.10). * Nota
 * técnica: Se utiliza {@code MockedStatic} sobre {@link Instant} para fijar el
 * reloj del sistema durante las pruebas. Esto garantiza que las validaciones de
 * horarios (diurno/nocturno) sean deterministas y evita los flaky tests.
 */
@ExtendWith(MockitoExtension.class)
class FraudeScoreCalculatorTest {

	@InjectMocks
	private FraudeScoreCalculator calculator;

	@Mock
	private TransaccionRepository transaccionRepository;

	@Mock
	private CuentaRepository cuentaRepository;

	@Mock
	private Clock clock;

	private static final String CUENTA_ORIGEN = "ES1234567890123456789012";
	private static final String CUENTA_DESTINO = "ES9876543210987654321098";
	private static final String PAIS_HABITUAL = "ES";

	// Fechas estáticas para evitar Flaky Tests
	private static final Instant INSTANT_DIURNO = ZonedDateTime.of(2026, 4, 7, 14, 0, 0, 0, ZoneId.systemDefault())
			.toInstant();
	private static final Instant INSTANT_NOCTURNO = ZonedDateTime.of(2026, 4, 7, 3, 0, 0, 0, ZoneId.systemDefault())
			.toInstant();

	@BeforeEach
	void setUp() {
		Mockito.lenient().when(clock.instant()).thenReturn(INSTANT_DIURNO);
		Mockito.lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
	}

	@Test
	@DisplayName("Debe retornar score 0.0 y sin motivos cuando ninguna regla se cumple")
	void calcularScore_SinRiesgo_RetornaCero() {
		TransferenciaDTO request = crearRequest(new BigDecimal("5000.00"), PAIS_HABITUAL);

		configurarMocksBase(2, 10, PAIS_HABITUAL);

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(0.0, resultado.score(), 0.001);
		assertTrue(resultado.motivos().isEmpty(), "No debe registrar ningún motivo de fraude");
	}

	@Test
	@DisplayName("Debe retornar score 1.0 y todos los motivos cuando TODAS las reglas se cumplen")
	void calcularScore_RiesgoMaximo_RetornaUno() {
		when(clock.instant()).thenReturn(INSTANT_NOCTURNO);
		TransferenciaDTO request = crearRequest(new BigDecimal("15000.00"), "FR");

		configurarMocksBase(5, 2, PAIS_HABITUAL);

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(1.0, resultado.score(), 0.001);
		assertEquals(5, resultado.motivos().size(), "Deben registrarse exactamente 5 motivos");
		assertTrue(resultado.motivos().contains("Monto elevado (>10.000)"));
		assertTrue(resultado.motivos().contains("Horario inusual (00:00-05:00)"));
		assertTrue(resultado.motivos().contains("Alta frecuencia (>3 transacciones en 5 min)"));
		assertTrue(resultado.motivos().contains("Cuenta destino reciente (<7 días)"));
		assertTrue(resultado.motivos().stream().anyMatch(m -> m.contains("País destino inusual")));
	}

	@Test
	@DisplayName("Debe sumar 0.30 y registrar el motivo cuando solo el monto supera el umbral")
	void calcularScore_SoloMontoAlto_SumaTreinta() {
		TransferenciaDTO request = crearRequest(new BigDecimal("10000.01"), PAIS_HABITUAL);
		configurarMocksBase(1, 10, PAIS_HABITUAL);

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(0.30, resultado.score(), 0.001);
		assertEquals(1, resultado.motivos().size());
		assertEquals("Monto elevado (>10.000)", resultado.motivos().get(0));
	}

	@Test
	@DisplayName("Debe sumar 0.20 y registrar el motivo cuando la transacción es en horario nocturno")
	void calcularScore_SoloHoraNocturna_SumaVeinte() {
		when(clock.instant()).thenReturn(INSTANT_NOCTURNO);
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		configurarMocksBase(1, 10, PAIS_HABITUAL);

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(0.20, resultado.score(), 0.001);
		assertEquals("Horario inusual (00:00-05:00)", resultado.motivos().get(0));
	}

	@Test
	@DisplayName("Debe sumar 0.25 y registrar el motivo cuando hay alta frecuencia de transacciones")
	void calcularScore_SoloFrecuencia_SumaVeinticinco() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		configurarMocksBase(4, 10, PAIS_HABITUAL);

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(0.25, resultado.score(), 0.001);
		assertEquals("Alta frecuencia (>3 transacciones en 5 min)", resultado.motivos().get(0));
	}

	@Test
	@DisplayName("Debe sumar 0.15 y registrar el motivo cuando la cuenta destino es reciente (< 7 días)")
	void calcularScore_SoloCuentaNueva_SumaQuince() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		configurarMocksBase(1, 5, PAIS_HABITUAL);

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(0.15, resultado.score(), 0.001);
		assertEquals("Cuenta destino reciente (<7 días)", resultado.motivos().get(0));
	}

	@Test
	@DisplayName("Debe sumar 0.10 y registrar el motivo cuando el país es inusual basado en el historial")
	void calcularScore_SoloPaisInusual_SumaDiez() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), "US");
		configurarMocksBase(1, 10, PAIS_HABITUAL);

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(0.10, resultado.score(), 0.001);
		assertTrue(resultado.motivos().get(0).contains("País destino inusual (US)"));
	}

	@Test
	@DisplayName("Evalúa país inusual usando el fallback de la cuenta cuando no hay historial")
	void calcularScore_PaisInusual_FallbackCuenta_SumaDiez() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), "US");

		when(transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(eq(CUENTA_ORIGEN), any(Instant.class)))
				.thenReturn(1L);

		when(transaccionRepository.findPaisHabitual(CUENTA_ORIGEN)).thenReturn(Optional.empty());

		Cliente clienteDestino = mock(Cliente.class);
		LocalDate fechaAlta = LocalDate.ofInstant(INSTANT_DIURNO, ZoneId.systemDefault()).minusDays(10);
		when(clienteDestino.getFechaAlta()).thenReturn(fechaAlta);
		Cuenta cuentaDestino = Cuenta.builder().cliente(clienteDestino).build();
		when(cuentaRepository.findByNumeroCuenta(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaDestino));

		Cliente clienteOrigen = mock(Cliente.class);
		when(clienteOrigen.getPaisResidencia()).thenReturn(PAIS_HABITUAL);
		Cuenta cuentaOrigen = Cuenta.builder().cliente(clienteOrigen).build();
		when(cuentaRepository.findByNumeroCuenta(CUENTA_ORIGEN)).thenReturn(Optional.of(cuentaOrigen));

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(0.10, resultado.score(), 0.001);
		assertTrue(resultado.motivos().get(0).contains("País destino inusual (US)"));
	}

	@Test
	@DisplayName("No debe sumar puntos ni motivos si la cuenta destino no existe")
	void calcularScore_CuentaDestinoInexistente_NoSumaAntiguedad() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		when(cuentaRepository.findByNumeroCuenta(CUENTA_DESTINO)).thenReturn(Optional.empty());

		when(transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(any(), any())).thenReturn(0L);
		when(transaccionRepository.findPaisHabitual(any())).thenReturn(Optional.of(PAIS_HABITUAL));

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(0.0, resultado.score(), 0.001);
		assertTrue(resultado.motivos().isEmpty());
	}

	@Test
	@DisplayName("Debe manejar correctamente cliente o fecha de alta nulos sin sumar motivos extra")
	void calcularScore_DatosClienteIncompletos_NoSumaAntiguedad() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);

		Cuenta cuentaSinCliente = Cuenta.builder().cliente(null).build();
		when(cuentaRepository.findByNumeroCuenta(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaSinCliente));

		when(transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(any(), any())).thenReturn(0L);
		when(transaccionRepository.findPaisHabitual(any())).thenReturn(Optional.of(PAIS_HABITUAL));

		ResultadoFraude resultado = calculator.calcularScore(request);

		assertEquals(0.0, resultado.score(), 0.001);
		assertTrue(resultado.motivos().isEmpty());
	}

	// --- MÉTODOS AUXILIARES ---
	private TransferenciaDTO crearRequest(BigDecimal monto, String pais) {
		return new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, monto, pais, "Prueba");
	}

	private void configurarMocksBase(long txRecientes, int diasAntiguedad, String paisHabitual) {
		when(transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(eq(CUENTA_ORIGEN), any(Instant.class)))
				.thenReturn(txRecientes);

		when(transaccionRepository.findPaisHabitual(CUENTA_ORIGEN)).thenReturn(Optional.of(paisHabitual));

		Cliente clienteMock = mock(Cliente.class);
		LocalDate fechaAlta = LocalDate.ofInstant(INSTANT_DIURNO, ZoneId.systemDefault()).minusDays(diasAntiguedad);
		when(clienteMock.getFechaAlta()).thenReturn(fechaAlta);

		Cuenta cuentaDestino = Cuenta.builder().numeroCuenta(CUENTA_DESTINO).cliente(clienteMock).build();

		when(cuentaRepository.findByNumeroCuenta(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaDestino));
	}
}