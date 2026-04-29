package com.banco.transacciones.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.banco.transacciones.domain.enums.EstadoCuenta;
import com.banco.transacciones.domain.enums.EstadoTransaccion;
import com.banco.transacciones.domain.enums.NivelRiesgo;
import com.banco.transacciones.domain.enums.TipoTransaccion;
import com.banco.transacciones.domain.models.AlertaFraude;
import com.banco.transacciones.domain.models.Cuenta;
import com.banco.transacciones.domain.models.Transaccion;
import com.banco.transacciones.dto.request.TransferenciaDTO;
import com.banco.transacciones.dto.response.ResumenLoteDTO;
import com.banco.transacciones.exception.CuentaBloqueadaException;
import com.banco.transacciones.exception.CuentaNotFoundException;
import com.banco.transacciones.exception.SaldoInsuficienteException;
import com.banco.transacciones.exception.TransaccionNotFoundException;
import com.banco.transacciones.repository.AlertaFraudeRepository;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;
import com.banco.transacciones.util.FraudeScoreCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio encargado del procesamiento central aislado de las transacciones.
 * Ejecuta cálculos de riesgo y movimientos financieros bajo bloqueos
 * pesimistas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionProcesador {

	private final CuentaRepository cuentaRepository;
	private final TransaccionRepository transaccionRepository;
	private final FraudeScoreCalculator fraudeScoreCalculator;
	private final AlertaFraudeRepository alertaFraudeRepository;

	@Autowired
	@Lazy
	private TransaccionProcesador self;

	@Async("transaccionExecutor")
	public CompletableFuture<Transaccion> procesarTransferenciaAsync(Long txId, TransferenciaDTO dto) {
		log.info("Entrada: Iniciando procesamiento asíncrono para transferencia con TX ID: {}", txId);
		try {
			Transaccion tx = self.procesarTransferenciaPorId(txId, dto);
			log.info("Salida: Procesamiento asíncrono completado. TX ID: {}", tx.getId());
			return CompletableFuture.completedFuture(tx);
		} catch (Exception e) {
			log.error("Fallo grave en procesamiento asíncrono de transferencia TX ID {}: {}", txId, e.getMessage(), e);
			return CompletableFuture.failedFuture(e);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Transaccion procesarTransferenciaPorId(Long txId, TransferenciaDTO dto) {
		Transaccion tx = transaccionRepository.findById(txId)
				.orElseThrow(() -> new TransaccionNotFoundException("Transacción con ID " + txId + " no encontrada"));
		return ejecutarLogicaTransaccion(tx, dto);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public Transaccion procesarTransferencia(TransferenciaDTO dto) {
		log.info("Entrada: procesarTransferencia iniciada para monto {}", dto.monto());
		final Transaccion tx = transaccionRepository.save(crearEntidadInicial(dto));
		log.debug("TX ID: {} persistida con estado inicial PENDIENTE", tx.getId());
		return ejecutarLogicaTransaccion(tx, dto);
	}

	/**
	 * Concentra la lógica de negocio real (bloqueo, validación, score). Utilizado
	 * tanto por procesos asíncronos individuales como por el procesamiento de
	 * lotes.
	 */
	private Transaccion ejecutarLogicaTransaccion(final Transaccion tx, TransferenciaDTO dto) {
		try {
			// 2. Obtener cuentas con bloqueo pesimista y verificar existencia
			Cuenta origen = cuentaRepository.findByNumeroCuentaWithLock(dto.cuentaOrigen()).orElseThrow(() -> {
				log.error("TX ID: {} - Cuenta origen {} no encontrada", tx.getId(), dto.cuentaOrigen());
				return new CuentaNotFoundException("Cuenta origen no encontrada");
			});

			Cuenta destino = cuentaRepository.findByNumeroCuentaWithLock(dto.cuentaDestino()).orElseThrow(() -> {
				log.error("TX ID: {} - Cuenta destino {} no encontrada", tx.getId(), dto.cuentaDestino());
				return new CuentaNotFoundException("Cuenta destino no encontrada");
			});

			if (origen.getEstado() != EstadoCuenta.ACTIVADA || destino.getEstado() != EstadoCuenta.ACTIVADA) {
				log.error("TX ID: {} - Una o ambas cuentas inactivas. Origen: {}, Destino: {}", tx.getId(),
						origen.getEstado(), destino.getEstado());
				throw new CuentaBloqueadaException("Ambas cuentas deben estar activas para la transacción");
			}

			log.debug("TX ID: {} - Iniciando cálculo de score de riesgo...", tx.getId());
			double riesgo = fraudeScoreCalculator.calcularScore(dto);

			tx.setRiesgoFraude(riesgo);

			log.debug("TX ID: {} - Score de riesgo calculado: {}", tx.getId(), riesgo);

			if (riesgo >= 0.50 && riesgo <= 0.75) {
				log.warn("TX ID: {} - Riesgo medio detectado ({}). La transacción continúa pero requiere monitoreo.",
						tx.getId(), riesgo);
			}

			if (origen.getSaldo().compareTo(dto.monto()) < 0) {
				log.error("TX ID: {} - Saldo insuficiente. Cuenta: {}, Requerido: {}, Disponible: {}", tx.getId(),
						origen.getNumeroCuenta(), dto.monto(), origen.getSaldo());
				throw new SaldoInsuficienteException(
						"La cuenta " + origen.getNumeroCuenta() + " no tiene saldo suficiente");
			}

			// 3. Validaciones y ejecución final
			if (riesgo > 0.75) {
				tx.setEstado(EstadoTransaccion.RECHAZADA);
				log.warn("ALERTA DE FRAUDE: TX ID: {} rechazada por riesgo crítico ({}). Requiere revisión manual.",
						tx.getId(), riesgo);
				generarAlerta(tx, NivelRiesgo.CRITICO, "Riesgo alto detectado: " + riesgo);
			} else {
				origen.setSaldo(origen.getSaldo().subtract(dto.monto()));
				destino.setSaldo(destino.getSaldo().add(dto.monto()));
				tx.setEstado(EstadoTransaccion.COMPLETADA);
				log.info("Salida: Transacción {} completada exitosamente", tx.getId());
			}
		} catch (Exception e) {
			tx.setEstado(EstadoTransaccion.RECHAZADA);
			log.error("Error validando/procesando TX ID: {}. Causa: {}", tx.getId(), e.getMessage());
			throw e;
		} finally {
			transaccionRepository.save(tx);
			log.debug("TX ID: {} - Estado final sincronizado con base de datos a {}", tx.getId(), tx.getEstado());
		}

		return tx;
	}

	/**
	 * Procesa un sublote de transacciones en un hilo separado.
	 */
	@Async
	public CompletableFuture<ResumenLoteDTO> procesarSubloteAsync(List<TransferenciaDTO> sublote) {
		log.info("Entrada: Iniciando procesamiento de sublote con {} transacciones", sublote.size());
		int exitosas = 0;
		int fallidas = 0;

		for (TransferenciaDTO dto : sublote) {
			try {
				log.debug("Procesando TX interna del sublote - Origen: {}, Destino: {}", dto.cuentaOrigen(),
						dto.cuentaDestino());
				self.procesarTransferencia(dto);
				exitosas++;
			} catch (Exception e) {
				log.error("Fallo al procesar TX en sublote para cuenta origen {}: {}", dto.cuentaOrigen(),
						e.getMessage());
				fallidas++;
			}
		}

		log.info("Salida: Procesamiento de sublote finalizado. Exitosas: {}, Fallidas: {}", exitosas, fallidas);
		return CompletableFuture.completedFuture(new ResumenLoteDTO(sublote.size(), exitosas, fallidas));
	}

	private Transaccion crearEntidadInicial(TransferenciaDTO dto) {
		return Transaccion.builder().cuentaOrigen(dto.cuentaOrigen()).cuentaDestino(dto.cuentaDestino())
				.monto(dto.monto()).codigoPais(dto.codigoPais()).tipo(TipoTransaccion.TRANSFERENCIA)
				.estado(EstadoTransaccion.PENDIENTE).fechaHora(Instant.now()).build();
	}

	private void generarAlerta(Transaccion tx, NivelRiesgo nivel, String descripcion) {
		log.debug("TX ID: {} - Inserción de alerta de fraude en base de datos", tx.getId());
		AlertaFraude alerta = AlertaFraude.builder().transaccion(tx).nivel(nivel).motivo(descripcion).revisada(false)
				.build();
		alertaFraudeRepository.save(alerta);
	}
}