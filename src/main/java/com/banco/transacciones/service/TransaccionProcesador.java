package com.banco.transacciones.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.enums.NivelRiesgo;
import com.banco.transacciones.domain.enums.TipoTransaccion;
import com.banco.transacciones.domain.models.AlertaFraude;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.dto.response.ResumenLoteDTO;
import com.banco.transacciones.exception.CuentaBloqueadaException;
import com.banco.transacciones.exception.SaldoInsuficienteException;
import com.banco.transacciones.repository.AlertaFraudeRepository;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;
import com.banco.transacciones.util.FraudeScoreCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Motor de procesamiento asíncrono para transacciones financieras. Implementa
 * la lógica de validación de saldo, bloqueo pesimista de cuentas, detección de
 * fraude en paralelo y gestión de estados finales.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionProcesador {

	private final CuentaRepository cuentaRepository;
	private final TransaccionRepository transaccionRepository;
	private final FraudeScoreCalculator fraudeScoreCalculator;
	private final AlertaFraudeRepository alertaFraudeRepository;

	/**
	 * Ejecuta el flujo completo de una transferencia en un hilo del pool
	 * configurado. Restaura el contexto de log (MDC) para mantener la trazabilidad.
	 *
	 * @param dto           Datos de la transferencia.
	 * @param transaccionId ID de la transacción persistida previamente.
	 * @param correlationId ID único de seguimiento para logs.
	 */
	@Async("transaccionExecutor")
	@Transactional
	public void ejecutarTransferenciaAsync(TransferenciaDTO dto, Long transaccionId, String correlationId) {
		MDC.put("correlationId", correlationId);
		log.info("Iniciando procesamiento asíncrono para TX ID: {}", transaccionId);
		try {
			Transaccion tx = transaccionRepository.findById(transaccionId)
					.orElseThrow(() -> new RuntimeException("TX no encontrada"));

			procesarTransferenciaInternal(dto, tx);
			log.info("Procesamiento finalizado para TX ID: {}", transaccionId);
		} catch (Exception e) {
			log.error("Fallo crítico en TX {}: {}", transaccionId, e.getMessage());
		} finally {
			MDC.clear();
		}
	}

	/**
	 * Procesa un sublote de transacciones de forma aislada. Utiliza
	 * PROPAGATION_REQUIRES_NEW para asegurar que el fallo de una transacción no
	 * comprometa la atomicidad de las demás dentro del mismo hilo.
	 *
	 * @param sublote       Lista de hasta 50 transferencias.
	 * @param correlationId ID de seguimiento.
	 * @return CompletableFuture con el resumen del procesamiento del sublote.
	 */
	@Async("transaccionExecutor")
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CompletableFuture<ResumenLoteDTO> procesarSubloteAsync(List<TransferenciaDTO> sublote,
			String correlationId) {
		MDC.put("correlationId", correlationId);
		int exitosas = 0;
		int fallidas = 0;

		for (TransferenciaDTO dto : sublote) {
			try {
				Transaccion tx = crearEntidadInicial(dto);
				procesarTransferenciaInternal(dto, tx);
				exitosas++;
			} catch (Exception e) {
				log.error("Fallo en transacción de sublote: {}", e.getMessage());
				fallidas++;
			}
		}
		MDC.clear();
		return CompletableFuture.completedFuture(new ResumenLoteDTO(sublote.size(), exitosas, fallidas));
	}

	/**
	 * Lógica central de procesamiento: bloqueo de cuentas, validación de reglas de
	 * negocio y orquestación del análisis de fraude en paralelo.
	 *
	 * @param dto Datos de entrada.
	 * @param tx  Entidad de transacción a actualizar.
	 */
	private void procesarTransferenciaInternal(TransferenciaDTO dto, Transaccion tx) {
		tx.setEstado(EstadoTransaccion.PROCESANDO);
		transaccionRepository.saveAndFlush(tx);

		// 1. Ejecutar análisis de fraude en paralelo
		CompletableFuture<Double> fraudeFuture = CompletableFuture
				.supplyAsync(() -> fraudeScoreCalculator.calcularScore(dto));

		try {
			// 2. Bloqueo Pesimista Eficiente y Ordenado
			boolean origenPrimero = dto.cuentaOrigen().compareTo(dto.cuentaDestino()) < 0;
			String c1 = origenPrimero ? dto.cuentaOrigen() : dto.cuentaDestino();
			String c2 = origenPrimero ? dto.cuentaDestino() : dto.cuentaOrigen();

			// Obtenemos la entidad directamente con el bloqueo aplicado
			Cuenta cuenta1 = cuentaRepository.findByNumeroCuentaWithLock(c1).orElseThrow();
			Cuenta cuenta2 = cuentaRepository.findByNumeroCuentaWithLock(c2).orElseThrow();

			Cuenta origen = origenPrimero ? cuenta1 : cuenta2;
			Cuenta destino = origenPrimero ? cuenta2 : cuenta1;

			// 3. Validaciones de Negocio
			if (!origen.getEstado().name().equals("ACTIVA")) {
				throw new CuentaBloqueadaException("Cuenta origen bloqueada");
			}
			if (origen.getSaldo().compareTo(dto.monto()) < 0) {
				throw new SaldoInsuficienteException("Sin fondos suficientes");
			}

			// 4. Sincronizamos con el resultado del modelo de fraude
			double riesgo = fraudeFuture.join();
			tx.setRiesgoFraude(riesgo);

			if (riesgo > 0.75) {
				tx.setEstado(EstadoTransaccion.RECHAZADA);
				generarAlerta(tx, NivelRiesgo.CRITICO, "Riesgo alto detectado: " + riesgo);
				log.warn("Transacción {} rechazada por riesgo de fraude", tx.getId());
			} else {
				// Actualizamos saldos
				origen.setSaldo(origen.getSaldo().subtract(dto.monto()));
				destino.setSaldo(destino.getSaldo().add(dto.monto()));
				cuentaRepository.save(origen);
				cuentaRepository.save(destino);

				tx.setEstado(EstadoTransaccion.COMPLETADA);
			}
		} catch (SaldoInsuficienteException | CuentaBloqueadaException e) {
			tx.setEstado(EstadoTransaccion.RECHAZADA);
			log.warn("Transacción {} rechazada por regla de negocio: {}", tx.getId(), e.getMessage());
			throw e;
		} catch (Exception e) {
			tx.setEstado(EstadoTransaccion.REVERTIDA);
			log.error("Error técnico procesando TX {}: {}", tx.getId(), e.getMessage());
			throw new RuntimeException("Error inesperado", e);
		} finally {
			transaccionRepository.save(tx);
		}
	}

	/**
	 * Helper para inicializar una entidad Transaccion en procesos de lote.
	 */
	private Transaccion crearEntidadInicial(TransferenciaDTO dto) {
		return Transaccion.builder().cuentaOrigen(dto.cuentaOrigen()).cuentaDestino(dto.cuentaDestino())
				.monto(dto.monto()).tipo(TipoTransaccion.TRANSFERENCIA).estado(EstadoTransaccion.PENDIENTE)
				.fechaHora(Instant.now()).build();
	}

	/**
	 * Persiste una alerta de fraude vinculada a una transacción sospechosa.
	 */
	private void generarAlerta(Transaccion tx, NivelRiesgo nivel, String motivo) {
		alertaFraudeRepository
				.save(AlertaFraude.builder().transaccion(tx).nivel(nivel).motivo(motivo).revisada(false).build());
	}
}
