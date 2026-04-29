package com.banco.transacciones.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.enums.TipoTransaccion;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.dto.response.ResumenLoteDTO;
import com.banco.transacciones.dto.response.TransaccionDTO;
import com.banco.transacciones.exception.TransaccionNotFoundException;
import com.banco.transacciones.mapper.TransaccionMapper;
import com.banco.transacciones.repository.TransaccionRepository;
import com.banco.transacciones.service.TransaccionProcesador;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación de la lógica de orquestación de transacciones financieras.
 * Gestiona la persistencia inicial, la validación de límites de lotes y la
 * asignación de IDs de correlación para trazabilidad distribuida.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionServiceImpl {

	private final TransaccionProcesador transaccionProcesador;
	private final TransaccionRepository transaccionRepository;
	private final TransaccionMapper transaccionMapper;

	/**
	 * Inicia el proceso de transferencia. Guarda el estado PENDIENTE síncronamente
	 * para obtener un ID real y delega el análisis pesado a un hilo secundario
	 * asíncrono.
	 */
	public TransaccionDTO iniciarTransferencia(TransferenciaDTO dto) {
		log.info("Entrada: Orquestando inicio asíncrono para transferencia de {} a {}", dto.cuentaOrigen(),
				dto.cuentaDestino());

		// 1. Guardamos la transacción preliminar de forma síncrona para que Hibernate
		// genere el ID.
		Transaccion txInicial = Transaccion.builder().cuentaOrigen(dto.cuentaOrigen())
				.cuentaDestino(dto.cuentaDestino()).monto(dto.monto()).codigoPais(dto.codigoPais())
				.tipo(TipoTransaccion.TRANSFERENCIA).estado(EstadoTransaccion.PENDIENTE).fechaHora(Instant.now())
				.build();

		txInicial = transaccionRepository.save(txInicial);
		log.info("Transacción preliminar guardada con éxito en BD. TX ID real asignado: {}", txInicial.getId());

		// 2. Delegamos el procesamiento pesado (score, bloqueos) pasándole el ID real.
		transaccionProcesador.procesarTransferenciaAsync(txInicial.getId(), dto);

		log.info("Salida: Tarea asíncrona delegada con éxito. Retornando acuse de recibo.");
		return transaccionMapper.toDto(txInicial);
	}

	/**
	 * Procesa un lote de transacciones dividiéndolo en fragmentos para ejecución
	 * paralela.
	 */
	public ResumenLoteDTO procesarLote(List<TransferenciaDTO> lote) {
		log.info("Entrada: Iniciando procesamiento masivo de {} transacciones", lote.size());

		int size = 50;
		List<CompletableFuture<ResumenLoteDTO>> futuros = new ArrayList<>();

		for (int i = 0; i < lote.size(); i += size) {
			int endIndex = Math.min(i + size, lote.size());

			// Partir el lote en sublotes de 50.
			// Clonar el subList en un nuevo ArrayList para evitar
			// problemas de concurrencia al leer la memoria compartida de la lista original.
			List<TransferenciaDTO> sublote = new ArrayList<>(lote.subList(i, endIndex));

			log.debug("Generando fragmentación: enviando sublote de tamaño {} (índices {} a {})", sublote.size(), i,
					endIndex - 1);
			futuros.add(transaccionProcesador.procesarSubloteAsync(sublote));
		}

		log.debug("Esperando la resolución de {} hilos de ejecución...", futuros.size());

		// Esperar de forma segura a que todos los hilos terminen
		CompletableFuture.allOf(futuros.toArray(new CompletableFuture[0])).join();

		// Agregar los resultados y retornar un resumen
		ResumenLoteDTO resumen = futuros.stream().map(CompletableFuture::join).reduce(new ResumenLoteDTO(0, 0, 0),
				ResumenLoteDTO::sumar);

		log.info("Salida: Lote procesado totalmente. Exitosas: {}, Fallidas: {}", resumen.totalExitosas(),
				resumen.totalFallidas());
		return resumen;
	}

	@Transactional(readOnly = true)
	public TransaccionDTO obtenerEstadoTransaccion(Long id) {
		log.info("Entrada: Consultando estado para TX ID: {}", id);

		Transaccion tx = transaccionRepository.findById(id).orElseThrow(() -> {
			log.error("Consulta fallida: Transacción con ID {} no fue encontrada en sistema", id);
			return new TransaccionNotFoundException("Transacción no encontrada con id: " + id);
		});

		log.debug("Mapeando entidad Transaccion a capa DTO inmutable para TX ID: {}", id);
		TransaccionDTO dto = transaccionMapper.toDto(tx);

		log.info("Salida: Estado de TX ID: {} validado como {}", id, dto.estado());
		return dto;
	}
}