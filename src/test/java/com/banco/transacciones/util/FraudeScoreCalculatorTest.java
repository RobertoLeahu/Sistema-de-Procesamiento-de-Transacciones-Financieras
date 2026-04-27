package com.banco.transacciones.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banco.transacciones.domain.models.Cliente;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;

/**
 * Clase de pruebas unitarias para la validación de reglas en {@link FraudeScoreCalculator}.
 * * Verifica el correcto cálculo del "score" (puntuación) de riesgo de fraude 
 * sumando diferentes pesos basados en las siguientes reglas de negocio:
 * - Monto elevado: La transferencia supera un umbral establecido (ej. +0.30).
 * - Horario nocturno: La transacción se realiza de madrugada (ej. +0.20).
 * - Alta frecuencia: El origen tiene demasiadas operaciones en poco tiempo (ej. +0.25).
 * - Cuenta reciente: La cuenta destino tiene pocos días de antigüedad (ej. +0.15).
 * - País inusual: El país de la transferencia no coincide con el habitual del cliente (ej. +0.10).
 * * Nota técnica: Se utiliza {@code MockedStatic} sobre {@link Instant} para fijar 
 * el reloj del sistema durante las pruebas. Esto garantiza que las validaciones de 
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

	private MockedStatic<Instant> mockedInstant;

	private final String CUENTA_ORIGEN = "ES1234567890123456789012";
	private final String CUENTA_DESTINO = "ES9876543210987654321098";
	private final String PAIS_HABITUAL = "ES";

	// Fechas estáticas para evitar Flaky Tests
	private final Instant INSTANT_DIURNO = ZonedDateTime.of(2026, 4, 7, 14, 0, 0, 0, ZoneId.systemDefault())
			.toInstant();
	private final Instant INSTANT_NOCTURNO = ZonedDateTime.of(2026, 4, 7, 3, 0, 0, 0, ZoneId.systemDefault())
			.toInstant();

	@BeforeEach
	void setUp() {
		mockedInstant = mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS);
		mockedInstant.when(Instant::now).thenReturn(INSTANT_DIURNO);
	}

	@AfterEach
	void tearDown() {
		mockedInstant.close();
	}

	@Test
	@DisplayName("Debe retornar score 0.0 cuando ninguna regla de fraude se cumple")
	void calcularScore_SinRiesgo_RetornaCero() {
		TransferenciaDTO request = crearRequest(new BigDecimal("5000.00"), PAIS_HABITUAL);

		configurarMocksBase(2, 10, PAIS_HABITUAL);

		double score = calculator.calcularScore(request);

		assertEquals(0.0, score, 0.001);
	}

	@Test
	@DisplayName("Debe retornar score 1.0 cuando TODAS las reglas se cumplen")
	void calcularScore_RiesgoMaximo_RetornaUno() {
		// Cambiamos a horario nocturno
		mockedInstant.when(Instant::now).thenReturn(INSTANT_NOCTURNO);

		// Monto > 10,000 y país distinto al habitual ("FR")
		TransferenciaDTO request = crearRequest(new BigDecimal("15000.00"), "FR");

		// 5 tx recientes (> 3), 2 días de antigüedad (< 7)
		configurarMocksBase(5, 2, PAIS_HABITUAL);

		double score = calculator.calcularScore(request);

		assertEquals(1.0, score, 0.001);
	}

	@Test
	@DisplayName("Debe sumar 0.30 cuando solo el monto supera el umbral")
	void calcularScore_SoloMontoAlto_SumaTreinta() {
		TransferenciaDTO request = crearRequest(new BigDecimal("10000.01"), PAIS_HABITUAL);
		configurarMocksBase(1, 10, PAIS_HABITUAL);

		double score = calculator.calcularScore(request);
		assertEquals(0.30, score, 0.001);
	}

	@Test
	@DisplayName("Debe sumar 0.20 cuando la transacción es en horario nocturno")
	void calcularScore_SoloHoraNocturna_SumaVeinte() {
		mockedInstant.when(Instant::now).thenReturn(INSTANT_NOCTURNO);
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		configurarMocksBase(1, 10, PAIS_HABITUAL);

		double score = calculator.calcularScore(request);
		assertEquals(0.20, score, 0.001);
	}

	@Test
	@DisplayName("Debe sumar 0.25 cuando hay alta frecuencia de transacciones")
	void calcularScore_SoloFrecuencia_SumaVeinticinco() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		configurarMocksBase(4, 10, PAIS_HABITUAL); // 4 transacciones (supera las 3 permitidas)

		double score = calculator.calcularScore(request);
		assertEquals(0.25, score, 0.001);
	}

	@Test
	@DisplayName("Debe sumar 0.15 cuando la cuenta destino es reciente (< 7 días)")
	void calcularScore_SoloCuentaNueva_SumaQuince() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		configurarMocksBase(1, 5, PAIS_HABITUAL); // Cuenta creada hace 5 días

		double score = calculator.calcularScore(request);
		assertEquals(0.15, score, 0.001);
	}

	@Test
	@DisplayName("Debe sumar 0.10 cuando el país es inusual basado en el historial")
	void calcularScore_SoloPaisInusual_SumaDiez() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), "US"); // País de envío US
		configurarMocksBase(1, 10, PAIS_HABITUAL); // Historial dice ES

		double score = calculator.calcularScore(request);
		assertEquals(0.10, score, 0.001);
	}

	@Test
	@DisplayName("Evalúa país inusual usando el fallback de la cuenta cuando no hay historial")
	void calcularScore_PaisInusual_FallbackCuenta_SumaDiez() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), "US");

		// Frecuencia baja
		when(transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(eq(CUENTA_ORIGEN), any(Instant.class)))
				.thenReturn(1L);

		// Sin historial de país en transacciones
		when(transaccionRepository.findPaisHabitual(CUENTA_ORIGEN)).thenReturn(Optional.empty());

		// Cuenta destino (para la validación de antigüedad, ponemos > 7 días)
		Cliente clienteDestino = mock(Cliente.class);
		LocalDate fechaAlta = LocalDate.ofInstant(INSTANT_DIURNO, ZoneId.systemDefault()).minusDays(10);
		when(clienteDestino.getFechaAlta()).thenReturn(fechaAlta);
		Cuenta cuentaDestino = Cuenta.builder().cliente(clienteDestino).build();
		when(cuentaRepository.findByNumeroCuenta(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaDestino));

		// Cuenta origen para extraer el país por defecto (ES)
		Cliente clienteOrigen = mock(Cliente.class);
		when(clienteOrigen.getPaisResidencia()).thenReturn(PAIS_HABITUAL);
		Cuenta cuentaOrigen = Cuenta.builder().cliente(clienteOrigen).build();
		when(cuentaRepository.findByNumeroCuenta(CUENTA_ORIGEN)).thenReturn(Optional.of(cuentaOrigen));

		double score = calculator.calcularScore(request);
		assertEquals(0.10, score, 0.001);
	}

	// --- MÉTODOS AUXILIARES ---
	private TransferenciaDTO crearRequest(BigDecimal monto, String pais) {
		return new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, monto, pais, "Prueba");
	}

	private void configurarMocksBase(long txRecientes, int diasAntiguedad, String paisHabitual) {
		// Frecuencia
		when(transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(eq(CUENTA_ORIGEN), any(Instant.class)))
				.thenReturn(txRecientes);

		// País Habitual
		when(transaccionRepository.findPaisHabitual(CUENTA_ORIGEN)).thenReturn(Optional.of(paisHabitual));

		// Antigüedad Cuenta Destino
		Cliente clienteMock = mock(Cliente.class);
		LocalDate fechaAlta = LocalDate.ofInstant(Instant.now(), ZoneId.systemDefault()).minusDays(diasAntiguedad);
		when(clienteMock.getFechaAlta()).thenReturn(fechaAlta);

		Cuenta cuentaDestino = Cuenta.builder().numeroCuenta(CUENTA_DESTINO).cliente(clienteMock).build();

		when(cuentaRepository.findByNumeroCuenta(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaDestino));
	}
}