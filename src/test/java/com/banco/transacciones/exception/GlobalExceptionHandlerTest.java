package com.banco.transacciones.exception;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

@WebMvcTest(controllers = TestExceptionController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("Debe retornar 400 cuando el saldo es insuficiente")
	void testHandleSaldoInsuficiente() throws Exception {
		mockMvc.perform(get("/test/saldo-insuficiente")).andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.timestamp", notNullValue())).andExpect(jsonPath("$.status", is(400)))
				.andExpect(jsonPath("$.error", is("Saldo insuficiente")))
				.andExpect(jsonPath("$.detalle").value(is("La cuenta 0012-3456 no tiene saldo suficiente")))
				.andExpect(jsonPath("$.path", is("/test/saldo-insuficiente")));
	}

	@Test
	@DisplayName("Debe retornar 403 cuando la cuenta esté bloqueada")
	void testHandleCuentabloqueada() throws Exception {
		mockMvc.perform(get("/test/cuenta-bloqueada")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status", is(403)))
				.andExpect(jsonPath("$.error", is("Cuenta bloqueada")));
	}
}