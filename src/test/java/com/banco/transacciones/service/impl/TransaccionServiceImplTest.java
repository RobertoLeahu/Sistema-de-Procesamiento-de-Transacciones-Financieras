package com.banco.transacciones.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.dto.response.ResumenLoteDTO;
import com.banco.transacciones.dto.response.TransaccionDTO;
import com.banco.transacciones.exception.TransaccionNotFoundException;
import com.banco.transacciones.mapper.TransaccionMapper;
import com.banco.transacciones.repository.TransaccionRepository;
import com.banco.transacciones.service.TransaccionProcesador;

/**
 * Clase de pruebas unitarias para la validacion de la capa de orquestacion en
 * TransaccionServiceImpl. Verifica la correcta gestion del ciclo de vida
 * inicial de las transacciones y su enrutamiento hacia los motores de ejecucion
 * asincrona, garantizando tiempos de respuesta optimos en la API. * Escenarios
 * arquitectonicos y de negocio validados: - Delegacion asincrona: Persistencia
 * del estado PENDIENTE y pase de control al procesador sin bloquear el hilo
 * principal. - Procesamiento masivo (Lotes): Division matematica de cargas
 * grandes en sublotes manejables (tamano 50) para no saturar el ThreadPool. -
 * Proteccion de memoria (Circuit Breaker logico): Rechazo inmediato y
 * lanzamiento de excepcion si el payload supera el limite estricto de 500
 * transacciones. - Consultas transaccionales: Recuperacion limpia de estados
 * mediante transacciones de solo lectura y correcto manejo de identidades
 * inexistentes (TransaccionNotFoundException).
 */
@ExtendWith(MockitoExtension.class)
class TransaccionServiceImplTest {

	@Mock
	private TransaccionRepository transaccionRepository;

	@Mock
	private TransaccionProcesador transaccionProcesador;

	@Mock
	private TransaccionMapper mapper;

	@InjectMocks
	private TransaccionServiceImpl transaccionService;

	private TransferenciaDTO transferenciaDTO;
	private Transaccion transaccionMock;

	private static final String CUENTA_ORIGEN = "ES1234567890123456789012";
	private static final String CUENTA_DESTINO = "ES9876543210987654321098";
	private static final String PAIS_HABITUAL = "ES";

	@BeforeEach
	void setUp() {
		transferenciaDTO = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("100.00"), PAIS_HABITUAL,
				"Transferencia de prueba");
		transaccionMock = Transaccion.builder().id(1L).estado(EstadoTransaccion.PENDIENTE).build();
	}

	/**
	 * Verifica que al iniciar una transferencia con datos validos, la transaccion
	 * se persista inicialmente en estado PENDIENTE y se delegue su ejecucion al
	 * procesador asincrono para garantizar una respuesta no bloqueante.
	 */
	@Test
	@DisplayName("Debe iniciar transferencia, persistir estado PENDIENTE y delegar a asincronía")
	void iniciarTransferencia_Exito() {
		when(transaccionRepository.save(any(Transaccion.class))).thenReturn(transaccionMock);
		when(mapper.toResponse(transaccionMock))
				.thenReturn(new TransaccionDTO(1L, CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("100.00"),
						"TRANSFERENCIA", EstadoTransaccion.PENDIENTE.name(), Instant.now(), "Prueba"));

		TransaccionDTO result = transaccionService.iniciarTransferencia(transferenciaDTO);

		assertNotNull(result);
		assertEquals(EstadoTransaccion.PENDIENTE.name(), result.estado());
		verify(transaccionProcesador).ejecutarTransferenciaAsync(transferenciaDTO, 1L);
	}

	/**
	 * Valida el procesamiento de un lote masivo valido. Asegura que el lote
	 * principal se divida correctamente en sublotes configurados y que el resultado
	 * final agrupe con precision todas las ejecuciones paralelas.
	 */
	@Test
	@DisplayName("Debe procesar lote de transacciones correctamente dividiendo en sublotes")
	void procesarLote_LoteValido_Exito() {
		// Lote de 120 (Debería dividir en sublotes de 50, 50, 20)
		List<TransferenciaDTO> lote = new ArrayList<>();
		for (int i = 0; i < 120; i++) {
			lote.add(transferenciaDTO);
		}

		// Simulamos que el procesador devuelve futuros completados
		when(transaccionProcesador.procesarSubloteAsync(anyList())).thenAnswer(invocation -> {
			List<?> sublote = invocation.getArgument(0);
			return CompletableFuture.completedFuture(new ResumenLoteDTO(sublote.size(), sublote.size(), 0));
		});

		ResumenLoteDTO resumen = transaccionService.procesarLote(lote);

		assertEquals(120, resumen.totalRecibidas());
		assertEquals(120, resumen.totalExitosas());
		assertEquals(0, resumen.totalFallidas());
		verify(transaccionProcesador, times(3)).procesarSubloteAsync(anyList());
	}

	/**
	 * Comprueba la regla de seguridad y limite de memoria para lotes. Garantiza que
	 * el sistema lance una IllegalArgumentException si el payload de entrada supera
	 * el limite estricto de 500 transacciones.
	 */
	@Test
	@DisplayName("Debe lanzar excepción si el lote supera las 500 transacciones")
	void procesarLote_LoteExcedeMaximo_LanzaExcepcion() {
		List<TransferenciaDTO> lote = new ArrayList<>();
		for (int i = 0; i < 501; i++) {
			lote.add(transferenciaDTO);
		}

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> transaccionService.procesarLote(lote));
		assertTrue(ex.getMessage().contains("Máximo 500 transacciones"));
	}

	/**
	 * Asegura la correcta recuperacion del estado de una transaccion en curso,
	 * verificando la consulta a la base de datos y su correcto mapeo hacia el DTO
	 * de respuesta.
	 */
	@Test
	@DisplayName("Debe obtener el estado de una transacción existente")
	void obtenerEstadoTransaccion_TransaccionExiste_Exito() {
		when(transaccionRepository.findById(1L)).thenReturn(Optional.of(transaccionMock));
		when(mapper.toResponse(transaccionMock))
				.thenReturn(new TransaccionDTO(1L, CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("100.00"),
						"TRANSFERENCIA", EstadoTransaccion.PENDIENTE.name(), Instant.now(), "Prueba"));

		TransaccionDTO result = transaccionService.obtenerEstadoTransaccion(1L);

		assertEquals(EstadoTransaccion.PENDIENTE.name(), result.estado());
	}

	/**
	 * Valida el manejo de errores en operaciones de lectura. Verifica que se
	 * dispare la excepcion de negocio TransaccionNotFoundException cuando se
	 * solicita el estado de un identificador inexistente.
	 */
	@Test
	@DisplayName("Debe lanzar TransaccionNotFoundException si la transacción no existe")
	void obtenerEstadoTransaccion_TransaccionNoExiste_LanzaExcepcion() {
		when(transaccionRepository.findById(1L)).thenReturn(Optional.empty());

		assertThrows(TransaccionNotFoundException.class, () -> transaccionService.obtenerEstadoTransaccion(1L));
	}
}