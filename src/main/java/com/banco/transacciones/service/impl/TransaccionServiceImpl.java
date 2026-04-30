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

	private final TransaccionRepository transaccionRepository;
	private final TransaccionMapper transaccionMapper;
	private final TransaccionProcesador transaccionProcesador;

	@Transactional
	public TransaccionDTO iniciarTransferencia(TransferenciaDTO dto) {
		log.info("Entrada: Iniciando transferencia asíncrona de cuenta {} a cuenta {}", dto.cuentaOrigen(),
				dto.cuentaDestino());

		// 1. Persistimos el estado inicial
		Transaccion tx = Transaccion.builder().cuentaOrigen(dto.cuentaOrigen()).cuentaDestino(dto.cuentaDestino())
				.monto(dto.monto()).codigoPais(dto.codigoPais()).tipo(TipoTransaccion.TRANSFERENCIA)
				.estado(EstadoTransaccion.PENDIENTE).fechaHora(Instant.now()).build();

		tx = transaccionRepository.save(tx);
		log.debug("Transacción inicial persistida con estado PENDIENTE. ID: {}", tx.getId());

		// 2. Delegamos la ejecución pasándole el ID generado para que NO cree
		// duplicados
		log.debug("Delegando procesamiento concurrente al pool de hilos.");
		transaccionProcesador.procesarTransferenciaAsync(tx.getId(), dto);

		TransaccionDTO response = transaccionMapper.toDto(tx);
		log.info("Salida: Transferencia aceptada para procesamiento. ID: {}", response.id());
		return response;
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
			List<TransferenciaDTO> sublote = new ArrayList<>(lote.subList(i, endIndex));

			log.debug("Generando fragmentación: enviando sublote de tamaño {} (índices {} a {})", sublote.size(), i,
					endIndex - 1);

			futuros.add(transaccionProcesador.procesarSubloteAsync(sublote, i));
		}

		log.debug("Esperando la resolución de {} hilos de ejecución...", futuros.size());

		// Reducir resultados usando el acumulador neutral
		ResumenLoteDTO resumen = futuros.stream().map(CompletableFuture::join)
				.reduce(new ResumenLoteDTO(0, 0, 0, new ArrayList<>()), ResumenLoteDTO::sumar);

		log.info("Salida: Lote procesado totalmente. Exitosas: {}, Fallidas: {}", resumen.totalExitosas(),
				resumen.totalFallidas());
		return resumen;
	}

	@Transactional(readOnly = true)
	public TransaccionDTO obtenerEstadoTransaccion(Long id) {
		log.info("Entrada: Consultando estado para TX ID: {}", id);

		Transaccion tx = transaccionRepository.findById(id).orElseThrow(() -> {
			log.error("Consulta fallida: Transacción con ID {} no fue encontrada en sistema", id);
			return new TransaccionNotFoundException("Transacción no encontrada");
		});

		TransaccionDTO response = transaccionMapper.toDto(tx);
		log.info("Salida: Transacción encontrada. Estado: {}", response.estado());
		return response;
	}

}