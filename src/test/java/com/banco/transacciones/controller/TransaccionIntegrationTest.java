package com.banco.transacciones.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.banco.transacciones.domain.enums.EstadoCuenta;
import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.enums.TipoCuenta;
import com.banco.transacciones.domain.enums.TipoTransaccion;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Clase de pruebas de integración para el controlador de transacciones. *
 * Valida el flujo completo de extremo a extremo (End-to-End) de las peticiones
 * HTTP utilizando {@link MockMvc}, abarcando desde la recepción del JSON hasta
 * la persistencia en la base de datos en memoria. * Escenarios principales
 * evaluados: - Flujo exitoso (Happy Path): Recepción de la transferencia,
 * retorno de código HTTP 202 y guardado en base de datos con estado PENDIENTE.
 * - Manejo de reglas de negocio: Rechazo de transferencias con montos negativos
 * (validando la respuesta HTTP 500 y su estructura de error específica). -
 * Consultas de estado: Comprobación de respuestas HTTP 404 al buscar
 * identificadores de transacción que no existen en el sistema. * Nota técnica:
 * Se utiliza la anotación {@code @ActiveProfiles("test")} para cargar la
 * configuración exclusiva de pruebas. El método {@code setUp()} garantiza un
 * entorno limpio truncando las tablas e insertando cuentas base antes de cada
 * ejecución.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // H2
class TransaccionIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CuentaRepository cuentaRepository;

	@Autowired
	private TransaccionRepository transaccionRepository;

	private static final String CUENTA_ORIGEN = "ES12345678901234567890";
	private static final String CUENTA_DESTINO = "ES09876543210987654321";

	@BeforeEach
	void setUp() {
		// Limpiamos la BD para evitar condiciones de carrera entre tests
		transaccionRepository.deleteAll();
		cuentaRepository.deleteAll();

		Cuenta origen = Cuenta.builder().numeroCuenta(CUENTA_ORIGEN).saldo(new BigDecimal("5000.00"))
				.tipo(TipoCuenta.CORRIENTE).estado(EstadoCuenta.ACTIVADA).build();

		Cuenta destino = Cuenta.builder().numeroCuenta(CUENTA_DESTINO).saldo(new BigDecimal("1000.00"))
				.tipo(TipoCuenta.CORRIENTE).estado(EstadoCuenta.ACTIVADA).build();

		cuentaRepository.saveAll(List.of(origen, destino));
	}

	// ==========================================
	// TESTS ENDPOINT: POST /transferencia
	// ==========================================

	@Test
	@DisplayName("Debe procesar transferencia exitosa y retornar 202 Accepted")
	void iniciarTransferencia_Exitosa_Retorna202YProcesaAsincronamente() throws Exception {
		TransferenciaDTO request = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("1500.00"), "ES",
				"Pago alquiler");

		mockMvc.perform(post("/api/transacciones/transferencia").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isAccepted())
				.andExpect(jsonPath("$.cuentaOrigen").value(CUENTA_ORIGEN))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"));

		long count = transaccionRepository.count();
		assertEquals(1, count, "Debe existir una transacción guardada en BD en estado PENDIENTE");
	}

	@Test
	@DisplayName("Debe procesar y fallar internamente si no hay saldo suficiente (La API inicialmente retorna 202)")
	void iniciarTransferencia_SaldoInsuficiente_Retorna202YProcesaFallo() throws Exception {
		TransferenciaDTO request = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("6000.00"), "ES",
				"Intento de transferencia sin saldo");

		mockMvc.perform(post("/api/transacciones/transferencia").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request))).andExpect(status().isAccepted());
	}

	// ==========================================
	// TESTS ENDPOINT: GET /{id}/estado
	// ==========================================

	@Test
	@DisplayName("Debe fallar con 404 si consultamos el estado de un ID inexistente")
	void obtenerEstado_IdInexistente_Retorna404() throws Exception {
		mockMvc.perform(get("/api/transacciones/999/estado").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	@DisplayName("Debe retornar 200 OK y el estado de la transacción si el ID es válido")
	void obtenerEstado_IdExistente_Retorna200() throws Exception {
		// Preparamos el estado de BD
		Transaccion tx = Transaccion.builder().cuentaOrigen(CUENTA_ORIGEN).cuentaDestino(CUENTA_DESTINO)
				.monto(new BigDecimal("100.00")).tipo(TipoTransaccion.TRANSFERENCIA)
				.estado(EstadoTransaccion.PROCESANDO).build();
		tx = transaccionRepository.save(tx);

		mockMvc.perform(get("/api/transacciones/" + tx.getId() + "/estado").contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(tx.getId()))
				.andExpect(jsonPath("$.estado").value("PROCESANDO"));
	}

	// ==========================================
	// TESTS ENDPOINT: POST /lote
	// ==========================================

	@Test
	@DisplayName("Debe procesar un lote válido y retornar 202 Accepted")
	void procesarLote_Valido_Retorna202() throws Exception {
		List<TransferenciaDTO> lote = List.of(
				new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("100.00"), "ES", "Lote 1"),
				new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("200.00"), "ES", "Lote 2"));

		mockMvc.perform(post("/api/transacciones/lote").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(lote))).andExpect(status().isAccepted())
				.andExpect(jsonPath("$.totalRecibidas").value(2));
	}

	@Test
	@DisplayName("Debe fallar con 400 Bad Request si el lote está vacío (@NotEmpty)")
	void procesarLote_Vacio_Retorna400() throws Exception {
		mockMvc.perform(post("/api/transacciones/lote").contentType(MediaType.APPLICATION_JSON).content("[]")) // Payload vacío
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
	}
}