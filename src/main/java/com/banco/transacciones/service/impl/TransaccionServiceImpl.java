package com.banco.transacciones.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.MDC;
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
		// Generamos el ID de correlación para la trazabilidad
		String correlationId = UUID.randomUUID().toString();
		MDC.put("correlationId", correlationId);

		try {
			log.info("Iniciando transferencia origen: {}, destino: {}", dto.cuentaOrigen(), dto.cuentaDestino());

			Transaccion tx = Transaccion.builder().cuentaOrigen(dto.cuentaOrigen()).cuentaDestino(dto.cuentaDestino())
					.monto(dto.monto()).tipo(TipoTransaccion.TRANSFERENCIA).estado(EstadoTransaccion.PENDIENTE)
					.fechaHora(Instant.now()).build();

			tx = transaccionRepository.save(tx);

			// Pasamos el correlationId al hilo asíncrono
			transaccionProcesador.ejecutarTransferenciaAsync(dto, tx.getId(), correlationId);

			return mapper.toResponse(tx);
		} finally {
			MDC.clear();
		}
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

		String correlationId = UUID.randomUUID().toString();
		MDC.put("correlationId", correlationId);

		try {
			log.info("Iniciando procesamiento de lote de {} transacciones", lote.size());

			int size = 50; // Partir en sublotes de 50
			List<CompletableFuture<ResumenLoteDTO>> futuros = new ArrayList<>();

			for (int i = 0; i < lote.size(); i += size) {
				List<TransferenciaDTO> sublote = lote.subList(i, Math.min(i + size, lote.size()));
				futuros.add(transaccionProcesador.procesarSubloteAsync(sublote, correlationId));
			}

			ResumenLoteDTO resumen = futuros.stream().map(CompletableFuture::join).reduce(new ResumenLoteDTO(0, 0, 0),
					ResumenLoteDTO::sumar); //

			log.info("Lote procesado. Exitosas: {}, Fallidas: {}", resumen.totalExitosas(), resumen.totalFallidas());
			return resumen;
		} finally {
			MDC.clear();
		}
	}
}