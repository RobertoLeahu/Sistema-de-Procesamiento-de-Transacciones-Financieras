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
 * Motor de procesamiento asíncrono para transacciones financieras.
 * Implementa la lógica de validación de saldo, bloqueo pesimista de cuentas,
 * detección de fraude en paralelo y gestión de estados finales.
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
     * Ejecuta el flujo completo de una transferencia en un hilo del pool configurado.
     * Restaura el contexto de log (MDC) para mantener la trazabilidad.
     *
     * @param dto Datos de la transferencia.
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
}
