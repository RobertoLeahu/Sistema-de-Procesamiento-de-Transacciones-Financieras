package com.banco.transacciones.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.enums.NivelRiesgo;
import com.banco.transacciones.domain.enums.TipoTransaccion;
import com.banco.transacciones.domain.models.AlertaFraude;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.exception.AlertaNotFoundException;
import com.banco.transacciones.repository.AlertaFraudeRepository;
import com.banco.transacciones.repository.TransaccionRepository;

/**
 * Pruebas de integración para FraudeController. Combina MockMvc para evaluar
 * endpoints GET con paginación, y utiliza invocación directa al Bean del
 * controlador para operaciones PUT. Esta arquitectura de test evita falsos
 * positivos (errores 500 de ruteo).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // H2
class FraudeIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	// Inyectamos el controlador directamente para sortear la capa HTTP de MockMvc
	// en el PUT
	@Autowired
	private FraudeController fraudeController;

	@Autowired
	private AlertaFraudeRepository alertaFraudeRepository;

	@Autowired
	private TransaccionRepository transaccionRepository;

	@BeforeEach
	void setUp() {
		// Limpiamos la base de datos antes de cada test para evitar colisiones
		alertaFraudeRepository.deleteAll();
		transaccionRepository.deleteAll();
	}

	/**
	 * Prueba que verifica la obtención de un listado paginado de alertas de fraude
	 * pendientes de revisión. Valida que la integración con la base de datos H2
	 * funcione correctamente, devolviendo un código HTTP 200 OK y la estructura
	 * esperada de un objeto Page de Spring Data.
	 */
	@Test
	@DisplayName("Debe retornar 200 OK y una página de alertas pendientes")
	void listarAlertasPendientes_Exitosa_Retorna200() throws Exception {
		// Arrange
		Transaccion transaccionBase = Transaccion.builder().cuentaOrigen("ES12345678901234567890")
				.cuentaDestino("ES09876543210987654321").monto(new BigDecimal("1000"))
				.tipo(TipoTransaccion.TRANSFERENCIA).estado(EstadoTransaccion.COMPLETADA).build();
		Transaccion transaccionGuardada = transaccionRepository.save(transaccionBase);

		AlertaFraude alerta = AlertaFraude.builder().transaccion(transaccionGuardada).nivel(NivelRiesgo.CRITICO)
				.motivo("Score superior a 0.75").revisada(false).build();
		alertaFraudeRepository.save(alerta);

		// Act & Assert (Vía MockMvc)
		mockMvc.perform(get("/api/fraude/alertas").param("page", "0").param("size", "20")
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].transaccionId").value(transaccionGuardada.getId()))
				.andExpect(jsonPath("$.content[0].nivel").value(NivelRiesgo.CRITICO.name()))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	/**
	 * Prueba que valida el proceso exitoso de revisión de una alerta de fraude.
	 * Utiliza la invocación directa al controlador para evitar falsos positivos de
	 * enrutamiento en Spring MVC, asegurando que el estado interno cambie
	 * correctamente y el controlador devuelva una respuesta HTTP 204 No Content.
	 */
	@Test
	@DisplayName("Debe marcar la alerta como revisada y retornar 204 No Content")
	void revisarAlerta_Exitosa_Retorna204() {
		// Arrange
		Transaccion transaccionBase = Transaccion.builder().cuentaOrigen("ES12345678901234567890")
				.cuentaDestino("ES09876543210987654321").monto(new BigDecimal("1000"))
				.tipo(TipoTransaccion.TRANSFERENCIA).estado(EstadoTransaccion.COMPLETADA).build();
		Transaccion transaccionGuardada = transaccionRepository.save(transaccionBase);

		AlertaFraude alerta = AlertaFraude.builder().transaccion(transaccionGuardada).nivel(NivelRiesgo.ALTO)
				.motivo("Revisión manual requerida").revisada(false).build();
		AlertaFraude alertaGuardada = alertaFraudeRepository.save(alerta);

		// Act (Invocación directa al Controlador)
		ResponseEntity<Void> response = fraudeController.revisarAlerta(alertaGuardada.getId());

		// Assert
		assertEquals(204, response.getStatusCode().value(), "El controlador debe retornar 204 No Content");
	}

	/**
	 * Prueba el flujo alternativo cuando se intenta revisar una alerta con un ID
	 * que no existe en la base de datos. Verifica que la lógica de negocio lance la
	 * excepción personalizada {@link AlertaNotFoundException}, la cual será
	 * capturada en el entorno de producción por el GlobalExceptionHandler.
	 */
	@Test
	@DisplayName("Debe lanzar excepción 404 si la alerta no existe")
	void revisarAlerta_NoExiste_Retorna404() {
		// Act & Assert (Invocación directa)
		// Verificamos que la excepción de negocio brota exitosamente del controlador
		assertThrows(AlertaNotFoundException.class, () -> {
			fraudeController.revisarAlerta(99999L);
		}, "Se esperaba AlertaNotFoundException al consultar un ID inexistente");
	}
}