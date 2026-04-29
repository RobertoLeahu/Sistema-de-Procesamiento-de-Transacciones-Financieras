package com.banco.transacciones.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * Clase de pruebas unitarias para {@link TransaccionServiceImpl}. Verifica el
 * correcto enrutamiento, la persistencia inicial rapida y el troceado seguro de
 * peticiones en lote delegando en el procesador asíncrono.
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

	private static final String CUENTA_ORIGEN = "ES11";
	private static final String CUENTA_DESTINO = "ES22";

	@BeforeEach
	void setUp() {
		transferenciaDTO = new TransferenciaDTO(CUENTA_ORIGEN, CUENTA_DESTINO, new BigDecimal("100.00"), "ES",
				"Test unitario");

		transaccionMock = Transaccion.builder().id(1L).cuentaOrigen(CUENTA_ORIGEN).cuentaDestino(CUENTA_DESTINO)
				.monto(new BigDecimal("100.00")).estado(EstadoTransaccion.PENDIENTE).fechaHora(Instant.now()).build();
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

		// CORRECCIÓN: El método correcto en tu mapper es toDto, no toResponse.
		when(mapper.toDto(any(Transaccion.class)))
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

	/**
	 * Verifica la lógica de partición de sublotes. Un lote de 120 elementos debe
	 * partirse en 3 sublotes (50, 50 y 20) y acumular los resultados.
	 */
	@Test
	@DisplayName("Debe procesar correctamente un lote partiendo en sublotes de máximo 50")
	void procesarLote_LoteValido_ProcesaPorSublotes() {
		List<TransferenciaDTO> lote = new ArrayList<>();
		for (int i = 0; i < 120; i++) {
			lote.add(transferenciaDTO);
		}

		ResumenLoteDTO resumenSublote1 = new ResumenLoteDTO(50, 50, 0);
		ResumenLoteDTO resumenSublote2 = new ResumenLoteDTO(50, 48, 2);
		ResumenLoteDTO resumenSublote3 = new ResumenLoteDTO(20, 20, 0);

		when(transaccionProcesador.procesarSubloteAsync(anyList())).thenReturn(
				CompletableFuture.completedFuture(resumenSublote1), CompletableFuture.completedFuture(resumenSublote2),
				CompletableFuture.completedFuture(resumenSublote3));

		ResumenLoteDTO resultadoFinal = transaccionService.procesarLote(lote);

		assertNotNull(resultadoFinal);
		assertEquals(120, resultadoFinal.totalRecibidas(), "Debe sumar el total enviado");
		assertEquals(118, resultadoFinal.totalExitosas(), "Debe sumar los éxitos de todos los sublotes");
		assertEquals(2, resultadoFinal.totalFallidas(), "Debe sumar los fallos de todos los sublotes");

		verify(transaccionProcesador, times(3)).procesarSubloteAsync(anyList());
	}

	/**
	 * Verifica la ramificación inicial del ciclo "for" en procesarLote. Si la lista
	 * viene vacía, no debe intentar procesar nada y devolver un ResumenLoteDTO
	 * vacío.
	 */
	@Test
	@DisplayName("Debe retornar resumen en cero si la lista del lote está vacía")
	void procesarLote_ListaVacia_RetornaResumenEnCero() {
		List<TransferenciaDTO> loteVacio = new ArrayList<>();

		ResumenLoteDTO resultadoFinal = transaccionService.procesarLote(loteVacio);

		assertNotNull(resultadoFinal);
		assertEquals(0, resultadoFinal.totalRecibidas());
		assertEquals(0, resultadoFinal.totalExitosas());
		assertEquals(0, resultadoFinal.totalFallidas());

		verify(transaccionProcesador, times(0)).procesarSubloteAsync(anyList());
	}
}