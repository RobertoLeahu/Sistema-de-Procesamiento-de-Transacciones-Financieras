package com.banco.transacciones.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.banco.transacciones.dto.response.CuentaResumenDTO;

/**
 * Clase de pruebas unitarias para la validacion del algoritmo de ordenamiento de cuentas por riesgo.
 * Verifica la aplicacion estricta de la matriz de prioridades del negocio:
 * - Criterio 1: Mayor score de fraude acumulado tiene prioridad absoluta.
 * - Criterio 2: En caso de empate estadistico, mayor cantidad de alertas criticas asume prioridad.
 * - Criterio 3: Ante empate tecnico total, las cuentas mas recientes (menor antiguedad) escalan en prioridad.
 * - Proteccion: Manejo seguro y matematico de valores nulos, asumiendo riesgo cero.
 */
class CuentaRiesgoComparatorTest {

	private CuentaRiesgoComparator comparator;

	@BeforeEach
	void setUp() {
		comparator = new CuentaRiesgoComparator();
	}

	@Test
	@DisplayName("Debe ordenar por score de riesgo de forma descendente")
	void compare_PorScoreDeRiesgo_Exito() {
		CuentaResumenDTO cuentaAltoRiesgo = crearDTO(0.9, 0, LocalDate.now());
		CuentaResumenDTO cuentaBajoRiesgo = crearDTO(0.2, 0, LocalDate.now());

		int resultado = comparator.compare(cuentaAltoRiesgo, cuentaBajoRiesgo);
		assertTrue(resultado < 0, "La cuenta con mayor riesgo debe posicionarse primero");

		int resultadoInverso = comparator.compare(cuentaBajoRiesgo, cuentaAltoRiesgo);
		assertTrue(resultadoInverso > 0, "La cuenta con menor riesgo debe posicionarse despues");
	}

	@Test
	@DisplayName("Debe desempatar por alertas criticas si el score es exactamente igual")
	void compare_EmpateEnScore_DesempataPorAlertas() {
		CuentaResumenDTO cuentaMasAlertas = crearDTO(0.5, 5, LocalDate.now());
		CuentaResumenDTO cuentaMenosAlertas = crearDTO(0.5, 2, LocalDate.now());

		int resultado = comparator.compare(cuentaMasAlertas, cuentaMenosAlertas);
		assertTrue(resultado < 0, "A igual score, la cuenta con mas alertas criticas debe ir primero");
	}

	@Test
	@DisplayName("Debe desempatar por antiguedad (mas nuevas primero) si score y alertas empatan")
	void compare_EmpateEnScoreYAlertas_DesempataPorAntiguedad() {
		// La cuenta nueva tiene fecha de alta de hoy, la antigua de hace 5 años
		CuentaResumenDTO cuentaNueva = crearDTO(0.5, 2, LocalDate.now());
		CuentaResumenDTO cuentaAntigua = crearDTO(0.5, 2, LocalDate.now().minusYears(5));

		int resultado = comparator.compare(cuentaNueva, cuentaAntigua);
		assertTrue(resultado < 0, "A igual score y alertas, la cuenta de creacion mas reciente debe ir primero");
	}

	@Test
	@DisplayName("Debe manejar valores nulos en el score asumiendo riesgo 0.0")
	void compare_ValoresNulosEnScore_AsumeCero() {
		CuentaResumenDTO cuentaConRiesgoNulo1 = crearDTO(null, 0, LocalDate.now());
		CuentaResumenDTO cuentaConRiesgoNulo2 = crearDTO(null, 0, LocalDate.now());
		CuentaResumenDTO cuentaConRiesgoBajo = crearDTO(0.1, 0, LocalDate.now());

		// Al comparar dos nulos, ambos se evalúan como 0.0 y terminan empatando en todo
		// (resultado 0)
		int empate = comparator.compare(cuentaConRiesgoNulo1, cuentaConRiesgoNulo2);
		assertEquals(0, empate, "Dos scores nulos deben ser tratados como iguales (0.0)");

		// Al comparar un nulo (0.0) contra un 0.1, el 0.1 debe ganar
		int resultado = comparator.compare(cuentaConRiesgoBajo, cuentaConRiesgoNulo1);
		assertTrue(resultado < 0, "Un riesgo > 0 debe ordenarse antes que un riesgo nulo (evaluado como 0.0)");

		// Al comparar un nulo en el primer parámetro contra un 0.1 en el segundo
		int resultadoInverso = comparator.compare(cuentaConRiesgoNulo1, cuentaConRiesgoBajo);
		assertTrue(resultadoInverso > 0, "El nulo debe ordenarse despues del riesgo > 0");
	}

	/**
	 * Factory Helper Pattern para construir instancias del Record inmutable
	 * rápidamente
	 */
	private CuentaResumenDTO crearDTO(Double score, long alertas, LocalDate fechaAlta) {
		return new CuentaResumenDTO("ES1234567890", BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0.0, score, alertas,
				fechaAlta);
	}
}