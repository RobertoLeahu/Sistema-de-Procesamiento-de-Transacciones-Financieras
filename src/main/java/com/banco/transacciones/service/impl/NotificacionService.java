package com.banco.transacciones.service.impl;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.banco.transacciones.domain.models.AlertaFraude;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificacionService {

	/**
	 * Envía la notificación de forma asíncrona real pasando por el Proxy de Spring.
	 */
	@Async("transaccionExecutor")
	public void enviarNotificacionAsincrona(AlertaFraude alerta) {
		log.warn("INICIANDO ENVÍO ASÍNCRONO DE ALERTA CRÍTICA - ID: {}", alerta.getId());
		// Lógica de envío (ej. email, SMS, Kafka, etc.)
		
		log.info("Notificación enviada con éxito para alerta: {}", alerta.getId());
	}
}