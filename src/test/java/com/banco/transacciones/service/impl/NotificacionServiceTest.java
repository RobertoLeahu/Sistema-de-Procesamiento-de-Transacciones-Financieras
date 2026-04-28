package com.banco.transacciones.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banco.transacciones.domain.models.AlertaFraude;

/**
 * Clase de pruebas unitarias para la validacion del simulador de notificaciones.
 * Verifica la estabilidad del punto de salida de mensajeria asincrona del sistema,
 * garantizando que el procesamiento de las alertas no genere excepciones no controladas.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

	private NotificacionService notificacionService;

	@BeforeEach
	void setUp() {
		notificacionService = new NotificacionService();
	}

	/**
	 * Comprueba la instanciacion correcta de la entidad de dominio y asegura que el
	 * servicio de mensajeria loguea la informacion de forma segura sin corromper el
	 * hilo de ejecucion ni lanzar excepciones.
	 */
	@Test
	@DisplayName("Debe ejecutar el log de notificación asíncrona sin lanzar excepciones")
	void enviarNotificacionAsincrona_EjecutaCorrectamente() {
		// Arrange: Aprovechamos para cubrir la instanciación de AlertaFraude
		AlertaFraude alerta = AlertaFraude.builder().id(1L).motivo("Alerta Critica Simulada").revisada(false).build();

		// Act & Assert
		// Verificamos que la construcción de la entidad es válida
		assertNotNull(alerta);
		assertEquals(1L, alerta.getId());

		// Verificamos que el servicio puede consumir el objeto sin fallar
		assertDoesNotThrow(() -> notificacionService.enviarNotificacionAsincrona(alerta));
	}
}