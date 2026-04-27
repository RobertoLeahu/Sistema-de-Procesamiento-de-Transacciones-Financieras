package com.banco.transacciones.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banco.transacciones.domain.models.Cliente;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.response.CuentaResumenDTO;
import com.banco.transacciones.exception.CuentaNotFoundException;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

	@Mock
	private CuentaRepository cuentaRepository;

	@Mock
	private TransaccionRepository transaccionRepository;

	@InjectMocks
	private CuentaServiceImpl cuentaService;

	private Cuenta cuentaMock;
	private Cliente clienteMock;

	@BeforeEach
	void setUp() {
		clienteMock = Cliente.builder().id(1L).fechaAlta(LocalDate.now().minusDays(30)).build();

		cuentaMock = Cuenta.builder().id(1L).numeroCuenta("ES1234567890").saldo(new BigDecimal("1500.00"))
				.cliente(clienteMock).build();
	}

	/**
	 * Verifica que la generacion del resumen estadistico calcule con exactitud
	 * matematica el total de movimientos, montos promedios, desviacion estandar y
	 * la puntuacion de riesgo acumulado para una cuenta con historial activo.
	 */
	@Test
	@DisplayName("Debe retornar resumen estadístico con cálculos matemáticos correctos")
	void obtenerResumen_CuentaConTransacciones_CalculosCorrectos() {
		// Arrange
		String numeroCuenta = "ES1234567890";

		Transaccion tx1 = Transaccion.builder().monto(new BigDecimal("100.00")).riesgoFraude(0.2).build();
		Transaccion tx2 = Transaccion.builder().monto(new BigDecimal("200.00")).riesgoFraude(0.4).build();
		Transaccion tx3 = Transaccion.builder().monto(new BigDecimal("150.00")).riesgoFraude(0.1).build();

		when(cuentaRepository.findByNumeroCuenta(numeroCuenta)).thenReturn(Optional.of(cuentaMock));
		when(transaccionRepository.findByCuentaOrigenOrCuentaDestino(numeroCuenta, numeroCuenta))
				.thenReturn(Arrays.asList(tx1, tx2, tx3));

		// Act
		CuentaResumenDTO result = cuentaService.obtenerResumen(numeroCuenta);

		// Assert
		assertNotNull(result);
		assertEquals(numeroCuenta, result.numeroCuenta());
		assertEquals(3, result.totalMovimientos());

		// Suma = 450. Promedio = 450 / 3 = 150.00
		assertEquals(new BigDecimal("150.00"), result.montoPromedio());

		// Varianza de (100, 200, 150) con media 150 -> (2500 + 2500 + 0)/3 = 1666.66...
		// Desviación estándar = sqrt(1666.66...) = 40.8248...
		assertEquals(40.8248, result.desviacionEstandar(), 0.001);

		// Riesgo total = 0.2 + 0.4 + 0.1 = 0.7
		assertEquals(0.7, result.puntuacionRiesgoAcumulada(), 0.001);
	}

	/**
	 * Valida el comportamiento del sistema ante una cuenta sin historial de
	 * transacciones. Asegura que los calculos matematicos se resuelvan de forma
	 * segura devolviendo valores en cero, previniendo excepciones por division
	 * entre cero o inconsistencias de datos.
	 */
	@Test
	@DisplayName("Debe retornar resumen con promedios cero cuando no hay transacciones")
	void obtenerResumen_CuentaSinTransacciones_RetornaCeros() {
		// Arrange
		String numeroCuenta = "ES1234567890";

		when(cuentaRepository.findByNumeroCuenta(numeroCuenta)).thenReturn(Optional.of(cuentaMock));
		when(transaccionRepository.findByCuentaOrigenOrCuentaDestino(numeroCuenta, numeroCuenta))
				.thenReturn(Collections.emptyList());

		// Act
		CuentaResumenDTO result = cuentaService.obtenerResumen(numeroCuenta);

		// Assert
		assertNotNull(result);
		assertEquals(0, result.totalMovimientos());
		assertEquals(BigDecimal.ZERO, result.montoPromedio());
		assertEquals(0.0, result.desviacionEstandar());
		assertEquals(0.0, result.puntuacionRiesgoAcumulada());
	}

	/**
	 * Comprueba el manejo de excepciones de negocio en la capa de servicio.
	 * Garantiza que se interrumpa el flujo y se lance una CuentaNotFoundException
	 * cuando se solicita el resumen de un numero de cuenta no registrado en la base
	 * de datos.
	 */
	@Test
	@DisplayName("Debe lanzar CuentaNotFoundException si la cuenta no existe")
	void obtenerResumen_CuentaNoExiste_LanzaExcepcion() {
		// Arrange
		String numeroCuenta = "ES_FALSA";
		when(cuentaRepository.findByNumeroCuenta(numeroCuenta)).thenReturn(Optional.empty());

		// Act & Assert
		CuentaNotFoundException exception = assertThrows(CuentaNotFoundException.class, () -> {
			cuentaService.obtenerResumen(numeroCuenta);
		});

		assertTrue(exception.getMessage().contains("Cuenta no encontrada"));
		verify(transaccionRepository, never()).findByCuentaOrigenOrCuentaDestino(anyString(), anyString());
	}
}