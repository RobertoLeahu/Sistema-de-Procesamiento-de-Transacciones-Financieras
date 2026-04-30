package com.banco.transacciones.service;

import java.time.Instant;
import java.util.ArrayList;
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
import com.banco.transacciones.dto.response.DetalleRechazoDTO;
import com.banco.transacciones.dto.response.ResumenLoteDTO;
import com.banco.transacciones.exception.CuentaBloqueadaException;
import com.banco.transacciones.exception.CuentaNotFoundException;
import com.banco.transacciones.exception.SaldoInsuficienteException;
import com.banco.transacciones.repository.AlertaFraudeRepository;
import com.banco.transacciones.repository.CuentaRepository;
import com.banco.transacciones.repository.TransaccionRepository;
import com.banco.transacciones.util.FraudeScoreCalculator;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio especializado en el procesamiento unitario y en pequeños fragmentos
 * asíncronos de transacciones. Gestiona las políticas de bloqueo optimista para
 * la concurrencia y la invocación de algoritmos de fraude.
 */
@Slf4j
@Service
public class TransaccionProcesador {

	private final CuentaRepository cuentaRepository;
	private final TransaccionRepository transaccionRepository;
	private final FraudeScoreCalculator fraudeScoreCalculator;
	private final AlertaFraudeRepository alertaFraudeRepository;

	@Lazy
	@Autowired
	private TransaccionProcesador self;

	public TransaccionProcesador(CuentaRepository cuentaRepository, TransaccionRepository transaccionRepository,
			FraudeScoreCalculator fraudeScoreCalculator, AlertaFraudeRepository alertaFraudeRepository) {
		this.cuentaRepository = cuentaRepository;
		this.transaccionRepository = transaccionRepository;
		this.fraudeScoreCalculator = fraudeScoreCalculator;
		this.alertaFraudeRepository = alertaFraudeRepository;
	}

	/**
	 * Procesa un sublote de transacciones en un hilo separado. REGLA CRÍTICA: Este
	 * método NO debe estar anotado con @Transactional. Si lo estuviera, acapararía
	 * conexiones a base de datos y causaría un Deadlock (Starvation del pool
	 * HikariCP) al procesar lotes grandes.
	 * 
	 * @param offsetIndice El índice global donde empieza este sublote.
	 */
	@Async
	public CompletableFuture<ResumenLoteDTO> procesarSubloteAsync(List<TransferenciaDTO> sublote, int offsetIndice) {
		log.info("Entrada: Iniciando procesamiento de sublote con {} transacciones", sublote.size());
		int exitosas = 0;
		int fallidas = 0;
		List<DetalleRechazoDTO> detallesRechazo = new ArrayList<>();

		for (int i = 0; i < sublote.size(); i++) {
			TransferenciaDTO dto = sublote.get(i);
			int indiceGlobal = offsetIndice + i;

			try {
				log.debug("Procesando TX interna del sublote - Origen: {}, Destino: {}", dto.cuentaOrigen(),
						dto.cuentaDestino());
				self.procesarTransferencia(dto);
				exitosas++;
			} catch (Exception e) {
				// FIX DE SEGURIDAD: Saneamiento del mensaje de error para no exponer SQL ni
				// trazas internas en el API
				String mensajeErrorSaneado = e.getMessage();
				if (mensajeErrorSaneado != null && mensajeErrorSaneado.contains("JDBC exception")) {
					if (mensajeErrorSaneado.contains("Deadlock")) {
						mensajeErrorSaneado = "Interbloqueo detectado por alta concurrencia (Deadlock). Transacción revertida de forma segura.";
					} else {
						mensajeErrorSaneado = "Error interno de base de datos al procesar la transacción.";
					}
				} else if (mensajeErrorSaneado == null) {
					mensajeErrorSaneado = "Error desconocido del servidor.";
				}

				// CUMPLIMIENTO REQUISITO: Loguear en terminal el fallo del sublote (En el log
				// interno SÍ dejamos la traza real para depuración)
				log.error("❌ Fallo en sublote (Índice {}). Origen {}: {}", indiceGlobal, dto.cuentaOrigen(),
						e.getMessage());

				fallidas++;
				detallesRechazo.add(new DetalleRechazoDTO(indiceGlobal, mensajeErrorSaneado));
			}
		}

		log.info("Salida: Procesamiento de sublote finalizado. Exitosas: {}, Fallidas: {}", exitosas, fallidas);

		return CompletableFuture
				.completedFuture(new ResumenLoteDTO(sublote.size(), exitosas, fallidas, detallesRechazo));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void procesarTransferencia(TransferenciaDTO dto) {
		log.debug("Ejecutando lógica de negocio para transferencia en hilo actual.");
		Transaccion tx = crearEntidadInicial(dto);
		transaccionRepository.save(tx);

		// Bloqueo Pesimista (causante de los Deadlocks sanos de base de datos en alta
		// concurrencia)
		Cuenta cuentaOrigen = cuentaRepository.findByNumeroCuentaWithLock(dto.cuentaOrigen())
				.orElseThrow(() -> new CuentaNotFoundException("Cuenta origen no encontrada: " + dto.cuentaOrigen()));

		Cuenta cuentaDestino = cuentaRepository.findByNumeroCuentaWithLock(dto.cuentaDestino())
				.orElseThrow(() -> new CuentaNotFoundException("Cuenta destino no encontrada: " + dto.cuentaDestino()));

		if (cuentaOrigen.getEstado() != EstadoCuenta.ACTIVADA) {
			throw new CuentaBloqueadaException("La cuenta origen no está activa.");
		}
		if (cuentaDestino.getEstado() != EstadoCuenta.ACTIVADA) {
			throw new CuentaBloqueadaException("La cuenta destino no está activa.");
		}

		if (cuentaOrigen.getSaldo().compareTo(dto.monto()) < 0) {
			throw new SaldoInsuficienteException("Saldo insuficiente para la transacción.");
		}

		double fraudeScore = fraudeScoreCalculator.calcularScore(dto);
		tx.setRiesgoFraude(fraudeScore);

		if (fraudeScore > 0.75) {
			tx.setEstado(EstadoTransaccion.RECHAZADA);
			transaccionRepository.save(tx);
			generarAlerta(tx, NivelRiesgo.CRITICO);
			log.warn("Transacción RECHAZADA por alto riesgo de fraude (Score: {}).", fraudeScore);
			return;
		}

		if (fraudeScore > 0.50) {
			generarAlerta(tx, NivelRiesgo.ALTO);
		}

		cuentaOrigen.setSaldo(cuentaOrigen.getSaldo().subtract(dto.monto()));
		cuentaDestino.setSaldo(cuentaDestino.getSaldo().add(dto.monto()));

		cuentaRepository.save(cuentaOrigen);
		cuentaRepository.save(cuentaDestino);

		tx.setEstado(EstadoTransaccion.COMPLETADA);
		transaccionRepository.save(tx);
		log.debug("Transferencia completada con éxito.");
	}

	private Transaccion crearEntidadInicial(TransferenciaDTO dto) {
		return Transaccion.builder().cuentaOrigen(dto.cuentaOrigen()).cuentaDestino(dto.cuentaDestino())
				.monto(dto.monto()).codigoPais(dto.codigoPais()).tipo(TipoTransaccion.TRANSFERENCIA)
				.estado(EstadoTransaccion.PENDIENTE).fechaHora(Instant.now()).build();
	}

	private void generarAlerta(Transaccion tx, NivelRiesgo nivelRiesgo) {
		AlertaFraude alerta = AlertaFraude.builder().transaccion(tx).nivel(nivelRiesgo).revisada(false).build();
		alertaFraudeRepository.save(alerta);
	}
}