package com.banco.transacciones.exception;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.banco.transacciones.controller.TestExceptionController;

/**
 * Clase de pruebas unitarias para {@link GlobalExceptionHandler}. Se utiliza el
 * controlador {@link TestExceptionController} para forzar el lanzamiento de
 * cada tipo de excepción y asertar que el manejador global las procesa y
 * estandariza correctamente al formato JSON esperado.
 */
@WebMvcTest(controllers = TestExceptionController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	private static final String VALIDATION_ERROR = "Validation Error";
	private static final String ERROR_INTERNO = "Error interno";

	/**
	 * Verifica el manejo de la excepción de fondos insuficientes. Se espera un
	 * código HTTP 400 (Bad Request).
	 */
	@Test
	@DisplayName("Debe retornar 400 cuando el saldo es insuficiente")
	void testHandleSaldoInsuficiente() throws Exception {
		mockMvc.perform(get("/test/saldo-insuficiente")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status", is(400))).andExpect(jsonPath("$.path", is("/test/saldo-insuficiente")));
	}

	/**
	 * Verifica el manejo de la excepción de cuenta bloqueada. Se espera un código
	 * HTTP 403 (Forbidden).
	 */
	@Test
	@DisplayName("Debe retornar 403 cuando la cuenta esté bloqueada")
	void testHandleCuentabloqueada() throws Exception {
		mockMvc.perform(get("/test/cuenta-bloqueada")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status", is(403))).andExpect(jsonPath("$.error", is("Cuenta bloqueada")));
	}

	/**
	 * Verifica el manejo de la excepción de transacción no encontrada. Se espera un
	 * código HTTP 404 (Not Found).
	 */
	@Test
	@DisplayName("Debe retornar 404 cuando no se encuentre la transacción buscada")
	void testHandleTransaccionNotFound() throws Exception {
		mockMvc.perform(get("/test/not-found")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status", is(404)))
				.andExpect(jsonPath("$.error", is("Transacción no encontrada")));
	}

	/**
	 * Verifica el manejo de la excepción de conflicto de concurrencia o bloqueo. Se
	 * espera un código HTTP 409 (Conflict).
	 */
	@Test
	@DisplayName("Debe retornar 409 cuando haya un conflicto de concurrencia")
	void testHandleConcurrencyFailureException() throws Exception {
		mockMvc.perform(get("/test/concurrency")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.status", is(409)))
				.andExpect(jsonPath("$.error", is("Conflicto de concurrencia")));
	}

	/**
	 * Verifica el manejo de la excepción de cuenta no encontrada. Se espera un
	 * código HTTP 404 (Not Found).
	 */
	@Test
	@DisplayName("Debe retornar 404 cuando no se encuentre la cuenta")
	void testHandleCuentaNotFound() throws Exception {
		mockMvc.perform(get("/test/cuenta-not-found")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status", is(404))).andExpect(jsonPath("$.error", notNullValue()));
	}

	/**
	 * Verifica el manejo de la excepción de alerta de fraude no encontrada. Se
	 * espera un código HTTP 404 (Not Found).
	 */
	@Test
	@DisplayName("Debe retornar 404 cuando no se encuentre la alerta")
	void testHandleAlertaNotFound() throws Exception {
		mockMvc.perform(get("/test/alerta-not-found")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status", is(404))).andExpect(jsonPath("$.error", notNullValue()));
	}

	/**
	 * Verifica el manejo de excepciones de negocio generales. Se espera un código
	 * HTTP 500 (Internal Server Error) por defecto.
	 */
	@Test
	@DisplayName("Debe retornar 500 para GeneralException")
	void testHandleGeneralException() throws Exception {
		mockMvc.perform(get("/test/general-exception")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status", is(500))).andExpect(jsonPath("$.error", is(ERROR_INTERNO)));
	}

	/**
	 * Verifica el manejo de excepciones imprevistas (fallos críticos). Se espera un
	 * código HTTP 500 (Internal Server Error).
	 */
	@Test
	@DisplayName("Debe retornar 500 para Exception genérica no controlada")
	void testHandleUnknownException() throws Exception {
		mockMvc.perform(get("/test/exception")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status", is(500))).andExpect(jsonPath("$.error", is(ERROR_INTERNO)));
	}

	/**
	 * Verifica el manejo de violaciones de restricciones de base de datos o
	 * validaciones directas. Se espera un código HTTP 400 (Bad Request).
	 */
	@Test
	@DisplayName("Debe retornar 400 para ConstraintViolationException")
	void testHandleConstraintViolationException() throws Exception {
		mockMvc.perform(get("/test/constraint-violation")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status", is(400))).andExpect(jsonPath("$.error", is(VALIDATION_ERROR)));
	}

	/**
	 * Verifica el manejo de errores de validación lanzados por la anotación @Valid
	 * en cuerpos de petición. Se espera un código HTTP 400 (Bad Request).
	 */
	@Test
	@DisplayName("Debe retornar 400 para MethodArgumentNotValidException")
	void testHandleMethodArgumentNotValidException() throws Exception {
		mockMvc.perform(
				post("/test/method-argument").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status", is(400)))
				.andExpect(jsonPath("$.error", is(VALIDATION_ERROR)));
	}

	/**
	 * Verifica el manejo de la nueva excepción de validación de parámetros en
	 * Spring Boot 3.x. Se espera un código HTTP 400 (Bad Request).
	 */
	@Test
	@DisplayName("Debe retornar 400 para HandlerMethodValidationException en Spring Boot 3.x")
	void testHandleHandlerMethodValidationException() throws Exception {
		mockMvc.perform(get("/test/handler-method").param("param", "123")) // Fuerza el fallo del @Size(min=5)
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status", is(400)))
				.andExpect(jsonPath("$.error", is(VALIDATION_ERROR)));
	}
}