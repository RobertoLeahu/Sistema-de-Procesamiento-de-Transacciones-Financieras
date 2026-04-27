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
	private final TransaccionProcesador transaccionProcesador;
	private final TransaccionMapper mapper;

	/**
	 * Inicia una transferencia individual entre cuentas de forma asíncrona.
	 * Persiste la transacción en estado PENDIENTE y delega el procesamiento real a
	 * un hilo separado para cumplir con el tiempo de respuesta 202 Accepted.
	 *
	 * @param dto Datos de la transferencia (origen, destino, monto).
	 * @return DTO de la transacción creada con su estado inicial.
	 */
	@Transactional
	public TransaccionDTO iniciarTransferencia(TransferenciaDTO dto) {
		log.info("Iniciando transferencia - Origen: {}, Destino: {}, País: {}", dto.cuentaOrigen(), dto.cuentaDestino(),
				dto.codigoPais());

		Transaccion tx = Transaccion.builder().cuentaOrigen(dto.cuentaOrigen()).cuentaDestino(dto.cuentaDestino())
				.monto(dto.monto()).codigoPais(dto.codigoPais()).tipo(TipoTransaccion.TRANSFERENCIA)
				.estado(EstadoTransaccion.PENDIENTE).fechaHora(Instant.now()).build();

		tx = transaccionRepository.save(tx);

		transaccionProcesador.ejecutarTransferenciaAsync(dto, tx.getId());

		return mapper.toResponse(tx);
	}

	/**
	 * Procesa un conjunto masivo de transacciones (hasta 500) en paralelo. Divide
	 * el lote principal en sublotes de 50 para optimizar el uso del ThreadPool.
	 *
	 * @param lote Lista de transferencias a procesar.
	 * @return Resumen estadístico de transacciones procesadas, exitosas y fallidas.
	 * @throws IllegalArgumentException si el lote supera las 500 unidades.
	 */
	public ResumenLoteDTO procesarLote(List<TransferenciaDTO> lote) {
		if (lote.size() > 500) {
			throw new IllegalArgumentException("Máximo 500 transacciones por lote permitidas");
		}

		log.info("Iniciando procesamiento de lote de {} transacciones", lote.size());

		int size = 50;
		List<CompletableFuture<ResumenLoteDTO>> futuros = new ArrayList<>();

		for (int i = 0; i < lote.size(); i += size) {
			List<TransferenciaDTO> sublote = lote.subList(i, Math.min(i + size, lote.size()));
			futuros.add(transaccionProcesador.procesarSubloteAsync(sublote));
		}

		ResumenLoteDTO resumen = futuros.stream().map(CompletableFuture::join).reduce(new ResumenLoteDTO(0, 0, 0),
				ResumenLoteDTO::sumar);

		log.info("Lote procesado. Exitosas: {}, Fallidas: {}", resumen.totalExitosas(), resumen.totalFallidas());
		return resumen;
	}

	/**
	 * Consulta el estado actual de una transacción en proceso. Utiliza
	 * {@code readOnly = true} para optimizar el rendimiento al evitar la gestión de
	 * persistencia de cambios innecesaria en la base de datos.
	 * 
	 * @param id Identificador único de la transacción.
	 * @return DTO con la información de estado y riesgo de la transacción.
	 * @throws TransaccionNotFoundException si el ID no corresponde a ninguna
	 *                                      transacción.
	 */
	@Transactional(readOnly = true)
	public TransaccionDTO obtenerEstadoTransaccion(Long id) {
		log.info("Entrada: Consultando estado para TX ID: {}", id);

		Transaccion tx = transaccionRepository.findById(id).orElseThrow(() -> {
			log.warn("Salida: Transacción {} no encontrada", id);
			return new TransaccionNotFoundException("Transacción no encontrada con ID: " + id);
		});

		log.info("Salida: Estado recuperado para TX ID: {} - Estado: {}", id, tx.getEstado());

		return mapper.toResponse(tx);
	}
}