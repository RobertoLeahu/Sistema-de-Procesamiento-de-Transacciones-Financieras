package com.banco.transacciones.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.banco.transacciones.domain.enums.EstadoCuenta;
import com.banco.transacciones.domain.enums.TipoCuenta;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Clase de pruebas de integración para el controlador de transacciones.
 * * Valida el flujo completo de extremo a extremo (End-to-End) de las peticiones HTTP 
 * utilizando {@link MockMvc}, abarcando desde la recepción del JSON hasta la 
 * persistencia en la base de datos en memoria.
 * * Escenarios principales evaluados:
 * - Flujo exitoso (Happy Path): Recepción de la transferencia, retorno de código HTTP 202 
 * y guardado en base de datos con estado PENDIENTE.
 * - Manejo de reglas de negocio: Rechazo de transferencias con montos negativos 
 * (validando la respuesta HTTP 500 y su estructura de error específica).
 * - Consultas de estado: Comprobación de respuestas HTTP 404 al buscar 
 * identificadores de transacción que no existen en el sistema.
 * * Nota técnica: Se utiliza la anotación {@code @ActiveProfiles("test")} para cargar 
 * la configuración exclusiva de pruebas. El método {@code setUp()} garantiza un entorno 
 * limpio truncando las tablas e insertando cuentas base antes de cada ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransaccionIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CuentaRepository cuentaRepository;

	@Autowired
	private TransaccionRepository transaccionRepository;

	private final String CUENTA_ORIGEN = "ES1234567890123456789012";
	private final String CUENTA_DESTINO = "ES9876543210987654321098";

	@BeforeEach
	void setUp() {
		// Limpiamos la BD en memoria antes de cada test
		transaccionRepository.deleteAll();
		cuentaRepository.deleteAll();

		// Preparamos las cuentas necesarias para la integración
		Cuenta origen = Cuenta.builder().numeroCuenta(CUENTA_ORIGEN).saldo(new BigDecimal("5000.00"))
				.tipo(TipoCuenta.ACTIVO).estado(EstadoCuenta.ACTIVADA).build();

		Cuenta destino = Cuenta.builder().numeroCuenta(CUENTA_DESTINO).saldo(new BigDecimal("1000.00"))
				.tipo(TipoCuenta.ACTIVO).estado(EstadoCuenta.ACTIVADA).build();

		cuentaRepository.save(origen);
		cuentaRepository.save(destino);
	}

	@Test
	@DisplayName("Debe aceptar la transferencia, retornar 202 y guardar en estado PENDIENTE")
	void iniciarTransferencia_FlujoFeliz_Retorna202() throws Exception {
		TransferenciaDTO request = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("500.00"), "ES",
				"Pago alquiler");

		MvcResult result = mockMvc
				.perform(post("/api/transacciones/transferencia").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.cuentaOrigen").value(CUENTA_ORIGEN))
				.andExpect(jsonPath("$.estado").value("PENDIENTE")).andReturn();

		// Validamos que se ha guardado en BD H2
		long count = transaccionRepository.count();
		assertEquals(1, count, "Debe existir una transacción guardada en BD");
	}

	@Test
	@DisplayName("Debe fallar con 500 Internal Server Error según los requisitos si el monto es negativo")
	void iniciarTransferencia_MontoNegativo_Retorna500() throws Exception {
		TransferenciaDTO request = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("-100.00"), "ES",
				"Pago");

		mockMvc.perform(post("/api/transacciones/transferencia").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.error").value("Error interno"))
				.andExpect(jsonPath("$.detalle").value("Ocurrió un error inesperado en el servidor"))
				.andExpect(jsonPath("$.path").value("/api/transacciones/transferencia"));
	}

	@Test
	@DisplayName("Debe fallar con 404 si consultamos el estado de un ID inexistente")
	void obtenerEstado_IdInexistente_Retorna404() throws Exception {
		mockMvc.perform(get("/api/transacciones/999/estado").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Transacción no encontrada"))
				.andExpect(jsonPath("$.detalle").value("Transacción no encontrada con ID: 999"))
				.andExpect(jsonPath("$.path").value("/api/transacciones/999/estado"));
	}
}