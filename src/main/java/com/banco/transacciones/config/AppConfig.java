package com.banco.transacciones.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración general de beans utilitarios y de infraestructura de la aplicación.
 */
@Configuration
public class AppConfig {

	/**
	 * Expone el reloj del sistema como un Bean. 
	 * Esto permite inyectar el tiempo en los servicios (ej. FraudeScoreCalculator)
	 */
	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}