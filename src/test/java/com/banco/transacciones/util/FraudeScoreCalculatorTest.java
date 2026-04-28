package com.banco.transacciones.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	/**
	 * Verifica el flujo nominal de una operacion legitima. Asegura que si ningun
	 * umbral o heuristica de riesgo se dispara, el motor estadistico mantenga el
	 * acumulador estrictamente en 0.0.
	 */
	@Test
	@DisplayName("Debe retornar score 0.0 cuando ninguna regla de fraude se cumple")
	void calcularScore_SinRiesgo_RetornaCero() {
		TransferenciaDTO request = crearRequest(new BigDecimal("5000.00"), PAIS_HABITUAL);

		configurarMocksBase(2, 10, PAIS_HABITUAL);

		double score = calculator.calcularScore(request);

		assertEquals(0.0, score, 0.001);
	}

	/**
	 * Comprueba el peor escenario estadistico posible. Garantiza que la
	 * coincidencia simultanea de todos los indicadores de riesgo sume exactamente
	 * 1.0 (100%), marcando el limite superior matematico del calculador.
	 */
	@Test
	@DisplayName("Debe retornar score 1.0 cuando TODAS las reglas se cumplen")
	void calcularScore_RiesgoMaximo_RetornaUno() {
		// Cambiamos a horario nocturno
		when(clock.instant()).thenReturn(INSTANT_NOCTURNO);
		// Monto > 10,000 y país distinto al habitual ("FR")
		TransferenciaDTO request = crearRequest(new BigDecimal("15000.00"), "FR");

		// 5 tx recientes (> 3), 2 días de antigüedad (< 7)
		configurarMocksBase(5, 2, PAIS_HABITUAL);

		double score = calculator.calcularScore(request);

		assertEquals(1.0, score, 0.001);
	}

	/**
	 * Aísla y valida la regla de impacto financiero. Asegura que el sobrepasar el
	 * limite monetario configurado incremente la ponderacion exacta estipulada para
	 * este factor individual.
	 */
	@Test
	@DisplayName("Debe sumar 0.30 cuando solo el monto supera el umbral")
	void calcularScore_SoloMontoAlto_SumaTreinta() {
		TransferenciaDTO request = crearRequest(new BigDecimal("10000.01"), PAIS_HABITUAL);
		configurarMocksBase(1, 10, PAIS_HABITUAL);

		double score = calculator.calcularScore(request);
		assertEquals(0.30, score, 0.001);
	}

	/**
	 * Comprueba la heuristica cronologica. Mediante la inyeccion de un reloj fijo,
	 * asegura que las transacciones ejecutadas en horario no comercial o madrugada
	 * reciban la penalizacion por comportamiento inusual.
	 */
	@Test
	@DisplayName("Debe sumar 0.20 cuando la transacción es en horario nocturno")
	void calcularScore_SoloHoraNocturna_SumaVeinte() {
		when(clock.instant()).thenReturn(INSTANT_NOCTURNO);
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		configurarMocksBase(1, 10, PAIS_HABITUAL);

		double score = calculator.calcularScore(request);
		assertEquals(0.20, score, 0.001);
	}

	/**
	 * Valida la regla de deteccion de rafagas o ataques automatizados. Verifica que
	 * la acumulacion acelerada de peticiones desde un mismo origen infle el score
	 * de riesgo preventivamente.
	 */
	@Test
	@DisplayName("Debe sumar 0.25 cuando hay alta frecuencia de transacciones")
	void calcularScore_SoloFrecuencia_SumaVeinticinco() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		configurarMocksBase(4, 10, PAIS_HABITUAL); // 4 transacciones (supera las 3 permitidas)

		double score = calculator.calcularScore(request);
		assertEquals(0.25, score, 0.001);
	}

	/**
	 * Aísla la verificacion anti-mulas. Penaliza el envio de fondos hacia cuentas
	 * de nueva creacion que no cuentan con un historial comprobado en el banco.
	 */
	@Test
	@DisplayName("Debe sumar 0.15 cuando la cuenta destino es reciente (< 7 días)")
	void calcularScore_SoloCuentaNueva_SumaQuince() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		configurarMocksBase(1, 5, PAIS_HABITUAL); // Cuenta creada hace 5 días

		double score = calculator.calcularScore(request);
		assertEquals(0.15, score, 0.001);
	}

	/**
	 * Verifica la geolocalizacion del riesgo basado en inteligencia historica.
	 * Aumenta el score si el pais de destino difiere del comportamiento habitual
	 * que el modelo ha registrado previamente para esa cuenta.
	 */
	@Test
	@DisplayName("Debe sumar 0.10 cuando el país es inusual basado en el historial")
	void calcularScore_SoloPaisInusual_SumaDiez() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), "US"); // País de envío US
		configurarMocksBase(1, 10, PAIS_HABITUAL); // Historial dice ES

		double score = calculator.calcularScore(request);
		assertEquals(0.10, score, 0.001);
	}

	/**
	 * Comprueba la resiliencia del modelo predictivo frente a la falta de
	 * historial. Asegura que, si no hay registros previos, el motor se apoye en los
	 * datos base de residencia del cliente para ejecutar la evaluacion geografica.
	 */
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

	/**
	 * Garantiza la estabilidad del sistema frente a inconsistencias
	 * transaccionales. Valida que una peticion hacia una cuenta inexistente no
	 * detenga el motor de calculo y simplemente obvie la metrica de antiguedad.
	 */
	@Test
	@DisplayName("No debe sumar puntos si la cuenta destino no existe")
	void calcularScore_CuentaDestinoInexistente_NoSumaAntiguedad() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);
		when(cuentaRepository.findByNumeroCuenta(CUENTA_DESTINO)).thenReturn(Optional.empty());

		when(transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(any(), any())).thenReturn(0L);
		when(transaccionRepository.findPaisHabitual(any())).thenReturn(Optional.of(PAIS_HABITUAL));

		double score = calculator.calcularScore(request);
		assertEquals(0.0, score);
	}

	/**
	 * Valida el manejo seguro de corrupcion de datos en capa de dominio. Si un
	 * registro carece de fechas o datos obligatorios en su cliente vinculado, el
	 * calculador ignora silenciosamente ese peso en lugar de fallar.
	 */
	@Test
	@DisplayName("Debe manejar correctamente cliente o fecha de alta nulos")
	void calcularScore_DatosClienteIncompletos_NoSumaAntiguedad() {
		TransferenciaDTO request = crearRequest(new BigDecimal("1000.00"), PAIS_HABITUAL);

		// Caso: Cliente nulo
		Cuenta cuentaSinCliente = Cuenta.builder().cliente(null).build();
		when(cuentaRepository.findByNumeroCuenta(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaSinCliente));

		// Evitamos que sume por otros motivos
		when(transaccionRepository.countByCuentaOrigenAndFechaHoraAfter(any(), any())).thenReturn(0L);
		when(transaccionRepository.findPaisHabitual(any())).thenReturn(Optional.of(PAIS_HABITUAL));

		double score = calculator.calcularScore(request);
		assertEquals(0.0, score, 0.001);
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
		LocalDate fechaAlta = LocalDate.ofInstant(INSTANT_DIURNO, ZoneId.systemDefault()).minusDays(diasAntiguedad);
		when(clienteMock.getFechaAlta()).thenReturn(fechaAlta);

		Cuenta cuentaDestino = Cuenta.builder().numeroCuenta(CUENTA_DESTINO).cliente(clienteMock).build();

		when(cuentaRepository.findByNumeroCuenta(CUENTA_DESTINO)).thenReturn(Optional.of(cuentaDestino));
	}
}