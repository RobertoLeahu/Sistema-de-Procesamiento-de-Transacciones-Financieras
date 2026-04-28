package com.banco.transacciones.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.banco.transacciones.dto.response.CuentaResumenDTO;
import com.banco.transacciones.exception.CuentaNotFoundException;
import com.banco.transacciones.service.impl.CuentaServiceImpl;

/**
 * Pruebas unitarias para CuentaController. Utiliza @WebMvcTest para instanciar
 * únicamente la capa web, aislando el controlador de la base de datos y la
 * lógica de negocio real.
 */
@WebMvcTest(CuentaController.class)
class CuentaIntregationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private CuentaServiceImpl cuentaService;

	/**
	 * Prueba que verifica el escenario exitoso de obtención de un resumen
	 * estadístico de cuenta. Se simula el comportamiento del servicio para devolver
	 * un DTO válido y se evalúa que el controlador responda con un código HTTP 200
	 * (OK), además de asegurar que la estructura JSON resultante mapea
	 * correctamente las propiedades de los records.
	 */
	@Test
	@DisplayName("Debe retornar 200 OK y el resumen estadístico cuando la cuenta existe")
	void obtenerResumenCuenta_Exitosa_Retorna200() throws Exception {
		// Arrange
		String numeroCuenta = "ES12345678901234567890";
		CuentaResumenDTO mockResumen = new CuentaResumenDTO(numeroCuenta, new BigDecimal("5000.00"), 10L, // totalMovimientos
				new BigDecimal("500.00"), // montoPromedio
				12.5, // desviacionEstandar
				0.45, // puntuacionRiesgoAcumulada
				0L, // alertasCriticas
				LocalDate.now() // fechaAlta
		);

		// Simulamos la respuesta del servicio
		when(cuentaService.obtenerResumen(numeroCuenta)).thenReturn(mockResumen);

		// Act & Assert
		mockMvc.perform(get("/api/cuentas/{id}/resumen", numeroCuenta).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk()).andExpect(jsonPath("$.numeroCuenta").value(numeroCuenta))
				.andExpect(jsonPath("$.saldoActual").value(5000.00)).andExpect(jsonPath("$.totalMovimientos").value(10))
				.andExpect(jsonPath("$.desviacionEstandar").value(12.5))
				.andExpect(jsonPath("$.puntuacionRiesgoAcumulada").value(0.45));
	}

	/**
	 * Prueba que verifica el manejo de errores cuando se consulta una cuenta que no
	 * existe en el sistema. Se simula el lanzamiento de la excepción
	 * {@link CuentaNotFoundException} desde la capa de servicio y se evalúa que el
	 * controlador (respaldado por el manejador de excepciones global) intercepte el
	 * error devolviendo un código HTTP 404 (Not Found) y el formato JSON
	 * estandarizado de error.
	 */
	@Test
	@DisplayName("Debe retornar 404 Not Found cuando la cuenta no existe")
	void obtenerResumenCuenta_NoExiste_Retorna404() throws Exception {
		// Arrange
		String numeroCuentaInexistente = "ES99999999999999999999";

		// Simulamos que el servicio lanza la excepción de negocio
		when(cuentaService.obtenerResumen(anyString()))
				.thenThrow(new CuentaNotFoundException("Cuenta no encontrada: " + numeroCuentaInexistente));

		// Act & Assert
		// El @WebMvcTest cargará automáticamente el GlobalExceptionHandler
		mockMvc.perform(
				get("/api/cuentas/{id}/resumen", numeroCuentaInexistente).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Cuenta no encontrada"))
				.andExpect(jsonPath("$.status").value(404));
	}
}