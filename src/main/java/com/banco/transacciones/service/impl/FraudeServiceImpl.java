package com.banco.transacciones.service.impl;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banco.transacciones.domain.enums.NivelRiesgo;
import com.banco.transacciones.domain.models.AlertaFraude;
import com.banco.transacciones.dto.response.AlertaFraudeDTO;
import com.banco.transacciones.exception.AlertaNotFoundException;
import com.banco.transacciones.mapper.AlertaFraudeMapper;
import com.banco.transacciones.repository.AlertaFraudeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación de la gestión del ciclo de vida de las alertas generadas por
 * el sistema, la recuperación paginada de alertas sospechosas y la lógica de
 * revisión manual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudeServiceImpl {

	private final AlertaFraudeRepository alertaFraudeRepository;
	private final AlertaFraudeMapper alertaFraudeMapper;

	/**
	 * Retorna alertas no revisadas ordenadas por riesgo (CRITICO primero) y fecha.
	 */
	@Transactional(readOnly = true)
	public Page<AlertaFraudeDTO> obtenerAlertasNoRevisadas(Pageable pageable) {
	    log.info("Consultando alertas de fraude pendientes");
	    return alertaFraudeRepository.findByRevisadaFalse(pageable)
	    		.map(alerta -> alertaFraudeMapper.toDto(alerta));	
	    }

	/**
	 * Marca una alerta como revisada y dispara notificaciones si es nivel CRITICO.
	 */
	@Transactional
	public void revisarAlerta(Long alertaId) {
		String correlationId = MDC.get("correlationId");

		AlertaFraude alerta = alertaFraudeRepository.findById(alertaId)
				.orElseThrow(() -> new AlertaNotFoundException("Alerta no encontrada"));

		alerta.setRevisada(true);
		alertaFraudeRepository.save(alerta);

		log.info("Alerta {} revisada correctamente", alertaId);

		if (NivelRiesgo.CRITICO.equals(alerta.getNivel())) {
			enviarNotificacionAsincrona(alerta, correlationId);
		}
	}

	@Async("transaccionExecutor")
	public void enviarNotificacionAsincrona(AlertaFraude alerta, String correlationId) {
		MDC.put("correlationId", correlationId);
		try {
			log.warn("NOTIFICACIÓN CRÍTICA: Transacción sospechosa detectada ID: {}", alerta.getTransaccion().getId());
		} finally {
			MDC.clear();
		}
	}
}
